package cl.induztek.access;

import cl.induztek.common.Json;
import cl.induztek.common.Usuarios;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController {
    @GetMapping(value = "/api/session", produces = "application/json")
    String sesion(Authentication autenticacion) {
        return Json.GSON.toJson(Usuarios.actual(autenticacion));
    }
}
