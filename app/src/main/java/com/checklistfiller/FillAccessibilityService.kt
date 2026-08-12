package com.checklistfiller

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class FillAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "ChecklistFiller"
        @Volatile var instance: FillAccessibilityService? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility conectado")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun currentPackage(): String = rootInActiveWindow?.packageName?.toString() ?: ""

    fun captureCurrentScreen(): List<FieldEntry> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<FieldEntry>()
        walk(root) { node ->
            val cls = node.className?.toString() ?: ""
            val id = node.viewIdResourceName
            val hint = node.hintText()
            val current = node.text?.toString() ?: ""
            val isSpinner = cls.contains("Spinner", true)
            val isEditable = node.isEditable || cls.contains("EditText", true)
            if (isSpinner || isEditable) {
                val matchType = if (!id.isNullOrBlank()) "id" else "hint"
                val matchKey = if (matchType == "id") id!! else hint
                if (matchKey.isNotBlank()) {
                    out.add(
                        FieldEntry(
                            matchType = matchType,
                            matchKey = matchKey,
                            kind = if (isSpinner) "spinner" else "text",
                            value = current,
                            variable = false,
                            label = if (hint.isNotBlank()) hint else (id ?: "campo")
                        )
                    )
                }
            }
        }
        Log.d(TAG, "Capturados ${out.size} campos")
        return out
    }

    fun fill(template: Template) {
        val root = rootInActiveWindow
        if (root == null) { toast("Nada na tela para preencher"); return }
        val textFields = template.fields.filter { !it.variable && it.kind == "text" }
        val spinnerFields = template.fields.filter { !it.variable && it.kind == "spinner" }
        var textOk = 0
        for (f in textFields) {
            val node = findNode(root, f) ?: continue
            if (setText(node, f.value)) textOk++
        }
        Log.d(TAG, "Textos: $textOk/${textFields.size}")
        fillSpinnersSequential(spinnerFields, 0)
        toast("Preenchendo ${textFields.size} textos e ${spinnerFields.size} selecoes...")
    }

    private fun fillSpinnersSequential(list: List<FieldEntry>, index: Int) {
        if (index >= list.size) {
            main.postDelayed({ toast("Preenchimento concluido") }, 300)
            return
        }
        val f = list[index]
        val root = rootInActiveWindow
        val spinner = if (root != null) findNode(root, f) else null
        if (spinner == null) { fillSpinnersSequential(list, index + 1); return }
        spinner.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        main.postDelayed({
            val opened = rootInActiveWindow
            val option = if (opened != null) findByText(opened, f.value) else null
            if (option != null) {
                clickable(option)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            main.postDelayed({ fillSpinnersSequential(list, index + 1) }, 350)
        }, 450)
    }

    private fun findNode(root: AccessibilityNodeInfo, f: FieldEntry): AccessibilityNodeInfo? {
        if (f.matchType == "id") {
            val byId = root.findAccessibilityNodeInfosByViewId(f.matchKey)
            if (!byId.isNullOrEmpty()) return byId[0]
            return null
        }
        var found: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (found != null) return@walk
            val cls = node.className?.toString() ?: ""
            val ok = node.isEditable || cls.contains("EditText", true) || cls.contains("Spinner", true)
            if (ok && node.hintText().equals(f.matchKey, ignoreCase = true)) found = node
        }
        return found
    }

    private fun findByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val byText = root.findAccessibilityNodeInfosByText(text)
        byText?.firstOrNull { it.text?.toString().equals(text, ignoreCase = true) }?.let { return it }
        return byText?.firstOrNull()
    }

    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 5) {
            if (n.isClickable) return n
            n = n.parent
            hops++
        }
        return node
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        return try {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) { false }
    }

    private inline fun walk(node: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            action(n)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
    }

    private fun AccessibilityNodeInfo.hintText(): String {
        val h = this.hintText?.toString()
        if (!h.isNullOrBlank()) return h
        val cd = this.contentDescription?.toString()
        return cd ?: ""
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
