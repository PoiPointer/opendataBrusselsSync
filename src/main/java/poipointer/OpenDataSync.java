package poipointer;

import org.djodjo.json.JsonArray;
import org.djodjo.json.JsonElement;
import org.djodjo.json.JsonObject;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import poipointer.model.GeoJson;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;

/**
 * Created by sic on 17/10/14.
 */
public class OpenDataSync {


    //TODO use WS to automate the data sets download
    static TransportClient tc= null;

    static final String indexName = "poipointer";
    static final String documentType = "poi";

    static String baseURI = "http://opendata.brussels.be/api/records/1.0/search?rows=10000&dataset=";
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
        typeMap.put("comic-book-route", "comicBookRoute");
        typeMap.put("art-heritage-of-regional-roads-monuments", "heritageMonument");
        typeMap.put("art-heritage-of-regional-roads-fountains0", "heritageFountain");
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


        for(String dataset:typeMap.keySet()) {
            processDataSet(dataset);
        }



    }


    private static void processDataSet(String dataSet) {
        System.out.println("Processing: " + dataSet);
        try {
            HttpURLConnection conn = (HttpURLConnection)new URL(baseURI+dataSet).openConnection();
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


    private static String addName(GeoJson geoJson) {
        String res = null;

        return res;
    }



}
