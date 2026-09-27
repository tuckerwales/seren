package wales.tucker.seren.files.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.core.ui.LockScreen
import wales.tucker.seren.core.ui.Messenger
import wales.tucker.seren.files.RevealRequest
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.fs.AndroidStorage
import wales.tucker.seren.files.fs.Reveal
import wales.tucker.seren.files.ui.folder.FolderScreen
import wales.tucker.seren.files.ui.home.HomeScreen
import java.io.File

object Routes {
    const val HOME = "home"
    const val FOLDER = "folder?path={path}&search={search}&highlight={highlight}"

    /** [highlight] names an item to scroll to and pick out, for "Show in Seren Files". */
    fun folder(path: String, search: Boolean = false, highlight: String? = null) =
        "folder?path=${android.net.Uri.encode(path)}&search=$search&highlight=${android.net.Uri.encode(highlight.orEmpty())}"
}

/** Whether Seren Files may use shared storage, and how to ask. Rechecked each time the app resumes. */
class StorageAccessState(val granted: Boolean, val request: () -> Unit)

val LocalStorageAccess = staticCompositionLocalOf { StorageAccessState(granted = true, request = {}) }

/** Opens folders and files from anywhere in the app. */
class Navigator(val openFolder: (File) -> Unit, val search: (File) -> Unit, val back: () -> Unit)

@Composable
fun FilesAppUi(settings: Settings, locked: Boolean, onUnlock: () -> Unit, reveals: Channel<RevealRequest> = Channel()) {
    // The nav controller and the screens' saveable state live above the lock check, so unlocking
    // returns to the screen that was open.
    val navController = rememberNavController()
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(scope) }
    val container = appContainer()
    val context = LocalContext.current

    var granted by remember { mutableStateOf(container.storage.hasAccess()) }
    LifecycleResumeEffect(Unit) {
        granted = container.storage.hasAccess()
        onPauseOrDispose {}
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = container.storage.hasAccess()
    }
    val access = StorageAccessState(granted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intents = listOfNotNull(AndroidStorage.accessSettingsIntent(context), AndroidStorage.allAccessSettingsIntent())
            val opened = intents.any { intent ->
                try {
                    context.startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
            if (!opened) messenger.show("Allow all files access for Seren Files in the system settings")
        } else {
            permissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    CompositionLocalProvider(LocalMessenger provides messenger, LocalStorageAccess provides access) {
        if (locked) {
            LockScreen(appName = "Seren Files", message = "Authenticate to see your files", onUnlock = onUnlock)
            return@CompositionLocalProvider
        }
        LaunchedEffect(Unit) {
            container.operations.messages.collect { messenger.show(it.text, it.action, it.onAction) }
        }
        // Another Seren app asked to show a file: open its folder with it picked out.
        LaunchedEffect(Unit) {
            for (request in reveals) {
                val file = withContext(Dispatchers.IO) {
                    Reveal.locate(context, request.uri, container.storage.volumes(), request.displayName)
                }
                val name = request.displayName ?: file?.name ?: "that file"
                val folder = file?.parentFile
                if (file == null || folder == null) {
                    messenger.show("Seren Files couldn't find where $name is")
                    continue
                }
                val there = withContext(Dispatchers.IO) { file.exists() }
                withContext(Dispatchers.Main) {
                    navController.navigate(Routes.folder(folder.path, highlight = file.name))
                    if (!there) messenger.show("$name isn't in ${folder.name} any more")
                }
            }
        }
        stateHolder.SaveableStateProvider("app") {
            AppNavHost(navController, settings)
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, settings: Settings) {
    val navigator = remember(navController) {
        Navigator(
            openFolder = { folder -> navController.navigate(Routes.folder(folder.path)) },
            search = { folder -> navController.navigate(Routes.folder(folder.path, search = true)) },
            back = { navController.popBackStack() },
        )
    }
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeOut(tween(280)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(settings = settings, navigator = navigator)
        }
        composable(
            Routes.FOLDER,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType },
                navArgument("search") { type = NavType.BoolType; defaultValue = false },
                navArgument("highlight") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            FolderScreen(
                start = File(path),
                startSearching = entry.arguments?.getBoolean("search") ?: false,
                settings = settings,
                onClose = { navController.popBackStack() },
                highlight = entry.arguments?.getString("highlight")?.ifEmpty { null },
            )
        }
    }
}
