package cl.induztek.protocol;

import cl.induztek.common.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api", produces = "application/json")
public class ProtocolController {
    private final ProtocolService servicio;
    public ProtocolController(ProtocolService servicio) { this.servicio = servicio; }
    @GetMapping("/servicios/protocolos")
    public String sesion(Authentication auth) { return Json.GSON.toJson(Usuarios.actual(auth)); }
    @GetMapping("/snapshot") public String snapshot(Authentication auth, @RequestHeader("Authorization") String authorization) { return servicio.snapshot(Usuarios.actual(auth),authorization).toString(); }
    @GetMapping("/actas/{visitaId}")
    public String acta(Authentication auth, @RequestHeader("Authorization") String authorization, @PathVariable String visitaId) {
        return servicio.acta(Usuarios.actual(auth), authorization, visitaId).toString();
    }
    @PostMapping(value = "/events", consumes = "application/json")
    public String evento(Authentication auth, @RequestHeader("Authorization") String authorization, @RequestBody String cuerpo) {
        var evento = Json.obj(cuerpo); servicio.aplicar(Usuarios.actual(auth),authorization,evento); return Json.dato("id",Json.s(evento,"id")).toString();
    }
}
