package com.example.frida

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FridaScript(
    val id: String,
    val title: String,
    val description: String,
    val code: String,
    val category: String = "Hooks"
)

enum class FridaSessionStatus {
    DETACHED,
    ATTACHING,
    HOOKING_ACTIVE,
    PAUSED,
    ERROR
}

data class HookConsoleLog(
    val id: String,
    val timestamp: String,
    val tag: String,
    val level: String, // INFO, HOOK, WARN, ERROR
    val message: String
)

object FridaManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var hookingJob: Job? = null

    private val _sessionStatus = MutableStateFlow(FridaSessionStatus.DETACHED)
    val sessionStatus: StateFlow<FridaSessionStatus> = _sessionStatus.asStateFlow()

    private val _logs = MutableStateFlow<List<HookConsoleLog>>(emptyList())
    val logs: StateFlow<List<HookConsoleLog>> = _logs.asStateFlow()

    private val _targetPackageName = MutableStateFlow("com.example.targetapp")
    val targetPackageName: StateFlow<String> = _targetPackageName.asStateFlow()

    val defaultScripts = listOf(
        FridaScript(
            id = "ssl_bypass",
            title = "Universal SSL Pinning Bypass",
            description = "Hooks TrustManager, OkHttpClient, and Conscrypt to bypass TLS validation.",
            code = """
Java.perform(function() {
    var TrustManagerImpl = Java.use('com.android.org.conscrypt.TrustManagerImpl');
    TrustManagerImpl.verifyChain.implementation = function(untrustedChain, authType, session, parameters, authType2, host) {
        console.log('[+] Bypassed TrustManagerImpl.verifyChain for: ' + host);
        return untrustedChain;
    };
    var OkHttpClient = Java.use('okhttp3.OkHttpClient');
    console.log('[+] Frida SSL Pinning Hook Active.');
});
            """.trimIndent(),
            category = "Security"
        ),
        FridaScript(
            id = "crypto_monitor",
            title = "AES/RSA Cipher Monitor",
            description = "Intersects javax.crypto.Cipher.doFinal to inspect plaintext & keys.",
            code = """
Java.perform(function() {
    var Cipher = Java.use('javax.crypto.Cipher');
    Cipher.doFinal.overload('[B').implementation = function(input) {
        console.log('[Cipher.doFinal] Intercepted payload len: ' + input.length);
        return this.doFinal(input);
    };
    console.log('[+] Crypto Hook Attached.');
});
            """.trimIndent(),
            category = "Crypto"
        ),
        FridaScript(
            id = "native_dlopen",
            title = "Native dlopen & Syscall Tracer",
            description = "Traces native .so library loading and symbol resolutions.",
            code = """
Interceptor.attach(Module.findExportByName(null, "dlopen"), {
    onEnter: function(args) {
        var path = Memory.readUtf8String(args[0]);
        console.log('[dlopen] Loading native library: ' + path);
    }
});
            """.trimIndent(),
            category = "Native"
        ),
        FridaScript(
            id = "custom_script",
            title = "Custom Instrumentation Script",
            description = "User-authored JavaScript runtime hooking script.",
            code = """
Java.perform(function() {
    console.log('[+] Custom Frida Script Initialized on target process');
});
            """.trimIndent(),
            category = "Custom"
        )
    )

    fun setTargetPackage(packageName: String) {
        _targetPackageName.value = packageName
    }

    fun startHookingSession(scriptCode: String) {
        hookingJob?.cancel()
        _sessionStatus.value = FridaSessionStatus.ATTACHING

        logMessage("SYS", "INFO", "Attaching Frida session to target process: ${_targetPackageName.value}")

        hookingJob = scope.launch {
            delay(1000)
            _sessionStatus.value = FridaSessionStatus.HOOKING_ACTIVE
            logMessage("FRIDA", "HOOK", "Agent script successfully compiled & injected into PID.")
            logMessage("FRIDA", "INFO", "Hooking runtime functions...")

            // Stream simulated runtime intercept logs
            val sampleEvents = listOf(
                "Hooked java.net.URL.openConnection() -> https://api.service.internal/v1/sync",
                "Intercepted javax.crypto.Cipher.init(opmode=1, key=AES_256)",
                "dlopen('/data/app/lib/arm64/libnative-core.so') [SUCCESS]",
                "Bypassed CertificatePinner check for api.domain.org",
                "Function entry: com.example.security.KeyManager.getSecretKey()",
                "Intercepted SQLite Query: SELECT * FROM user_credentials"
            )

            var index = 0
            while (_sessionStatus.value == FridaSessionStatus.HOOKING_ACTIVE) {
                delay(3000)
                val event = sampleEvents[index % sampleEvents.size]
                logMessage("HOOK", "HOOK", event)
                index++
            }
        }
    }

    fun pauseHookingSession() {
        if (_sessionStatus.value == FridaSessionStatus.HOOKING_ACTIVE) {
            _sessionStatus.value = FridaSessionStatus.PAUSED
            logMessage("SYS", "WARN", "Hooking session paused by user.")
        }
    }

    fun stopHookingSession() {
        hookingJob?.cancel()
        _sessionStatus.value = FridaSessionStatus.DETACHED
        logMessage("SYS", "INFO", "Frida session detached. Hooks removed.")
    }

    fun clearConsoleLogs() {
        _logs.value = emptyList()
    }

    private fun logMessage(tag: String, level: String, message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val time = sdf.format(Date())
        val newLog = HookConsoleLog(
            id = System.nanoTime().toString(),
            timestamp = time,
            tag = tag,
            level = level,
            message = message
        )
        _logs.value = (_logs.value + newLog).takeLast(200)
    }
}
