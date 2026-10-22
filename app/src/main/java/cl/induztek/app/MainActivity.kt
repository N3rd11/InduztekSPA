
package cl.induztek.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import cl.induztek.app.viewmodel.RegistroViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                RegistroScreen()
            }
        }
    }
}

@Composable
fun RegistroScreen(
    vm: RegistroViewModel = viewModel()
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // TITULO PRINCIPAL
        Text(
            text = "Induztek",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Registro de pruebas eléctricas en terreno",
            style = MaterialTheme.typography.bodyMedium
        )

        // INFORMACION DEL EQUIPO
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Text(
                    text = "Información del equipo",
                    style = MaterialTheme.typography.titleMedium
                )

                Text("Equipo: TR-01")
                Text("Tipo: Transformador")
                Text("Instalación: Planta Lampa")
                Text("Prueba: Resistencia de aislación")
            }
        }

        // FORMULARIO DE MEDICIONES
        Text(
            text = "Ingresar mediciones",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = vm.lectura1,
            onValueChange = vm::cambiarLectura1,
            label = {
                Text("Lectura 1 minuto (MΩ)")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = vm.lectura10,
            onValueChange = vm::cambiarLectura10,
            label = {
                Text("Lectura 10 minutos (MΩ)")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // BOTON PARA CALCULAR
        Button(
            onClick = {
                vm.calcular()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Calcular resultado")
        }

        // MOSTRAR ERRORES
        vm.error?.let { mensajeError ->

            Text(
                text = mensajeError,
                color = MaterialTheme.colorScheme.error
            )
        }

        // MOSTRAR RESULTADO
        vm.evaluacion?.let { resultado ->

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = "Resultado de la prueba",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Índice de polarización: %.2f"
                            .format(resultado.indice)
                    )

                    Text(
                        text = "Estado: ${resultado.resultado.name}",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // BOTON PARA GUARDAR EN ROOM
        Button(
            onClick = {
                vm.guardar()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar medición")
        }

        // MENSAJE DE CONFIRMACION
        vm.mensaje?.let { mensaje ->

            Text(
                text = mensaje,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider()

        // HISTORIAL DE MEDICIONES
        Text(
            text = "Historial de mediciones",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (vm.historial.isEmpty()) {

            Text(
                text = "No hay mediciones guardadas.",
                style = MaterialTheme.typography.bodyMedium
            )

        } else {

            vm.historial.forEach { registro ->

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        Text(
                            text = "Equipo: ${registro.equipoTag}",
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Instalación: ${registro.instalacion}"
                        )

                        Text(
                            text = "Lectura 1 min: ${registro.lectura1} MΩ"
                        )

                        Text(
                            text = "Lectura 10 min: ${registro.lectura10} MΩ"
                        )

                        Text(
                            text = "IP: %.2f".format(registro.indice)
                        )

                        Text(
                            text = "Resultado: ${registro.resultado}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
