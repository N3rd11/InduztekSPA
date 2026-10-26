package cl.induztek.common;

import com.google.gson.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Json {
    public static final Gson GSON = new Gson();
    public static JsonObject obj(String texto) { return JsonParser.parseString(texto).getAsJsonObject(); }
    public static String s(JsonObject o, String campo) { return !o.has(campo) || o.get(campo).isJsonNull() ? "" : o.get(campo).getAsString(); }
    public static long l(JsonObject o, String campo) { return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsLong() : 0; }
    public static double d(JsonObject o, String campo) { return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsDouble() : Double.NaN; }
    public static boolean b(JsonObject o, String campo) { return o.has(campo) && !o.get(campo).isJsonNull() && o.get(campo).getAsBoolean(); }
    public static void exigir(boolean condicion, String mensaje) { if (!condicion) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje); }
    public static void permiso(boolean condicion) { if (!condicion) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso"); }
    public static JsonObject dato(Object... pares) {
        var o = new JsonObject();
        for (int i = 0; i < pares.length; i += 2) o.add(pares[i].toString(), GSON.toJsonTree(pares[i+1]));
        return o;
    }
    public static boolean rut(String texto) {
        var rut = texto.replace(".", "").replace("-", "").trim().toUpperCase();
        if (!rut.matches("[0-9]{7,8}[0-9K]") || rut.substring(0, rut.length()-1).matches("0+")) return false;
        int suma = 0, factor = 2;
        for (int i = rut.length()-2; i >= 0; i--) { suma += (rut.charAt(i)-'0') * factor; factor = factor == 7 ? 2 : factor+1; }
        int dv = 11 - suma % 11;
        String esperado = dv == 11 ? "0" : dv == 10 ? "K" : String.valueOf(dv);
        return esperado.equals(rut.substring(rut.length()-1));
    }
    private Json() {}
}
