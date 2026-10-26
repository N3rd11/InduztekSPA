package cl.induztek.protocol;

import java.util.*;
import static cl.induztek.common.Json.*;

public final class ReglasElectricas {
    public static List<String> tipos(String equipo) {
        return switch (equipo) {
            case "TRANSFORMADOR" -> List.of("AISLACION","TTR","DEVANADOS");
            case "INTERRUPTOR","CELDA_MT" -> List.of("AISLACION","CONTACTOS");
            case "CABLE" -> List.of("AISLACION");
            case "RELE" -> List.of("OPERACION_RELE");
            default -> throw new IllegalArgumentException("Tipo de equipo inválido");
        };
    }
    public static List<String> elementos(String equipo, String prueba) {
        return switch (prueba) {
            case "AISLACION" -> equipo.equals("TRANSFORMADOR") ? List.of("AT-BT","AT-T","BT-T") : List.of("Fase U","Fase V","Fase W");
            case "TTR" -> List.of("H1-X1","H2-X2","H3-X3");
            case "DEVANADOS" -> List.of("AT-U","AT-V","AT-W","BT-U","BT-V","BT-W");
            case "CONTACTOS" -> List.of("Polo 1","Polo 2","Polo 3");
            case "OPERACION_RELE" -> List.of("50/51");
            default -> throw new IllegalArgumentException("Prueba inválida");
        };
    }
    public static Map<String,String> parametros(String equipo, String prueba) {
        return switch (prueba) {
            case "AISLACION" -> equipo.equals("TRANSFORMADOR") || equipo.equals("CABLE") ? Map.of("tension","kV","lectura1","MΩ","lectura10","MΩ") : Map.of("tension","kV","lectura1","MΩ");
            case "TTR" -> Map.of("teorica","","medida","","tap","");
            case "DEVANADOS" -> Map.of("resistencia","mΩ");
            case "CONTACTOS" -> Map.of("resistencia","µΩ");
            case "OPERACION_RELE" -> Map.of("ajuste","A","corriente","A","esperado","ms","tiempo","ms");
            default -> throw new IllegalArgumentException("Prueba inválida");
        };
    }
    public static String evaluar(String equipo, String prueba, Map<String,Double> valores, double maximo, boolean opera) {
        exigir(tipos(equipo).contains(prueba),"Prueba no aplicable");
        if (prueba.equals("OPERACION_RELE") && !opera) return "RECHAZADO";
        parametros(equipo,prueba).keySet().forEach(k -> exigir(valores.containsKey(k) && Double.isFinite(valores.get(k)) && valores.get(k)>0,"Valor inválido o incompleto: "+k));
        if (valores.containsKey("tap")) exigir(valores.get("tap")%1==0,"Tap debe ser entero");
        return switch (prueba) {
            case "AISLACION" -> {
                if (!valores.containsKey("lectura10")) yield null;
                double ip = valores.get("lectura10")/valores.get("lectura1");
                exigir(Double.isFinite(ip),"Índice fuera de rango");
                yield valores.get("lectura1")<100 || ip<1 ? "RECHAZADO" : ip<2 ? "OBSERVADO" : "APROBADO";
            }
            case "TTR" -> clasificar(Math.abs(valores.get("medida")-valores.get("teorica"))/valores.get("teorica")*100,0.5,1);
            case "CONTACTOS" -> { exigir(Double.isFinite(maximo) && maximo>0,"Máximo de placa inválido"); yield clasificar(valores.get("resistencia")/maximo*100,100,120); }
            case "OPERACION_RELE" -> clasificar(Math.abs(valores.get("tiempo")-valores.get("esperado"))/valores.get("esperado")*100,5,10);
            default -> null;
        };
    }
    private static String clasificar(double valor, double aprobado, double observado) {
        exigir(Double.isFinite(valor),"Resultado fuera de rango");
        return valor<=aprobado+1e-9 ? "APROBADO" : valor<=observado+1e-9 ? "OBSERVADO" : "RECHAZADO";
    }
    private ReglasElectricas() {}
}
