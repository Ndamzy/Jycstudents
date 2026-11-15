package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class InstitutionSettings(
  @DocumentId val id: String = "institution",
  val institutionName: String = "JY-COMPUTERS",
  val motto: String = "LEARN • PRACTICE • EXCEL — Empowering People Through Technology",
  val slogan: String = JyCatalogDefaults.OFFICIAL_SLOGAN,
  val contactPhone: String = JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE,
  val alternativePhone: String = JyCatalogDefaults.OFFICIAL_ALT_PHONE,
  val whatsappNumber: String = JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE,
  val email: String = JyCatalogDefaults.OFFICIAL_EMAIL,
  val website: String = JyCatalogDefaults.OFFICIAL_WEBSITE,
  val physicalAddress: String = JyCatalogDefaults.OFFICIAL_ADDRESS,
  val opayBankName: String = JyCatalogDefaults.OFFICIAL_OPAY_BANK,
  val opayAccountName: String = JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME,
  val opayAccountNumber: String = JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER,
  val opayInstructions: String = JyCatalogDefaults.OFFICIAL_OPAY_INSTRUCTIONS,
  val onlinePaymentUrl: String = "",
  val paymentsEnabled: Boolean = true,
  val allowPartialPayment: Boolean = true,
  val activeSession: String = "2026/2027 Session",
  val signatoryName: String = "Dr. Joshua Dangana",
  val signatoryTitle: String = "Director of Academic Studies",
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Programme(
  @DocumentId val id: String = "",
  val title: String = "",
  val description: String = "",
  val duration: String = "",
  val feeNaira: Long = 0L,
  val category: String = "Foundation",
  val level: String = "All Levels",
  val instructorUid: String = "",
  val instructorName: String = "Faculty Instructor",
  val scheduleText: String = "Mon - Thu • 10:00 AM",
  val active: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val role: String = "student",
  val studentIdNumber: String = "",
  val fullName: String = "",
  val gender: String = "Male",
  val dateOfBirth: String = "",
  val phoneNumber: String = "",
  val email: String = "",
  val residentialAddress: String = "",
  val stateOfOrigin: String = "",
  val localGovernmentArea: String = "",
  val nextOfKin: String = "",
  val nextOfKinPhone: String = "",
  val passportPhotoBase64: String = "",
  val selectedProgrammeId: String = "",
  val selectedProgrammeTitle: String = "",
  val trainingLevel: String = "Beginner",
  val registrationDate: String = "",
  val approvalStatus: String = "pending",
  val paymentStatus: String = "unpaid",
  val courseProgressPercent: Int = 0,
  val status: String = "active",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DirectoryEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val fullName: String = "",
  val studentIdNumber: String = "",
  val role: String = "student",
  val selectedProgrammeTitle: String = "",
  val isOnline: Boolean = true,
  @ServerTimestamp val lastSeenAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class InstructorRole(
  @DocumentId val id: String = "",
  val uid: String = "",
  val fullName: String = "",
  val email: String = "",
  val specialization: String = "",
  val assignedCourseIds: List<String> = emptyList(),
  val active: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Enrollment(
  @DocumentId val id: String = "",
  val userId: String = "",
  val studentName: String = "",
  val studentIdNumber: String = "",
  val programmeId: String = "",
  val programmeTitle: String = "",
  val instructorUid: String = "",
  val session: String = "",
  val totalFeeNaira: Long = 0L,
  val amountPaidNaira: Long = 0L,
  val status: String = "active",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val outstandingNaira: Long
    get() = (totalFeeNaira - amountPaidNaira).coerceAtLeast(0L)
}

@IgnoreExtraProperties
data class JyServiceItem(
  @DocumentId val id: String = "",
  val title: String = "",
  val category: String = "Cyber Café & Online Services",
  val description: String = "",
  val defaultFeeNaira: Long = 0L,
  val paymentEnabled: Boolean = true,
  val allowPartialPayment: Boolean = true,
  val isTrainingCourse: Boolean = false,
  val linkedProgrammeId: String = "",
  val active: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ServiceRequest(
  @DocumentId val id: String = "",
  val userId: String = "",
  val customerName: String = "",
  val customerPhone: String = "",
  val customerEmail: String = "",
  val customerRegNumber: String = "",
  val requestNumber: String = "",
  val invoiceNumber: String = "",
  val serviceId: String = "",
  val serviceTitle: String = "",
  val serviceCategory: String = "",
  val details: String = "",
  val totalFeeNaira: Long = 0L,
  val amountPaidNaira: Long = 0L,
  val paymentStatus: String = "unpaid",
  val status: String = "pending",
  val adminNotes: String = "",
  val invoiceDate: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val outstandingNaira: Long
    get() = (totalFeeNaira - amountPaidNaira).coerceAtLeast(0L)
}

@IgnoreExtraProperties
data class Payment(
  @DocumentId val id: String = "",
  val userId: String = "",
  val studentName: String = "",
  val studentIdNumber: String = "",
  val enrollmentId: String = "",
  val serviceRequestId: String = "",
  val requestNumber: String = "",
  val invoiceNumber: String = "",
  val programmeId: String = "",
  val programmeTitle: String = "",
  val serviceCategory: String = "Training Courses",
  val amountNaira: Long = 0L,
  val method: String = "opay_transfer",
  val reference: String = "",
  val referenceNormalized: String = "",
  val paymentDate: String = "",
  val senderName: String = "",
  val senderBank: String = "",
  val evidenceBase64: String = "",
  val status: String = "pending",
  val adminRemarks: String = "",
  val verifiedByUid: String = "",
  val verifiedByName: String = "",
  val verifiedAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AttendanceRecord(
  @DocumentId val id: String = "",
  val studentUid: String = "",
  val studentName: String = "",
  val studentIdNumber: String = "",
  val programmeId: String = "",
  val programmeTitle: String = "",
  val instructorUid: String = "",
  val dateKey: String = "",
  val status: String = "present",
  val markedByUid: String = "",
  val remarks: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AcademicResult(
  @DocumentId val id: String = "",
  val studentUid: String = "",
  val studentName: String = "",
  val studentIdNumber: String = "",
  val programmeId: String = "",
  val programmeTitle: String = "",
  val instructorUid: String = "",
  val session: String = "",
  val assignmentScore: Long = 0L,
  val examScore: Long = 0L,
  val totalScore: Long = 0L,
  val grade: String = "F",
  val remarks: String = "",
  val published: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Certificate(
  @DocumentId val id: String = "",
  val studentUid: String = "",
  val studentName: String = "",
  val studentIdNumber: String = "",
  val programmeId: String = "",
  val programmeTitle: String = "",
  val graduationDate: String = "",
  val verificationCode: String = "",
  val signatoryName: String = "",
  val signatoryTitle: String = "",
  val issuedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Announcement(
  @DocumentId val id: String = "",
  val title: String = "",
  val body: String = "",
  val category: String = "general",
  val targetRole: String = "all",
  val eventDate: String = "",
  val authorName: String = "",
  val authorUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LearningMaterial(
  @DocumentId val id: String = "",
  val programmeId: String = "",
  val programmeTitle: String = "",
  val title: String = "",
  val description: String = "",
  val resourceUrl: String = "",
  val authorName: String = "",
  val authorUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppNotification(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val message: String = "",
  val type: String = "announcement",
  val isRead: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatRoom(
  @DocumentId val id: String = "",
  val roomType: String = "group",
  val title: String = "",
  val programmeId: String = "",
  val memberUids: List<String> = emptyList(),
  val isGroupPublic: Boolean = false,
  val typingUids: List<String> = emptyList(),
  val lastMessage: String = "",
  val lastSenderName: String = "",
  val lastMessageAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val roomId: String = "",
  val senderUid: String = "",
  val senderName: String = "",
  val text: String = "",
  val imageBase64: String = "",
  val attachmentName: String = "",
  val memberUids: List<String> = emptyList(),
  val isGroupPublic: Boolean = false,
  val readBy: List<String> = emptyList(),
  val reported: Boolean = false,
  val deleted: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatReport(
  @DocumentId val id: String = "",
  val reporterUid: String = "",
  val reportedUid: String = "",
  val roomId: String = "",
  val messageId: String = "",
  val reason: String = "",
  val messagePreview: String = "",
  val status: String = "open",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class BlockedUser(
  @DocumentId val id: String = "",
  val blockedUid: String = "",
  val blockedName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ActivityLog(
  @DocumentId val id: String = "",
  val actorUid: String = "",
  val actorName: String = "",
  val action: String = "",
  val targetId: String = "",
  val details: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

object JyCatalogDefaults {
  const val SINGLE_ADMIN_UID = "jy_single_owner_admin"
  const val SINGLE_ADMIN_ROLE_LABEL = "Single Owner Administrator"
  const val ADMIN_LOGIN_USERNAME = "admin"
  const val ADMIN_MAPPED_EMAIL = "admin@jy-computers.com"

  fun resolveAdminAuthEmail(username: String): String? =
    if (username.trim() == ADMIN_LOGIN_USERNAME) ADMIN_MAPPED_EMAIL else null

  fun isOwnerAdminIdentity(uid: String, email: String? = null): Boolean =
    uid == SINGLE_ADMIN_UID ||
      (!email.isNullOrBlank() && email.trim().equals(ADMIN_MAPPED_EMAIL, ignoreCase = true))

  const val OFFICIAL_PRIMARY_PHONE = "08156879310"
  const val OFFICIAL_ALT_PHONE = "09167689248"
  const val OFFICIAL_WHATSAPP_WA_ME = "https://wa.me/2348156879310"
  const val OFFICIAL_EMAIL = "jycomputers2019@gmail.com"
  const val OFFICIAL_WEBSITE = "www.jy-computers.com"
  const val OFFICIAL_ADDRESS =
    "Shop 4, Maiangwa Street, Dagbana, Jikwoyi Phase 3 Extension, FCT Abuja, Nigeria."
  const val OFFICIAL_SLOGAN = "Your Trusted Partner for ICT Excellence."

  const val OFFICIAL_OPAY_ACCOUNT_NAME = "Jy-computers"
  const val OFFICIAL_OPAY_ACCOUNT_NUMBER = "6118366524"
  const val OFFICIAL_OPAY_BANK = "OPay"
  const val OFFICIAL_OPAY_INSTRUCTIONS =
    "Transfer the exact payable amount to Bank: OPay | Account Name: Jy-computers | Account Number: 6118366524. After transfer, enter your transaction reference and upload payment evidence for administrator verification."

  val TRAINING_LEVELS = listOf("Beginner", "Intermediate", "Advanced")

  val SERVICE_CATEGORIES =
    listOf(
      "Training Courses",
      "Repairs & Maintenance",
      "Web & IT Solutions",
      "Cyber Café & Online Services",
      "Design & Printing",
    )

  val NIGERIAN_BANKS =
    listOf(
      "OPay",
      "Access Bank",
      "First Bank of Nigeria",
      "Guaranty Trust Bank (GTBank)",
      "United Bank for Africa (UBA)",
      "Zenith Bank",
      "Moniepoint MFB",
      "PalmPay",
      "Kuda Bank",
      "Fidelity Bank",
      "Stanbic IBTC Bank",
      "Sterling Bank",
      "Union Bank",
      "Wema Bank",
      "FCMB",
      "Polaris Bank",
      "Other Nigerian Bank",
    )

  val DEFAULT_SERVICES =
    listOf(
      ServiceSeed(
        id = "srv_basic_computer_training",
        title = "Basic Computer Training",
        category = "Training Courses",
        description = "Essential digital literacy, Windows, Microsoft Office suite, and internet skills.",
        defaultFeeNaira = 25_000L,
        isTrainingCourse = true,
        linkedProgrammeId = "basic_computer_course",
      ),
      ServiceSeed(
        id = "srv_certificate_course_training",
        title = "Certificate Course Training",
        category = "Training Courses",
        description = "Office automation, spreadsheets, presentation design, and productivity workflows.",
        defaultFeeNaira = 47_000L,
        isTrainingCourse = true,
        linkedProgrammeId = "certificate_course",
      ),
      ServiceSeed(
        id = "srv_diploma_1_training",
        title = "Diploma 1 Course Training",
        category = "Training Courses",
        description = "Intermediate computing covering databases, desktop publishing, and IT operations.",
        defaultFeeNaira = 50_000L,
        isTrainingCourse = true,
        linkedProgrammeId = "diploma_1",
      ),
      ServiceSeed(
        id = "srv_diploma_2_training",
        title = "Diploma 2 Course Training",
        category = "Training Courses",
        description = "Advanced diploma in enterprise software systems, networking, and web design.",
        defaultFeeNaira = 80_000L,
        isTrainingCourse = true,
        linkedProgrammeId = "diploma_2",
      ),
      ServiceSeed(
        id = "srv_computer_repairs_training",
        title = "Computer Repairs Training",
        category = "Training Courses",
        description = "Hands-on laptop and desktop hardware diagnostics, BIOS, and motherboard repairs.",
        defaultFeeNaira = 200_000L,
        isTrainingCourse = true,
        linkedProgrammeId = "computer_repairs",
      ),
      ServiceSeed(
        id = "srv_computer_repairs_maintenance",
        title = "Computer Repairs and Maintenance",
        category = "Repairs & Maintenance",
        description = "Laptop/desktop hardware diagnostics, screen/keyboard replacement, OS reinstall, and servicing.",
        defaultFeeNaira = 15_000L,
      ),
      ServiceSeed(
        id = "srv_website_development",
        title = "Website Development",
        category = "Web & IT Solutions",
        description = "Custom responsive business, school, e-commerce, and institutional websites.",
        defaultFeeNaira = 150_000L,
      ),
      ServiceSeed(
        id = "srv_website_management",
        title = "Website Management",
        category = "Web & IT Solutions",
        description = "Ongoing website hosting care, backups, SSL security, content updates, and uptime monitoring.",
        defaultFeeNaira = 30_000L,
      ),
      ServiceSeed(
        id = "srv_it_support_consultancy",
        title = "IT Support and Consultancy",
        category = "Web & IT Solutions",
        description = "Enterprise ICT advisory, software deployment, office workstation setup, and tech support.",
        defaultFeeNaira = 25_000L,
      ),
      ServiceSeed(
        id = "srv_computer_networking",
        title = "Computer Networking",
        category = "Repairs & Maintenance",
        description = "LAN/Wi-Fi structured cabling, router/switch setup, CCTV networking, and office intranet.",
        defaultFeeNaira = 45_000L,
      ),
      ServiceSeed(
        id = "srv_cyber_cafe_services",
        title = "Cyber Café Services",
        category = "Cyber Café & Online Services",
        description = "High-speed browsing, scanning, lamination, passport photography, and email assistance.",
        defaultFeeNaira = 2_000L,
        allowPartialPayment = false,
      ),
      ServiceSeed(
        id = "srv_nysc_registration_assistance",
        title = "NYSC Registration Assistance",
        category = "Cyber Café & Online Services",
        description = "Complete NYSC mobilization portal registration, biometric capture, and remita processing.",
        defaultFeeNaira = 5_000L,
        allowPartialPayment = false,
      ),
      ServiceSeed(
        id = "srv_green_card_callup_printing",
        title = "Green Card and Call-up Letter Printing",
        category = "Cyber Café & Online Services",
        description = "Official NYSC Green Card and Call-up Letter retrieval, colour printing, and lamination.",
        defaultFeeNaira = 2_500L,
        allowPartialPayment = false,
      ),
      ServiceSeed(
        id = "srv_nerd_academic_upload",
        title = "NERD Academic Document Upload",
        category = "Cyber Café & Online Services",
        description = "Accurate scanning, formatting, and verified portal upload for NERD academic documents.",
        defaultFeeNaira = 4_000L,
        allowPartialPayment = false,
      ),
      ServiceSeed(
        id = "srv_cac_registration_assistance",
        title = "CAC Registration Assistance",
        category = "Cyber Café & Online Services",
        description = "Business name reservation, CAC pre-incorporation filing, and certificate retrieval support.",
        defaultFeeNaira = 35_000L,
      ),
      ServiceSeed(
        id = "srv_bulk_printing",
        title = "Bulk Printing",
        category = "Design & Printing",
        description = "High-volume black & white or full-colour printing, binding, and institutional handouts.",
        defaultFeeNaira = 10_000L,
      ),
      ServiceSeed(
        id = "srv_document_processing",
        title = "Document Processing",
        category = "Design & Printing",
        description = "Professional typesetting, CV/project formatting, PDF editing, and official letters.",
        defaultFeeNaira = 3_000L,
        allowPartialPayment = false,
      ),
      ServiceSeed(
        id = "srv_online_registration_services",
        title = "Online Registration Services",
        category = "Cyber Café & Online Services",
        description = "JAMB, WAEC, NECO, NIN, visa forms, scholarship portals, and official online applications.",
        defaultFeeNaira = 5_000L,
        allowPartialPayment = false,
      ),
      ServiceSeed(
        id = "srv_graphics_design",
        title = "Graphics Design",
        category = "Design & Printing",
        description = "Logos, flyers, banners, ID cards, business branding, and social media graphics.",
        defaultFeeNaira = 15_000L,
      ),
    )

  val DEFAULT_PROGRAMMES =
    listOf(
      ProgrammeSeed(
        id = "basic_computer_course",
        title = "Basic Computer Course",
        description =
          "Essential digital literacy: operating systems, Microsoft Office suite, internet productivity, and safe computing.",
        duration = "1 Month",
        feeNaira = 25_000L,
        category = "Foundation",
        level = "Beginner",
        scheduleText = "Mon & Wed • 9:00 AM - 11:00 AM",
      ),
      ProgrammeSeed(
        id = "certificate_course",
        title = "Certificate Course",
        description =
          "Comprehensive office automation, spreadsheets, presentation design, email collaboration, and cloud document workflows.",
        duration = "2 Months",
        feeNaira = 47_000L,
        category = "Foundation",
        level = "Beginner",
        scheduleText = "Tue & Thu • 9:00 AM - 12:00 PM",
      ),
      ProgrammeSeed(
        id = "diploma_1",
        title = "Diploma 1",
        description =
          "Intermediate computing covering database management, desktop publishing, graphics basics, and IT operations.",
        duration = "3 Months",
        feeNaira = 50_000L,
        category = "Diploma",
        level = "Intermediate",
        scheduleText = "Mon, Wed & Fri • 11:30 AM - 1:30 PM",
      ),
      ProgrammeSeed(
        id = "diploma_2",
        title = "Diploma 2",
        description =
          "Advanced diploma in enterprise software systems, networking fundamentals, web design, and project management.",
        duration = "6 Months",
        feeNaira = 80_000L,
        category = "Diploma",
        level = "Advanced",
        scheduleText = "Mon - Thu • 2:00 PM - 4:00 PM",
      ),
      ProgrammeSeed(
        id = "computer_repairs",
        title = "Computer Repairs",
        description =
          "Hands-on PC and laptop hardware diagnostics, component replacement, BIOS configuration, and preventive maintenance.",
        duration = "6 Months",
        feeNaira = 200_000L,
        category = "Hardware",
        level = "Intermediate",
        scheduleText = "Tue, Thu & Fri • 10:00 AM - 1:00 PM",
      ),
      ProgrammeSeed(
        id = "advanced_computer_repairs",
        title = "Advanced Computer Repairs",
        description =
          "Master motherboard micro-soldering, schematic reading, power rail repair, BGA rework, and commercial workshop management.",
        duration = "1 Year",
        feeNaira = 350_000L,
        category = "Hardware",
        level = "Advanced",
        scheduleText = "Mon - Fri • 1:00 PM - 4:00 PM",
      ),
      ProgrammeSeed(
        id = "computer_troubleshooting",
        title = "Computer Troubleshooting",
        description =
          "Systematic OS recovery, malware remediation, driver diagnostics, network fault isolation, and data recovery.",
        duration = "6 Months",
        feeNaira = 250_000L,
        category = "Hardware",
        level = "Intermediate",
        scheduleText = "Wed & Fri • 9:00 AM - 12:30 PM",
      ),
      ProgrammeSeed(
        id = "coding_programme",
        title = "Coding Programme",
        description =
          "Full-stack software engineering with Python, JavaScript, Git, REST APIs, relational/NoSQL databases, and deployment.",
        duration = "6 Months",
        feeNaira = 450_000L,
        category = "Software",
        level = "All Levels",
        scheduleText = "Mon, Wed & Fri • 10:00 AM - 1:00 PM",
      ),
      ProgrammeSeed(
        id = "android_app_development",
        title = "Android App Development",
        description =
          "Native Kotlin & Jetpack Compose engineering, Material 3 UI, Firebase backend integration, and Google Play release.",
        duration = "6 Months",
        feeNaira = 450_000L,
        category = "Software",
        level = "All Levels",
        scheduleText = "Tue, Thu & Sat • 10:00 AM - 1:00 PM",
      ),
    )

  val NIGERIAN_STATES =
    listOf(
      "Abia", "Adamawa", "Akwa Ibom", "Anambra", "Bauchi", "Bayelsa", "Benue", "Borno",
      "Cross River", "Delta", "Ebonyi", "Edo", "Ekiti", "Enugu", "FCT Abuja", "Gombe",
      "Imo", "Jigawa", "Kaduna", "Kano", "Katsina", "Kebbi", "Kogi", "Kwara", "Lagos",
      "Nasarawa", "Niger", "Ogun", "Ondo", "Osun", "Oyo", "Plateau", "Rivers", "Sokoto",
      "Taraba", "Yobe", "Zamfara", "International",
    )

  fun normalizePaymentReferenceKey(reference: String): String =
    reference.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "_")

  fun calculateGrade(totalScore: Long): String =
    when {
      totalScore >= 75 -> "A"
      totalScore >= 65 -> "B"
      totalScore >= 55 -> "C"
      totalScore >= 45 -> "D"
      totalScore >= 40 -> "E"
      else -> "F"
    }

  fun formatNaira(amount: Long): String {
    return "₦%,d".format(amount)
  }
}

data class ServiceSeed(
  val id: String,
  val title: String,
  val category: String,
  val description: String,
  val defaultFeeNaira: Long,
  val paymentEnabled: Boolean = true,
  val allowPartialPayment: Boolean = true,
  val isTrainingCourse: Boolean = false,
  val linkedProgrammeId: String = "",
)

data class ProgrammeSeed(
  val id: String,
  val title: String,
  val description: String,
  val duration: String,
  val feeNaira: Long,
  val category: String,
  val level: String = "All Levels",
  val scheduleText: String,
)
