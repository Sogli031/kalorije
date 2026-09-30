package com.kalorije.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalorije.app.R

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_light, FontWeight.W300),
    Font(R.font.space_grotesk_regular, FontWeight.W400),
    Font(R.font.space_grotesk_medium, FontWeight.W500),
    Font(R.font.space_grotesk_semibold, FontWeight.W600),
    Font(R.font.space_grotesk_bold, FontWeight.W700)
)

object AppColors {
    val Background = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val SurfaceLight = Color(0xFF21262D)
    val SurfaceRaised = Color(0xFF1C222B)
    val SurfacePressed = Color(0xFF262E38)
    val SurfaceOverlay = Color(0xE61A2028)

    val Primary = Color(0xFF4CAF50)
    val PrimaryDark = Color(0xFF388E3C)
    val PrimarySoft = Color(0x263FB950)

    val Calories = Color(0xFFFF6B6B)
    val Protein = Color(0xFF4ECDC4)
    val Fat = Color(0xFFFFD166)
    val Carbs = Color(0xFF95E1D3)

    val TextPrimary = Color(0xFFF0F6FC)
    val TextSecondary = Color(0xFFB7C0CC)
    val TextMuted = Color(0xFF7D8590)

    val GlassBorder = Color(0xFF30363D)
    val GlassBorderActive = Color(0xFF4A5564)
    val GlassBackground = Color(0xFF161B22)

    val Success = Color(0xFF3FB950)
    val Warning = Color(0xFFD29922)
    val Error = Color(0xFFF85149)
}

object AppTypography {
    val titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W600,
        fontSize = 28.sp,
        color = AppColors.TextPrimary
    )
    val titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        color = AppColors.TextPrimary
    )
    val bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        color = AppColors.TextPrimary
    )
    val bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        color = AppColors.TextSecondary
    )
    val labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        color = AppColors.TextPrimary
    )
    val labelMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        color = AppColors.TextSecondary
    )
    val labelSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        color = AppColors.TextMuted
    )
    val bodySmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        color = AppColors.TextSecondary
    )
    val numberLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W700,
        fontSize = 36.sp,
        color = AppColors.TextPrimary
    )
}

object AppDimens {
    val paddingXSmall = 4.dp
    val paddingSmall = 8.dp
    val paddingMedium = 16.dp
    val paddingLarge = 24.dp
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val cornerRadius = radiusMedium
    val iconButtonSize = 44.dp
    val cardElevation = 0.dp
}

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.Background,
    secondary = AppColors.Protein,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    onBackground = AppColors.TextPrimary,
    error = AppColors.Error
)

@Composable
fun KalorijeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
