package poipointer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.djodjo.json.JsonArray;
import org.djodjo.json.JsonElement;
import org.djodjo.json.JsonObject;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import poipointer.model.GeoJson;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

/**
 * Created by sic on 17/10/14.
 */
public class OpenDataSync {


    static TransportClient tc= null;

    static final String indexName = "poipointer";
    static final String documentType = "poi";

    static String baseURIopenDataBrussels = "http://opendata.brussels.be/api/records/1.0/search?rows=10000&dataset=";
    static String baseURIurbis = "http://geoserver.gis.irisnet.be/geoserver/urbis/wfs?service=WFS&version=1.1.0&request=GetFeature&typeName=URB_M_ZIPOINT&CQL_FILTER=TYPE=%27CU%27&outputFormat=json";
    static HashMap<String, String> typeMap = new HashMap<String, String>();
    static HashMap<String, String> nameMap = new HashMap<String, String>();


    static {
        nameMap.put("theatres", "description");
        nameMap.put("museums0", "museum");
        nameMap.put("cultural-places", "cultural_place");
        nameMap.put("comic-book-route", "character_author");
        nameMap.put("art-heritage-of-regional-roads-monuments", "fr_name");
        nameMap.put("art-heritage-of-regional-roads-fountains0", "fr_name");

        typeMap.put("theatres", "theatre");
        typeMap.put("museums0", "museum");
        typeMap.put("cultural-places", "culturalPlace");
        typeMap.put("comic-book-route", "comic");
        typeMap.put("art-heritage-of-regional-roads-monuments", "monument");
        typeMap.put("art-heritage-of-regional-roads-fountains0", "fountain");
    }

    public static void main(String[] args) {
        tc = new TransportClient(ImmutableSettings.settingsBuilder().build());
        tc.addTransportAddress(new InetSocketTransportAddress("localhost",
                9300));
        List<DiscoveryNode> nodes = tc.listedNodes();
        System.out.println("NODES: ");
        for (DiscoveryNode n : nodes) {
            System.out.println("node: " + n);
        }
        List<DiscoveryNode> nodes2 = tc.connectedNodes();
        System.out.println("CONNECTED NODES: ");
        for (DiscoveryNode n : nodes2) {
            System.out.println("node: " + n);
        }

        //--set geo mapping
       final IndicesExistsResponse res = tc.admin().indices().prepareExists(indexName).execute().actionGet();
       if (!res.isExists())
        {
            CreateIndexRequestBuilder cirb =  tc.admin().indices().prepareCreate(indexName);
            for(String dataset:typeMap.values()) {
                cirb.addMapping(dataset,"{\"properties\":{\"geometry\":{\n" +
                        "\"properties\":{\"coordinates\":{\n" +
                        "\"type\": \"geo_point\"\n" +
                        "}}}}}");
            }
            cirb.execute().actionGet();
        }

        for(String dataset:typeMap.keySet()) {
            processOpendataBrusselsDataSet(dataset);
        }

        processUrbisDataSet();

    }

    private static void processOpendataBrusselsDataSet(String dataSet) {
        System.out.println("Processing: " + dataSet);
        try {
            HttpURLConnection conn = (HttpURLConnection)new URL(baseURIopenDataBrussels+dataSet).openConnection();
            BufferedReader in;
            if(conn.getResponseCode()>299) {
                in = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream()));
            } else {
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
            }
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            JsonObject jsonResult = JsonElement.readFrom(response.toString()).asJsonObject();
            System.out.println("Result: " + jsonResult.toString());
            //-- process the array results to right geojson format as specified: http://geojson.org/geojson-spec.html
            JsonArray results =  jsonResult.getJsonArray("records");
            for(JsonElement feature:results) {
                GeoJson geoJson = new GeoJson()
                        .setType("Feature")
                        .setGeometry(feature.asJsonObject().optJsonObject("geometry"))
                        .setProperties(feature.asJsonObject().optJsonObject("fields"));


                geoJson.getProperties()
                        .put("type", typeMap.get(dataSet))
                        .put("name", feature.asJsonObject().optJsonObject("fields").get(nameMap.get(dataSet)))
                        .put("recordId", feature.asJsonObject().optString("recordid"))
                        .put("recordTimestamp", feature.asJsonObject().optString("record_timestamp"));
                System.out.println("Result: " + geoJson.toString());

                //-- put to ES
                writeObject2es(geoJson.getProperties().getString("name"), geoJson.getProperties().getString("type"), geoJson.toString());
            }



        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static JsonArray getWGS84fromEPSG31370(JsonArray coordinates) {
        JsonArray res =  new JsonArray();
        try {
            System.out.println("Coords transform from:" + coordinates);
            GeometryFactory gf = new GeometryFactory();
            Coordinate c = new Coordinate(coordinates.getDouble(1), coordinates.getDouble(0));

            Point p = gf.createPoint(c);

            CoordinateReferenceSystem utmCrs = null;

            utmCrs = CRS.decode("EPSG:31370");

            MathTransform mathTransform = CRS.findMathTransform(utmCrs, DefaultGeographicCRS.WGS84, false);
            Point p1 = (Point) JTS.transform(p, mathTransform);
            res.put(p1.getCoordinate().y).put(p1.getCoordinate().x);

            System.out.println("Coords transform to:" + res);
        } catch (FactoryException e) {
            e.printStackTrace();
        } catch (TransformException e) {
            e.printStackTrace();
        }
        return res;
    }

    private static void processUrbisDataSet() {
        System.out.println("Processing: urbis");
        try {
            HttpURLConnection conn = (HttpURLConnection)new URL(baseURIurbis).openConnection();
            BufferedReader in;
            if(conn.getResponseCode()>299) {
                in = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream()));
            } else {
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
            }
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            JsonObject jsonResult = JsonElement.readFrom(response.toString()).asJsonObject();
            System.out.println("Result: " + jsonResult.toString());
            //-- process the array results to right geojson format as specified: http://geojson.org/geojson-spec.html
            JsonArray results =  jsonResult.getJsonArray("features");
            for(JsonElement feature:results) {
                GeoJson geoJson = new GeoJson()
                        .setType("Feature")
                        .setGeometry(feature.asJsonObject().optJsonObject("geometry"))
                        .setProperties(feature.asJsonObject().optJsonObject("properties"));

                geoJson.setCoordinates(getWGS84fromEPSG31370(geoJson.getCoordinates()));

                geoJson.getProperties()
                        .put("type", "culturalPlace")
                        .put("name", feature.asJsonObject().optJsonObject("properties").get("TXT_FRE").toString().toLowerCase())
                        .put("recordId", feature.asJsonObject().optString("id"))
                ;
                System.out.println("Result: " + geoJson.toString());

                //-- put to ES
                writeObject2es(geoJson.getProperties().getString("name"), geoJson.getProperties().getString("type"), geoJson.toString());
            }



        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void writeObject2es(String objectId, String objectType, String jsonObject) {
        if(objectId==null || objectId.isEmpty() || jsonObject==null) return;
        //
        IndexRequestBuilder irb = tc
                .prepareIndex(indexName, objectType, objectId);
        irb.setSource(jsonObject);

        final ListenableActionFuture<IndexResponse> laf = irb .execute();
        final IndexResponse response =     laf.actionGet();
        System.out.println( "======= Written idx:" + response.getId() + " :: " + response.getIndex() + " :: " + response.getType() + " :: " + response.getVersion() + " =======");
    }


    private static void putURLs() {

    }

    private static void putIMGs() {

    }




    private static String addName(GeoJson geoJson) {
        String res = null;

        return res;
    }



}
