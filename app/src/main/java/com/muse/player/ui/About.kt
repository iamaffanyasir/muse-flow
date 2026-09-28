package com.muse.player.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muse.player.data.OnlineArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private object Dev {
    const val NAME = "Mohammad Affan Yasir"
    const val ROLE = "Software Developer · Visualter"
    const val LOCATION = "New Delhi, India"
    const val BIO = "Code, create, innovate. Passionate about web dev, mobile apps, and IoT. " +
        "Building smarter solutions, multiple projects at a time."
    const val WORK = "Software Developer at Chilly Robotics Kitchen (The AutoChef), Noida"
    const val EDUCATION = "Jamia Hamdard, 2020–2024"
    const val AVATAR = "https://avatars.githubusercontent.com/u/37272627?v=4"
    const val WEBSITE = "https://visualter.in"
    const val GITHUB = "https://github.com/iamaffanyasir"
    const val LINKEDIN = "https://www.linkedin.com/in/mohammad-affan-yasir-6a1976179/"
    val SKILLS = listOf("React", "Node.js", "Python", "FastAPI", "Android", "IoT", "UI/UX")
    val PROJECTS = listOf(
        "Muse & Muse Flow" to "The music players you're holding — minimal, fast and offline-first.",
        "VisiVault" to "An offline, encrypted password manager with a liquid-glass UI.",
        "CManager" to "Client and project management for freelancers and small teams.",
        "30 Days · 30 Apps" to "A challenge to design and ship one new app every day for a month.",
    )
}

// Brand marks (Simple Icons, 24×24).
private val GithubMark: ImageVector by lazy {
    brand("GitHub", "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12")
}
private val LinkedinMark: ImageVector by lazy {
    brand("LinkedIn", "M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z")
}

private fun brand(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

// ------------------------------------------------------------------ footer

@Composable
fun BuiltWithLove() {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val beat by rememberInfiniteTransition(label = "heart").animateFloat(
        1f, 1.22f,
        infiniteRepeatable(keyframes { durationMillis = 1200; 1f at 0; 1.22f at 140; 1f at 280; 1.14f at 420; 1f at 600 }),
        label = "beat",
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { uri.openUri(Dev.WEBSITE) }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Built with ", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Icon(
                Icons.Rounded.Favorite, "love", tint = Color(0xFFFF375F),
                modifier = Modifier.size(15.dp).graphicsLayer { scaleX = beat; scaleY = beat },
            )
            Text(" by ", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Text("Visualter", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = cs.primary)
        }
        Text("Muse Flow 1.0", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

// ------------------------------------------------------------------ disclaimer

@Composable
fun DisclaimerDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer),
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp)) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFF9F0A).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Gavel, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.height(14.dp))
                Text("Disclaimer", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                Paragraph(
                    "Why I built Muse Flow",
                    "Muse Flow is a personal project: a beautiful, minimal and offline-first music player that can also " +
                        "save music you are allowed to keep — royalty-free and copyright-free tracks, Creative Commons " +
                        "releases, your own uploads, and music whose creators permit downloading.",
                )
                Paragraph(
                    "Only for non-copyrighted music",
                    "This app is developed only for downloading non-copyrighted content, or content you have the " +
                        "rights or permission to download. Please don't use it to download or share copyrighted " +
                        "material without the owner's permission.",
                )
                Paragraph(
                    "No responsibility for misuse",
                    "The developer does not host, store or distribute any content — downloads are made by you, " +
                        "directly from third-party services. The developer is not responsible if this app is used " +
                        "unlawfully, or for any copyright conflicts or claims arising from its use. You are solely " +
                        "responsible for following copyright laws and each platform's terms of service.",
                )
                Text(
                    "Muse Flow is not affiliated with, endorsed by or connected to YouTube, Google or any other platform.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp).height(48.dp),
            ) { Text("I understand") }
        }
    }
}

@Composable
private fun Paragraph(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

// ------------------------------------------------------------------ from the developer

private data class GithubLive(val bio: String?, val repos: Int?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val dark = cs.background.luminance() < 0.5f

    val avatar by produceState<android.graphics.Bitmap?>(null) {
        val file = OnlineArt.image(Dev.AVATAR).file
        value = file?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it.path) } }
    }
    // Live details from the public GitHub API; falls back to what's baked in when offline.
    val live by produceState(GithubLive(null, null)) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL("https://api.github.com/users/iamaffanyasir").openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    GithubLive(o.optString("bio").takeIf { it.isNotBlank() && it != "null" }, o.optInt("public_repos", -1).takeIf { it >= 0 })
                } finally {
                    c.disconnect()
                }
            }.getOrDefault(GithubLive(null, null))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 700.dp)
                .clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer),
        ) {
            // soft accent glow behind the header
            Box(
                Modifier.fillMaxWidth().height(170.dp)
                    .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.28f), Color.Transparent)))
            )
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(104.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(cs.primary, Color(0xFFBF5AF2), Color(0xFF64D2FF), cs.primary)))
                        .padding(4.dp).clip(CircleShape).background(cs.surfaceContainer).padding(3.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val a = avatar
                    if (a != null) Image(a.asImageBitmap(), Dev.NAME, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text("AY", style = MaterialTheme.typography.headlineMedium, color = cs.primary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(Dev.NAME, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(Dev.ROLE, style = MaterialTheme.typography.titleSmall, color = cs.primary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Rounded.Place, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(Dev.LOCATION, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "“${live.bio ?: Dev.BIO}”",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = cs.onSurface,
                )

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Social(Icons.Rounded.Language, "Website", cs.primary, Modifier.weight(1f)) { uri.openUri(Dev.WEBSITE) }
                    Social(GithubMark, "GitHub", if (dark) Color.White else Color(0xFF181717), Modifier.weight(1f)) { uri.openUri(Dev.GITHUB) }
                    Social(LinkedinMark, "LinkedIn", Color(0xFF0A66C2), Modifier.weight(1f)) { uri.openUri(Dev.LINKEDIN) }
                }

                Spacer(Modifier.height(18.dp))
                InfoLine(Icons.Rounded.Work, Dev.WORK)
                InfoLine(Icons.Rounded.School, Dev.EDUCATION)
                InfoLine(Icons.Rounded.Code, "${live.repos ?: 64} public repositories on GitHub")

                SectionLabel("Skills")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Dev.SKILLS.forEach { skill ->
                        Text(
                            skill, style = MaterialTheme.typography.labelLarge, color = cs.primary,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                SectionLabel("Things I've built")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh),
                ) {
                    Dev.PROJECTS.forEachIndexed { i, (name, desc) ->
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        if (i < Dev.PROJECTS.lastIndex) HorizontalDivider(Modifier.padding(start = 14.dp), thickness = 0.5.dp, color = cs.outlineVariant)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "Thanks for using Muse Flow! If you like it, say hi on any of the links above.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun Social(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).pressScale(0.92f, onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
    )
}
