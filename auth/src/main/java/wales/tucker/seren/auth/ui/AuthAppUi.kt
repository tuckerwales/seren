package wales.tucker.seren.auth.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.auth.data.Settings
import wales.tucker.seren.auth.otp.GoogleMigration
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.otp.OtpFormatException
import wales.tucker.seren.auth.qr.QrCodes
import wales.tucker.seren.auth.ui.common.LocalMessenger
import wales.tucker.seren.auth.ui.common.Messenger
import wales.tucker.seren.auth.ui.editor.AccountEditorScreen
import wales.tucker.seren.auth.ui.home.HomeScreen
import wales.tucker.seren.auth.ui.importing.ImportDialogs
import wales.tucker.seren.auth.ui.importing.ImportViewModel
import wales.tucker.seren.auth.ui.scan.ScanScreen
import wales.tucker.seren.core.ui.LockScreen

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val EDITOR = "editor?id={id}&link={link}"

    fun newAccount(link: String? = null) = "editor?id=-1&link=${Uri.encode(link.orEmpty())}"

    fun editAccount(id: Long) = "editor?id=$id&link="
}

/** The ways to add an account, offered from the Add account sheet, the empty state and the scanner. */
class AddActions(
    val scan: () -> Unit,
    val pickImage: () -> Unit,
    val enterKey: () -> Unit,
    val importFile: () -> Unit,
)

@Composable
fun AuthAppUi(settings: Settings, locked: Boolean, onUnlock: () -> Unit, openLinks: Channel<String>) {
    // The nav controller and the screens' saveable state live above the lock check, so unlocking
    // returns to the screen that was open.
    val navController = rememberNavController()
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(scope) }
    val importVm = containerViewModel { ImportViewModel(it) }
    val context = LocalContext.current

    /** Adds whatever a QR code, image or link held: one account opens in the editor to check first. */
    fun openCode(text: String) {
        when {
            OtpAuthUri.isOtpAuth(text) -> try {
                OtpAuthUri.parse(text)
                navController.navigate(Routes.newAccount(text)) {
                    popUpTo(Routes.SCAN) { inclusive = true }
                    launchSingleTop = true
                }
            } catch (e: OtpFormatException) {
                navController.popBackStack(Routes.SCAN, inclusive = true)
                messenger.show("Couldn't add the account. ${e.message}")
            }
            GoogleMigration.isMigration(text) -> {
                navController.popBackStack(Routes.SCAN, inclusive = true)
                importVm.importText(text)
            }
            else -> messenger.show("That QR code isn't for two-factor authentication")
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.Default) {
                    QrCodes.loadBitmap(context.contentResolver, uri)?.let(QrCodes::decodeBitmap)
                }
                if (text == null) messenger.show("No QR code found in that image") else openCode(text)
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(importVm::importFile)
    }
    val actions = remember(navController) {
        AddActions(
            scan = { navController.navigate(Routes.SCAN) { launchSingleTop = true } },
            pickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enterKey = {
                navController.navigate(Routes.newAccount()) {
                    popUpTo(Routes.SCAN) { inclusive = true }
                    launchSingleTop = true
                }
            },
            importFile = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        )
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        if (locked) {
            LockScreen(appName = "Seren Auth", message = "Authenticate to see your codes", onUnlock = onUnlock)
            return@CompositionLocalProvider
        }
        LaunchedEffect(Unit) {
            for (link in openLinks) openCode(link)
        }
        LaunchedEffect(importVm) {
            importVm.messages.collect { messenger.show(it) }
        }
        stateHolder.SaveableStateProvider("app") {
            AppNavHost(navController, settings, actions, onScanned = ::openCode)
        }
        ImportDialogs(importVm)
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, settings: Settings, actions: AddActions, onScanned: (String) -> Unit) {
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
                actions = actions,
                onEdit = { id -> navController.navigate(Routes.editAccount(id)) },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onScanned = onScanned,
                onEnterKey = actions.enterKey,
                onPickImage = actions.pickImage,
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("link") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            val link = entry.arguments?.getString("link").orEmpty()
            AccountEditorScreen(
                id = id.takeIf { it >= 0 },
                link = link.ifEmpty { null },
                onClose = { navController.popBackStack() },
            )
        }
    }
}
