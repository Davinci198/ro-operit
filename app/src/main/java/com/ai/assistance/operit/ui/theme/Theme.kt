package com.ai.assistance.operit.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_DARK
import com.ai.assistance.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_LIGHT
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

private val DarkColorScheme =
        darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
        lightColorScheme(
                primary = Purple40,
                secondary = PurpleGrey40,
                tertiary = Pink40,

                /* Other default colors to override
                background = Color(0xFFFFFBFE),
                surface = Color(0xFFFFFBFE),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onTertiary = Color.White,
                onBackground = Color(0xFF1C1B1F),
                onSurface = Color(0xFF1C1B1F),
                */
                )


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID),
    )
    val themeSnapshot = rememberActiveThemePreferenceSnapshot()

    fun disableBackgroundForTarget(target: ActivePrompt) {
        coroutineScope.launch {
            activePromptManager.mutateActiveThemeForPrompt(target) { values ->
                values.withBoolean("use_background_image", false)
            }
        }
    }

    val useSystemTheme = themeSnapshot.useSystemTheme
    val themeMode = themeSnapshot.themeMode
    val useCustomColors = themeSnapshot.useCustomColors
    val customPrimaryColor = themeSnapshot.customPrimaryColor
    val customSecondaryColor = themeSnapshot.customSecondaryColor
    val onColorMode = themeSnapshot.onColorMode
    val useBackgroundImage = themeSnapshot.useBackgroundImage
    val backgroundImageUri = themeSnapshot.backgroundImageUri
    val backgroundImageOpacity = themeSnapshot.backgroundImageOpacity
    val backgroundMediaType = themeSnapshot.backgroundMediaType
    val videoBackgroundMuted = themeSnapshot.videoBackgroundMuted
    val videoBackgroundLoop = themeSnapshot.videoBackgroundLoop
    val useCustomStatusBarColor = themeSnapshot.useCustomStatusBarColor
    val customStatusBarColorValue = themeSnapshot.customStatusBarColor
    val statusBarTransparent = themeSnapshot.statusBarTransparent
    val statusBarHidden = themeSnapshot.statusBarHidden
    val useBackgroundBlur = themeSnapshot.useBackgroundBlur
    val backgroundBlurRadius = themeSnapshot.backgroundBlurRadius
    val useCustomFont = themeSnapshot.useCustomFont
    val fontType = themeSnapshot.fontType
    val systemFontName = themeSnapshot.systemFontName
    val customFontPath = themeSnapshot.customFontPath
    val fontScale = themeSnapshot.fontScale

    // 创建自定义 Typography
    val customTypography = remember(useCustomFont, fontType, systemFontName, customFontPath, fontScale) {
        createCustomTypography(
            context = context,
            useCustomFont = useCustomFont,
            fontType = fontType,
            systemFontName = systemFontName,
            customFontPath = customFontPath,
            fontScale = fontScale
        )
    }

    // 确定是否使用暗色主题
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
            if (useSystemTheme) {
                systemDarkTheme
            } else {
                themeMode == UserPreferencesManager.THEME_MODE_DARK
            }

    // Dynamic color is available on Android 12+
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 基础主题色调
    var colorScheme =
            when {
                dynamicColor -> {
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }

    // 应用自定义颜色和文本颜色
    if (useCustomColors) {
        customPrimaryColor?.let { primaryArgb ->
            val primary = Color(primaryArgb)
            val secondary = customSecondaryColor?.let { Color(it) } ?: colorScheme.secondary

            colorScheme = if (darkTheme) {
                generateDarkColorScheme(primary, secondary, onColorMode)
                    } else {
                generateLightColorScheme(primary, secondary, onColorMode)
                    }
        }
    }

    // Colours of the two system bars. Android 15+ ignores Window.setStatusBarColor and
    // Window.setNavigationBarColor for apps targeting SDK 35 or newer, so the very same values are
    // also drawn as bands at the end of this composable. Computing them once keeps the window
    // property and the drawn band on the same colour by construction.
    val statusBarBandColour: Color =
        when {
            statusBarHidden -> Color.Transparent
            statusBarTransparent -> Color.Transparent
            // A background image or video shows through the bar.
            useBackgroundImage && backgroundImageUri != null -> Color.Transparent
            useCustomStatusBarColor && customStatusBarColorValue != null ->
                Color(customStatusBarColorValue)
            else -> colorScheme.primary
        }
    val navigationBarBandColour: Color =
        if (useBackgroundImage && backgroundImageUri != null) {
            Color.Transparent
        } else {
            colorScheme.background
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = window.decorView.let { decorView ->
                androidx.core.view.WindowCompat.getInsetsController(window, decorView)
            }
            
            // 始终保持沉浸式模式，让Compose处理状态栏背景
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // 隐藏或显示状态栏
            if (statusBarHidden) {
                // 隐藏状态栏
                insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                insetsController?.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // 显示状态栏
                insetsController?.show(WindowInsetsCompat.Type.statusBars())
                
                // The colour itself is painted by the status bar band at the end of this composable;
                // this window property is what Android 14 and below still honour.
                window.statusBarColor = statusBarBandColour.toArgb()

                // A light status bar means dark icons, which suits a light background.
                insetsController?.isAppearanceLightStatusBars = !isColorLight(statusBarBandColour)
            }
            
            // Navigation bar (the gesture bar area): transparent over a background image, otherwise
            // the theme background. The band at the end of this composable paints the same colour, so
            // the choice stays visible on Android 15+, where this window property is ignored.
            window.navigationBarColor = navigationBarBandColour.toArgb()
            if (useBackgroundImage && backgroundImageUri != null) {
                // Contrast enforcement would add a scrim the background image does not want.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                insetsController?.isAppearanceLightNavigationBars = !darkTheme
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
                insetsController?.isAppearanceLightNavigationBars =
                    !isColorLight(colorScheme.background)
            }
        }
    }

    // 视频播放器状态
    val exoPlayer =
            remember(
                    useBackgroundImage,
                    backgroundImageUri,
                    backgroundMediaType,
                    videoBackgroundLoop,
                    videoBackgroundMuted
            ) {
                if (useBackgroundImage &&
                                backgroundImageUri != null &&
                                backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_VIDEO
                ) {
                    ExoPlayer.Builder(context)
                            // Add memory optimizations
                            .setLoadControl(
                                    DefaultLoadControl.Builder()
                                            .setBufferDurationsMs(
                                                    5000,  // 最小缓冲时间，减少到5秒
                                                    10000, // 最大缓冲时间，减少到10秒
                                                    500,   // 回放所需的最小缓冲
                                                    1000   // 重新缓冲后回放所需的最小缓冲
                                            )
                                            .setTargetBufferBytes(5 * 1024 * 1024) // 将缓冲限制为5MB
                                            .setPrioritizeTimeOverSizeThresholds(true)
                                            .build()
                            )
                            .build()
                            .apply {
                                // 设置循环播放
                                repeatMode =
                                        if (videoBackgroundLoop) Player.REPEAT_MODE_ALL
                                        else Player.REPEAT_MODE_OFF
                                // 设置静音
                                volume = if (videoBackgroundMuted) 0f else 1f
                                playWhenReady = true

                                // 加载视频
                                try {
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(backgroundImageUri))
                                        .build()
                                    setMediaItem(mediaItem)
                                    prepare()
                                } catch (e: Exception) {
                                    AppLogger.e(
                                            "OperitTheme",
                                            "Error loading video background: ${e.message}",
                                            e
                                    )
                                    disableBackgroundForTarget(activePrompt)
                                }
                            }
                } else {
                    null
                }
            }

    // 释放ExoPlayer资源
    DisposableEffect(key1 = Unit) { 
        onDispose { 
            try {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                exoPlayer?.release() 
            } catch (e: Exception) {
                AppLogger.e("OperitTheme", "ExoPlayer释放错误", e)
            }
        } 
    }

    // 监听应用生命周期，控制视频播放
    if (exoPlayer != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        exoPlayer.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        exoPlayer.play()
                    }
                    else -> {}
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // 应用主题和自定义背景
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val liquidGlassBackdrop = rememberLayerBackdrop()
        val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null

        CompositionLocalProvider(
            LocalThemePreferenceSnapshot provides themeSnapshot,
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop)
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(if (darkTheme) Color.Black else Color.White)
                            .then(
                                if (waterGlassState != null) {
                                    Modifier.liquefiable(waterGlassState)
                                } else {
                                    Modifier
                                },
                            )
                )

                if (useBackgroundImage && backgroundImageUri != null) {
                    val uri = Uri.parse(backgroundImageUri)

                    if (backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
                        val painter =
                            rememberAsyncImagePainter(
                                model = uri,
                                error =
                                    rememberAsyncImagePainter(
                                        if (darkTheme) Color.Black else Color.White
                                    ),
                            )

                        LaunchedEffect(painter) {
                            if (painter.state is AsyncImagePainter.State.Error) {
                                AppLogger.e(
                                    "OperitTheme",
                                    "Error loading background image from URI: $backgroundImageUri",
                                )

                                if (uri.scheme == "file") {
                                    val file = uri.path?.let { File(it) }
                                    if (file == null || !file.exists()) {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "Internal file doesn't exist: ${file?.absolutePath}",
                                        )
                                    } else {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "File exists but couldn't be loaded: ${file.absolutePath}, size: ${file.length()}",
                                        )
                                    }
                                }

                                disableBackgroundForTarget(activePrompt)
                            }
                        }

                        Image(
                            painter = painter,
                            contentDescription = "Background Image",
                            modifier =
                                Modifier.fillMaxSize()
                                    .alpha(backgroundImageOpacity)
                                    .then(
                                        if (useBackgroundBlur) {
                                            Modifier.blur(radius = backgroundBlurRadius.dp)
                                        } else {
                                            Modifier
                                        },
                                    ).then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        exoPlayer?.let { player ->
                            val videoBackgroundColor =
                                if (darkTheme) {
                                    android.graphics.Color.BLACK
                                } else {
                                    android.graphics.Color.WHITE
                                }
                            AndroidView(
                                factory = { ctx ->
                                    (LayoutInflater.from(ctx).inflate(
                                        R.layout.view_background_texture_player,
                                        null,
                                        false,
                                    ) as StyledPlayerView).apply {
                                        this.player = player
                                        useController = false
                                        layoutParams =
                                            ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setBackgroundColor(videoBackgroundColor)
                                        setShutterBackgroundColor(videoBackgroundColor)
                                        setKeepContentOnPlayerReset(true)
                                        foreground =
                                            android.graphics.drawable.ColorDrawable(
                                                android.graphics.Color.argb(
                                                    ((1f - backgroundImageOpacity) * 255).toInt(),
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                )
                                            )
                                    }
                                },
                                update = { view ->
                                    if (view.player != player) {
                                        view.player = player
                                    }

                                    view.setBackgroundColor(videoBackgroundColor)
                                    view.setShutterBackgroundColor(videoBackgroundColor)
                                    view.setKeepContentOnPlayerReset(true)
                                    view.foreground =
                                        android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.argb(
                                                ((1f - backgroundImageOpacity) * 255).toInt(),
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                            )
                                        )
                                },
                                modifier =
                                    Modifier.fillMaxSize().then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            if (useBackgroundImage && backgroundImageUri != null) {
                MaterialTheme(
                    colorScheme =
                        colorScheme.copy(
                            surface = colorScheme.surface.copy(alpha = 1f),
                            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 1f),
                            background = colorScheme.background.copy(alpha = 1f),
                            surfaceContainer = colorScheme.surfaceContainer.copy(alpha = 1f),
                            surfaceContainerHigh =
                                colorScheme.surfaceContainerHigh.copy(alpha = 1f),
                            surfaceContainerHighest =
                                colorScheme.surfaceContainerHighest.copy(alpha = 1f),
                            surfaceContainerLow =
                                colorScheme.surfaceContainerLow.copy(alpha = 1f),
                            surfaceContainerLowest =
                                colorScheme.surfaceContainerLowest.copy(alpha = 1f),
                        ),
                    typography = customTypography,
                    content = content,
                )
            } else {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = customTypography,
                    content = content,
                )
            }

            // The two system bar bands, drawn over the content because this app draws edge to edge
            // (setDecorFitsSystemWindows(false) above). Android 15+ ignores the window colour
            // properties for apps targeting SDK 35, so on those versions this is what actually shows
            // the colour the user picked. Each band is exactly as tall as its own inset, so it
            // collapses to nothing when the bar is hidden, and neither band consumes touch input.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(statusBarBandColour)
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(navigationBarBandColour)
            )
        }
    }
}

/** 为亮色主题生成基于主色的完整颜色方案 */
private fun generateLightColorScheme(
        primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String
): ColorScheme {
    val onPrimary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(primaryColor)
    }
    val onSecondary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(secondaryColor)
    }

    val primaryContainer = lightenColor(primaryColor, 0.7f)
    val onPrimaryContainer = getContrastingTextColor(primaryContainer)
    val secondaryContainer = lightenColor(secondaryColor, 0.7f)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer)

    // Return a complete color scheme, ensuring onSurface and onSurfaceVariant are consistent
    return LightColorScheme.copy(
            primary = primaryColor,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryColor,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
        // Ensure other colors are consistent with a light theme
        onSurface = Color.Black,
        onSurfaceVariant = Color.Black.copy(alpha = 0.7f),
        onBackground = Color.Black
    )
}

/** 为暗色主题生成基于主色的完整颜色方案 */
private fun generateDarkColorScheme(
        primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String
): ColorScheme {
    val adjustedPrimaryColor = lightenColor(primaryColor, 0.2f)
    val adjustedSecondaryColor = lightenColor(secondaryColor, 0.2f)

    val onPrimary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(adjustedPrimaryColor)
    }
    val onSecondary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(adjustedSecondaryColor)
    }

    val primaryContainer = darkenColor(primaryColor, 0.3f)
    val onPrimaryContainer = getContrastingTextColor(primaryContainer, forceLight = true)
    val secondaryContainer = darkenColor(secondaryColor, 0.3f)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer, forceLight = true)

    // Return a complete color scheme, ensuring onSurface and onSurfaceVariant are consistent
    return DarkColorScheme.copy(
            primary = adjustedPrimaryColor,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = adjustedSecondaryColor,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
        // Ensure other colors are consistent with a dark theme
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        onBackground = Color.White
    )
}

/** Add a new helper function to determine appropriate text color based on background color */
private fun getContrastingTextColor(
        backgroundColor: Color,
        forceDark: Boolean = false,
        forceLight: Boolean = false
): Color {
    // If forced, return the specified color
    if (forceDark) return Color.Black
    if (forceLight) return Color.White

    // Calculate color contrast and return appropriate color
    // Using luminance formula from Web Content Accessibility Guidelines (WCAG)
    val luminance =
            0.299 * backgroundColor.red +
                    0.587 * backgroundColor.green +
                    0.114 * backgroundColor.blue

    // Use a threshold of 0.5 for deciding between white and black text
    // Higher threshold (e.g., 0.6) would use white text more often
    return if (luminance > 0.5) Color.Black else Color.White
}

/** 使颜色变亮 */
private fun lightenColor(color: Color, factor: Float): Color {
    val r = color.red + (1f - color.red) * factor
    val g = color.green + (1f - color.green) * factor
    val b = color.blue + (1f - color.blue) * factor
    return Color(r, g, b, color.alpha)
}

/** 使颜色变暗 */
private fun darkenColor(color: Color, factor: Float): Color {
    val r = color.red * (1f - factor)
    val g = color.green * (1f - factor)
    val b = color.blue * (1f - factor)
    return Color(r, g, b, color.alpha)
}

/** 混合两种颜色 */
private fun blendColors(color1: Color, color2: Color, ratio: Float): Color {
    val r = color1.red * (1 - ratio) + color2.red * ratio
    val g = color1.green * (1 - ratio) + color2.green * ratio
    val b = color1.blue * (1 - ratio) + color2.blue * ratio
    return Color(r, g, b)
}

/** 判断颜色是否较浅 */
private fun isColorLight(color: Color): Boolean {
    // 计算颜色亮度 (0.0-1.0)
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}

/** 判断颜色是否较深 */
private fun isColorDark(color: Color): Boolean {
    return !isColorLight(color)
}
