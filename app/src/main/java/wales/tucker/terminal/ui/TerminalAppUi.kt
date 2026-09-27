package wales.tucker.terminal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import wales.tucker.terminal.SshLink
import wales.tucker.terminal.data.Settings
import wales.tucker.terminal.ui.common.appContainer
import wales.tucker.terminal.ui.hosts.HostEditorScreen
import wales.tucker.terminal.ui.home.HomeScreen
import wales.tucker.terminal.ui.keys.KeyImportScreen
import wales.tucker.terminal.ui.settings.KnownHostsScreen
import wales.tucker.terminal.ui.sftp.SftpScreen
import wales.tucker.terminal.ui.terminal.TerminalScreen

object Routes {
    const val HOME = "home"
    const val HOST_EDITOR = "host?id={id}&duplicate={duplicate}"
    const val TERMINAL = "terminal/{sessionId}"
    const val SFTP = "sftp/{sessionId}"
    const val KNOWN_HOSTS = "knownHosts"
    const val KEY_IMPORT = "keyImport"

    fun hostEditor(id: Long? = null, duplicate: Boolean = false) = "host?id=${id ?: -1}&duplicate=$duplicate"
    fun terminal(sessionId: Int) = "terminal/$sessionId"
    fun sftp(sessionId: Int) = "sftp/$sessionId"
}

@Composable
fun TerminalAppUi(
    settings: Settings,
    locked: Boolean,
    onUnlock: () -> Unit,
    deepLinks: Channel<SshLink>,
) {
    // The nav controller and the screens' saveable state live above the lock check, so unlocking
    // returns to the screen that was open rather than starting again from the hosts list.
    val navController = rememberNavController()
    val stateHolder = rememberSaveableStateHolder()
    if (locked) {
        LockScreen(onUnlock)
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

    LaunchedEffect(Unit) {
        for (link in deepLinks) {
            openSession { container.sessionManager.openQuick(link.username, link.hostname, link.port).id }
        }
    }

    stateHolder.SaveableStateProvider("app") { AppNavHost(navController, settings, openSession) }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    settings: Settings,
    openSession: (suspend () -> Int) -> Unit,
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
                onConnect = { host -> openSession { container.sessionManager.open(host).id } },
                onQuickConnect = { link -> openSession { container.sessionManager.openQuick(link.username, link.hostname, link.port).id } },
                onOpenSession = { id -> navController.navigate(Routes.terminal(id)) { launchSingleTop = true } },
                onEditHost = { id, duplicate -> navController.navigate(Routes.hostEditor(id, duplicate)) },
                onImportKey = { navController.navigate(Routes.KEY_IMPORT) },
                onKnownHosts = { navController.navigate(Routes.KNOWN_HOSTS) },
            )
        }
        composable(
            Routes.HOST_EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("duplicate") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val duplicate = entry.arguments?.getBoolean("duplicate") ?: false
            HostEditorScreen(hostId = id, duplicate = duplicate, onDone = { navController.popBackStack() })
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
                onClosed = { navController.popBackStackTo(Routes.HOME) },
            )
        }
        composable(Routes.SFTP, arguments = listOf(navArgument("sessionId") { type = NavType.IntType })) { entry ->
            SftpScreen(sessionId = entry.arguments?.getInt("sessionId") ?: 0, onBack = { navController.popBackStack() })
        }
        composable(Routes.KNOWN_HOSTS) {
            KnownHostsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.KEY_IMPORT) {
            KeyImportScreen(onDone = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.popBackStackTo(route: String) {
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

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Terminal is locked", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Authenticate to access your servers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onUnlock, modifier = Modifier.padding(horizontal = 32.dp)) {
                    Icon(Icons.Rounded.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Unlock")
                }
            }
        }
    }
}
