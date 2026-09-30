package com.kalorije.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    bgColor: Color = AppColors.GlassBackground,
    borderColor: Color = AppColors.GlassBorder,
    cornerRadius: Dp = AppDimens.cornerRadius,
    contentPadding: Dp = AppDimens.paddingMedium,
    selected: Boolean = false,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val resolvedBorder = when {
        selected -> accentColor ?: AppColors.GlassBorderActive
        accentColor != null -> accentColor.copy(alpha = 0.6f)
        else -> borderColor
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, resolvedBorder, shape)
            .padding(contentPadding),
        content = content
    )
}
