package com.example.familysafetube.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.familysafetube.R

/** The FamilyTube logo (transparent PNG). Decorative: the app name is always written next to it or implied by the screen. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(painter = painterResource(R.drawable.logo), contentDescription = null, modifier = modifier)
}
