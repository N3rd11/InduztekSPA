package cl.induztek.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.ContentValues
import android.content.Context
import androidx.test.espresso.Espresso
import org.hamcrest.Matchers.anyOf
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import java.io.File
import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.graphics.toPixelMap

class AccesoNavigationTest {
    @get:Rule(order = 0) val permisos: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    )
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun entrar(correo: String, clave: String = "1234") {
        compose.onNodeWithText("Correo electrónico").performTextInput(correo)
        compose.onNodeWithText("Contraseña", substring = false).performTextInput(clave)
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasText("Panel principal") or hasText("Documentos de mi empresa"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("No abrió el panel para $correo\n${compose.onRoot().printToString()}", e)
        }
    }

    @Test @RequiereBackend fun tecnicoAccedeACamaraYVisitas() {
        entrar("mgodoy@induztek.ejemplo")
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        compose.onNodeWithText("Tomar foto de perfil").assertExists()
        compose.onNodeWithText("Elegir de la galería").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Visitas y actas de terreno").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Crear visita").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crear visita").assertIsDisplayed()
    }

    @Test @RequiereBackend fun clienteSoloVeEquiposDeSuEmpresa() {
        entrar("mfuentes@envpacifico.ejemplo")
        compose.onNodeWithText("Mis equipos").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("TR-01").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("TR-01", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("CEL-MT-03", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Agregar Equipo").assertDoesNotExist()
    }

    @Test @RequiereBackend fun jefeCreaCuentaYClienteEntraConSuPropiaClave() {
        val sufijo = System.nanoTime().toString().takeLast(7)
        val cuerpo = "3$sufijo"
        val suma = cuerpo.reversed().mapIndexed { i, c -> c.digitToInt() * (2 + i % 6) }.sum()
        val dv = when (val valor = 11 - suma % 11) { 11 -> "0"; 10 -> "K"; else -> valor.toString() }
        val correo = "cliente.$sufijo@induztek.ejemplo"
        val clave = "DemoAlta-$sufijo"
        entrar("respinoza@induztek.ejemplo")
        compose.onNodeWithText("Administrar cuentas").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Seleccionar empresa").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nombre", substring = false).performTextInput("Cuenta ficticia $sufijo")
        compose.onNodeWithText("Correo", substring = false).performTextInput(correo)
        compose.onNodeWithText("RUT", substring = false).performTextInput("$cuerpo-$dv")
        compose.onNodeWithText("Contraseña", substring = false).performTextInput(clave)
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Seleccionar empresa").performClick()
        compose.onNode(hasText("Envasadora del Pacífico S.A.") and hasClickAction()).performClick()
        compose.onNodeWithText("Crear cuenta cliente").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Cuenta cliente creada.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        entrar(correo, clave)
        compose.onNodeWithText("Administrar cuentas").assertDoesNotExist()
        compose.onNodeWithText("Mis equipos").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("TR-01", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("CEL-MT-03", substring = true)).assertCountEquals(0)
    }

    @Test @RequiereBackend fun resultadoDeCamaraSeGuardaEnLaRutaDePerfilExistente() {
        val contextoAnterior = InstrumentationRegistry.getInstrumentation().targetContext
        val archivoAnterior = File(contextoAnterior.filesDir, "foto_tecnico1.jpg")
        val copiaAnterior = archivoAnterior.takeIf { it.exists() }?.readBytes()
        Intents.init()
        try {
            val imagen = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(220,40,30)) }
            Intents.intending(IntentMatchers.hasAction(MediaStore.ACTION_IMAGE_CAPTURE))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().putExtra("data", imagen)))
            entrar("mgodoy@induztek.ejemplo")
            compose.onNodeWithContentDescription("Mi perfil").performClick()
            compose.onNodeWithText("Tomar foto de perfil").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Fotografía guardada").fetchSemanticsNodes().isNotEmpty() }
            val contexto = InstrumentationRegistry.getInstrumentation().targetContext
            val archivo = File(contexto.filesDir, "foto_tecnico1.jpg")
            assertTrue(archivo.exists())
            val guardada = BitmapFactory.decodeFile(archivo.absolutePath)
            assertEquals(64,guardada.width)
            assertEquals(64,guardada.height)
            guardada.recycle()
            compose.onNodeWithContentDescription("Volver").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("foto-inicio", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            val avatar = compose.onNodeWithTag("foto-inicio", useUnmergedTree = true).captureToImage().toPixelMap()
            val centro = avatar[avatar.width / 2, avatar.height / 2]
            assertTrue("El avatar del inicio debe mostrar la foto recién tomada", centro.red > 0.7f && centro.green < 0.3f)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("foto-inicio", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("foto-inicio", useUnmergedTree = true).assertIsDisplayed()
        } finally { Intents.release(); if (copiaAnterior != null) archivoAnterior.writeBytes(copiaAnterior) }
    }
    @Test fun loginMuestraValidacionesPorCampo() {
        compose.onNodeWithText("Seleccionar usuario").assertDoesNotExist()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Ingresa tu correo").assertExists()
        compose.onNodeWithText("Ingresa tu contraseña").assertExists()
        compose.onNodeWithText("Correo electrónico").performTextInput("correo incorrecto")
        compose.onNodeWithText("Contraseña", substring = false).performTextInput("1234")
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Ingresa un correo válido").assertExists()
    }

    @Test fun sePuedeAbrirElRegistroYValidarAntesDeEnviar() {
        compose.onNodeWithText("Registrarse").performScrollTo().performClick()
        compose.onNodeWithText("Registro de cliente").assertIsDisplayed()
        compose.onNodeWithText("Enviar solicitud").performScrollTo().performClick()
        compose.onAllNodesWithText("Campo obligatorio").assertCountEquals(3)
        compose.onNodeWithText("Volver al inicio de sesión").performScrollTo().performClick()
        compose.onNodeWithText("Iniciar sesión").assertExists()
    }

    @Test fun noAbreSesionCuandoUsuariosNoResponde() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("sincronizacion", Context.MODE_PRIVATE)
        val acceso = prefs.getString("acceso", null)
        check(prefs.edit().putString("acceso", "http://127.0.0.1:1/").commit())
        try {
            compose.onNodeWithText("Correo electrónico").performTextInput("mgodoy@induztek.ejemplo")
            compose.onNodeWithText("Contraseña").performTextInput("1234")
            compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Conéctate al servidor para iniciar sesión.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Panel principal").assertDoesNotExist()
        } finally {
            check(prefs.edit().putString("acceso", acceso).commit())
        }
    }

    @Test @RequiereBackend fun clienteSolicitaCuentaYEntraTrasAprobacionDelJefe() {
        val sufijo = System.nanoTime().toString().takeLast(7)
        val cuerpo = "4$sufijo"
        val suma = cuerpo.reversed().mapIndexed { i, c -> c.digitToInt() * (2 + i % 6) }.sum()
        val dv = when (val valor = 11 - suma % 11) { 11 -> "0"; 10 -> "K"; else -> valor.toString() }
        val correo = "solicitud.$sufijo@induztek.ejemplo"
        val clave = "Solicitud-$sufijo"
        compose.onNodeWithText("Registrarse").performScrollTo().performClick()
        compose.onNodeWithText("Nombre completo").performTextInput("Solicitud $sufijo")
        compose.onNodeWithText("Correo electrónico").performTextInput(correo)
        compose.onNodeWithText("RUT").performTextInput("$cuerpo-$dv")
        compose.onNodeWithText("Contraseña").performTextInput(clave)
        compose.onNodeWithText("Repite la contraseña").performTextInput(clave)
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Enviar solicitud").performScrollTo().performClick()
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Solicitud enviada", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(compose.onRoot().printToString(), e)
        }
        compose.onNodeWithText("Correo electrónico").performTextInput(correo)
        compose.onNodeWithText("Contraseña").performTextInput(clave)
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Correo o contraseña incorrectos.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Panel principal").assertDoesNotExist()
        compose.onNodeWithText("Correo electrónico").performTextClearance()
        compose.onNodeWithText("Contraseña").performTextClearance()
        entrar("respinoza@induztek.ejemplo")
        compose.onNodeWithText("Administrar cuentas").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("Actualizar lista") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        val cuenta = hasTestTag("cuenta-cliente") and hasAnyDescendant(hasText(correo))
        compose.onNodeWithTag("lista-cuentas").performScrollToNode(cuenta)
        compose.onNode(hasText("Seleccionar empresa") and hasAnyAncestor(cuenta)).performScrollTo().performClick()
        compose.onNode(hasText("Envasadora del Pacífico S.A.") and hasClickAction()).performClick()
        compose.onNode(hasText("Aprobar y activar") and hasAnyAncestor(cuenta)).performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("Solicitud $sufijo · Activa")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        entrar(correo, clave)
        compose.onNodeWithText("Administrar cuentas").assertDoesNotExist()
    }

    @Test @RequiereBackend fun losCuatroRolesInicianSesionConPanelYLogout() {
        for (correo in listOf("mgodoy@induztek.ejemplo", "acontreras@induztek.ejemplo", "respinoza@induztek.ejemplo", "mfuentes@envpacifico.ejemplo")) {
            entrar(correo)
            if (correo == "mfuentes@envpacifico.ejemplo") {
                compose.onNodeWithText("Documentos de mi empresa").assertExists()
                compose.onNodeWithTag("portal-cliente-lista").performScrollToNode(hasText("Cerrar sesión"))
                compose.onNodeWithText("Cerrar sesión").performClick()
            } else {
                compose.onNodeWithText("Visitas y actas de terreno").assertExists()
                compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Iniciar sesión").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Panel principal").assertDoesNotExist()
        }
    }

    @Test @RequiereBackend fun sePuedeCambiarEntreCuatroCuentasAutenticadas() {
        cl.induztek.app.sync.ServiciosRemotos.cerrarTodas()
        entrar("respinoza@induztek.ejemplo")
        compose.onNodeWithText("Supervisión y administración").assertExists()
        val perfiles = listOf(
            "mgodoy@induztek.ejemplo" to "Trabajo en terreno",
            "acontreras@induztek.ejemplo" to "Revisión de protocolos",
            "mfuentes@envpacifico.ejemplo" to "Documentos de mi empresa"
        )
        for ((correo, panel) in perfiles) {
            compose.onNodeWithText("Cambiar cuenta").performScrollTo().performClick()
            compose.onNodeWithText("Agregar otra cuenta").performScrollTo().performClick()
            entrar(correo)
            compose.onNodeWithText(panel).assertExists()
            compose.onNodeWithText("Administrar cuentas").assertDoesNotExist()
        }
        compose.onNodeWithTag("portal-cliente-lista").performScrollToNode(hasText("Cambiar cuenta"))
        compose.onNodeWithText("Cambiar cuenta").performClick()
        compose.onNodeWithText("Ricardo Espinoza Lagos").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Supervisión y administración").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Administrar cuentas").assertExists()
        cl.induztek.app.sync.ServiciosRemotos.cerrarTodas()
    }

    @Test @RequiereBackend fun navegacionInternaConservaElBorradorAlVolver() {
        entrar("mgodoy@induztek.ejemplo")
        compose.onNodeWithText("Visitas y actas de terreno").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Crear visita").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crear visita").performScrollTo().performClick()
        compose.onNodeWithText("Número de OT *").performScrollTo().performTextReplacement("OT-BORRADOR-EP2")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Crear visita").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crear visita").performScrollTo().performClick()
        compose.onNodeWithText("OT-BORRADOR-EP2").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("OT-BORRADOR-EP2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("OT-BORRADOR-EP2").assertExists()
    }

    @Test @RequiereBackend fun galeriaGuardaImagenYLaRecuperaTrasRecrearActividad() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val archivo = File(contexto.filesDir, "foto_tecnico2.jpg")
        val anterior = archivo.takeIf { it.exists() }?.readBytes()
        val uri = contexto.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "induztek-test-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/InduztekPruebas")
        })!!
        contexto.contentResolver.openOutputStream(uri)!!.use { Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        Intents.init()
        try {
            Intents.intending(anyOf(IntentMatchers.hasAction(MediaStore.ACTION_PICK_IMAGES), IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT), IntentMatchers.hasAction(Intent.ACTION_GET_CONTENT)))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            entrar("jtapia@induztek.ejemplo")
            compose.onNodeWithContentDescription("Mi perfil").performClick()
            compose.onNodeWithText("Elegir de la galería").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Imagen de galería guardada").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(archivo.exists())
            val foto = BitmapFactory.decodeFile(archivo.absolutePath)
            assertEquals(80, foto.width); assertEquals(120, foto.height)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Foto de perfil").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Foto de perfil").assertExists()
            compose.onNodeWithContentDescription("Volver").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("foto-inicio", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("foto-inicio", useUnmergedTree = true).assertIsDisplayed()
        } finally {
            Intents.release()
            if (anterior != null) archivo.writeBytes(anterior)
            // Solo se retira la imagen sintética creada por esta prueba.
            contexto.contentResolver.delete(uri, null, null)
        }
    }

    @Test @RequiereBackend fun cancelarGaleriaConservaLaFotoAnterior() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val archivo = File(contexto.filesDir, "foto_tecnico3.jpg")
        val anterior = archivo.takeIf { it.exists() }?.readBytes()
        archivo.outputStream().use { Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG,85,it) }
        val contenido = archivo.readBytes()
        Intents.init()
        try {
            Intents.intending(anyOf(IntentMatchers.hasAction(MediaStore.ACTION_PICK_IMAGES), IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT)))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            entrar("crondon@induztek.ejemplo")
            compose.onNodeWithContentDescription("Mi perfil").performClick()
            compose.onNodeWithText("Elegir de la galería").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Selección cancelada; se conserva tu foto").fetchSemanticsNodes().isNotEmpty() }
            assertArrayEquals(contenido,archivo.readBytes())
        } finally { Intents.release(); if (anterior != null) archivo.writeBytes(anterior) }
    }

    @Test @RequiereBackend fun recorridoCompletoDesdeVisitaHastaActa() {
        fun pulsar(texto: String) = compose.onNodeWithText(texto).performScrollTo().performClick()
        fun escribir(campo: String, valor: String) { compose.onNodeWithText(campo).performScrollTo().performTextReplacement(valor) }
        fun seleccionar(titulo: String, opcion: String) {
            compose.onNodeWithText(titulo, substring = true).performScrollTo().performClick()
            compose.onNode(hasText(opcion, substring = true) and hasClickAction()).performScrollTo().performClick()
        }
        fun esperar(texto: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty() } }
        entrar("mgodoy@induztek.ejemplo")
        pulsar("Visitas y actas de terreno"); esperar("Crear visita"); pulsar("Crear visita")
        escribir("Número de OT *", "OT-EP2-${System.nanoTime() % 1_000_000}")
        seleccionar("Instalación *:", "Planta Molienda")
        seleccionar("Ingeniero a cargo *:", "Andrea Contreras Silva")
        pulsar("Guardar visita offline"); esperar("Iniciar protocolo del equipo")
        compose.onNodeWithText("Consultar clima de Puente Alto").assertExists()
        seleccionar("Equipo:", "REL-50/51-A")
        pulsar("Iniciar protocolo del equipo"); esperar("Guardar datos del protocolo")
        escribir("Temperatura ambiente (°C) *", "20")
        escribir("Humedad relativa (%) *", "50")
        pulsar("Guardar datos del protocolo"); esperar("Datos guardados")
        fun registrarPrueba() {
            pulsar("Registrar o corregir pruebas")
            seleccionar("Instrumento *:", "Inyección secundaria")
            escribir("Corriente de ajuste (A) *", "5")
            escribir("Corriente de operación (A) *", "5,1")
            escribir("Tiempo esperado (ms) *", "100")
            escribir("Tiempo medido (ms) *", "104")
            compose.onNodeWithText("Resultado calculado · vista previa").performScrollTo().assertIsDisplayed()
            pulsar("Guardar prueba offline"); esperar("Prueba y resultado guardados")
        }
        registrarPrueba()
        pulsar("Editar prueba")
        escribir("Tiempo medido (ms) *", "102")
        pulsar("Guardar prueba offline")
        esperar("tiempo: 102.0 ms")
        pulsar("Eliminar prueba")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNode(hasText("tiempo: 102.0 ms", substring = true) and
            hasText("Instrumento: Inyección secundaria", substring = true)).assertExists()
        pulsar("Eliminar prueba")
        compose.onNodeWithText("Eliminar").performClick()
        esperar("Prueba eliminada")
        compose.onNodeWithText("tiempo: 102.0 ms", substring = true).assertDoesNotExist()
        registrarPrueba()
        pulsar("Pasar a Registrado en terreno"); esperar("Registrado en terreno; pendiente de formalización")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Volver a la pantalla anterior").performClick()
        escribir("Nombre de quien recibe *", "Marcela Fuentes Rojas")
        escribir("RUT del receptor *", "14.226.791-8")
        escribir("Cargo *", "Jefa de mantenimiento")
        pulsar("Cerrar visita y generar acta"); esperar("Guardar acta en PDF")
        compose.onNodeWithText("PENDIENTE DE FORMALIZACIÓN", substring = true).assertExists()
        compose.onNodeWithText("Compartir acta de terreno").assertExists()
    }
}
