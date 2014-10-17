package poipointer.model;

import org.djodjo.json.JsonObject;
import org.djodjo.json.wrapper.JsonObjectWrapper;

/**
 * Created by sic on 16/10/14.
 */

public class GeoJson extends JsonObjectWrapper {



    public Double getLat() {
        return getJson().getJsonObject("geometry").getJsonArray("coordinates").getDouble(1);
    }

    public Double getLon() {
        return getJson().getJsonObject("geometry").getJsonArray("coordinates").getDouble(0);
    }

    public JsonObject getProperties() {
        return getJson().optJsonObject("properties");
    }

    public GeoJson setGeometry(JsonObject geometry) {
        getJson().put("geometry", geometry);
        return this;
    }

    public GeoJson setProperties(JsonObject properties) {
        getJson().put("properties", properties);
        return this;
    }

    public GeoJson setType(String type) {
        getJson().put("type", type);
        return this;
    }



}
