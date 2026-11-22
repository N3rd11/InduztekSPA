package cl.induztek.catalog;

import cl.induztek.common.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api", produces = "application/json")
public class CatalogController {
    private final CatalogService servicio;
    public CatalogController(CatalogService servicio) { this.servicio = servicio; }
    @GetMapping({"/session", "/servicios/catalogos"})
    public String sesion(Authentication auth) { return Json.GSON.toJson(Usuarios.actual(auth)); }
    @GetMapping("/snapshot") public String snapshot(Authentication auth) { return servicio.snapshot(Usuarios.actual(auth)).toString(); }
    @GetMapping("/clientes/{id}") public String cliente(Authentication auth, @PathVariable("id") String id) {
        return servicio.cliente(Usuarios.actual(auth), id).toString();
    }
    @PostMapping(value = "/events", consumes = "application/json")
    public String evento(Authentication auth, @RequestBody String cuerpo) {
        var evento = Json.obj(cuerpo); servicio.aplicar(Usuarios.actual(auth),evento); return Json.dato("id",Json.s(evento,"id")).toString();
    }
}
