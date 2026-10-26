package cl.induztek.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.induztek.app.R

/** Logo original, sin tintes ni recortes de su contenido. */
@Composable
fun LogoInduztek(modifier: Modifier = Modifier, tamano: Dp = 64.dp) {
    Image(painterResource(R.drawable.logo_induztek), contentDescription = "Logo de Induztek Ingeniería",
        contentScale = ContentScale.Fit, modifier = modifier.size(tamano).clip(RoundedCornerShape(tamano / 5)))
}
