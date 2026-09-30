package com.kalorije.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.ui.theme.SpaceGrotesk

@Composable
fun CrashLogScreen(crashLog: String) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Aplikacija je pukla",
            style = AppTypography.titleLarge.copy(
                color = AppColors.Error,
                fontFamily = SpaceGrotesk,
                fontSize = 26.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                clipboardManager.setText(AnnotatedString(crashLog))
                copied = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Error
            )
        ) {
            Text(
                text = if (copied) "Kopirano!" else "Kopiraj crash log",
                style = AppTypography.labelLarge.copy(
                    color = AppColors.Background
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AppColors.Surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = crashLog,
                style = AppTypography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary
                )
            )
        }
    }
}
