package com.invisiblesushi.hanzilens.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

private val GREEN = Color(0xFF00FF88)
private val AMBER = Color(0xFFFFBB33)
private val DIM   = Color(0xFFAAAAAA)
private val MONO  = FontFamily.Monospace

@Composable
fun AboutScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // App header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        ) {
            Text("汉字镜", color = GREEN, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("HanziLens", color = DIM, fontSize = 14.sp, fontFamily = MONO)
            Text("Version 1.0", color = DIM, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
            Text(
                text     = "Camera-based Chinese character OCR with pinyin and definitions.",
                color    = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
                lineHeight = 20.sp
            )
        }

        // Donate button (placeholder)
        Button(
            onClick  = { /* TODO: link to donation page */ },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = AMBER),
            shape    = RoundedCornerShape(8.dp)
        ) {
            Text("☕  Support Development", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 16.dp))

        // App license
        SectionTitle("App License")
        LicenseCard(
            name    = "HanziLens",
            license = "MIT License",
            note    = "Copyright © 2025 invisiblesushi. Free to use, modify, and distribute."
        )

        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 16.dp))
        SectionTitle("Dictionary Data")

        LicenseCard(
            name    = "CC-CEDICT",
            license = "CC BY-SA 3.0",
            note    = "© MDBG — https://www.mdbg.net\nChina-English dictionary with 120,000+ entries.\nMust be attributed; share-alike applies to the data only, not app code."
        )

        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 16.dp))
        SectionTitle("Open Source Libraries")

        LibraryItem("Jetpack Compose",         "Google",      "Apache 2.0", "UI framework")
        LibraryItem("Material Design 3",        "Google",      "Apache 2.0", "UI components and theming")
        LibraryItem("CameraX",                  "Google",      "Apache 2.0", "Camera lifecycle management and preview")
        LibraryItem("ML Kit Text Recognition", "Google",      "ML Kit ToS", "On-device Chinese character OCR")
        LibraryItem("Room",                     "Google",      "Apache 2.0", "SQLite database ORM")
        LibraryItem("Navigation Compose",       "Google",      "Apache 2.0", "In-app navigation")
        LibraryItem("Lifecycle / ViewModel",    "Google",      "Apache 2.0", "Lifecycle-aware state management")
        LibraryItem("Kotlin Coroutines",        "JetBrains",   "Apache 2.0", "Async/concurrent programming")
        LibraryItem("Accompanist Permissions",  "Google",      "Apache 2.0", "Runtime permission helpers for Compose")
        LibraryItem("KSP",                      "Google",      "Apache 2.0", "Kotlin annotation processing (Room codegen)")
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text       = text.uppercase(),
        color      = GREEN,
        fontSize   = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = MONO,
        modifier   = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun LicenseCard(name: String, license: String, note: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape    = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(license, color = AMBER, fontSize = 11.sp, fontFamily = MONO)
            }
            Text(note, color = DIM, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun LibraryItem(name: String, author: String, license: String, description: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("$author · $description", color = DIM, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Text(license, color = AMBER, fontSize = 10.sp, fontFamily = MONO,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp))
    }
    HorizontalDivider(color = Color(0xFF1F1F1F))
}
