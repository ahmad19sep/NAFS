package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.R
import com.ahmad.nafs.ui.theme.JakartaSans
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Google Play listing graphics, drawn from the launcher icon layers so the store matches the app:
 * `build/outputs/store/icon-512.png` and `build/outputs/store/feature-graphic.png`.
 * At mdpi one dp is one pixel, so each window is exactly the size Play asks for.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class StoreGraphicsTest {
    @get:Rule val compose = createComposeRule()

    /** 512 × 512, full square: Play applies its own rounded mask. */
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    @Test fun icon() {
        compose.setContent { BrandIcon(Modifier.fillMaxSize()) }
        compose.onRoot().captureRoboImage("build/outputs/store/icon-512.png")
    }

    /** 1024 × 500 feature graphic, shown at the top of the listing. */
    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    @Test fun featureGraphic() {
        compose.setContent {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF14423F), Color(0xFF06141B)), start = Offset.Zero, end = Offset(1024f, 500f)),
                ),
            ) {
                Box(Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color(0x40E8C171), Color(0x00E8C171)), center = Offset(260f, 230f), radius = 420f),
                ))
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                ) {
                    BrandIcon(Modifier.size(300.dp).clip(RoundedCornerShape(64.dp)))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("NAFS", color = Color(0xFFE8C171), fontFamily = JakartaSans, fontWeight = FontWeight.Bold,
                            fontSize = 76.sp, letterSpacing = 6.sp)
                        Text("Plan your day. Keep your habits.\nPray on time. Grow honestly.", color = Color(0xFFEAF2F0),
                            fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 40.sp)
                        Text("Tasks · Habits · Prayer times · Coach", color = Color(0xFF9DB3B1), fontFamily = JakartaSans,
                            fontSize = 22.sp)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/store/feature-graphic.png")
    }
}

/** The adaptive launcher icon's two layers, drawn flat. */
@Composable
private fun BrandIcon(modifier: Modifier) = Box(modifier) {
    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
}
