package com.checklistfiller

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var statusView: TextView
    private var listView: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)
        setContentView(scroll)

        title("Checklist Filler")
        statusView = TextView(this).apply { setPadding(0, dp(8), 0, dp(16)) }
        root.addView(statusView)

        button("1. Permitir Acessibilidade") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("2. Permitir Sobreposicao") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        button("3. Iniciar botao flutuante") {
            if (!canDrawOverlays()) { toast("Conceda a sobreposicao primeiro"); return@button }
            val i = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
            toast("Botao flutuante ligado")
        }
        button("Parar botao flutuante") {
            stopService(Intent(this, FloatingButtonService::class.java))
        }
        divider()
        button("Capturar tela atual (criar template)") { capturarTemplate() }
        divider()
        root.addView(TextView(this).apply {
            text = "Templates salvos:"; textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
        })
        listView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listView)
        renderTemplates()
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    private fun capturarTemplate() {
        val svc = FillAccessibilityService.instance
        if (svc == null) { toast("Ative a Acessibilidade primeiro"); return }
        val campos = svc.captureCurrentScreen()
        if (campos.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Nada capturado")
                .setMessage("Nao encontrei campos. Abra o checklist preenchido e volte aqui para capturar.")
                .setPositiveButton("Ok", null).show()
            return
        }
        val input = EditText(this).apply { hint = "Ex.: Checklist Vistoria" }
        AlertDialog.Builder(this)
            .setTitle("Nome do template")
            .setMessage("${campos.size} campos capturados.")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val nome = input.text.toString().ifBlank { "Template ${System.currentTimeMillis()}" }
                TemplateStore.upsert(this, Template(nome, svc.currentPackage(), campos.toMutableList()))
                toast("Salvo: $nome"); renderTemplates()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun renderTemplates() {
        val container = listView ?: return
        container.removeAllViews()
        val templates = TemplateStore.load(this)
        if (templates.isEmpty()) {
            container.addView(TextView(this).apply { text = "(nenhum ainda)" })
            return
        }
        templates.forEach { tpl ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundColor(0xFFF2F2F2.toInt())
            }
            card.addView(TextView(this).apply {
                text = "${tpl.name}  (${tpl.fields.size} campos)"; textSize = 15f
            })
            card.addView(TextView(this).apply {
                text = "App: ${tpl.targetPackage}"; textSize = 11f
                setTextColor(0xFF777777.toInt())
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(smallButton("Editar campos") { editarCampos(tpl) })
            row.addView(smallButton("Excluir") { TemplateStore.delete(this, tpl.name); renderTemplates() })
            card.addView(row)
            container.addView(card)
            container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
            })
        }
    }

    private fun editarCampos(tpl: Template) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(box) }
        val edits = mutableListOf<Triple<FieldEntry, EditText, CheckBox>>()
        tpl.fields.forEach { f ->
            box.addView(TextView(this).apply {
                text = "${f.label}  [${f.kind}]"; textSize = 13f
                setPadding(0, dp(8), 0, dp(2))
            })
            val value = EditText(this).apply {
                setText(f.value); hint = "valor"
                inputType = InputType.TYPE_CLASS_TEXT
            }
            val variavel = CheckBox(this).apply {
                text = "Campo variavel (o app pula)"; isChecked = f.variable
            }
            box.addView(value); box.addView(variavel)
            edits.add(Triple(f, value, variavel))
        }
        AlertDialog.Builder(this)
            .setTitle("Editar: ${tpl.name}")
            .setView(scroll)
            .setPositiveButton("Salvar") { _, _ ->
                edits.forEach { (f, ed, cb) -> f.value = ed.text.toString(); f.variable = cb.isChecked }
                TemplateStore.upsert(this, tpl); toast("Atualizado"); renderTemplates()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun atualizarStatus() {
        val acess = FillAccessibilityService.instance != null
        val overlay = canDrawOverlays()
        statusView.text = buildString {
            append(if (acess) "Acessibilidade: ativa\n" else "Acessibilidade: desativada\n")
            append(if (overlay) "Sobreposicao: permitida" else "Sobreposicao: nao permitida")
        }
    }

    private fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun title(t: String) {
        root.addView(TextView(this).apply {
            text = t; textSize = 22f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
    }

    private fun button(text: String, onClick: () -> Unit) {
        root.addView(Button(this).apply {
            this.text = text; isAllCaps = false
            setOnClickListener { onClick() }
        })
    }

    private fun smallButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text; isAllCaps = false; textSize = 12f
            setOnClickListener { onClick() }
        }

    private fun divider() {
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).also {
                it.topMargin = dp(12); it.bottomMargin = dp(12)
            }
            setBackgroundColor(0xFFCCCCCC.toInt())
        })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
