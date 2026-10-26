package cl.induztek.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.ContentValues
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

    private fun entrar(correo: String) {
        compose.onNodeWithText("Correo electrónico").performTextInput(correo)
        compose.onNodeWithText("Contraseña", substring = false).performTextInput("1234")
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Panel principal").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun tecnicoAccedeACamaraYVisitas() {
        entrar("mgodoy@induztek.ejemplo")
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        compose.onNodeWithText("Tomar foto de perfil").assertExists()
        compose.onNodeWithText("Elegir de la galería").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Visitas y actas de terreno").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Crear visita").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crear visita").assertIsDisplayed()
    }

    @Test fun clienteSoloVeEquiposDeSuEmpresa() {
        entrar("mfuentes@envpacifico.ejemplo")
        compose.onNodeWithText("Clientes, equipos e instrumentos").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Catálogos").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("TR-01", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("CEL-MT-03", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Agregar Equipo").assertDoesNotExist()
    }

    @Test fun resultadoDeCamaraSeGuardaEnLaRutaDePerfilExistente() {
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
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Ingresa tu correo").assertExists()
        compose.onNodeWithText("Ingresa tu contraseña").assertExists()
        compose.onNodeWithText("Correo electrónico").performTextInput("correo incorrecto")
        compose.onNodeWithText("Contraseña", substring = false).performTextInput("1234")
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Ingresa un correo válido").assertExists()
    }

    @Test fun losCuatroRolesInicianSesionConPanelYLogout() {
        for (correo in listOf("mgodoy@induztek.ejemplo", "acontreras@induztek.ejemplo", "respinoza@induztek.ejemplo", "mfuentes@envpacifico.ejemplo")) {
            entrar(correo)
            compose.onNodeWithText("Visitas y actas de terreno").assertExists()
            compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Iniciar sesión").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Panel principal").assertDoesNotExist()
        }
    }

    @Test fun navegacionInternaConservaElBorradorAlVolver() {
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

    @Test fun galeriaGuardaImagenYLaRecuperaTrasRecrearActividad() {
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

    @Test fun cancelarGaleriaConservaLaFotoAnterior() {
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

    @Test fun recorridoOfflineCompletoDesdeVisitaHastaActa() {
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
        seleccionar("Equipo:", "REL-50/51-A")
        pulsar("Iniciar protocolo del equipo"); esperar("Guardar datos del protocolo")
        escribir("Temperatura ambiente (°C) *", "20")
        escribir("Humedad relativa (%) *", "50")
        pulsar("Guardar datos del protocolo"); esperar("Datos guardados")
        pulsar("Registrar o corregir pruebas")
        seleccionar("Instrumento *:", "Inyección secundaria")
        escribir("Corriente de ajuste (A) *", "5")
        escribir("Corriente de operación (A) *", "5,1")
        escribir("Tiempo esperado (ms) *", "100")
        escribir("Tiempo medido (ms) *", "104")
        compose.onNodeWithText("Resultado calculado · vista previa").performScrollTo().assertIsDisplayed()
        pulsar("Guardar prueba offline"); esperar("Prueba y resultado guardados")
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
