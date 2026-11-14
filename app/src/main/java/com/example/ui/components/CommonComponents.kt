package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.ui.theme.JyDeepNavy
import com.example.ui.theme.JyIceBlue
import com.example.ui.theme.JyRoyalBlue
import com.example.util.ImageCompressionUtil

@Composable
fun OfficialJyLogoBadge(
  size: Dp = 64.dp,
  cornerRadius: Dp = 14.dp,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White,
    shape = RoundedCornerShape(cornerRadius),
    shadowElevation = 2.dp,
    modifier = modifier.size(size).border(1.dp, JyRoyalBlue.copy(alpha = 0.14f), RoundedCornerShape(cornerRadius)),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_institution_logo),
      contentDescription = "Official JY-COMPUTERS Logo",
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize().padding(4.dp),
    )
  }
}

fun launchDialer(context: Context, phone: String, onNotify: (String) -> Unit) {
  val digits = phone.trim().replace(" ", "")
  try {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    onNotify("No phone app found. Dial $digits manually.")
  } catch (_: Exception) {
    onNotify("Unable to open phone dialer for $digits.")
  }
}

fun launchWhatsApp(context: Context, rawPhone: String, onNotify: (String) -> Unit) {
  val clean = rawPhone.filter { it.isDigit() }
  val intl =
    when {
      clean.startsWith("234") -> clean
      clean.startsWith("0") && clean.length == 11 -> "234${clean.drop(1)}"
      clean.isNotBlank() -> clean
      else -> "2348156879310"
    }
  val url = "https://wa.me/$intl"
  try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    onNotify("Open $url in your browser or WhatsApp.")
  } catch (_: Exception) {
    onNotify("Unable to launch WhatsApp.")
  }
}

fun launchEmail(context: Context, email: String, onNotify: (String) -> Unit) {
  val target = email.trim().ifBlank { JyCatalogDefaults.OFFICIAL_EMAIL }
  try {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$target")).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    onNotify("No email app found. Contact $target.")
  } catch (_: Exception) {
    onNotify("Unable to open email client.")
  }
}

fun launchWebsite(context: Context, website: String, onNotify: (String) -> Unit) {
  val raw = website.trim().ifBlank { JyCatalogDefaults.OFFICIAL_WEBSITE }
  val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
  try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    onNotify("Visit $url in your browser.")
  } catch (_: Exception) {
    onNotify("Unable to open website.")
  }
}

fun launchOpayOrBankApp(context: Context, onNotify: (String) -> Unit) {
  try {
    val opayIntent = context.packageManager.getLaunchIntentForPackage("team.opay.pay")
    if (opayIntent != null) {
      opayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(opayIntent)
      onNotify("Opened OPay app. After transfer, return here to submit your transaction reference.")
      return
    }
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://opayweb.com")).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(webIntent)
    onNotify("OPay mobile portal opened. Return here after transfer to submit your reference.")
  } catch (_: Exception) {
    onNotify("Use your banking or OPay app to transfer to 6118366524 (Jy-computers), then submit your reference below.")
  }
}

@Composable
fun OfficialOpayBankCard(
  settings: InstitutionSettings,
  onCopied: (String) -> Unit,
  amountPayableNaira: Long? = null,
  showLaunchBankButton: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val bankName = settings.opayBankName.ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_BANK }
  val accountName =
    if (settings.opayAccountName.isBlank() || settings.opayAccountName.contains("Training Academy", ignoreCase = true)) {
      JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME
    } else {
      settings.opayAccountName
    }
  val accountNumber =
    if (
      settings.opayAccountNumber.isBlank() ||
        settings.opayAccountNumber == "8030001122" ||
        settings.opayAccountNumber == "0000000000" ||
        settings.opayAccountNumber == "1234567890"
    ) {
      JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER
    } else {
      settings.opayAccountNumber
    }
  val instructions =
    settings.opayInstructions.ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_INSTRUCTIONS }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = JyIceBlue),
    modifier = modifier.fillMaxWidth().border(1.dp, JyRoyalBlue.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = JyRoyalBlue)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            "Official Business Payment Account",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = JyDeepNavy,
          )
        }
        if (amountPayableNaira != null && amountPayableNaira > 0L) {
          StatusPill(
            text = "Due: ${JyCatalogDefaults.formatNaira(amountPayableNaira)}",
            containerColor = JyRoyalBlue,
            contentColor = Color.White,
          )
        }
      }

      Text("Bank: $bankName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = JyDeepNavy)

      Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Account Name",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              accountName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = JyDeepNavy,
            )
          }
          OutlinedButton(
            onClick = {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cm.setPrimaryClip(ClipData.newPlainText("JY-COMPUTERS Account Name", accountName))
              onCopied("Account Name ($accountName) copied!")
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("copy_opay_name_button"),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy account name", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy Name", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
          }
        }
      }

      Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Account Number ($bankName)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              accountNumber,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = JyRoyalBlue,
            )
          }
          Button(
            onClick = {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cm.setPrimaryClip(ClipData.newPlainText("JY-COMPUTERS OPay Account", accountNumber))
              onCopied("Account Number ($accountNumber) copied!")
            },
            colors = ButtonDefaults.buttonColors(containerColor = JyRoyalBlue),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("copy_opay_account_button"),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy account number", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Number", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
          }
        }
      }

      Text(
        instructions,
        style = MaterialTheme.typography.bodySmall,
        color = JyDeepNavy.copy(alpha = 0.85f),
      )

      if (showLaunchBankButton) {
        OutlinedButton(
          onClick = { launchOpayOrBankApp(context, onCopied) },
          modifier = Modifier.fillMaxWidth().testTag("open_opay_app_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Open OPay / Bank App to Pay", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@Composable
fun OfficialContactCard(
  settings: InstitutionSettings,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val primaryPhone = settings.contactPhone.ifBlank { JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE }
  val altPhone = settings.alternativePhone.ifBlank { JyCatalogDefaults.OFFICIAL_ALT_PHONE }
  val whatsappPhone = settings.whatsappNumber.ifBlank { JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE }
  val email = settings.email.ifBlank { JyCatalogDefaults.OFFICIAL_EMAIL }
  val website = settings.website.ifBlank { JyCatalogDefaults.OFFICIAL_WEBSITE }
  val address = settings.physicalAddress.ifBlank { JyCatalogDefaults.OFFICIAL_ADDRESS }
  val slogan = settings.slogan.ifBlank { JyCatalogDefaults.OFFICIAL_SLOGAN }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OfficialJyLogoBadge(size = 56.dp, cornerRadius = 12.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            settings.institutionName.ifBlank { "JY-COMPUTERS" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            slogan,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      HorizontalDivider()

      Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.LocationOn, contentDescription = "Address", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(address, style = MaterialTheme.typography.bodySmall)
      }

      Text(
        "Primary Contact: $primaryPhone (Calls & WhatsApp)",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = { launchDialer(context, primaryPhone, onNotify) },
          modifier = Modifier.weight(1f).testTag("contact_call_primary_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.Call, contentDescription = "Call $primaryPhone", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Call $primaryPhone", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
          onClick = { launchWhatsApp(context, whatsappPhone, onNotify) },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
          modifier = Modifier.weight(1f).testTag("contact_whatsapp_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp $primaryPhone", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("WhatsApp", maxLines = 1)
        }
      }

      OutlinedButton(
        onClick = { launchDialer(context, altPhone, onNotify) },
        modifier = Modifier.fillMaxWidth().testTag("contact_call_alt_button"),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Filled.Call, contentDescription = "Call alternative number", modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Call Alternative Line: $altPhone")
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { launchEmail(context, email, onNotify) },
          modifier = Modifier.weight(1f).testTag("contact_email_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.Email, contentDescription = "Email", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(email, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = { launchWebsite(context, website, onNotify) },
          modifier = Modifier.weight(1f).testTag("contact_website_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.Language, contentDescription = "Website", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(website, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
fun JyBusinessFooter(
  settings: InstitutionSettings,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val primaryPhone = settings.contactPhone.ifBlank { JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE }
  val altPhone = settings.alternativePhone.ifBlank { JyCatalogDefaults.OFFICIAL_ALT_PHONE }
  val whatsappPhone = settings.whatsappNumber.ifBlank { JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE }
  val email = settings.email.ifBlank { JyCatalogDefaults.OFFICIAL_EMAIL }
  val website = settings.website.ifBlank { JyCatalogDefaults.OFFICIAL_WEBSITE }
  val address = settings.physicalAddress.ifBlank { JyCatalogDefaults.OFFICIAL_ADDRESS }
  val slogan = settings.slogan.ifBlank { JyCatalogDefaults.OFFICIAL_SLOGAN }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        "${settings.institutionName.ifBlank { "JY-COMPUTERS" }} — $slogan",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        "Primary: $primaryPhone • Alt: $altPhone • $email • $website",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          onClick = { launchDialer(context, primaryPhone, onNotify) },
          modifier = Modifier.testTag("footer_call_primary_button"),
        ) {
          Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Call", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
          onClick = { launchWhatsApp(context, whatsappPhone, onNotify) },
          modifier = Modifier.testTag("footer_whatsapp_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
          onClick = { launchDialer(context, altPhone, onNotify) },
          modifier = Modifier.testTag("footer_call_alt_button"),
        ) {
          Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Alt Line", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
          onClick = { launchWebsite(context, website, onNotify) },
          modifier = Modifier.testTag("footer_website_button"),
        ) {
          Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Web", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
fun SimpleDistributionChartCard(
  title: String,
  items: List<Triple<String, Int, Color>>,
  modifier: Modifier = Modifier,
) {
  val maxVal = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      items.forEach { (label, count, color) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(count.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
          }
          LinearProgressIndicator(
            progress = { (count.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
          )
        }
      }
    }
  }
}

@Composable
fun JyGradientHeaderCard(
  title: String,
  subtitle: String,
  badgeText: String? = null,
  showOfficialLogo: Boolean = false,
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
        if (showOfficialLogo) {
          OfficialJyLogoBadge(size = 52.dp, cornerRadius = 12.dp)
          Spacer(modifier = Modifier.width(12.dp))
        }
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
