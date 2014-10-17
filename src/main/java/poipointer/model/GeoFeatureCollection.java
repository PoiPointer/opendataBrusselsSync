package poipointer.model;

import org.djodjo.json.wrapper.JsonObjectArrayWrapper;
import org.djodjo.json.wrapper.JsonObjectWrapper;

import java.util.ArrayList;

/**
 * Created by sic on 16/10/14.
 */
public class GeoFeatureCollection extends JsonObjectWrapper {


    public ArrayList<GeoJson> getFeatures() {
        return new JsonObjectArrayWrapper<GeoJson>().wrap(this.getJson().optJsonArray("features"), GeoJson.class).getJsonWrappersList();
    }

}
