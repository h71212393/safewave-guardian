package com.safewave.guardian.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 어르신도 보기 쉽게: 흰 바탕에 아주 진한 글씨, 상태는 색 + 그림 + 글자로 함께 표시 */
object C {
    val bg = Color(0xFFF2F4F8)
    val card = Color.White
    val ink = Color(0xFF0F172A)
    val sub = Color(0xFF334155)
    val line = Color(0xFFCBD5E1)
    val brand = Color(0xFF3557D6)
    val ok = Color(0xFF15803D)
    val warn = Color(0xFFB45309)
    val crit = Color(0xFFB91C1C)
    val empty = Color(0xFF1D4E89)
    val off = Color(0xFF475569)
}

/** 글자 크기 단계별 배율: 보통 / 크게 / 아주 크게 */
fun fontFactor(level: Int) = when (level) { 0 -> 1.0f; 2 -> 1.35f; else -> 1.15f }

@Composable
fun SafeTheme(fontLevel: Int, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.brand, onPrimary = Color.White, background = C.bg, surface = C.card,
            onBackground = C.ink, onSurface = C.ink, error = C.crit
        )
    ) {
        CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * fontFactor(fontLevel))) {
            content()
        }
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = C.brand,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    outlined: Boolean = false,
    outlinedContainer: Color = C.card
) {
    val inner: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(text, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
    val m = modifier.fillMaxWidth().heightIn(min = 76.dp)
    if (outlined) {
        OutlinedButton(
            onClick = onClick, modifier = m, enabled = enabled, shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, contentColor),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = outlinedContainer, contentColor = contentColor),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) { inner() }
    } else {
        Button(
            onClick = onClick, modifier = m, enabled = enabled, shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) { inner() }
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 4.dp), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = C.sub, size: Int = 20) {
    Text(text, modifier = modifier, fontSize = size.sp, color = color, lineHeight = (size * 1.45).sp)
}
