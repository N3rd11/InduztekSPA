package cl.induztek.protocol;

import com.google.gson.JsonObject;

public interface CatalogGateway { JsonObject snapshot(String authorization); }
