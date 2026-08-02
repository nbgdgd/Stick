package com.vpet.waifu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vpet.waifu.MainActivity
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.feedback.Cue
import com.vpet.waifu.feedback.PetSounds
import com.vpet.waifu.ui.character.SpritePacks
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.di.ApplicationScope
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.overlay.PetBubble
import com.vpet.waifu.ui.theme.VPetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the floating pet.
 *
 * It is a foreground service for one reason: the bubble has to survive the
 * player switching to another app, and a background service would be killed
 * within minutes. It is a [LifecycleService] — and additionally a
 * [ViewModelStoreOwner] and [SavedStateRegistryOwner] — because the bubble is a
 * Compose tree, and a `ComposeView` refuses to compose without those three
 * owners set on its view tree.
 */
@AndroidEntryPoint
class PetOverlayService :
    LifecycleService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    @Inject lateinit var repository: PetRepository
    @Inject lateinit var preferences: PetPreferences
    @Inject lateinit var tuning: PetTuning

    // The bubble was the one surface in the app with no sound at all: six
    // controls, every one of them mute.
    @Inject lateinit var sounds: PetSounds
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var overlayWindow: OverlayWindow

    /** Compose state, fed from the repository flow. */
    private var snapshot by mutableStateOf<PetSnapshot?>(null)

    /** Which character the player picked; the bubble has to agree with the app. */
    private var skinId by mutableStateOf("")
    private var panelExpanded by mutableStateOf(false)

    /**
     * A half-second clock. The bubble shows a live countdown and the character
     * FSM has time-limited reactions, so it needs "now" as state, not as a
     * value captured once at composition.
     */
    private var nowMillis by mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate() {
        // Must happen before the lifecycle reaches CREATED.
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        startForegroundNotification()
        overlayWindow = OverlayWindow(this)
        observePet()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            dismiss()
            return START_NOT_STICKY
        }

        // Losing the permission while running (or never having had it) means
        // there is nothing to show — don't sit in the foreground for nothing.
        if (!OverlayPermission.isGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!overlayWindow.isShowing) overlayWindow.show(createBubbleView())
        // START_STICKY so the system brings the pet back if it reclaims memory.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        overlayWindow.hide()
        viewModelStore.clear()
        super.onDestroy()
    }

    // --- state ---------------------------------------------------------------

    private fun observePet() {
        lifecycleScope.launch {
            repository.snapshot.collect { snapshot = it }
        }
        lifecycleScope.launch {
            preferences.settings.collect { skinId = it.petSkin }
        }
    }

    /**
     * The real game clock. WorkManager cannot go below 15 minutes, so while the
     * bubble is up the service ticks once a minute; the simulation is driven by
     * timestamps either way, so the two never double-count.
     */
    private fun startTicker() {
        lifecycleScope.launch {
            while (isActive) {
                repository.tick()
                delay(TICK_INTERVAL_MILLIS)
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                nowMillis = System.currentTimeMillis()
                delay(CLOCK_INTERVAL_MILLIS)
            }
        }
    }

    private fun act(cue: Cue? = null, action: suspend PetRepository.() -> PetSnapshot) {
        cue?.let(sounds::play)
        lifecycleScope.launch { repository.action() }
    }

    /**
     * Dismissing the bubble is a decision, not an accident: remember it so the
     * boot receiver doesn't resurrect her. The write runs on the application
     * scope because this service's own scope dies with [stopSelf].
     */
    private fun dismiss() {
        applicationScope.launch { preferences.setBubbleEnabled(false) }
        stopSelf()
    }

    // --- view ----------------------------------------------------------------

    private fun createBubbleView(): ComposeView = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@PetOverlayService)
        setViewTreeViewModelStoreOwner(this@PetOverlayService)
        setViewTreeSavedStateRegistryOwner(this@PetOverlayService)

        setContent {
            VPetTheme {
                val current = snapshot
                if (current != null) {
                    PetBubble(
                        pack = SpritePacks.load(this@PetOverlayService, skinId),
                        snapshot = current,
                        tuning = tuning,
                        nowMillis = nowMillis,
                        expanded = panelExpanded,
                        onTap = {
                            // She sleeps through taps; only a long press reaches her.
                            if (current.acceptsInteraction) {
                                panelExpanded = !panelExpanded
                                sounds.play(Cue.POP)
                            } else {
                                sounds.play(Cue.TAP)
                            }
                        },
                        onLongPress = {
                            // A long press is the shortcut: wake her, or a quick
                            // head pat without opening the panel.
                            if (current.isSleeping) act(Cue.TAP) { wake() } else act(Cue.PET_TAP) { pet() }
                        },
                        onDrag = { dx, dy -> overlayWindow.moveBy(dx, dy) },
                        onDragEnd = { overlayWindow.snapToNearestEdge() },
                        onFeed = { act(Cue.EAT) { feed() } },
                        onPet = { act(Cue.HAPPY) { pet() } },
                        onToggleSleep = {
                            if (current.isSleeping) act(Cue.TAP) { wake() } else act(Cue.TAP) { startSleep() }
                        },
                        onOpenApp = {
                            sounds.play(Cue.TAP)
                            panelExpanded = false
                            startActivity(
                                Intent(this@PetOverlayService, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        onHide = { sounds.play(Cue.TAP); dismiss() },
                    )
                }
            }
        }
    }

    // --- notification --------------------------------------------------------

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_channel_name),
                    // LOW: it is a status entry, not something to buzz about.
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = getString(R.string.overlay_channel_description) },
            )
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val hide = PendingIntent.getService(
            this,
            1,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pet_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_hide_bubble), hide)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.vpet.waifu.action.STOP_OVERLAY"
        private const val TICK_INTERVAL_MILLIS = 60_000L
        private const val CLOCK_INTERVAL_MILLIS = 500L

        fun start(context: Context) {
            if (!OverlayPermission.isGranted(context)) return
            context.startForegroundService(Intent(context, PetOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PetOverlayService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
