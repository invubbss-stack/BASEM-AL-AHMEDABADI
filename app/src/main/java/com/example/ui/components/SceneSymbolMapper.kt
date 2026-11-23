package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.SceneSymbol

fun SceneSymbol.toImageVector(): ImageVector = when (this) {
    SceneSymbol.NONE -> Icons.Filled.Block
    SceneSymbol.SPARKLES -> Icons.Filled.AutoAwesome
    SceneSymbol.FLASH -> Icons.Filled.Bolt
    SceneSymbol.ROCKET -> Icons.Filled.RocketLaunch
    SceneSymbol.SHOPPING -> Icons.Filled.ShoppingBag
    SceneSymbol.GIFT -> Icons.Filled.CardGiftcard
    SceneSymbol.STAR -> Icons.Filled.Star
    SceneSymbol.TIMER -> Icons.Filled.Timer
    SceneSymbol.SHIELD -> Icons.Filled.VerifiedUser
    SceneSymbol.TRENDING -> Icons.AutoMirrored.Filled.TrendingUp
    SceneSymbol.CAFE -> Icons.Filled.LocalCafe
    SceneSymbol.CELEBRATE -> Icons.Filled.Celebration
    SceneSymbol.PLANET -> Icons.Filled.Public
    SceneSymbol.GAMEPAD -> Icons.Filled.SportsEsports
    SceneSymbol.FILM -> Icons.Filled.Movie
    SceneSymbol.BOOK -> Icons.Filled.AutoStories
    SceneSymbol.CAMERA -> Icons.Filled.PhotoCamera
    SceneSymbol.MUSIC -> Icons.Filled.MusicNote
    SceneSymbol.BRAIN -> Icons.Filled.Psychology
    SceneSymbol.TROPHY -> Icons.Filled.EmojiEvents
    SceneSymbol.COMPASS -> Icons.Filled.Explore
    SceneSymbol.ECO -> Icons.Filled.Eco
    SceneSymbol.CODE -> Icons.Filled.Code
    SceneSymbol.MICROPHONE -> Icons.Filled.Mic
}
