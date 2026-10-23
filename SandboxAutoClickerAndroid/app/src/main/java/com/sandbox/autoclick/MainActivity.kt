package com.sandbox.autoclick

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val interpreter = RuleInterpreter()
    private lateinit var nameInput: EditText
    private lateinit var rulesInput: EditText
    private lateinit var status: TextView
    private lateinit var timePicker: TimePicker
    private var selectedMacroId: Long = -1L
    private val projectionRequest = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 24) }
        val scroll = ScrollView(this); scroll.addView(layout); setContentView(scroll)
        layout.addView(TextView(this).apply { text = "Sandbox AutoClicker"; textSize = 26f })
        layout.addView(TextView(this).apply {
            text = "Reglas por color, macros locales y programación horaria. La captura requiere autorización explícita de Android."
            textSize = 15f; setPadding(0, 12, 0, 16)
        })
        layout.addView(Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, matchWidth())

        layout.addView(TextView(this).apply { text = "Nombre de macro"; textSize = 17f; setPadding(0, 16, 0, 6) })
        nameInput = EditText(this).apply { setText("Mi macro"); hint = "Nombre" }
        layout.addView(nameInput, matchWidth())
        layout.addView(TextView(this).apply { text = "Reglas (una por línea)"; textSize = 17f; setPadding(0, 12, 0, 6) })
        rulesInput = EditText(this).apply {
            minLines = 4; gravity = Gravity.TOP or Gravity.START
            setText("SI pixel(540, 1200) == #00FF55 ENTONCES click(540, 1200) ESPERAR 1000")
        }
        layout.addView(rulesInput, matchWidth())
        status = TextView(this).apply { text = "Las coordenadas son píxeles de pantalla; el color se compara en RGB."; setPadding(0, 10, 0, 10) }
        layout.addView(status, matchWidth())

        layout.addView(Button(this).apply {
            text = "Validar reglas"
            setOnClickListener {
                try {
                    val rules = interpreter.parseAll(rulesInput.text.toString())
                    status.text = "Correcto: ${rules.size} regla(s) válida(s)."
                } catch (e: Exception) { status.text = e.message ?: "Regla inválida." }
            }
        }, matchWidth())

        layout.addView(Button(this).apply {
            text = "Guardar macro"
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val text = rulesInput.text.toString()
                if (name.isBlank()) { status.text = "Escribe un nombre para la macro."; return@setOnClickListener }
                try { interpreter.parseAll(text) } catch (e: Exception) { status.text = e.message ?: "Regla inválida."; return@setOnClickListener }
                scope.launch {
                    selectedMacroId = withContext(Dispatchers.IO) {
                        MacroDatabase.get(this@MainActivity).macroDao().insert(MacroEntity(name = name, rulesText = text))
                    }
                    status.text = "Macro guardada (ID $selectedMacroId)."
                    refreshMacros()
                }
            }
        }, matchWidth())

        layout.addView(TextView(this).apply { text = "Macros guardadas"; textSize = 17f; setPadding(0, 16, 0, 6) })
        val macroList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(macroList, matchWidth())
        layout.addView(Button(this).apply {
            text = "Actualizar lista"
            setOnClickListener { refreshMacros(macroList) }
        }, matchWidth())

        layout.addView(TextView(this).apply { text = "Programar macro seleccionada"; textSize = 17f; setPadding(0, 16, 0, 6) })
        timePicker = TimePicker(this).apply { setIs24HourView(true) }
        layout.addView(timePicker, matchWidth())
        layout.addView(Button(this).apply {
            text = "Programar diariamente (próxima hora)"
            setOnClickListener {
                if (selectedMacroId < 0) { status.text = "Guarda o selecciona una macro de la lista primero."; return@setOnClickListener }
                val hour = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
                val minute = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute
                try {
                    MacroScheduler(this@MainActivity).scheduleMacroAt(selectedMacroId, hour, minute)
                    status.text = "Aviso programado para las ${"%02d:%02d".format(hour, minute)}. Android mostrará una notificación para iniciar la macro."
                } catch (e: Exception) { status.text = "No se pudo programar: ${e.message}" }
            }
        }, matchWidth())

        layout.addView(Button(this).apply {
            text = "Iniciar captura y ejecutar reglas"
            setOnClickListener {
                try {
                    interpreter.parseAll(rulesInput.text.toString())
                    val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
                } catch (e: Exception) { status.text = e.message ?: "No se pudieron validar las reglas." }
            }
        }, matchWidth())
        layout.addView(Button(this).apply {
            text = "Detener captura"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
                status.text = "Solicitud de detener captura enviada."
            }
        }, matchWidth())
        refreshMacros(macroList)
    }

    @Deprecated("Deprecated by Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != projectionRequest) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "Captura cancelada: no se concedió permiso."
            return
        }
        val serviceIntent = Intent(this, CaptureService::class.java).apply {
            putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            putExtra(CaptureService.EXTRA_RULES, rulesInput.text.toString())
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        status.text = "Captura iniciada. Mantén activo el servicio de accesibilidad para que se puedan ejecutar los toques."
    }

    private fun refreshMacros(container: LinearLayout? = null) {
        val target = container ?: return
        scope.launch {
            val macros = withContext(Dispatchers.IO) { MacroDatabase.get(this@MainActivity).macroDao().getAll() }
            target.removeAllViews()
            if (macros.isEmpty()) {
                target.addView(TextView(this@MainActivity).apply { text = "Todavía no hay macros guardadas." })
            } else macros.forEach { macro ->
                target.addView(Button(this@MainActivity).apply {
                    text = "${macro.name} (ID ${macro.id}) — seleccionar"
                    setOnClickListener {
                        selectedMacroId = macro.id
                        nameInput.setText(macro.name)
                        rulesInput.setText(macro.rulesText)
                        status.text = "Seleccionada: ${macro.name}"
                    }
                }, matchWidth())
                target.addView(Button(this@MainActivity).apply {
                    text = "Eliminar ${macro.name}"
                    setOnClickListener {
                        scope.launch {
                            withContext(Dispatchers.IO) { MacroDatabase.get(this@MainActivity).macroDao().deleteById(macro.id) }
                            MacroScheduler(this@MainActivity).cancel(macro.id)
                            refreshMacros(target)
                            status.text = "Macro eliminada."
                        }
                    }
                }, matchWidth())
            }
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
