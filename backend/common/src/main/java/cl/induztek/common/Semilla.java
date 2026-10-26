package cl.induztek.common;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class Semilla {
    public static JsonObject catalogos() {
        try (var entrada = new InputStreamReader(Semilla.class.getResourceAsStream("/semillas.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(entrada).getAsJsonObject();
        } catch (Exception e) { throw new IllegalStateException("No se pudo leer el conjunto ficticio",e); }
    }
    public static JsonObject protocolos() {
        var resultado = new JsonObject();
        for (var tabla : List.of("visitas","participantes","protocolos","pruebas","mediciones","historial")) resultado.add(tabla, new JsonArray());
        String[][] ejemplos = {{"2024-09-14","PT-2024-0312","12500","31250","tecnico1"}, {"2025-09-13","PT-2025-0288","9800","21560","tecnico2"}, {"2026-09-26","PT-2026-0401","6100","10980","tecnico1"}};
        for (int i = 0; i < ejemplos.length; i++) {
            var e = ejemplos[i]; var id = "ejemplo-"+i; var visitaId = "visita-"+id;
            long fecha = LocalDate.parse(e[0]).atStartOfDay(ZoneId.of("America/Santiago")).toInstant().toEpochMilli();
            String estado = i < 2 ? "FORMALIZADO" : "REGISTRADO_TERRENO";
            double uno = Double.parseDouble(e[2]), diez = Double.parseDouble(e[3]);
            resultado.getAsJsonArray("visitas").add(Json.dato("id",visitaId,"instalacionId","lampa","ot",i==2?"OT-2026-1187":"OT-EJEMPLO-"+i,"fecha",e[0],"ingenieroId","ingeniero1","tipo","SOLO_PRUEBAS","creadorId",e[4],"receptor","","receptorRut","","receptorCargo","","acta",""));
            resultado.getAsJsonArray("participantes").add(Json.dato("visitaId",visitaId,"usuarioId",e[4]));
            resultado.getAsJsonArray("protocolos").add(Json.dato("id",id,"numero",e[1],"visitaId",visitaId,"equipoId","tr01","autorId",e[4],"fecha",fecha,"temperatura",19,"humedad",60,"estado",estado,"mantenimiento","","comentario","","revision",0));
            resultado.getAsJsonArray("pruebas").add(Json.dato("id","prueba-"+id,"protocoloId",id,"tipo","AISLACION","elemento","AT-T","momento","UNICA","realizada",true,"motivo","","instrumentoId","meg5","resultado",i<2?"APROBADO":"OBSERVADO","detalle","IP: "+diez/uno,"comentario","","opera",true));
            resultado.getAsJsonArray("mediciones").add(Json.dato("pruebaId","prueba-"+id,"codigo","tension","valor",5,"unidad","kV"));
            resultado.getAsJsonArray("mediciones").add(Json.dato("pruebaId","prueba-"+id,"codigo","lectura1","valor",uno,"unidad","MΩ"));
            resultado.getAsJsonArray("mediciones").add(Json.dato("pruebaId","prueba-"+id,"codigo","lectura10","valor",diez,"unidad","MΩ"));
            resultado.getAsJsonArray("historial").add(Json.dato("id","historia-"+id,"protocoloId",id,"nuevo",estado,"usuarioId",i<2?"ingeniero1":e[4],"fecha",fecha,"motivo","Dato ficticio del Anexo 9; detalle disponible: aislación AT-T"));
        }
        return resultado;
    }
    public static void cargar(Documentos db, JsonObject datos) {
        datos.entrySet().forEach(entrada -> entrada.getValue().getAsJsonArray().forEach(valor -> {
            var o = valor.getAsJsonObject(); var id = Json.s(o,"id");
            if (id.isEmpty()) id = o.has("pruebaId") ? Json.s(o,"pruebaId")+":"+Json.s(o,"codigo") : Json.s(o,"visitaId")+":"+Json.s(o,"usuarioId");
            db.guardar(entrada.getKey(), id, o);
        }));
    }
    private Semilla() {}
}
