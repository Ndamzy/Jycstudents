package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.Certificate
import com.example.data.model.Enrollment
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Payment
import com.example.data.model.UserProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportAndPdfUtil {

  private fun getExportsDir(context: Context): File {
    val dir = File(context.cacheDir, "exports")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser =
      Intent.createChooser(intent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(chooser)
  }

  fun generateStudentProfilePdf(
    context: Context,
    student: UserProfile,
    enrollments: List<Enrollment>,
    settings: InstitutionSettings,
  ): File {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas

    canvas.drawColor(Color.WHITE)
    val bannerPaint = Paint().apply { color = Color.parseColor("#0D47A1") }
    canvas.drawRect(0f, 0f, 595f, 118f, bannerPaint)

    try {
      val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_institution_logo)
      if (logoBitmap != null) {
        val whiteCard = Paint().apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(24f, 20f, 102f, 98f), 12f, 12f, whiteCard)
        canvas.drawBitmap(logoBitmap, null, RectF(28f, 24f, 98f, 94f), null)
      }
    } catch (_: Exception) {
      // Optional logo
    }

    val titlePaint =
      Paint().apply {
        color = Color.WHITE
        textSize = 20f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
      }
    val subPaint =
      Paint().apply {
        color = Color.parseColor("#DBEAFE")
        textSize = 11.5f
        isAntiAlias = true
      }
    canvas.drawText(settings.institutionName, 118f, 46f, titlePaint)
    canvas.drawText("OFFICIAL STUDENT REGISTRATION INFORMATION SHEET", 118f, 70f, subPaint)
    canvas.drawText("${settings.physicalAddress} • ${settings.contactPhone}", 118f, 90f, subPaint)

    // Student passport photo on top-right of body
    val passportBitmap = ImageCompressionUtil.decodeBase64ToBitmap(student.passportPhotoBase64)
    if (passportBitmap != null) {
      val framePaint =
        Paint().apply {
          color = Color.parseColor("#CBD5E1")
          style = Paint.Style.STROKE
          strokeWidth = 2f
        }
      val photoRect = RectF(445f, 140f, 555f, 250f)
      canvas.drawBitmap(passportBitmap, null, photoRect, null)
      canvas.drawRect(photoRect, framePaint)
    }

    val labelPaint =
      Paint().apply {
        color = Color.parseColor("#64748B")
        textSize = 11.5f
        isAntiAlias = true
      }
    val valuePaint =
      Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = 13f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
      }
    val dividerPaint =
      Paint().apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 1f
      }

    val totalFees = enrollments.sumOf { it.totalFeeNaira }
    val totalPaid = enrollments.sumOf { it.amountPaidNaira }
    val balance = (totalFees - totalPaid).coerceAtLeast(0L)

    val rows =
      listOf(
        "Registration Number" to student.studentIdNumber,
        "Full Name" to student.fullName,
        "Gender / Date of Birth" to "${student.gender} • ${student.dateOfBirth}",
        "Phone Number" to student.phoneNumber,
        "Email Address" to student.email,
        "Residential Address" to student.residentialAddress.take(48),
        "State / LGA" to "${student.stateOfOrigin}${if (student.localGovernmentArea.isNotBlank()) " / ${student.localGovernmentArea}" else ""}",
        "Next of Kin" to "${student.nextOfKin} (${student.nextOfKinPhone})",
        "Selected Course" to "${student.selectedProgrammeTitle} (${student.trainingLevel})",
        "Registration Date" to student.registrationDate,
        "Approval / Account Status" to "${student.approvalStatus.uppercase()} / ${student.status.uppercase()}",
        "Course Progress" to "${student.courseProgressPercent}%",
        "Tuition Summary" to "Paid ${JyCatalogDefaults.formatNaira(totalPaid)} • Balance ${JyCatalogDefaults.formatNaira(balance)}",
        "Official OPay Account" to "${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME} • ${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER} (${JyCatalogDefaults.OFFICIAL_OPAY_BANK})",
      )

    var y = 158f
    rows.forEachIndexed { idx, (label, value) ->
      val maxWidthX = if (idx < 3 && passportBitmap != null) 425f else 559f
      canvas.drawText(label, 32f, y, labelPaint)
      canvas.drawText(value.ifBlank { "-" }, 205f, y, valuePaint)
      canvas.drawLine(32f, y + 11f, maxWidthX, y + 11f, dividerPaint)
      y += 38f
    }

    val footerPaint =
      Paint().apply {
        color = Color.parseColor("#475569")
        textSize = 10.5f
        isAntiAlias = true
      }
    canvas.drawText(
      "Generated on ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())} • ${settings.email}",
      32f,
      798f,
      footerPaint,
    )

    doc.finishPage(page)
    val safeId = student.studentIdNumber.ifBlank { student.fullName }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val outFile = File(getExportsDir(context), "JYC_Student_${safeId}.pdf")
    FileOutputStream(outFile).use { doc.writeTo(it) }
    doc.close()
    return outFile
  }

  fun generateCertificatePdf(
    context: Context,
    certificate: Certificate,
    settings: InstitutionSettings,
  ): File {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas: Canvas = page.canvas

    canvas.drawColor(Color.WHITE)
    val outerBorder =
      Paint().apply {
        color = Color.parseColor("#0D3B66")
        style = Paint.Style.STROKE
        strokeWidth = 10f
      }
    val innerBorder =
      Paint().apply {
        color = Color.parseColor("#D4AF37")
        style = Paint.Style.STROKE
        strokeWidth = 3f
      }
    canvas.drawRect(24f, 24f, 818f, 571f, outerBorder)
    canvas.drawRect(36f, 36f, 806f, 559f, innerBorder)

    try {
      val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_institution_logo)
      if (logoBitmap != null) {
        canvas.drawBitmap(logoBitmap, null, RectF(64f, 52f, 144f, 132f), null)
      }
    } catch (_: Exception) {
      // Optional logo
    }

    val headerPaint =
      Paint().apply {
        color = Color.parseColor("#0D3B66")
        textSize = 26f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText(settings.institutionName.uppercase(Locale.getDefault()), 421f, 92f, headerPaint)

    val mottoPaint =
      Paint().apply {
        color = Color.parseColor("#475569")
        textSize = 12f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText(settings.motto, 421f, 114f, mottoPaint)

    try {
      val sealBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_certificate_seal)
      if (sealBitmap != null) {
        val dest = RectF(660f, 395f, 770f, 505f)
        canvas.drawBitmap(sealBitmap, null, dest, null)
      }
    } catch (_: Exception) {
      // Optional bitmap
    }

    val titlePaint =
      Paint().apply {
        color = Color.parseColor("#1565C0")
        textSize = 34f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText("CERTIFICATE OF COMPLETION", 421f, 172f, titlePaint)

    val bodyPaint =
      Paint().apply {
        color = Color.parseColor("#1E293B")
        textSize = 16f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText("This is to certify that", 421f, 215f, bodyPaint)

    val namePaint =
      Paint().apply {
        color = Color.parseColor("#0D3B66")
        textSize = 30f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText(certificate.studentName, 421f, 260f, namePaint)

    val idPaint =
      Paint().apply {
        color = Color.parseColor("#64748B")
        textSize = 13f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText("Student ID: ${certificate.studentIdNumber}", 421f, 285f, idPaint)

    canvas.drawText("has successfully completed all requirements for the training programme in", 421f, 322f, bodyPaint)

    val progPaint =
      Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = 22f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText(certificate.programmeTitle, 421f, 358f, progPaint)
    canvas.drawText("Awarded on ${certificate.graduationDate}", 421f, 392f, bodyPaint)

    val linePaint =
      Paint().apply {
        color = Color.parseColor("#0D3B66")
        strokeWidth = 1.5f
      }
    canvas.drawLine(90f, 480f, 310f, 480f, linePaint)
    val sigNamePaint =
      Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = 14f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText(certificate.signatoryName, 200f, 502f, sigNamePaint)
    canvas.drawText(certificate.signatoryTitle, 200f, 520f, mottoPaint)

    val verifyPaint =
      Paint().apply {
        color = Color.parseColor("#334155")
        textSize = 11f
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
      }
    canvas.drawText("Verification No: ${certificate.verificationCode}", 60f, 542f, verifyPaint)

    doc.finishPage(page)
    val safeName = certificate.studentIdNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val outFile = File(getExportsDir(context), "JYC_Certificate_${safeName}.pdf")
    FileOutputStream(outFile).use { doc.writeTo(it) }
    doc.close()
    return outFile
  }

  fun generatePaymentReceiptPdf(
    context: Context,
    payment: Payment,
    settings: InstitutionSettings,
  ): File {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas

    canvas.drawColor(Color.WHITE)
    val bannerPaint = Paint().apply { color = Color.parseColor("#0D47A1") }
    canvas.drawRect(0f, 0f, 595f, 120f, bannerPaint)

    try {
      val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_institution_logo)
      if (logoBitmap != null) {
        val whiteCard = Paint().apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(24f, 22f, 98f, 96f), 12f, 12f, whiteCard)
        canvas.drawBitmap(logoBitmap, null, RectF(28f, 26f, 94f, 92f), null)
      }
    } catch (_: Exception) {
      // Optional logo
    }

    val titlePaint =
      Paint().apply {
        color = Color.WHITE
        textSize = 21f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
      }
    val subPaint =
      Paint().apply {
        color = Color.parseColor("#DBEAFE")
        textSize = 12f
        isAntiAlias = true
      }
    canvas.drawText(settings.institutionName, 114f, 48f, titlePaint)
    canvas.drawText("OFFICIAL TUITION PAYMENT RECEIPT", 114f, 74f, subPaint)
    canvas.drawText("${settings.physicalAddress} • ${settings.contactPhone}", 114f, 94f, subPaint)

    val labelPaint =
      Paint().apply {
        color = Color.parseColor("#64748B")
        textSize = 12f
        isAntiAlias = true
      }
    val valuePaint =
      Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = 14f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
      }

    val dateStr =
      payment.paymentDate.ifBlank {
        payment.createdAt?.toDate()?.let {
          SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(it)
        } ?: SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
      }

    val methodLabel =
      when (payment.method) {
        "opay_transfer" -> "OPay Bank Transfer (${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER})"
        "cash" -> "Cash / Office Payment"
        else -> "Online Payment Gateway"
      }

    val rows =
      listOf(
        "Transaction Reference" to payment.reference,
        "Student Name" to payment.studentName,
        "Student ID Number" to payment.studentIdNumber,
        "Programme" to payment.programmeTitle,
        "Payment Method" to methodLabel,
        "Amount Paid" to JyCatalogDefaults.formatNaira(payment.amountNaira),
        "Payment Status" to payment.status.uppercase(Locale.getDefault()),
        "Payment Date" to dateStr,
        "Remarks" to payment.adminRemarks.ifBlank { "Tuition Fee Payment" },
      )

    var y = 170f
    val dividerPaint =
      Paint().apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 1f
      }
    rows.forEach { (label, value) ->
      canvas.drawText(label, 36f, y, labelPaint)
      canvas.drawText(value, 220f, y, valuePaint)
      canvas.drawLine(36f, y + 14f, 559f, y + 14f, dividerPaint)
      y += 44f
    }

    val footerPaint =
      Paint().apply {
        color = Color.parseColor("#475569")
        textSize = 11f
        isAntiAlias = true
      }
    canvas.drawText(
      "Official Bank: ${JyCatalogDefaults.OFFICIAL_OPAY_BANK} • ${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME} (${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER})",
      36f,
      756f,
      footerPaint,
    )
    canvas.drawText(
      "Generated by JY-Computers Student Management System • ${settings.email}",
      36f,
      780f,
      footerPaint,
    )

    doc.finishPage(page)
    val safeRef = payment.reference.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val outFile = File(getExportsDir(context), "Receipt_${safeRef}.pdf")
    FileOutputStream(outFile).use { doc.writeTo(it) }
    doc.close()
    return outFile
  }

  fun generateReportPdf(
    context: Context,
    reportTitle: String,
    headers: List<String>,
    rows: List<List<String>>,
    settings: InstitutionSettings,
  ): File {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    var page = doc.startPage(pageInfo)
    var canvas = page.canvas

    val headerPaint =
      Paint().apply {
        color = Color.parseColor("#0D47A1")
        textSize = 18f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
      }
    val metaPaint =
      Paint().apply {
        color = Color.parseColor("#475569")
        textSize = 11f
        isAntiAlias = true
      }
    val rowPaint =
      Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = 10f
        isAntiAlias = true
      }
    val colHeaderPaint =
      Paint().apply {
        color = Color.parseColor("#0D47A1")
        textSize = 10.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
      }

    canvas.drawText("${settings.institutionName} — $reportTitle", 32f, 48f, headerPaint)
    canvas.drawText(
      "Generated: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())} • Total rows: ${rows.size}",
      32f,
      68f,
      metaPaint,
    )

    val colWidth = (530f / headers.size.coerceAtLeast(1))
    var y = 105f
    headers.forEachIndexed { idx, h ->
      canvas.drawText(h.take(18), 32f + idx * colWidth, y, colHeaderPaint)
    }
    y += 22f

    rows.forEach { row ->
      if (y > 790f) {
        doc.finishPage(page)
        page = doc.startPage(pageInfo)
        canvas = page.canvas
        y = 50f
      }
      row.forEachIndexed { idx, cell ->
        canvas.drawText(cell.take(20), 32f + idx * colWidth, y, rowPaint)
      }
      y += 20f
    }

    doc.finishPage(page)
    val slug = reportTitle.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "_")
    val outFile = File(getExportsDir(context), "JYC_${slug}.pdf")
    FileOutputStream(outFile).use { doc.writeTo(it) }
    doc.close()
    return outFile
  }

  fun generateCsvReport(
    context: Context,
    reportTitle: String,
    headers: List<String>,
    rows: List<List<String>>,
  ): File {
    val slug = reportTitle.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "_")
    val outFile = File(getExportsDir(context), "JYC_${slug}.csv")
    val sb = StringBuilder()
    sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
    rows.forEach { row -> sb.appendLine(row.joinToString(",") { escapeCsv(it) }) }
    outFile.writeText(sb.toString())
    return outFile
  }

  private fun escapeCsv(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
  }
}
