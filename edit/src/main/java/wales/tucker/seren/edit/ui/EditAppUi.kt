package wales.tucker.seren.edit.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.channels.Channel
import wales.tucker.seren.core.ui.LockScreen
import wales.tucker.seren.edit.data.Settings
import wales.tucker.seren.edit.ui.editor.EditorScreen
import wales.tucker.seren.edit.ui.files.FolderScreen
import wales.tucker.seren.edit.ui.files.folderPath
import wales.tucker.seren.edit.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor?uri={uri}"
    const val FOLDER = "folder?tree={tree}&doc={doc}&title={title}&path={path}"

    fun editor(uri: Uri) = "editor?uri=${Uri.encode(uri.toString())}"

    fun folder(tree: Uri, documentId: String, title: String, path: String) =
        "folder?tree=${Uri.encode(tree.toString())}&doc=${Uri.encode(documentId)}&title=${Uri.encode(title)}&path=${Uri.encode(path)}"
}

@Composable
fun EditAppUi(settings: Settings, locked: Boolean, onUnlock: () -> Unit, openLinks: Channel<Uri>) {
    // The nav controller and the screens' saveable state live above the lock check, so unlocking
    // returns to the screen that was open.
    val navController = rememberNavController()
    val stateHolder = rememberSaveableStateHolder()
    if (locked) {
        LockScreen(appName = "Seren Edit", message = "Authenticate to see your files", onUnlock = onUnlock)
        return
    }
    LaunchedEffect(Unit) {
        for (uri in openLinks) navController.navigate(Routes.editor(uri)) { launchSingleTop = true }
    }
    stateHolder.SaveableStateProvider("app") {
        AppNavHost(navController, settings)
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, settings: Settings) {
    val openFile: (Uri) -> Unit = { uri -> navController.navigate(Routes.editor(uri)) }
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
                onOpenFile = openFile,
                onOpenFolder = { folder ->
                    val tree = Uri.parse(folder.uri)
                    val root = DocumentsContract.getTreeDocumentId(tree)
                    navController.navigate(Routes.folder(tree, root, folder.name, folderPath(tree, root, folder.location)))
                },
            )
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("uri") { type = NavType.StringType })) { entry ->
            val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
            EditorScreen(uri = uri, settings = settings, onClose = { navController.popBackStack() })
        }
        composable(
            Routes.FOLDER,
            arguments = listOf("tree", "doc", "title", "path").map { name -> navArgument(name) { type = NavType.StringType } },
        ) { entry ->
            val args = entry.arguments
            val tree = Uri.parse(args?.getString("tree").orEmpty())
            val path = args?.getString("path").orEmpty()
            FolderScreen(
                tree = tree,
                documentId = args?.getString("doc").orEmpty(),
                title = args?.getString("title").orEmpty(),
                path = path,
                onBack = { navController.popBackStack() },
                onOpenFile = openFile,
                onOpenFolder = { id, name -> navController.navigate(Routes.folder(tree, id, name, folderPath(tree, id, "$path/$name"))) },
            )
        }
    }
}
