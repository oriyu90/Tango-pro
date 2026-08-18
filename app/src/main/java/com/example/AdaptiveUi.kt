package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.StudyRound

@Composable
fun AdaptiveSingleLineText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified
) {
    BoxWithConstraints(modifier = modifier) {
        var useReducedSize by remember(text, fontSize, maxWidth) { mutableStateOf(false) }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontSize = if (useReducedSize) fontSize * 0.8f else fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.didOverflowWidth && !useReducedSize) useReducedSize = true
            }
        )
    }
}

@Composable
fun roundAccentColor(round: Int): Color = when (StudyRound.visualTier(round)) {
    2 -> Color(0xFF00897B)
    3 -> Color(0xFFF57C00)
    4 -> Color(0xFF7B1FA2)
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun RoundBadge(round: Int, modifier: Modifier = Modifier) {
    if (round <= 1) return
    val tier = StudyRound.visualTier(round)
    val accent = roundAccentColor(round)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (tier == 0) Color.Transparent else accent.copy(alpha = 0.14f),
        contentColor = if (tier == 0) MaterialTheme.colorScheme.onSurfaceVariant else accent,
        border = BorderStroke(1.dp, accent.copy(alpha = if (tier == 0) 0.55f else 0.8f))
    ) {
        Text(
            text = StudyRound.label(round),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
