package cl.induztek.common;

import com.google.gson.JsonParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errores {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> dominio(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).header("Content-Type","application/json").body(Json.dato("message",e.getReason()).toString()); }
    @ExceptionHandler({IllegalArgumentException.class,JsonParseException.class,java.time.DateTimeException.class})
    public ResponseEntity<String> entrada(Exception e) { return ResponseEntity.unprocessableContent().header("Content-Type","application/json").body(Json.dato("message","Entrada inválida: "+e.getMessage()).toString()); }
}
