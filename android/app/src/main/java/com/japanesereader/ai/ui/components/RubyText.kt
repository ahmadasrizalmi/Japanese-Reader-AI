package com.japanesereader.ai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.japanesereader.ai.ui.theme.PrimaryCrimson
import com.japanesereader.ai.ui.theme.TextMuted
import com.japanesereader.ai.ui.theme.TextPrimary

data class RubyToken(
    val surface: String,
    val reading: String? = null,
    val romaji: String? = null,
    val pos: String? = null,
    val meaning: String? = null,
    val jlpt: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RubyText(
    furiganaPayload: String,
    furiganaMode: String = "always", // "always", "tap", "off"
    fontFamily: FontFamily = FontFamily.Serif,
    fontSize: Int = 18,
    modifier: Modifier = Modifier
) {
    val tokens = remember(furiganaPayload) {
        try {
            val listType = object : TypeToken<List<RubyToken>>() {}.type
            Gson().fromJson<List<RubyToken>>(furiganaPayload, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center
    ) {
        tokens.forEach { token ->
            val hasReading = !token.reading.isNullOrBlank() &&
                    token.reading != token.surface &&
                    token.surface != "、" && token.surface != "。" && token.surface != "？" && token.surface != "！"

            var isTapped by remember { mutableStateOf(false) }

            val showFurigana = when (furiganaMode) {
                "always" -> hasReading
                "tap" -> hasReading && isTapped
                else -> false
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .clickable(enabled = furiganaMode == "tap" && hasReading) {
                        isTapped = !isTapped
                    }
            ) {
                if (showFurigana && token.reading != null) {
                    Text(
                        text = token.reading,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCrimson,
                        lineHeight = 12.sp
                    )
                } else if (hasReading && furiganaMode != "off") {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = token.surface,
                    fontSize = fontSize.sp,
                    fontFamily = fontFamily,
                    color = TextPrimary,
                    lineHeight = (fontSize + 6).sp
                )
            }
        }
    }
}
