package com.japanesereader.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanesereader.ai.ui.theme.*

@Composable
fun AnalyticsScreen() {
    val scrollState = rememberScrollState()

    val weeklyData = listOf(
        "Sen" to 25,
        "Sel" to 40,
        "Rab" to 30,
        "Kam" to 55,
        "Jum" to 65,
        "Sab" to 45,
        "Min" to 50
    )

    val jlptDist = listOf(
        "N5" to 45,
        "N4" to 60,
        "N3" to 28,
        "N2" to 8,
        "N1" to 3
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("STATISTIK BELAJAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text("Progres & Wawasan", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(CrimsonSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = "Waktu", tint = PrimaryCrimson, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("7 Hari Terakhir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                }
            }
        }

        // Dual Metric Cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Metric 1
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Waktu Baca", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(100))
                                .background(CanvasSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Timer, contentDescription = "Waktu", tint = PrimaryCrimson, modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("4.2 Jam", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.TrendingUp, contentDescription = "Tren", tint = PrimaryCrimson, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("+18% mgg ini", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                    }
                }
            }

            // Metric 2
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dikuasai", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(100))
                                .background(CanvasSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Dikuasai", tint = SecondaryVermilion, modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("86 Kalimat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Naik", tint = SecondaryVermilion, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("12 selesai hari ini", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SecondaryVermilion)
                    }
                }
            }
        }

        // Weekly Rhythm Bar Chart
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(100))
                                .background(CrimsonSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.BarChart, contentDescription = "Grafik", tint = PrimaryCrimson, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ritme Mingguan", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Text("Target: 30 mnt/hari", fontSize = 10.sp, color = TextMuted)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyData.forEach { (day, minutes) ->
                        val barHeight = ((minutes / 65f) * 80).dp
                        val isPeak = minutes >= 60

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${minutes}m",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                                    .background(if (isPeak) PrimaryCrimson else CrimsonDeep.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = day,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // JLPT Mastery Distribution
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Distribusi Level JLPT Kosakata",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    jlptDist.forEach { (level, count) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CanvasSecondary)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(level, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
