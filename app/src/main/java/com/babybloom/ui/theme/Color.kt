package com.babybloom.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Brand palette — extracted from your design
val PurpleLavender       = Color(0xFFD4C5F9)   // screen background gradient top
val LavenderLight        = Color(0xFFE8DFF8)   // gradient bottom
val NavyDark             = Color(0xFF1A1A3E)   // primary button background
val White                = Color(0xFFFFFFFF)
val TextPrimary          = Color(0xFF1A1A3E)   // dark navy for headings
val TextSecondary        = Color(0xFF6B6B8A)   // hint/placeholder text
val TextFieldBackground  = Color(0xFFF5F2FF)   // input field fill
val TextFieldBorder      = Color(0xFFDDD5F3)   // input field border
val ErrorRed             = Color(0xFFE53935)
val DividerGray          = Color(0xFFCCC5E0)
val ButtonText           = Color(0xFFFFFFFF)
val LinkColor            = Color(0xFF6B4EFF)   // "forgot password" link

// ── Register Screen specific ──────────────────────────────────────────────
val GradientTop         = Color(0xFFB8A9E8)   // top of background gradient (deeper purple)
val GradientBottom      = Color(0xFFD8D0F0)   // bottom of background gradient (soft lavender)
val CardWhite           = Color(0xFFFFFFFF)   // white card surface
val SunBackground       = Color(0xFFF0E8FF)   // subtle sun area background
val TextFieldBorderFocused = Color(0xFF7B68C8) // focused border color

// New Gradient Colors (from your image)
// New Gradient Colors
val GradientPurpleLight = Color(0xFFE1E3F7)  // Top - Light lavender
val GradientPurpleMedium = Color(0xFFB9BBEB) // Middle
val GradientPurpleDark = Color(0xFF7F80DA)   // Bottom - Deeper purple

val GradientPinkDark = Color(0xFFE5CDFE)
val GradientPinkMedium = Color(0xFFE7E0FB)
val GradientPinkLight = Color(0xFFE9F2F8)

val BorderGray = Color(0xFFD1D1D1)

// ── Child Profile ──────────────────────────────────────────────────────────
val BackgroundLight  = Color(0xFFF8F5FC)   // page background (very light purple-white)
val CardPurple       = Color(0xFFEDE7F6)   // card chip/badge/progress track background
val ProgressPurple   = Color(0xFF7C4DBC)   // primary progress bar fill

// ── Status ────────────────────────────────────────────────────────────────
val BadgeGreen       = Color(0xFF4CAF50)   // "نشيط" active status (not used in header but
// handy if you add a colored badge later)

// ── Danger Zone ───────────────────────────────────────────────────────────
val DangerRed        = Color(0xFFE53935)   // remove button + remove card border/text
val DangerRedLight   = Color(0xFFFFF8F8)   // remove card background

// ── Chart / Skill Lines ───────────────────────────────────────────────────
val ChartColorLanguage    = Color(0xFF7C4DBC)   // purple  — اللغة
val ChartColorNumeracy    = Color(0xFF42A5F5)   // blue    — العددية
val ChartColorInteractive = Color(0xFF26C6A4)   // teal    — الأنشطة
val ChartColorMotor       = Color(0xFFFF7043)   // orange  — الحركية

val GradientOrangeLight = Color(0xFFFDCC6D)  // Top - Light lavender

val GradientOrangeMedium= Color(0xFFF9A45E)  // Top - Light lavender

val GradientOrangeDark = Color(0xFFF5734C)

val DarkNavy = Color(0xFF1A1A2E)

// ── Parent Home Screen Colors ──────────────────────────────────────────────
// Screen Background
val ScreenBackgroundLight = Color(0xFFFFFFFF)  // Clean white background

// Header Colors
val HeaderGreetingColor = Color(0xFF6B6B8A)   // Subtle gray for "Good Morning"
val HeaderTitleColor = Color(0xFF1A1A3E)      // Navy for main title

// Overview Card Colors
val ParentStatsBlueBG = Color(0xFFE3F2FD)     // Light blue background
val ParentCardBlue = Color(0xFF2196F3)        // Bright blue accent

val ParentStatsOrangeBG = Color(0xFFFEE3C3)   // Light orange background
val ParentCardOrange = Color(0xFFFF9800)      // Bright orange accent

val ParentStatsPurpleBG = Color(0xFFF3E5F5)   // Light purple background
val ParentCardPurple = Color(0xFF9C27B0)      // Bright purple accent

// AI Insights Box Colors
val AIInsightsDarkBg = Color(0xFF2C2C4C)      // Dark navy/purple background
val AIInsightsIconBg = Color(0xFFFFB74D)      // Golden/amber icon background

// Quick Action Colors
val QuickActionIconPurple = Color(0xFF9C27B0) // Purple for search/explore action
val QuickActionIconBlue = Color(0xFF2196F3)   // Blue for add child action
val QuickActionIconOrange = Color(0xFFFF9800) // Orange for support action
val  ParentBackground = Color(0xFFF5F6F9)
val  ComponentBackground = Color(0xFFEAEFFF)
val DotNotification = Color(0xFFFF7A76)
val Purple       = Color(0xFF7B68B8)   // card chip/badge/progress track background

// ─────────────────────────────────────────────────────────────────────────────
// HARDCODED COLORS EXTRACTED FROM ParentView.kt
// Add these to your existing Color.kt / Theme colors file
// ─────────────────────────────────────────────────────────────────────────────

// Already referenced (should already exist in your Color.kt):
// BackgroundLight, DarkNavy, NavyDark, DotNotification, White, Purple,
// GradientPurpleMedium, GradientPurpleDark, ProgressPurple, TextPrimary,
// TextSecondary, BorderGray, PurpleLavender, ErrorRed

// ── NEW COLORS TO ADD ─────────────────────────────────────────────────────────

val SectionCardBackground   = Color(0xFFEAEFFF)   // SectionCard background
val DeleteRowBackground     = Color(0xFFFFEBEB)    // Delete account row background
val SuccessGreen            = Color(0xFF2E7D32)    // Success message in EditProfileDialog
val LoadingOverlay          = Color(0x4D000000)    // Black 30% alpha loading overlay
val ShadowColor             = Color(0xFF2E2645)    // SectionCard shadow color (7% alpha applied in code)

// ── NOTIFICATION SCREEN COLORS (new screen) ──────────────────────────────────
val NotificationHeaderBg    = Color(0xFF1E1F3B)    // Dark navy header background (from design image)
val NotifCardBg             = Color.White
val NotifUnreadDotGreen     = Color(0xFFB2F0C8)    // Soft green dot (Fatima card)
val NotifUnreadDotPink      = Color(0xFFF4B8B8)    // Soft pink/salmon dot (AI insight card)
val NotifAccentGreen        = Color(0xFF4CAF82)    // Green left-border accent (Fatima card)
val NotifUnreadDotOrange    = Color(0xFFFF9800)
val NotifAccentPurple       = Color(0xFF9B8EC4)    // Purple left-border accent (AI insight card)
val NotifAccentOrange       = Color(0xFFF5A623)    // Orange left-border accent (needs support card)
val NotifMarkAllReadBg      = Color(0xFF1E1F3B)    // "Mark all as read" button background
val NotifTimestampColor     = Color(0xFF9E9E9E)    // Timestamp text color

//Gehad colors

val DeepNavy = Color(0xFF1A1F3C)          // Home dark background, bottom nav
val SoftLavender = Color(0xFFEDE7F6)      // Register/Login background
val WarmPeach = Color(0xFFFFF3E0)         // Add child background
val PrimaryPurple = Color(0xFF7B5EA7)     // Buttons, accents
val AccentTeal = Color(0xFF4DB6AC)        // Overview cards
val AccentOrange = Color(0xFFFF8A65)      // Achievement cards
val AccentPink = Color(0xFFF48FB1)        // Hearts, soft accents
val CardDark = Color(0xFF252A4A)          // Dark cards on home screen
val TextOnDark = Color(0xFFFFFFFF)
val TextOnLight = Color(0xFF2D2D2D)
val InputBackground = Color(0xFFF5F5F5)
val ProgressBarFill = Color(0xFF030213)
val ProgressBarTrack = Color(0x1A030213)
val ChildCardBackground = Color(0xFFEAEFFF)
val ScreenBackground = Color(0xFFF5F6F9)
val AddChildButton = Color(0xFFB9B7E6)
val SearchBarIcon = Color(0xFF7B68B8)
val MyChildrenTextDark= Color(0xFF241726)
val StatusActiveBackground = Color(0xFFC1D8F2)
val StatusActiveDot = Color(0xFF5B9BB8)
val StatusCalmBackground = Color(0xFFC1E8D8)
val StatusCalmDot = Color(0xFF5BB89B)
val StatusNeedsSupportBackground = Color(0xFFFFE0C1)
val StatusNeedsSupportDot = Color(0xFFE89B5B)

val ChildName = Color(0xFF2E2645)
val AvatarBorder = Color(0xFF8E2357)

val Gradient1 = Color(0xFF161C36)
val Gradient2 = Color(0xFF8C59C0)
val Gradient3 = Color(0xFF7F80DA)

val Card1 = Color(0xFFDFCCED)

val Card2 = Color(0xFFB3DFED)

val Card3 = Color(0xFFFFDCC2)

val DarkPurple = Color(0xFF2E2645)

// ── Trace Game Colors ────────────────────────────────────────────────────────
val TraceCardBackground = Color(0xFFFFF8E7)
val TraceSvgGray        = Color(0xFFC8C8C8)
val TraceBadgeBorder    = Color(0xFFD0D0D0)
val TraceBadgeText     = Color(0xFF333333)
val TraceOverlayScrim   = Color(0xBB000000)
val TracePopupBackground = Color(0xFFFFFFFF)
val TraceSuccessText    = Color(0xFF26A69A)
val TraceWarningText    = Color(0xFFFF7043)
val TraceSecondaryText  = Color(0xFF78909C)
val HintOrbColor    = Color(0xFFFFEB3B)
val HintFingerColor = Color(0xFFFFDBAC)
val TraceStartPulse = Color(0xFF4CAF50)
val TraceActiveAccent  = Color(0xFFFF7043)
val TraceActiveCovered = Color(0xFF66BB6A)
val TraceCalmAccent    = Color(0xFF5B9BD5)
val TraceCalmCovered   = Color(0xFF26A69A)
