package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JyDeepNavy
import com.example.ui.theme.JyRoyalBlue
import com.example.util.ImageCompressionUtil

@Composable
fun JyGradientHeaderCard(
  title: String,
  subtitle: String,
  badgeText: String? = null,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit = {},
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(
          Brush.linearGradient(colors = listOf(JyDeepNavy, JyRoyalBlue))
        )
        .padding(20.dp)
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
          )
        }
        if (!badgeText.isNullOrBlank()) {
          Surface(
            color = Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = badgeText,
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
      }
      content()
    }
  }
}

@Composable
fun MetricStatCard(
  label: String,
  value: String,
  icon: ImageVector,
  accentColor: Color = MaterialTheme.colorScheme.primary,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  Card(
    onClick = { onClick?.invoke() },
    enabled = onClick != null,
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Box(
        modifier =
          Modifier.size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = accentColor,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(999.dp),
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun SectionHeader(
  title: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (actionLabel != null && onAction != null) {
      TextButton(onClick = onAction) {
        Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
fun Base64Avatar(
  base64: String,
  name: String,
  size: Dp = 52.dp,
  showOnlineDot: Boolean = false,
  isOnline: Boolean = false,
) {
  val bitmap = remember(base64) { ImageCompressionUtil.decodeBase64ToBitmap(base64) }
  Box(modifier = Modifier.size(size)) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "$name photo",
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(size)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
      )
    } else {
      val initials =
        name
          .trim()
          .split(" ")
          .filter { it.isNotBlank() }
          .take(2)
          .joinToString("") { it.first().uppercaseChar().toString() }
          .ifBlank { "JY" }
      Box(
        modifier =
          Modifier.size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        if (initials.isNotBlank()) {
          Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        } else {
          Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = name,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }

    if (showOnlineDot) {
      Box(
        modifier =
          Modifier.size(13.dp)
            .align(Alignment.BottomEnd)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isOnline) Color(0xFF22C55E) else Color(0xFF94A3B8))
      )
    }
  }
}

@Composable
fun EmptyStateCard(
  title: String,
  message: String,
  icon: ImageVector = Icons.Filled.Info,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ConfirmationDialog(
  title: String,
  message: String,
  confirmLabel: String = "Confirm",
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
    text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmLabel, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
