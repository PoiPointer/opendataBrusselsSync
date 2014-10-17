opendataBrusselsSync
====================

used to sync with http://opendata.brussels.be

Info
--
elastic search needs to be set and running


Query Distance Example
--
curl -XPOST 'http://127.0.0.1:9200/poipointer/_search' -d '
{
    "query" : {
"filtered": {
        "filter" : {
            "geo_distance" : {
                "distance" :"1km",
                "geometry.coordinates" : [4.351286,50.8553361]
            }
        }
    }
}
}
'
