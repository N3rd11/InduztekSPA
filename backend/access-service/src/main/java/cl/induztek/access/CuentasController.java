package cl.induztek.access;

import cl.induztek.common.Json;
import cl.induztek.common.Usuarios;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

@RestController
class CuentasController {
    private final Cuentas cuentas;
    private final RestClient catalogo;

    CuentasController(Cuentas cuentas, @Value("${induztek.catalog-url}") String url) {
        this.cuentas = cuentas;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(4000);
        catalogo = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @PostMapping("/api/registro")
    @ResponseStatus(HttpStatus.CREATED)
    void registrar(@RequestBody Cuentas.Alta alta) {
        cuentas.registrar(alta);
    }

    @GetMapping(value = "/api/personal", produces = "application/json")
    String personal(Authentication autenticacion) {
        Json.permiso(Usuarios.actual(autenticacion).interno());
        return Json.GSON.toJson(cuentas.personal());
    }

    @GetMapping(value = "/api/cuentas", produces = "application/json")
    String listar(Authentication autenticacion) {
        exigirJefe(autenticacion);
        return Json.GSON.toJson(cuentas.listar());
    }

    record AltaCuenta(String nombre, String correo, String rut, String clave, String clienteId, String rol) {}
    record PermisosCuenta(String rol, String clienteId, Boolean activo) {}

    @PostMapping(value = "/api/cuentas", produces = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    String crearCuenta(Authentication autenticacion, @RequestHeader("Authorization") String autorizacion,
                       @RequestBody AltaCuenta solicitud) {
        exigirJefe(autenticacion);
        var alta = new Cuentas.Alta(solicitud.nombre(), solicitud.correo(), solicitud.rut(), solicitud.clave(), solicitud.clienteId());
        cuentas.validarDatos(alta);
        String empresa = validarPerfil(solicitud.rol(), solicitud.clienteId(), autorizacion);
        return Json.GSON.toJson(cuentas.crear(alta, solicitud.rol(), empresa));
    }

    @PatchMapping(value = "/api/cuentas/{id}/permisos", produces = "application/json")
    String modificar(Authentication autenticacion, @RequestHeader("Authorization") String autorizacion,
                     @PathVariable("id") String id, @RequestBody PermisosCuenta cambios) {
        exigirJefe(autenticacion);
        Json.exigir(cambios.activo() != null, "Indica si la cuenta está activa");
        String empresa = validarPerfil(cambios.rol(), cambios.clienteId(), autorizacion);
        return Json.GSON.toJson(cuentas.modificar(Usuarios.actual(autenticacion).id(), id,
            cambios.rol(), empresa, cambios.activo()));
    }

    private String validarPerfil(String rol, String clienteId, String autorizacion) {
        Json.exigir(rol != null && Set.of("TECNICO", "INGENIERO", "JEFE_TECNICO", "CLIENTE").contains(rol), "Rol inválido");
        if (!"CLIENTE".equals(rol)) return null;
        String empresa = clienteId == null ? "" : clienteId.strip();
        Json.exigir(!empresa.isEmpty() && empresa.length() <= 150, "Selecciona una empresa existente");
        exigirEmpresa(empresa, autorizacion);
        return empresa;
    }

    @GetMapping(value = "/api/cuentas/clientes", produces = "application/json")
    String clientes(Authentication autenticacion) {
        exigirJefe(autenticacion);
        return Json.GSON.toJson(cuentas.clientes());
    }

    @PostMapping(value = "/api/cuentas/clientes", produces = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    String crear(Authentication autenticacion, @RequestHeader("Authorization") String autorizacion,
                 @RequestBody Cuentas.Alta alta) {
        exigirJefe(autenticacion);
        cuentas.validarAlta(alta);
        exigirEmpresa(alta.clienteId().strip(), autorizacion);
        return Json.GSON.toJson(cuentas.crear(alta));
    }

    private void exigirEmpresa(String empresa, String autorizacion) {
        try {
            catalogo.get().uri("/api/clientes/{id}", empresa)
                .header("Authorization", autorizacion).retrieve().toBodilessEntity();
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La empresa no existe");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogos no disponible");
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogos no disponible");
        }
    }

    record Estado(Boolean activo, String clienteId) {}

    @PatchMapping("/api/cuentas/clientes/{id}/estado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void estado(Authentication autenticacion, @PathVariable("id") String id,
                @RequestHeader("Authorization") String autorizacion, @RequestBody Estado estado) {
        exigirJefe(autenticacion);
        Json.exigir(estado.activo() != null, "Indica si la cuenta está activa");
        String empresa = estado.clienteId() == null ? null : estado.clienteId().strip();
        if (empresa != null) {
            Json.exigir(estado.activo() && !empresa.isEmpty() && empresa.length() <= 150, "Selecciona una empresa existente");
            exigirEmpresa(empresa, autorizacion);
        }
        cuentas.estado(id, estado.activo(), empresa);
    }

    private void exigirJefe(Authentication autenticacion) {
        Json.permiso("JEFE_TECNICO".equals(Usuarios.actual(autenticacion).rol()));
    }
}
