package cl.induztek.app.data

import cl.induztek.app.model.*
import java.time.LocalDate
import java.time.ZoneId

object Semillas {
    suspend fun cargar(dao: MvpDao) {
        if (dao.clientes().isNotEmpty()) return
        listOf(
            ClienteEntity("cliente-pacifico", "Envasadora del Pacífico S.A.", "76.482.915-8", "Agroindustria", "Marcela Fuentes Rojas", "+56 9 0000 0101", "mfuentes@envpacifico.ejemplo"),
            ClienteEntity("cliente-sanitaria", "Sanitaria Valle Central S.A.", "96.731.540-0", "Servicios sanitarios", "Jorge Arancibia Leiva"),
            ClienteEntity("cliente-minera", "Minera Cerro Alto SpA", "77.105.362-9", "Minería", "Patricio Navarro Soto"),
            ClienteEntity("cliente-clinica", "Clínica Los Aromos Ltda.", "76.903.218-5", "Salud", "Carolina Méndez Pizarro"),
            ClienteEntity("cliente-cementos", "Cementos Lomas Blancas S.A.", "99.524.170-6", "Construcción", "Luis Bravo Castillo"),
            ClienteEntity("cliente-datacenter", "Data Center Andes Norte SpA", "78.214.635-1", "Tecnología", "Felipe Órdenes Vera")
        ).forEach { dao.guardar(it) }
        listOf(
            InstalacionEntity("lampa", "cliente-pacifico", "Planta Lampa", "Sector industrial", "Lampa", "Metropolitana", "Inducción obligatoria de 30 min"),
            InstalacionEntity("sur", "cliente-sanitaria", "Planta Elevadora Sur", "Sector rural poniente", "San Bernardo", "Metropolitana", "Sala eléctrica sin señal"),
            InstalacionEntity("tiltil", "cliente-minera", "Faena Til Til", "Ruta 5 Norte", "Til Til", "Metropolitana", "Sin señal en toda la faena"),
            InstalacionEntity("clinica", "cliente-clinica", "Edificio Clínico", "Zona urbana", "La Florida", "Metropolitana"),
            InstalacionEntity("molienda", "cliente-cementos", "Planta Molienda", "Sector precordillera", "Puente Alto", "Metropolitana"),
            InstalacionEntity("site", "cliente-datacenter", "Site Huechuraba", "Parque empresarial", "Huechuraba", "Metropolitana")
        ).forEach { dao.guardar(it) }
        listOf(
            EquipoEntity("tr01", "lampa", "TR-01", "TRANSFORMADOR", "Rhona", serie = "RH-18733", anio = "2012", placa = "1.500 kVA; 12 / 0,4 kV"),
            EquipoEntity("tr02", "lampa", "TR-02", "TRANSFORMADOR", "Rhona", serie = "RH-21904", anio = "2017", placa = "1.000 kVA; 12 / 0,4 kV"),
            EquipoEntity("tra", "sur", "TR-A", "TRANSFORMADOR", "Tusan", serie = "TS-5521", anio = "2009", placa = "2.500 kVA; 23 / 0,4 kV"),
            EquipoEntity("cel03", "clinica", "CEL-MT-03", "CELDA_MT", "Ormazabal", serie = "OR-77812", anio = "2018", placa = "12 kV; 630 A", maxContactos = 60.0),
            EquipoEntity("int52", "tiltil", "INT-52-1", "INTERRUPTOR", "Schneider", serie = "SE-449021", anio = "2015", placa = "23 kV; 1.250 A", maxContactos = 45.0),
            EquipoEntity("rel50", "molienda", "REL-50/51-A", "RELE", "SEL", "751", "SEL-751-30219", "2020", "Sobrecorriente; ajuste 5 A"),
            EquipoEntity("cab02", "site", "CAB-MT-02", "CABLE", "Nexans", serie = "NX-2201-B", anio = "2019", placa = "15 kV; 3×240 mm²; 180 m"),
            EquipoEntity("trss", "tiltil", "TR-SS-01", "TRANSFORMADOR", "ABB", serie = "ABB-1180442", anio = "2011", placa = "5.000 kVA; 23 / 6,6 kV")
        ).forEach { dao.guardar(it) }
        listOf(
            InstrumentoEntity("meg5", "Megóhmetro 5 kV", "Megger", "MIT525", "MIT-0045821"),
            InstrumentoEntity("meg10", "Megóhmetro 10 kV", "Megger", "MIT1025", "MIT-0071233"),
            InstrumentoEntity("ttr", "Medidor TTR", "Megger", "TTR300", "TTR-30044"),
            InstrumentoEntity("micro", "Micro-ohmímetro", "Megger", "DLRO10X", "DLR-10882"),
            InstrumentoEntity("inyeccion", "Inyección secundaria", "Omicron", "CMC 356", "CMC-HB411")
        ).forEach { dao.guardar(it) }
        listOf(
            CalibracionEntity("cal-meg5", "meg5", "2026-03-15", "2027-03-15"),
            CalibracionEntity("cal-meg10", "meg10", "2025-10-10", "2026-10-10"),
            CalibracionEntity("cal-ttr", "ttr", "2026-06-02", "2027-06-02"),
            CalibracionEntity("cal-micro", "micro", "2025-08-20", "2026-08-20"),
            CalibracionEntity("cal-inyeccion", "inyeccion", "2026-01-05", "2027-01-05")
        ).forEach { dao.guardar(it) }
        val ejemplos = listOf(
            listOf("2024-09-14", "PT-2024-0312", "12500", "31250", "tecnico1"),
            listOf("2025-09-13", "PT-2025-0288", "9800", "21560", "tecnico2"),
            listOf("2026-09-26", "PT-2026-0401", "6100", "10980", "tecnico1")
        )
        for ((indice, e) in ejemplos.withIndex()) {
            val fecha = LocalDate.parse(e[0]).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val id = "ejemplo-$indice"
            val estado = if (indice < 2) "FORMALIZADO" else "REGISTRADO_TERRENO"
            dao.guardar(VisitaEntity("visita-$id", "lampa", if (indice == 2) "OT-2026-1187" else "OT-EJEMPLO-$indice", e[0], "ingeniero1", "SOLO_PRUEBAS", e[4]))
            dao.guardar(ParticipanteEntity("visita-$id", e[4]))
            dao.guardar(ProtocoloEntity(id, e[1], "visita-$id", "tr01", e[4], fecha, 19.0, 60.0, estado))
            val evaluacion = ReglasAislacion.evaluar(e[2].toDouble(), e[3].toDouble())
            dao.guardar(PruebaEntity("prueba-$id", id, "AISLACION", "AT-T", "UNICA", true, "", "meg5", evaluacion.resultado.name, "IP: ${evaluacion.indice}"))
            dao.guardar(MedicionEntity("prueba-$id", "tension", 5.0, "kV"))
            dao.guardar(MedicionEntity("prueba-$id", "lectura1", e[2].toDouble(), "MΩ"))
            dao.guardar(MedicionEntity("prueba-$id", "lectura10", e[3].toDouble(), "MΩ"))
            dao.guardar(HistorialEstadoEntity("historia-$id", id, null, estado, if (indice < 2) "ingeniero1" else e[4], fecha, "Detalle disponible: aislación AT-T"))
        }
    }
}
