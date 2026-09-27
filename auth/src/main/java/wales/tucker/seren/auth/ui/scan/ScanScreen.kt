package wales.tucker.seren.auth.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import wales.tucker.seren.auth.otp.GoogleMigration
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.qr.QrCodes
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.theme.SystemBarAppearance
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Whether a scanned QR code is one Seren Auth can add accounts from. */
fun isAccountCode(text: String) = OtpAuthUri.isOtpAuth(text) || GoogleMigration.isMigration(text)

/**
 * Scans a setup QR code with the camera. [onScanned] gets the first otpauth or Google
 * Authenticator transfer code found; other QR codes show a hint and scanning carries on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, onScanned: (String) -> Unit, onEnterKey: () -> Unit, onPickImage: () -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPermission = ok
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !asked) request.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopAppBar(
                title = { Text("Scan QR code") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                windowInsets = WindowInsets.statusBars,
            )
            EmptyState(
                icon = Icons.Rounded.NoPhotography,
                title = "Camera access is off",
                message = "Seren Auth uses the camera only to read setup QR codes. Nothing is recorded or sent anywhere.",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val activity = context as? Activity
                        val canAskAgain = activity != null &&
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                        if (asked && !canAskAgain) {
                            // Once Android stops asking, the only way back is the app's settings.
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        } else {
                            request.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text("Allow camera") }
                    TextButton(onClick = onPickImage) { Text("Scan from an image instead") }
                    TextButton(onClick = onEnterKey) { Text("Enter setup key instead") }
                }
            }
        }
        // Coming back from the app's settings: check again.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) hasPermission = granted()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        return
    }

    SystemBarAppearance(lightBars = false)
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) {
        if (hint) {
            delay(3000)
            hint = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            onQrCode = { text -> if (isAccountCode(text)) onScanned(text) else hint = true },
            modifier = Modifier.fillMaxSize(),
        )
        Viewfinder(Modifier.fillMaxSize())
        TopAppBar(
            title = { Text("Scan QR code") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
            windowInsets = WindowInsets.statusBars,
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut()) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                    Text(
                        "That QR code isn't for two-factor authentication",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Text(
                "Point the camera at the QR code the site shows when you turn on two-factor authentication",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanAction(Icons.Rounded.Image, "From an image", onPickImage)
                ScanAction(Icons.Rounded.Keyboard, "Enter setup key", onEnterKey)
            }
        }
    }
}

@Composable
private fun ScanAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f), contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Darkens everything but a rounded square in the middle, where the code should go. */
@Composable
private fun Viewfinder(modifier: Modifier) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val side = size.minDimension * 0.7f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2 - size.height * 0.05f)
        val radius = CornerRadius(28.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, Size(side, side), radius, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, topLeft, Size(side, side), radius, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun CameraPreview(onQrCode: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onQrCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val done = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var lastHint = 0L
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(executor) { image ->
                    val text = image.use { decodeFrame(it) }
                    if (text != null && !done.get()) {
                        if (isAccountCode(text)) {
                            done.set(true)
                            mainExecutor.execute { callback(text) }
                        } else if (System.currentTimeMillis() - lastHint > 3000) {
                            lastHint = System.currentTimeMillis()
                            mainExecutor.execute { callback(text) }
                        }
                    }
                }
            }
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            provider = runCatching { providerFuture.get() }.getOrNull()
            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, mainExecutor)
        onDispose {
            provider?.unbindAll()
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }
}

private fun decodeFrame(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    // The last row can be shorter than the stride; pad so the source's bounds check holds.
    val needed = plane.rowStride * image.height
    val data = if (bytes.size >= needed) bytes else bytes.copyOf(needed)
    return QrCodes.decodeLuminance(data, plane.rowStride, image.width, image.height)
}
