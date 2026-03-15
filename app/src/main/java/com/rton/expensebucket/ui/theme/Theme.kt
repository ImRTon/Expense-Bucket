package com.rton.expensebucket.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.data.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = Emerald40,
    onPrimary = Slate10,
    primaryContainer = Emerald20,
    onPrimaryContainer = Emerald80,
    secondary = Teal40,
    onSecondary = Slate10,
    secondaryContainer = Teal20,
    onSecondaryContainer = Teal80,
    tertiary = Sky60,
    background = SurfaceDark,
    onBackground = Slate90,
    surface = SurfaceDark,
    onSurface = Slate90,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = Slate80,
    error = Rose40,
    onError = Color.White,
    outline = Slate50
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald20,
    onPrimary = Color.White,
    primaryContainer = Emerald80,
    onPrimaryContainer = Emerald10,
    secondary = Teal20,
    onSecondary = Color.White,
    secondaryContainer = Teal80,
    onSecondaryContainer = Teal20,
    tertiary = Sky60,
    background = Color(0xFFF8FAFB),
    onBackground = Slate10,
    surface = Color.White,
    onSurface = Slate10,
    surfaceVariant = Slate90,
    onSurfaceVariant = Slate50,
    error = Rose40,
    onError = Color.White,
    outline = Slate50
)

private val LatteDarkColorScheme = darkColorScheme(
    primary = LatteSageDark,
    onPrimary = LatteSurfaceDark,
    primaryContainer = LatteContainerStroke,
    onPrimaryContainer = LatteSageDark,
    secondary = LatteCaramelDark,
    onSecondary = LatteSurfaceDark,
    secondaryContainer = LatteSurfaceDark,
    onSecondaryContainer = LatteCaramelDark,
    tertiary = Sky60,
    background = LatteSurfaceDark,
    onBackground = LatteSurfaceLight,
    surface = LatteSurfaceDark,
    onSurface = LatteSurfaceLight,
    surfaceVariant = LatteSurfaceDark,
    onSurfaceVariant = LatteSurfaceLight,
    error = LatteTerracottaDark,
    onError = LatteSurfaceDark,
    outline = LatteContainerStroke
)

private val LatteLightColorScheme = lightColorScheme(
    primary = LatteSageLight,
    onPrimary = LatteSurfaceLight,
    primaryContainer = LatteSageLight,
    onPrimaryContainer = LatteSurfaceLight,
    secondary = LatteCaramelLight,
    onSecondary = LatteSurfaceLight,
    secondaryContainer = LatteCaramelLight,
    onSecondaryContainer = LatteSurfaceLight,
    tertiary = Sky60,
    background = LatteSurfaceLight,
    onBackground = LatteSurfaceDark,
    surface = LatteSurfaceLight,
    onSurface = LatteSurfaceDark,
    surfaceVariant = LatteSurfaceSoftLight,
    onSurfaceVariant = LatteSurfaceDark,
    error = LatteTerracottaLight,
    onError = LatteSurfaceLight,
    outline = LatteContainerStroke
)

@Composable
fun ExpensesTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    appPalette: AppPalette = AppPalette.DEFAULT,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when(appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
    }

    val colorScheme = when {
        appPalette == AppPalette.LATTE -> if (darkTheme) LatteDarkColorScheme else LatteLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
