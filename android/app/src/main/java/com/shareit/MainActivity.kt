package com.shareit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import com.shareit.server.Server
import java.net.Inet4Address
import java.security.SecureRandom

private const val PORT = 8080
private const val USER = "phone"

private const val BACKGROUND = 0xFF14161A.toInt()
private const val CARD = 0xFF23272F.toInt()
private const val TEXT = 0xFFFFFFFF.toInt()
private const val MUTED = 0xFF9AA0A6.toInt()
private const val GREEN = 0xFF2E7D32.toInt()
private const val RED = 0xFFC62828.toInt()
private const val GREY = 0xFF3C4043.toInt()

class MainActivity : Activity() {
    private lateinit var state: TextView
    private lateinit var card: View
    private lateinit var url: TextView
    private lateinit var pass: TextView
    private lateinit var hint: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = TextView(this).apply { textSize = 14f; setTextColor(TEXT); setPadding(12.dp(), 4.dp(), 12.dp(), 4.dp()) }
        url = value()
        pass = value()
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 16)
            setPadding(20.dp(), 12.dp(), 20.dp(), 20.dp())
            addView(label("On the Mac: Finder → ⌘K → type"))
            addView(url)
            addView(label("Name"))
            addView(value().apply { text = USER })
            addView(label("Password"))
            addView(pass)
        }
        hint = TextView(this).apply { textSize = 15f; setTextColor(MUTED) }
        button = Button(this).apply {
            textSize = 18f
            isAllCaps = false
            setTextColor(TEXT)
            stateListAnimator = null
            setOnClickListener { if (ServerService.url == null) start() else stop() }
        }
        val version = TextView(this).apply {
            text = "Version ${packageManager.getPackageInfo(packageName, 0).versionName}"
            textSize = 12f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            // Android 15+ draws the app under the system bars, so pad by their size.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(24.dp(), bars.top + 24.dp(), 24.dp(), bars.bottom + 16.dp())
                insets
            }
            addView(TextView(context).apply {
                text = "share-it"
                textSize = 30f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
            })
            addView(state, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 8.dp() })
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 24.dp() })
            addView(hint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 16.dp() })
            addView(button, LayoutParams(LayoutParams.MATCH_PARENT, 56.dp()).apply { topMargin = 24.dp() })
            addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)) // pushes the version to the bottom
            addView(version)
        })
        // Android 13+ hides the "share-it is on" notification without this permission. The server still runs.
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }

    // The server can stop from the notification while this screen is closed, so read the state each time.
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun start() {
        if (!Environment.isExternalStorageManager()) {
            render("Allow \"All files access\" for share-it, then come back and tap Start.")
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val ip = wifiIp() ?: return render("Connect the phone to Wi-Fi first.")
        val password = (1..8).map { CHARS[random.nextInt(CHARS.length)] }.joinToString("")
        try {
            // Add each change to Android's media index, so Gallery and the Files app show it.
            Server.start(Environment.getExternalStorageDirectory().path, PORT.toLong(), USER, password) { path ->
                MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
            }
        } catch (e: Exception) {
            return render("Could not start: ${e.message}")
        }
        ServerService.url = "http://$ip:$PORT"
        ServerService.pass = password
        startForegroundService(Intent(this, ServerService::class.java).putExtra(ServerService.EXTRA_URL, ServerService.url))
        render()
    }

    private fun stop() {
        stopService(Intent(this, ServerService::class.java)) // its onDestroy stops the server
        Server.stop()
        ServerService.url = null
        ServerService.pass = null
        render()
    }

    private fun render(message: String? = null) {
        val running = ServerService.url != null
        state.text = if (running) "●  Sharing" else "●  Stopped"
        state.background = rounded(if (running) GREEN else GREY, 99)
        card.visibility = if (running) View.VISIBLE else View.GONE
        url.text = ServerService.url
        pass.text = ServerService.pass
        hint.text = message ?: if (running) {
            "Copies continue when the screen locks. Tap Stop when you finish."
        } else {
            "Connect this phone and the computer to the same Wi-Fi, then tap Start."
        }
        button.text = if (running) "Stop sharing" else "Start sharing"
        button.background = rounded(if (running) RED else GREEN, 12)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(MUTED)
        setPadding(0, 12.dp(), 0, 2.dp())
    }

    private fun value() = TextView(this).apply {
        textSize = 22f
        typeface = Typeface.MONOSPACE
        setTextColor(TEXT)
        setTextIsSelectable(true)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp.dp().toFloat()
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    // The Wi-Fi network is not always the active network (for example, Wi-Fi without internet), so check all networks.
    // A VPN over Wi-Fi also reports TRANSPORT_WIFI, so skip VPN networks: the Mac cannot reach the VPN address.
    private fun wifiIp(): String? {
        val cm = getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks
            .filter {
                val caps = cm.getNetworkCapabilities(it)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
            .flatMap { cm.getLinkProperties(it)?.linkAddresses.orEmpty() }
            .map { it.address }
            .firstOrNull { it is Inet4Address }
            ?.hostAddress
    }

    private companion object {
        const val CHARS = "abcdefghjkmnpqrstuvwxyz23456789" // no look-alike characters (l, 1, o, 0, i)
        val random = SecureRandom()
    }
}
