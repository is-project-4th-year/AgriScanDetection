package com.example.agriscan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using platform sans-serif; swap to a Google Font later if you like.
private val AppFont = FontFamily.SansSerif

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp),
    headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)
