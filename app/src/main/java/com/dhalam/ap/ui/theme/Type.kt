package com.dhalam.ap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.dhalam.ap.R

val BrandFont = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold)
)
private val base = Typography()
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = BrandFont),
    displayMedium = base.displayMedium.copy(fontFamily = BrandFont),
    displaySmall = base.displaySmall.copy(fontFamily = BrandFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = BrandFont),
    headlineMedium = base.headlineMedium.copy(fontFamily = BrandFont),
    headlineSmall = base.headlineSmall.copy(fontFamily = BrandFont),
    titleLarge = base.titleLarge.copy(fontFamily = BrandFont),
    titleMedium = base.titleMedium.copy(fontFamily = BrandFont),
    titleSmall = base.titleSmall.copy(fontFamily = BrandFont),
    bodyLarge = base.bodyLarge.copy(fontFamily = BrandFont),
    bodyMedium = base.bodyMedium.copy(fontFamily = BrandFont),
    bodySmall = base.bodySmall.copy(fontFamily = BrandFont),
    labelLarge = base.labelLarge.copy(fontFamily = BrandFont),
    labelMedium = base.labelMedium.copy(fontFamily = BrandFont),
    labelSmall = base.labelSmall.copy(fontFamily = BrandFont)
)
