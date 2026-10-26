package cl.induztek.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Símbolos vectoriales propios, legibles también con tema oscuro. */
enum class Simbolo { ENERGIA, INICIO, PERFIL, VISITA, HISTORIAL, CATALOGO, VOLVER, CORREO, CANDADO, CAMARA, GALERIA, CAMPO, ERROR, CORRECTO, SALIR }

@Composable
fun IconoInduztek(tipo: Simbolo, descripcion: String? = null, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp).semantics { if (descripcion != null) contentDescription = descripcion }) {
        val sx = size.width / 24f; val sy = size.height / 24f
        fun linea(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x*sx,y*sy),Offset(xx*sx,yy*sy),2*sx,StrokeCap.Round)
        fun caja(x: Float,y: Float,w: Float,h: Float) = drawRect(color,Offset(x*sx,y*sy),androidx.compose.ui.geometry.Size(w*sx,h*sy),style=Stroke(1.8f*sx))
        when(tipo) {
            Simbolo.ENERGIA -> { val p=Path().apply { moveTo(14*sx,2*sy); lineTo(5*sx,14*sy);lineTo(11*sx,14*sy);lineTo(10*sx,22*sy);lineTo(20*sx,9*sy);lineTo(13*sx,9*sy);close() };drawPath(p,color) }
            Simbolo.PERFIL -> { drawCircle(color,4*sx,Offset(12*sx,7*sy),style=Stroke(1.8f*sx));drawArc(color,180f,180f,false,Offset(4*sx,13*sy),androidx.compose.ui.geometry.Size(16*sx,14*sy),style=Stroke(1.8f*sx)) }
            Simbolo.VOLVER -> { linea(19f,12f,5f,12f);linea(5f,12f,11f,6f);linea(5f,12f,11f,18f) }
            Simbolo.CORREO -> { caja(3f,5f,18f,14f);linea(3f,6f,12f,13f);linea(21f,6f,12f,13f) }
            Simbolo.CANDADO -> { caja(5f,10f,14f,11f);drawArc(color,180f,180f,false,Offset(8*sx,2*sy),androidx.compose.ui.geometry.Size(8*sx,16*sy),style=Stroke(1.8f*sx));linea(12f,14f,12f,17f) }
            Simbolo.INICIO -> { linea(3f,10f,12f,3f);linea(12f,3f,21f,10f);linea(5f,9f,5f,21f);linea(19f,9f,19f,21f);linea(5f,21f,19f,21f) }
            Simbolo.CAMARA -> { caja(2f,7f,20f,14f);caja(8f,3f,8f,4f);drawCircle(color,4*sx,Offset(12*sx,14*sy),style=Stroke(1.8f*sx)) }
            Simbolo.GALERIA -> { caja(3f,3f,18f,18f);drawCircle(color,2*sx,Offset(8*sx,8*sy));linea(4f,19f,11f,12f);linea(11f,12f,20f,20f) }
            Simbolo.CORRECTO -> { linea(4f,12f,9f,17f);linea(9f,17f,20f,6f) }
            Simbolo.ERROR -> { drawCircle(color,9*sx,center,style=Stroke(1.8f*sx));linea(12f,7f,12f,13f);drawCircle(color,1*sx,Offset(12*sx,17*sy)) }
            Simbolo.SALIR -> { linea(9f,3f,3f,3f);linea(3f,3f,3f,21f);linea(3f,21f,9f,21f);linea(8f,12f,21f,12f);linea(21f,12f,16f,7f);linea(21f,12f,16f,17f) }
            Simbolo.VISITA -> { caja(3f,5f,18f,16f);linea(3f,10f,21f,10f);linea(8f,2f,8f,7f);linea(16f,2f,16f,7f);linea(7f,15f,10f,18f);linea(10f,18f,17f,12f) }
            Simbolo.HISTORIAL -> { drawCircle(color,9*sx,center,style=Stroke(1.8f*sx));linea(12f,6f,12f,12f);linea(12f,12f,17f,15f) }
            Simbolo.CATALOGO -> { caja(3f,3f,7f,7f);caja(14f,3f,7f,7f);caja(3f,14f,7f,7f);caja(14f,14f,7f,7f) }
            else -> { caja(4f,3f,16f,18f);linea(8f,8f,16f,8f);linea(8f,12f,16f,12f);linea(8f,16f,13f,16f) }
        }
    }
}

@Composable
fun CampoInduztek(valor: String, cambiar: (String) -> Unit, titulo: String, error: String? = null,
                  simbolo: Simbolo = Simbolo.CAMPO, teclado: KeyboardType = KeyboardType.Text,
                  multilinea: Boolean = false, transformacion: VisualTransformation = VisualTransformation.None,
                  enabled: Boolean = true, ayuda: String? = null, final: (@Composable () -> Unit)? = null) {
    OutlinedTextField(value = valor, onValueChange = cambiar, label = { Text(titulo) },
        modifier = Modifier.fillMaxWidth().animateContentSize(), enabled = enabled, singleLine = !multilinea,
        minLines = if (multilinea) 3 else 1, leadingIcon = { IconoInduztek(simbolo) },
        trailingIcon = final ?: if (error != null) ({ IconoInduztek(Simbolo.ERROR) }) else null,
        isError = error != null, supportingText = if (error != null || ayuda != null) ({ Text(error ?: ayuda.orEmpty()) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = teclado), visualTransformation = transformacion)
}

@Composable
fun AvisoInduztek(texto: String?, error: Boolean = false) {
    AnimatedVisibility(!texto.isNullOrBlank()) {
        Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconoInduztek(if (error) Simbolo.ERROR else Simbolo.CORRECTO)
                Text(texto.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
