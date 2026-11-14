package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.ImageCompressionUtil
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun PassportCameraCaptureDialog(
  onPhotoAccepted: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var permissionDenied by remember { mutableStateOf(false) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
  var capturedBase64 by remember { mutableStateOf<String?>(null) }
  var isCapturing by remember { mutableStateOf(false) }
  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
  var activeCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
      permissionDenied = !granted
    }

  LaunchedEffect(Unit) {
    if (!hasPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        activeCameraProvider?.unbindAll()
      } catch (_: Exception) {
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "Capture Passport Photograph",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close camera")
          }
        }

        if (!hasPermission || permissionDenied) {
          Text(
            "Camera permission is required to take a live passport photograph. You can grant permission or upload an image file from your device instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
              Text("Cancel")
            }
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier.weight(1f),
            ) {
              Text("Grant Camera")
            }
          }
        } else if (cameraError != null) {
          Text(
            cameraError!!,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
          )
          Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Use Device Photo Upload Instead")
          }
        } else if (capturedBase64 != null) {
          val previewBitmap = remember(capturedBase64) {
            ImageCompressionUtil.decodeBase64ToBitmap(capturedBase64!!)
          }
          if (previewBitmap != null) {
            Image(
              bitmap = previewBitmap.asImageBitmap(),
              contentDescription = "Captured passport photograph preview",
              contentScale = ContentScale.Crop,
              modifier =
                Modifier.size(260.dp)
                  .clip(RoundedCornerShape(18.dp))
                  .background(Color.Black),
            )
          }
          Text(
            "Review your passport photograph clearly before accepting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = { capturedBase64 = null },
              modifier = Modifier.weight(1f).testTag("camera_retake_button"),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Retake")
            }
            Button(
              onClick = { onPhotoAccepted(capturedBase64!!) },
              modifier = Modifier.weight(1f).testTag("camera_accept_button"),
            ) {
              Icon(Icons.Filled.Check, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Accept")
            }
          }
          TextButton(onClick = onDismiss) { Text("Cancel") }
        } else {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
          ) {
            AndroidView(
              factory = { ctx -> PreviewView(ctx) },
              update = { previewView ->
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener(
                  {
                    try {
                      val cameraProvider = providerFuture.get()
                      activeCameraProvider = cameraProvider
                      val preview =
                        Preview.Builder().build().also {
                          it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                      val capture =
                        ImageCapture.Builder()
                          .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                          .build()
                      imageCapture = capture

                      val desiredSelector =
                        CameraSelector.Builder().requireLensFacing(lensFacing).build()
                      val finalSelector =
                        when {
                          cameraProvider.hasCamera(desiredSelector) -> desiredSelector
                          cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                          cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                          else -> {
                            cameraError =
                              "No camera hardware is available on this device. Please upload a JPG or PNG photo from your gallery."
                            return@addListener
                          }
                        }

                      cameraProvider.unbindAll()
                      cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        finalSelector,
                        preview,
                        capture,
                      )
                    } catch (e: Exception) {
                      cameraError =
                        "Camera preview could not start (${e.localizedMessage ?: "Hardware unavailable"}). Please upload a photo from your device instead."
                    }
                  },
                  ContextCompat.getMainExecutor(context),
                )
              },
              modifier = Modifier.fillMaxSize(),
            )

            Surface(
              color = Color.Black.copy(alpha = 0.5f),
              shape = CircleShape,
              modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            ) {
              IconButton(
                onClick = {
                  lensFacing =
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                      CameraSelector.LENS_FACING_BACK
                    } else {
                      CameraSelector.LENS_FACING_FRONT
                    }
                }
              ) {
                Icon(
                  Icons.Filled.Cameraswitch,
                  contentDescription = "Switch front/back camera",
                  tint = Color.White,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
              Text("Cancel")
            }
            Button(
              onClick = {
                val cap = imageCapture ?: return@Button
                isCapturing = true
                val tempFile = File(context.cacheDir, "passport_capture_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                cap.takePicture(
                  outputOptions,
                  ContextCompat.getMainExecutor(context),
                  object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                      scope.launch {
                        val base64 = ImageCompressionUtil.compressFileToBase64(tempFile)
                        tempFile.delete()
                        isCapturing = false
                        if (base64 != null) {
                          capturedBase64 = base64
                        } else {
                          cameraError = "Failed to process captured photograph. Please retake."
                        }
                      }
                    }

                    override fun onError(exception: ImageCaptureException) {
                      isCapturing = false
                      cameraError = "Capture failed: ${exception.localizedMessage ?: "Try again"}"
                    }
                  },
                )
              },
              enabled = !isCapturing && imageCapture != null,
              modifier = Modifier.weight(1.4f).height(48.dp).testTag("camera_shutter_button"),
            ) {
              if (isCapturing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capturing...")
              } else {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capture Photo")
              }
            }
          }
        }
      }
    }
  }
}
