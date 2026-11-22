package cl.induztek.app

import cl.induztek.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PortalClienteTest {
    @Test fun limitaLaConsultaAEmpresaYSeparaActasDeFormalizacion() {
        val catalogo = EstadoLocal(
            clientes = listOf(ClienteEntity("propia", "Empresa del cliente", ""), ClienteEntity("ajena", "Otra empresa", "")),
            instalaciones = listOf(InstalacionEntity("uno", "propia", "Planta propia"), InstalacionEntity("dos", "ajena", "Planta ajena")),
            equipos = listOf(EquipoEntity("e1", "uno", "TR-01", "TRANSFORMADOR"), EquipoEntity("e2", "dos", "TR-02", "TRANSFORMADOR"))
        )
        val abierta = VisitaEntity("a", "uno", "OT-01", "2026-11-22", "", "SOLO_PRUEBAS", "")
        val revision = abierta.copy(id = "r", ot = "OT-02", fecha = "2026-11-21", cierre = 100, acta = "Acta en terreno")
        val formalizada = revision.copy(id = "f", ot = "OT-03", fecha = "2026-11-20")
        val cerradaSinActa = formalizada.copy(id = "c", ot = "OT-04", acta = "")
        val visitaAjena = formalizada.copy(id = "x", ot = "OT-OTRA", instalacionId = "dos")
        val protocolo = ProtocoloEntity("p", "P-01", formalizada.id, "e1", "", 100, estado = "FORMALIZADO")
        val estado = EstadoLocal(
            visitas = listOf(visitaAjena, formalizada, revision, abierta, cerradaSinActa),
            protocolos = listOf(protocolo, protocolo.copy(id = "b", estado = "BORRADOR"),
                protocolo.copy(id = "n", estado = "ANULADO"), protocolo.copy(id = "r", visitaId = revision.id, estado = "OBSERVADO"),
                protocolo.copy(id = "x", visitaId = visitaAjena.id, equipoId = "e2")),
            pruebas = listOf(PruebaEntity("prueba", protocolo.id, "AISLACION", "TR", "UNICA", true, "", null, "APROBADO", ""))
        )
        val datos = datosPortalCliente("propia", catalogo, estado)
        assertEquals("Empresa del cliente", datos.empresa)
        assertEquals(listOf("TR-01"), datos.equipos.map { it.equipo.tag })
        assertEquals(listOf("OT-01", "OT-02", "OT-03", "OT-04"), datos.trabajos.map { it.visita.ot })
        assertEquals(listOf(AvanceCliente.EN_CURSO, AvanceCliente.EN_REVISION, AvanceCliente.FORMALIZADA, AvanceCliente.CERRADA),
            datos.trabajos.map { it.avance })
        assertEquals(listOf(false, true, true, false), datos.trabajos.map { it.tieneActa })
        assertNull(datos.trabajos[1].protocolos.single().resultado)
        val resultado = datos.trabajos[2].protocolos.single()
        assertEquals("APROBADO", resultado.resultado)
        assertEquals(listOf("Resistencia de aislación"), resultado.pruebasRealizadas)
    }
}
