package com.example.bandcontroller

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.text.InputFilter
import android.text.InputType
import android.widget.*
import com.example.bandcontroller.macro.*
import com.example.bandcontroller.sms.DirectSmsSender
import com.example.bandcontroller.sms.SmsStatusStore

/** One-shot foreground macro: direct SMS after permission, or a WhatsApp Business draft. */
class MacroActivity : Activity() {
    private val engine = MacroEngine()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var smsChoice: RadioButton
    private lateinit var businessChoice: RadioButton
    private lateinit var phone: EditText
    private lateinit var message: EditText
    private lateinit var useBand: CheckBox
    private lateinit var audible: CheckBox
    private lateinit var arm: Button
    private lateinit var status: TextView
    private val inputs = mutableListOf<Button>()
    private var draft: MessageDraft? = null
    private var ownsSession = false
    private var smsSubscription: Int? = null
    private var notificationAsked = false
    private var result = "Disarmed. Review the recipient and message, then arm the macro."
    private var lastInput = "None"
    private var previousCount = 0
    private var playPauseCount = 0
    private var nextCount = 0
    private val refresh = object : Runnable {
        override fun run() {
            if (engine.expire(SystemClock.elapsedRealtime())) result = "Sequence timed out. Start again with ⏮."
            render(); handler.postDelayed(this, 200)
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(16),dp(20),dp(20)) }
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(android.view.WindowInsets.Type.systemBars()); view.setPadding(b.left,b.top,b.right,b.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }
            insets
        }
        scroll.requestApplyInsets()
        fun text(value: String, size: Float = 16f) = TextView(this).apply { text = value; textSize = size; setPadding(0,dp(8),0,dp(8)); layout.addView(this) }
        text("Android Macro Engine", 26f)
        text("⏮  →  ⏯  →  ⏭", 28f)
        text("Previous → Play/Pause → Next within 8 seconds sends SMS directly, or opens a WhatsApp Business draft. Each match disarms the macro. SMS uses your SIM and normal charges may apply.")
        text("Macro action")
        val targets = RadioGroup(this)
        businessChoice = RadioButton(this).apply { id = android.view.View.generateViewId(); text = "WhatsApp Business draft" }
        smsChoice = RadioButton(this).apply { id = android.view.View.generateViewId(); text = "Send SMS directly (uses your SIM)" }
        targets.addView(businessChoice); targets.addView(smsChoice)
        targets.check(if (savedInstanceState?.getString("target") == "WHATSAPP_BUSINESS") businessChoice.id else smsChoice.id)
        layout.addView(targets)
        text("Recipient (country code required for non-Sri Lankan numbers)")
        phone = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(savedInstanceState?.getString("phone") ?: MessageDraft.DEFAULT_PHONE)
            contentDescription = "Recipient with country code"
        }
        layout.addView(phone)
        text("0761069598 is treated as Sri Lanka +94 76 106 9598. Edit the country code if that is not your intended recipient.", 13f)
        text("Message")
        message = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(500))
            setText(savedInstanceState?.getString("message") ?: MessageDraft.DEFAULT_MESSAGE)
            contentDescription = "Message to prefill in the selected messaging app"
        }
        layout.addView(message)
        useBand = CheckBox(this).apply { text = "Use band media controls (optional; touch works without it)" }
        audible = CheckBox(this).apply { text = "Audible local loop (recommended for reliable band input)"; isChecked = true }
        layout.addView(useBand); layout.addView(audible)
        arm = Button(this).apply { setOnClickListener { if (engine.armed) disarm("Disarmed by you.") else armMacro() } }
        layout.addView(arm)
        val row = LinearLayout(this)
        listOf("⏮" to MacroButton.PREVIOUS, "⏯" to MacroButton.PLAY_PAUSE, "⏭" to MacroButton.NEXT).forEach { (label, command) ->
            val button = Button(this).apply {
                text = label; textSize = 26f
                contentDescription = when (command) { MacroButton.PREVIOUS -> "Previous"; MacroButton.PLAY_PAUSE -> "Play or pause"; MacroButton.NEXT -> "Next" }
                setOnClickListener { receive(command, "Touch ${command.name}") }
            }
            inputs.add(button); row.addView(button, LinearLayout.LayoutParams(0,dp(64),1f))
        }
        layout.addView(row)
        status = TextView(this).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(0,dp(12),0,dp(12)) }
        layout.addView(status)
        targets.setOnCheckedChangeListener { _, _ -> render() }
        text("SMS sends immediately when the armed sequence matches. Set a default SMS SIM in phone Settings; review the number and message before arming. Long messages can use multiple charged segments. WhatsApp still opens Business for you to tap Send. Game controls do not trigger this macro.", 13f)
        layout.addView(Button(this).apply { text = "Back to Band Controller"; setOnClickListener { finish() } })
        render()
    }
    private fun armMacro() {
        val normalized = MessageDraft.normalizePhone(phone.text.toString())
        if (normalized == null) { phone.error = "Enter a valid international number, e.g. +94761069598"; return }
        if (message.text.isBlank()) { message.error = "Enter a message"; return }
        if (smsChoice.isChecked) {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 4)
                result = "Allow SMS permission, then tap Arm SMS send again. Nothing is sent by granting permission."
                render(); return
            }
            smsSubscription = DirectSmsSender.defaultSubscription()
            if (smsSubscription == null) {
                result = "Choose a default SMS SIM in your phone's SIM settings (not Ask every time), then arm again. No SMS requested."
                render(); return
            }
        }
        if (useBand.isChecked && !notificationAsked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationAsked = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
            result = "Answer the notification prompt, then tap Arm again."; render(); return
        }
        phone.setText("+$normalized")
        draft = MessageDraft(normalized, message.text.toString(), if (smsChoice.isChecked) DraftTarget.SMS else DraftTarget.WHATSAPP_BUSINESS)
        engine.arm()
        result = "Armed for +$normalized. ${if (smsChoice.isChecked) "Matching sends SMS immediately using your selected default SIM." else "Matching opens a WhatsApp Business draft."} Enter ⏮ → ⏯ → ⏭ within 8 seconds."
        if (useBand.isChecked) {
            ownsSession = true
            startForegroundService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.START)
                .putExtra("macro", true).putExtra("audible", audible.isChecked))
        }
        Diagnostics.record(this, "Message macro armed; target=${draft?.target}; media=$ownsSession")
        render()
    }
    private fun receive(command: MacroButton, description: String) {
        lastInput = description
        when (command) { MacroButton.PREVIOUS -> previousCount++; MacroButton.PLAY_PAUSE -> playPauseCount++; MacroButton.NEXT -> nextCount++ }
        Diagnostics.record(this, "Macro input: $description; armed=${engine.armed}")
        when (engine.accept(command, SystemClock.elapsedRealtime())) {
            MacroResult.TRIGGERED -> openDraft()
            MacroResult.PROGRESS -> result = "Sequence ${engine.progress}/3 received."
            MacroResult.RESET -> result = "Wrong order. Begin again with ⏮."
            MacroResult.IGNORED -> result = "Input logged; macro is disarmed."
        }
        render()
    }
    private fun openDraft() {
        val request = draft ?: return
        val subscription = smsSubscription
        disarm("Sequence matched; macro disarmed before executing action.")
        if (request.target == DraftTarget.SMS) {
            result = if (subscription == null) "No SMS SIM selected. No SMS requested; choose a default SIM and arm again."
                else DirectSmsSender.send(this, request, subscription)
            return
        }
        val intent = when (request.target) {
            DraftTarget.WHATSAPP_BUSINESS -> {
                val uri = Uri.Builder().scheme("https").authority("wa.me").appendPath(request.phone)
                    .appendQueryParameter("text", request.message).build()
                Intent(Intent.ACTION_VIEW, uri).setPackage(MessageDraft.BUSINESS_PACKAGE)
            }
            DraftTarget.SMS -> return
        }
        val appName = "WhatsApp Business"
        if (intent.resolveActivity(packageManager) == null) {
            result = "$appName is not available to open this draft in this Android profile. Install/enable it, then arm again."
            Diagnostics.record(this, "Macro matched; ${request.target} draft handler unavailable")
            return
        }
        try {
            startActivity(intent)
            result = "$appName draft requested. Tap Send there. Sending and delivery are not verified."
            Diagnostics.record(this, "Macro matched; requested ${request.target} draft (not sent)")
        } catch (_: ActivityNotFoundException) {
            result = "$appName could not open. Update/enable it and try again."
        } catch (_: SecurityException) {
            result = "Android blocked opening $appName. Check its availability in this profile."
        }
    }

    private fun disarm(reason: String) {
        engine.disarm(); draft = null; smsSubscription = null
        if (ownsSession) stopService(Intent(this, PlaybackService::class.java))
        ownsSession = false; result = reason
        if (::status.isInitialized) render()
    }
    private fun render() {
        arm.text = if (engine.armed) "Disarm macro" else if (smsChoice.isChecked) "Arm SMS send" else "Arm WhatsApp draft"
        listOf(phone,message,useBand,audible,businessChoice,smsChoice).forEach { it.isEnabled = !engine.armed }
        inputs.forEach { it.isEnabled = engine.armed }
        fun direction(count: Int) = if (count == 0) "unverified" else "input received; band source unverified"
        status.text = "${if (engine.armed) "ARMED" else "DISARMED"} · sequence ${engine.progress}/3\nLast: $lastInput\n⏮ $previousCount (${direction(previousCount)})\n⏯ $playPauseCount\n⏭ $nextCount (${direction(nextCount)})\n$result\nLatest SMS result: ${SmsStatusStore.summary(this)}\nMedia session activity does not prove a band connection."
    }
    override fun onResume() {
        super.onResume()
        BandMediaController.attach(this, { raw -> MacroEngine.fromMedia(raw)?.let { receive(it, "Media $raw (source unknown)") } }, { reason ->
            if (ownsSession) disarm(reason)
        })
        handler.post(refresh)
    }
    override fun onPause() {
        if (engine.armed || ownsSession) disarm("Disarmed because the macro screen lost focus. Arm again when ready.")
        handler.removeCallbacks(refresh); BandMediaController.detach(this)
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && engine.armed) disarm("Disarmed because the macro screen lost focus.")
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4) {
            result = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                "SMS permission granted. Review the recipient/message and tap Arm SMS send."
                else "SMS permission was not granted. Enable SMS permission in Android app Settings to use direct sending. No SMS requested."
            render()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("target", if (smsChoice.isChecked) "SMS" else "WHATSAPP_BUSINESS")
        outState.putString("phone",phone.text.toString()); outState.putString("message",message.text.toString())
        super.onSaveInstanceState(outState)
    }
}
