package com.example.sanivox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.sanivox.ui.theme.SanivoxTheme
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {

    private lateinit var model: Model
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var keywordRecognizer: Recognizer? = null
    private var keywordService: SpeechService? = null
    private var recognizerThreadOcupado = false
    var actualizarTranscripciones: (() -> Unit)? = null




    var carpetaActual: String = "General"
    var transcripcionActiva: MutableState<Boolean> = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        solicitarPermisos()
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            Toast.makeText(this, "Este dispositivo no tiene micrófono", Toast.LENGTH_LONG).show()
            return
        }

        copiarModeloDesdeAssets()

        if (!cargarModeloVosk()) {
            Toast.makeText(this, "Error: No se encontró el modelo de voz", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Modelo cargado correctamente", Toast.LENGTH_SHORT).show()
            if (inicializarKeywordRecognizer()) {
                escucharPalabraClave {
                    runOnUiThread {
                        if (speechService == null) {
                            iniciarTranscripcion {
                                Log.d("Sanivox", "Transcripción iniciada por palabra clave")
                            }
                        }
                    }
                }
            }
        }

        setContent {
            SanivoxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TranscriptionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun solicitarPermisos() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Permiso necesario para grabar audio", Toast.LENGTH_LONG).show()
        }
    }

    private fun cargarModeloVosk(): Boolean {
        return try {
            val modelPath = File(filesDir, "vosk-model-es-0.42")
            if (!modelPath.exists()) return false
            model = Model(modelPath.absolutePath)
            recognizer = Recognizer(model, 16000.0f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun inicializarKeywordRecognizer(): Boolean {
        return try {
            keywordRecognizer = Recognizer(model, 16000.0f, "[\"dakota\"]")
            Log.d("Sanivox", "Recognizer de palabra clave inicializado")
            true
        } catch (e: Exception) {
            Log.e("Sanivox", "Error al inicializar keywordRecognizer", e)
            false
        }
    }



    fun escucharPalabraClave(onDetectado: () -> Unit) {
        if (keywordRecognizer == null) {
            Toast.makeText(this, "Reconocedor de palabra clave no inicializado", Toast.LENGTH_SHORT).show()
            Log.e("Sanivox", "Recognizer es null")
            return
        }

        speechService?.stop()
        speechService?.shutdown()
        speechService = null

        keywordService?.shutdown()
        keywordService = null

        keywordService = SpeechService(keywordRecognizer, 16000.0f)
        Log.d("Sanivox", "SpeechService de palabra clave inicializado, empezando a escuchar ...")

        keywordService?.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                Log.d("Sanivox", "Reconocimiento parcial (keyword): $hypothesis")
            }

            override fun onResult(hypothesis: String?) {
                Log.d("Sanivox", "Resultado final (keyword): $hypothesis")
                val texto = extraerTextoReconocido(hypothesis ?: "").trim()
                Log.d("Sanivox", "Texto reconocido (keyword): '$texto'")

                if (texto.equals("dakota", ignoreCase = true)) {
                    Log.i("Sanivox", "Palabra clave exacta 'dakota' detectada.")

                    lifecycleScope.launch {
                        // 🔐 Apagar keywordService y esperar
                        apagarKeywordServiceConEspera()

                        delay(200) // Espera adicional

                        // ✅ Solo iniciar si el micro ya está libre y no hay otra transcripción
                        if (!isMicrophoneInUse() && speechService == null) {
                            iniciarTranscripcion {
                                Log.d("Sanivox", "Transcripción iniciada tras palabra clave.")
                            }
                        } else {
                            Log.w("Sanivox", "No se pudo iniciar transcripción: micrófono ocupado o ya en uso.")
                        }
                    }
                }
            }


            override fun onFinalResult(hypothesis: String?) {}
            override fun onError(e: Exception?) {
                Log.e("Sanivox", "Error keyword: ${e?.message}", e)
                Toast.makeText(this@MainActivity, "Error keyword: ${e?.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onTimeout() {
                Log.w("Sanivox", "Timeout escuchando palabra clave")
            }
        })
    }


    fun iniciarTranscripcion(onNuevaTranscripcion: () -> Unit) {
        if (recognizer == null) {
            Log.e("Sanivox", "Recognizer nulo. No se puede iniciar transcripción.")
            return
        }

        lifecycleScope.launch {
            keywordService?.let { service ->
                try {
                    withContext(Dispatchers.IO) {
                        service.stop()
                        service.shutdown()
                        keywordService = null
                    }
                    Log.d("Sanivox", "keywordService detenido correctamente")
                } catch (e: Exception) {
                    Log.e("Sanivox", "Error al detener keywordService", e)
                }
            }

            var intentos = 0
            while (isMicrophoneInUse() && intentos < 10) {
                delay(100)
                intentos++
            }

            if (isMicrophoneInUse()) {
                Log.e("Sanivox", "Micrófono aún ocupado. Abortando.")
                return@launch
            }

            try {
                transcripcionActiva.value = true
                speechService = SpeechService(recognizer, 16000.0f)
                recognizerThreadOcupado = true

                speechService?.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        Log.d("Sanivox", "Parcial: $hypothesis")
                    }

                    override fun onResult(hypothesis: String?) {
                        val textoPlano = extraerTextoReconocido(hypothesis ?: "")
                        if (textoPlano.isNotBlank()) {
                            guardarYActualizar(textoPlano, onNuevaTranscripcion)
                        }
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        recognizerThreadOcupado = false
                        val textoPlano = extraerTextoReconocido(hypothesis ?: "")
                        if (textoPlano.isNotBlank()) {
                            guardarYActualizar(textoPlano, onNuevaTranscripcion)
                        }
                        detenerTranscripcion()
                    }

                    override fun onError(e: Exception?) {
                        recognizerThreadOcupado = false
                        Log.e("Sanivox", "Error transcripción: ${e?.message}", e)
                        detenerTranscripcion()
                    }

                    override fun onTimeout() {
                        recognizerThreadOcupado = false
                        Log.w("Sanivox", "Timeout transcripción")
                        detenerTranscripcion()
                    }
                })

            } catch (e: Exception) {
                recognizerThreadOcupado = false
                Log.e("Sanivox", "Fallo iniciando transcripción", e)
                detenerTranscripcion()
            }
        }
    }




    private fun guardarYActualizar(textoPlano: String, actualizarUI: () -> Unit) {
        val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val resultado = "$timestamp → $textoPlano"
        guardarTranscripcion(resultado)

        runOnUiThread {
            actualizarUI()
            actualizarTranscripciones?.invoke()
        }
    }


    fun detenerTranscripcion() {
        lifecycleScope.launch {
            Log.d("Sanivox", "Deteniendo servicios de transcripción...")

            try {
                withContext(Dispatchers.IO) {
                    speechService?.stop()
                    speechService?.shutdown()
                    speechService = null
                }
                Log.d("Sanivox", "speechService detenido correctamente.")
            } catch (e: Exception) {
                Log.e("Sanivox", "Error al detener speechService", e)
            }

            try {
                withContext(Dispatchers.IO) {
                    keywordService?.stop()
                    keywordService?.shutdown()
                    keywordService = null
                }
            } catch (e: Exception) {
                Log.e("Sanivox", "Error al detener keywordService", e)
            }

            runOnUiThread {
                transcripcionActiva.value = false
                Log.d("Sanivox", "Transcripción terminada. Esperando 1s para reactivar palabra clave...")

                Handler(Looper.getMainLooper()).postDelayed({
                    lifecycleScope.launch {
                        var intentos = 0
                        while (recognizerThreadOcupado && intentos < 10) {
                            Log.d("Sanivox", "Esperando a que se libere recognizerThread...")
                            delay(100)
                            intentos++
                        }

                        if (!recognizerThreadOcupado && speechService == null && keywordService == null) {
                            escucharPalabraClave {
                                if (speechService == null) {
                                    iniciarTranscripcion {}
                                }
                            }
                        } else {
                            Log.w("Sanivox", "No se reactivó palabra clave: recognizer aún ocupado o servicios activos.")
                        }
                    }
                }, 1000)
            }
        }
    }






    private suspend fun apagarKeywordServiceConEspera() {
        try {
            withContext(Dispatchers.IO) {
                keywordService?.stop()
                keywordService?.shutdown()
                keywordService = null
            }
            Log.d("Sanivox", "keywordService apagado correctamente.")
        } catch (e: Exception) {
            Log.e("Sanivox", "Error al apagar keywordService", e)
        }

        // Esperamos 500ms por defecto
        delay(500)

        // Verificamos si aún sigue ocupado
        var intentos = 0
        while (isMicrophoneInUse() && intentos < 10) {
            Log.d("Sanivox", "Esperando a que se libere el micrófono...")
            delay(100)
            intentos++
        }

        if (isMicrophoneInUse()) {
            Log.w("Sanivox", "El micrófono sigue ocupado tras esperar.")
        } else {
            Log.d("Sanivox", "Micrófono liberado correctamente.")
        }
    }


    private fun isMicrophoneInUse(): Boolean {
        return try {
            val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            audioManager.isMicrophoneMute // esta llamada puede funcionar como proxy en algunos dispositivos
            false // asumimos que si no lanza error, está libre
        } catch (e: Exception) {
            true // si lanza error, probablemente aún está ocupado
        }
    }


    private fun copiarModeloDesdeAssets() {
        val modelName = "vosk-model-es-0.42"
        val outDir = File(filesDir, modelName)
        if (outDir.exists()) return

        try {
            val assetManager = assets
            val files = assetManager.list(modelName) ?: return
            outDir.mkdirs()
            for (file in files) {
                assetManager.open("$modelName/$file").use { input ->
                    File(outDir, file).outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun guardarTranscripcion(texto: String) {
        val carpeta = File(filesDir, "transcripciones/$carpetaActual")
        if (!carpeta.exists()) carpeta.mkdirs()
        File(carpeta, "${System.currentTimeMillis()}.txt").writeText(texto)
    }

    fun leerTranscripciones(filtro: String? = null): List<String> {
        val carpeta = File(filesDir, "transcripciones/$carpetaActual")
        if (!carpeta.exists()) return emptyList()
        return carpeta.listFiles()?.sortedBy { it.name }
            ?.mapNotNull { it.readTextOrNull() }
            ?.filter { filtro.isNullOrBlank() || it.contains(filtro, ignoreCase = true) }
            ?: emptyList()
    }

    private fun File.readTextOrNull(): String? = try { readText() } catch (e: Exception) { null }

    fun borrarTranscripciones() {
        val carpeta = File(filesDir, "transcripciones/$carpetaActual")
        carpeta.listFiles()?.forEach { it.delete() }
    }

    fun extraerTextoReconocido(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.getString("text")
        } catch (e: Exception) {
            android.util.Log.e("Sanivox", "Error al parsear JSON: $json", e)
            ""
        }
    }

    fun obtenerCarpetas(): List<String> {
        val base = File(filesDir, "transcripciones")
        if (!base.exists()) base.mkdirs()
        return base.list()?.toList() ?: listOf()
    }

    fun crearCarpeta(nombre: String): Boolean {
        val nueva = File(filesDir, "transcripciones/$nombre")
        return if (!nueva.exists()) nueva.mkdirs() else false
    }

    fun renombrarCarpeta(viejoNombre: String, nuevoNombre: String): Boolean {
        val base = File(filesDir, "transcripciones")
        val origen = File(base, viejoNombre)
        val destino = File(base, nuevoNombre)
        return origen.exists() && !destino.exists() && origen.renameTo(destino)
    }

    fun borrarCarpeta(nombre: String): Boolean {
        val carpeta = File(filesDir, "transcripciones/$nombre")
        return carpeta.exists() && carpeta.deleteRecursively()
    }

    fun cambiarCarpeta(nombre: String) {
        carpetaActual = nombre
    }
}



@Composable
fun TranscriptionScreen(modifier: Modifier = Modifier) {

    val transcripciones = remember { mutableStateListOf<String>() }
    var expanded by remember { mutableStateOf(false) }
    var mostrarDialogoCrear by remember { mutableStateOf(false) }
    var mostrarDialogoRenombrar by remember { mutableStateOf(false) }
    var mostrarDialogoBorrar by remember { mutableStateOf(false) }
    var nombreNuevaCarpeta by remember { mutableStateOf("") }
    var nuevoNombreCarpeta by remember { mutableStateOf("") }
    var filtroBusqueda by remember { mutableStateOf("") }
    val context = LocalContext.current as MainActivity
    context.actualizarTranscripciones = {
        transcripciones.clear()
        transcripciones.addAll(context.leerTranscripciones(filtroBusqueda))
    }
    var carpetaActual by remember { mutableStateOf(context.carpetaActual) }
    var carpetas by remember { mutableStateOf(context.obtenerCarpetas()) }
    val transcripcionActiva = context.transcripcionActiva

    LaunchedEffect(carpetaActual) {
        context.cambiarCarpeta(carpetaActual)
        transcripciones.clear()
        transcripciones.addAll(context.leerTranscripciones(filtroBusqueda))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Carpeta activa:", style = MaterialTheme.typography.labelLarge)
                Box {
                    Button(onClick = { expanded = true }) {
                        Text(carpetaActual)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        carpetas.forEach { nombre ->
                            DropdownMenuItem(
                                text = { Text(nombre) },
                                onClick = {
                                    carpetaActual = nombre
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { mostrarDialogoCrear = true }) {
                    Text("+ Nueva carpeta")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { mostrarDialogoRenombrar = true }) { Text("Renombrar") }
                    TextButton(onClick = { mostrarDialogoBorrar = true }) { Text("Eliminar") }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = filtroBusqueda,
            onValueChange = {
                filtroBusqueda = it
                transcripciones.clear()
                transcripciones.addAll(context.leerTranscripciones(filtroBusqueda))
            },
            label = { Text("Buscar...") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Historial de Transcripciones",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(end = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (context.transcripcionActiva.value) {
                val alphaAnim by animateFloatAsState(
                    targetValue = if (context.transcripcionActiva.value) 1f else 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "● Grabando...",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(alphaAnim)
                    )
                }
            }
            if (transcripciones.isEmpty()) {
                Text("Esperando transcripción...", style = MaterialTheme.typography.bodyLarge)
            } else {
                transcripciones.forEach { linea ->
                    TranscripcionCard(linea)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    context.cambiarCarpeta(carpetaActual)
                    context.iniciarTranscripcion {
                        transcripciones.clear()
                        transcripciones.addAll(context.leerTranscripciones(filtroBusqueda))
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Iniciar") }

            Button(
                onClick = { context.detenerTranscripcion() },
                modifier = Modifier.weight(1f)
            ) { Text("Detener") }

            Button(
                onClick = {
                    context.borrarTranscripciones()
                    transcripciones.clear()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Borrar") }
        }
    }

    if (mostrarDialogoCrear) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoCrear = false },
            confirmButton = {
                Button(onClick = {
                    if (nombreNuevaCarpeta.isNotBlank()) {
                        context.crearCarpeta(nombreNuevaCarpeta)
                        carpetas = context.obtenerCarpetas()
                        carpetaActual = nombreNuevaCarpeta
                        context.cambiarCarpeta(nombreNuevaCarpeta)
                        transcripciones.clear()
                        transcripciones.addAll(context.leerTranscripciones(filtroBusqueda))
                        nombreNuevaCarpeta = ""
                        mostrarDialogoCrear = false
                    }
                }) { Text("Crear") }
            },
            dismissButton = { Button(onClick = { mostrarDialogoCrear = false }) { Text("Cancelar") } },
            title = { Text("Nueva carpeta") },
            text = {
                OutlinedTextField(
                    value = nombreNuevaCarpeta,
                    onValueChange = { nombreNuevaCarpeta = it },
                    label = { Text("Nombre de la carpeta") }
                )
            }
        )
    }

    if (mostrarDialogoRenombrar) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoRenombrar = false },
            confirmButton = {
                Button(onClick = {
                    if (nuevoNombreCarpeta.isNotBlank()) {
                        context.renombrarCarpeta(carpetaActual, nuevoNombreCarpeta)
                        carpetaActual = nuevoNombreCarpeta
                        carpetas = context.obtenerCarpetas()
                        context.cambiarCarpeta(nuevoNombreCarpeta)
                        transcripciones.clear()
                        transcripciones.addAll(context.leerTranscripciones(filtroBusqueda))
                        mostrarDialogoRenombrar = false
                    }
                }) { Text("Renombrar") }
            },
            dismissButton = { Button(onClick = { mostrarDialogoRenombrar = false }) { Text("Cancelar") } },
            title = { Text("Renombrar carpeta") },
            text = {
                OutlinedTextField(
                    value = nuevoNombreCarpeta,
                    onValueChange = { nuevoNombreCarpeta = it },
                    label = { Text("Nuevo nombre") }
                )
            }
        )
    }

    if (mostrarDialogoBorrar) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoBorrar = false },
            confirmButton = {
                Button(onClick = {
                    context.borrarCarpeta(carpetaActual)
                    carpetas = context.obtenerCarpetas()
                    carpetaActual = carpetas.firstOrNull() ?: "General"
                    context.cambiarCarpeta(carpetaActual)
                    transcripciones.clear()
                    transcripciones.addAll(context.leerTranscripciones(filtroBusqueda))
                    mostrarDialogoBorrar = false
                }) { Text("Eliminar") }
            },
            dismissButton = { Button(onClick = { mostrarDialogoBorrar = false }) { Text("Cancelar") } },
            title = { Text("Eliminar carpeta") },
            text = { Text("¿Seguro que deseas eliminar la carpeta '$carpetaActual'? Esta acción no se puede deshacer.") }
        )
    }
}


@Composable
fun TranscripcionCard(texto: String) {
    val partes = texto.split("→")
    val fecha = partes.getOrNull(0)?.trim() ?: "Sin fecha"
    val contenido = partes.getOrNull(1)?.trim() ?: texto

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = contenido, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fecha,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTranscriptionScreen() {
    SanivoxTheme {
        TranscriptionScreen()
    }
}
