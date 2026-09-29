package wales.tucker.seren.ssh.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.ui.LockScreen
import wales.tucker.seren.ssh.KeyFile
import wales.tucker.seren.ssh.MainActivity
import wales.tucker.seren.ssh.SessionLink
import wales.tucker.seren.ssh.SshLink
import wales.tucker.seren.ssh.data.Host
import wales.tucker.seren.ssh.data.Settings
import wales.tucker.seren.ssh.session.SessionState
import wales.tucker.seren.ssh.session.TerminalSession
import wales.tucker.seren.ssh.ui.common.appContainer
import wales.tucker.seren.ssh.ui.hosts.HostEditorScreen
import wales.tucker.seren.ssh.ui.home.HomeScreen
import wales.tucker.seren.ssh.ui.keys.KeyImportScreen
import wales.tucker.seren.ssh.ui.settings.ExtraKeysScreen
import wales.tucker.seren.ssh.ui.settings.KnownHostsScreen
import wales.tucker.seren.ssh.ui.sftp.SftpScreen
import wales.tucker.seren.ssh.ui.terminal.TerminalScreen
import wales.tucker.seren.ssh.ui.upload.SharedFiles
import wales.tucker.seren.ssh.ui.upload.UploadTargetScreen

object Routes {
    const val HOME = "home"
    const val HOST_EDITOR = "host?id={id}&duplicate={duplicate}&session={session}"
    const val TERMINAL = "terminal/{sessionId}"
    const val SFTP = "sftp/{sessionId}"
    const val KNOWN_HOSTS = "knownHosts"
    const val EXTRA_KEYS = "extraKeys"
    const val KEY_IMPORT = "keyImport"
    const val UPLOAD = "upload"

    fun hostEditor(id: Long? = null, duplicate: Boolean = false) = "host?id=${id ?: -1}&duplicate=$duplicate&session=-1"

    /** A new host filled in from a quick connect session. */
    fun saveSessionAsHost(sessionId: Int) = "host?id=-1&duplicate=false&session=$sessionId"
    fun terminal(sessionId: Int) = "terminal/$sessionId"
    fun sftp(sessionId: Int) = "sftp/$sessionId"
}

@Composable
fun TerminalAppUi(
    settings: Settings,
    locked: Boolean,
    onUnlock: () -> Unit,
    deepLinks: Channel<SshLink>,
    sessionLinks: Channel<SessionLink>,
    sharedFiles: Channel<List<Uri>> = Channel(),
    keyFiles: Channel<KeyFile> = Channel(),
) {
    // The nav controller and the screens' saveable state live above the lock check, so unlocking
    // returns to the screen that was open rather than starting again from the hosts list.
    val navController = rememberNavController()
    val stateHolder = rememberSaveableStateHolder()
    if (locked) {
        LockScreen(appName = "Seren SSH", message = "Authenticate to access your servers", onUnlock = onUnlock)
        return
    }
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val requestNotifications = rememberNotificationPermissionRequester()
    val context = LocalContext.current

    val openSession: (suspend () -> Int) -> Unit = { open ->
        requestNotifications()
        scope.launch(Dispatchers.Main) {
            try {
                val id = open()
                navController.navigate(Routes.terminal(id)) { launchSingleTop = true }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open session: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var quickConnectPrefill by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        for (link in deepLinks) {
            if (link.hasUser) {
                openSession { container.sessionManager.openQuick(link.username, link.hostname, link.port).id }
            } else {
                // Can't connect without a username: fill in quick connect and let the user add one.
                navController.popBackStackTo(Routes.HOME)
                quickConnectPrefill = "@${link.address}"
                Toast.makeText(context, "Enter a username to connect to ${link.hostname}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        for (link in sessionLinks) {
            if (container.sessionManager.get(link.sessionId) != null) {
                navController.navigate(Routes.terminal(link.sessionId)) {
                    // Replace any terminal (and SFTP screen) already open, as the switcher does.
                    popUpTo(Routes.TERMINAL) { inclusive = true }
                    launchSingleTop = true
                }
                if (link.openSftp) {
                    navController.navigate(Routes.sftp(link.sessionId))
                }
            }
        }
    }

    // Files shared with Seren SSH, until they are uploaded or people change their mind.
    var shared by remember { mutableStateOf<PendingUpload?>(null) }
    LaunchedEffect(Unit) {
        for (uris in sharedFiles) {
            val files = withContext(Dispatchers.IO) { SharedFiles.resolve(context, uris) }
            withContext(Dispatchers.Main) {
                shared = PendingUpload(files)
                navController.navigate(Routes.UPLOAD) { launchSingleTop = true }
            }
        }
    }

    // A private key from Seren Files, until Import key has read it.
    var pendingKey by remember { mutableStateOf<KeyFile?>(null) }
    LaunchedEffect(Unit) {
        for (key in keyFiles) {
            pendingKey = key
            navController.navigate(Routes.KEY_IMPORT) { launchSingleTop = true }
        }
    }

    val browseForUpload: (TerminalSession) -> Unit = { session ->
        shared = shared?.copy(sessionId = session.id)
        navController.navigate(Routes.sftp(session.id)) {
            popUpTo(Routes.UPLOAD) { inclusive = true }
        }
    }

    stateHolder.SaveableStateProvider("app") {
        AppNavHost(
            navController = navController,
            settings = settings,
            openSession = openSession,
            quickConnectPrefill = quickConnectPrefill,
            onPrefillConsumed = { quickConnectPrefill = null },
            shared = shared,
            onSharedDone = { shared = null },
            onUploadToSession = { session ->
                // An old session is connected again, asking for a password here if need be.
                if (session.state.value is SessionState.Failed || session.state.value is SessionState.Disconnected) session.reconnect()
                browseForUpload(session)
            },
            onUploadToHost = { host ->
                requestNotifications()
                scope.launch(Dispatchers.Main) {
                    try {
                        ensureAgentForHost(context as? MainActivity, container, host)
                        browseForUpload(container.sessionManager.open(host))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open session: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            pendingKey = pendingKey,
            onKeyRead = { pendingKey = null },
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    settings: Settings,
    openSession: (suspend () -> Int) -> Unit,
    quickConnectPrefill: String?,
    onPrefillConsumed: () -> Unit,
    shared: PendingUpload?,
    onSharedDone: () -> Unit,
    onUploadToSession: (TerminalSession) -> Unit,
    onUploadToHost: (Host) -> Unit,
    pendingKey: KeyFile?,
    onKeyRead: () -> Unit,
) {
    val container = appContainer()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeOut(tween(280)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                onConnect = { host ->
                    openSession {
                        ensureAgentForHost(context as? MainActivity, container, host)
                        container.sessionManager.open(host).id
                    }
                },
                onQuickConnect = { link -> openSession { container.sessionManager.openQuick(link.username, link.hostname, link.port).id } },
                quickConnectPrefill = quickConnectPrefill,
                onPrefillConsumed = onPrefillConsumed,
                onOpenSession = { id -> navController.navigate(Routes.terminal(id)) { launchSingleTop = true } },
                onEditHost = { id, duplicate -> navController.navigate(Routes.hostEditor(id, duplicate)) },
                onImportKey = { navController.navigate(Routes.KEY_IMPORT) },
                onKnownHosts = { navController.navigate(Routes.KNOWN_HOSTS) },
                onExtraKeys = { navController.navigate(Routes.EXTRA_KEYS) },
            )
        }
        composable(
            Routes.HOST_EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("duplicate") { type = NavType.BoolType; defaultValue = false },
                navArgument("session") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val duplicate = entry.arguments?.getBoolean("duplicate") ?: false
            val fromSession = entry.arguments?.getInt("session")?.takeIf { it > 0 }
            HostEditorScreen(hostId = id, duplicate = duplicate, fromSessionId = fromSession, onDone = { navController.popBackStack() })
        }
        composable(Routes.TERMINAL, arguments = listOf(navArgument("sessionId") { type = NavType.IntType })) { entry ->
            val sessionId = entry.arguments?.getInt("sessionId") ?: 0
            TerminalScreen(
                sessionId = sessionId,
                settings = settings,
                onBack = { navController.popBackStack() },
                onSwitchSession = { id ->
                    navController.navigate(Routes.terminal(id)) {
                        popUpTo(Routes.TERMINAL) { inclusive = true }
                    }
                },
                onOpenSftp = { navController.navigate(Routes.sftp(sessionId)) },
                onSaveAsHost = { navController.navigate(Routes.saveSessionAsHost(sessionId)) },
                onClosed = { navController.popBackStackTo(Routes.HOME) },
            )
        }
        composable(Routes.SFTP, arguments = listOf(navArgument("sessionId") { type = NavType.IntType })) { entry ->
            val sessionId = entry.arguments?.getInt("sessionId") ?: 0
            SftpScreen(
                sessionId = sessionId,
                onBack = {
                    if (shared?.sessionId == sessionId) onSharedDone()
                    navController.popBackStack()
                },
                incoming = shared?.takeIf { it.sessionId == sessionId }?.files,
                onIncomingHandled = onSharedDone,
            )
        }
        composable(Routes.UPLOAD) {
            val files = shared?.files
            // Nothing to upload (say after the app was restarted): leave, unless already leaving.
            LaunchedEffect(files) {
                if (files == null && navController.currentDestination?.route == Routes.UPLOAD) navController.popBackStack()
            }
            if (files != null) {
                UploadTargetScreen(
                    files = files,
                    onSession = onUploadToSession,
                    onHost = onUploadToHost,
                    onCancel = {
                        onSharedDone()
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(Routes.EXTRA_KEYS) {
            ExtraKeysScreen(settings = settings, onBack = { navController.popBackStack() })
        }
        composable(Routes.KNOWN_HOSTS) {
            KnownHostsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.KEY_IMPORT) {
            KeyImportScreen(
                keyFile = pendingKey,
                onKeyFileRead = onKeyRead,
                onDone = {
                    onKeyRead()
                    navController.popBackStack()
                },
            )
        }
    }
}


/** Unlocks the in-app agent when [host] needs ForwardAgent; throws with clear copy on refusal. */
private suspend fun ensureAgentForHost(
    activity: MainActivity?,
    container: wales.tucker.seren.ssh.AppContainer,
    host: Host,
) {
    if (!host.forwardAgent) return
    val settings = container.settings.settings.first()
    if (!settings.appLock) throw wales.tucker.seren.ssh.ssh.ForwardAgentRequiresAppLockException()
    if (container.agent.isUnlocked) return
    if (activity == null) throw wales.tucker.seren.ssh.ssh.AgentLockedException()
    val ok = suspendCancellableCoroutine { cont ->
        activity.unlockAgent { success -> cont.resume(success) }
    }
    if (!ok) throw wales.tucker.seren.ssh.ssh.AgentLockedException()
}

/** Shared files and, once people pick one, the session whose SFTP browser they go to. */
data class PendingUpload(val files: SharedFiles, val sessionId: Int? = null)

private fun NavHostController.popBackStackTo(route: String) {
    if (currentDestination?.route == route) return
    if (!popBackStack(route, inclusive = false)) navigate(route)
}

@Composable
private fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
