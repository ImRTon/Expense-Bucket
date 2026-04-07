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
    primary = Emerald60,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Emerald10,
    onPrimaryContainer = Emerald80,
    secondary = Teal60,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Teal20,
    onSecondaryContainer = Teal80,
    tertiary = Color(0xFF8FD2FF),
    onTertiary = Color(0xFF00344F),
    tertiaryContainer = Color(0xFF004B71),
    onTertiaryContainer = Color(0xFFCBE6FF),
    background = SurfaceDark,
    onBackground = Slate90,
    surface = SurfaceDark,
    onSurface = Slate90,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = Slate80,
    surfaceTint = Emerald60,
    inverseSurface = Slate90,
    inverseOnSurface = SurfaceDark,
    inversePrimary = Emerald20,
    error = Rose40,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8D99A8),
    outlineVariant = Color(0xFF414B59),
    scrim = Color.Black,
    surfaceBright = Color(0xFF313845),
    surfaceDim = SurfaceDark,
    surfaceContainerLowest = Color(0xFF070D18),
    surfaceContainerLow = Color(0xFF141B28),
    surfaceContainer = Color(0xFF181F2D),
    surfaceContainerHigh = Color(0xFF232C3B),
    surfaceContainerHighest = Color(0xFF2E3747)
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald20,
    onPrimary = Color.White,
    primaryContainer = Emerald80,
    onPrimaryContainer = Color(0xFF002117),
    secondary = Teal20,
    onSecondary = Color.White,
    secondaryContainer = Teal80,
    onSecondaryContainer = Color(0xFF004D45),
    tertiary = Color(0xFF236488),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCBE6FF),
    onTertiaryContainer = Color(0xFF001E2F),
    background = Color(0xFFF6FBF8),
    onBackground = Slate10,
    surface = Color.White,
    onSurface = Slate10,
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404944),
    surfaceTint = Emerald20,
    inverseSurface = Color(0xFF2C322F),
    inverseOnSurface = Color(0xFFEDF2EE),
    inversePrimary = Emerald60,
    error = Rose40,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C3),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF6FBF8),
    surfaceDim = Color(0xFFD7DDD9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6F2),
    surfaceContainer = Color(0xFFEAF0EC),
    surfaceContainerHigh = Color(0xFFE4EBE6),
    surfaceContainerHighest = Color(0xFFDEE5E0)
)

private val LatteDarkColorScheme = darkColorScheme(
    primary = LatteSageDark,
    onPrimary = Color(0xFF22351D),
    primaryContainer = Color(0xFF394C33),
    onPrimaryContainer = Color(0xFFD3E6C6),
    secondary = LatteCaramelDark,
    onSecondary = Color(0xFF472A0B),
    secondaryContainer = Color(0xFF623F1D),
    onSecondaryContainer = Color(0xFFFFDDB6),
    tertiary = LatteTerracottaDark,
    onTertiary = Color(0xFF552019),
    tertiaryContainer = Color(0xFF703931),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = LatteSurfaceDark,
    onBackground = Color(0xFFEEE2D5),
    surface = LatteSurfaceDark,
    onSurface = Color(0xFFEEE2D5),
    surfaceVariant = Color(0xFF4E463B),
    onSurfaceVariant = Color(0xFFD1C5B5),
    surfaceTint = LatteSageDark,
    inverseSurface = Color(0xFFEEE2D5),
    inverseOnSurface = Color(0xFF342F29),
    inversePrimary = LatteSageLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9C8F7F),
    outlineVariant = Color(0xFF4E463B),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3D3832),
    surfaceDim = LatteSurfaceDark,
    surfaceContainerLowest = Color(0xFF110D09),
    surfaceContainerLow = Color(0xFF1C1813),
    surfaceContainer = LatteSurfaceRaisedDark,
    surfaceContainerHigh = Color(0xFF2C2822),
    surfaceContainerHighest = Color(0xFF37332D)
)

private val LatteLightColorScheme = lightColorScheme(
    primary = LatteSageLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE9D4),
    onPrimaryContainer = Color(0xFF1C2618),
    secondary = LatteCaramelLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7DEBF),
    onSecondaryContainer = Color(0xFF2F1B08),
    tertiary = LatteTerracottaLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF39140C),
    background = LatteSurfaceLight,
    onBackground = Color(0xFF201B15),
    surface = LatteSurfaceLight,
    onSurface = Color(0xFF201B15),
    surfaceVariant = LatteSurfaceSoftLight,
    onSurfaceVariant = Color(0xFF4E4639),
    surfaceTint = LatteSageLight,
    inverseSurface = Color(0xFF35302A),
    inverseOnSurface = Color(0xFFFBEEE1),
    inversePrimary = LatteSageDark,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = LatteContainerStroke,
    outlineVariant = Color(0xFFD3C6B3),
    scrim = Color.Black,
    surfaceBright = LatteSurfaceLight,
    surfaceDim = Color(0xFFE4D8C8),
    surfaceContainerLowest = Color(0xFFFFFCF7),
    surfaceContainerLow = LatteSurfaceRaisedLight,
    surfaceContainer = Color(0xFFF5EBD8),
    surfaceContainerHigh = Color(0xFFEFE4D0),
    surfaceContainerHighest = Color(0xFFE9DECA)
)

private val StrawberryMilkDarkColorScheme = darkColorScheme(
    primary = StrawberryPrimaryDark,
    onPrimary = Color(0xFF691C45),
    primaryContainer = Color(0xFF8B3760),
    onPrimaryContainer = Color(0xFFFFD9E7),
    secondary = StrawberrySecondaryDark,
    onSecondary = Color(0xFF4C2236),
    secondaryContainer = Color(0xFF643A4E),
    onSecondaryContainer = Color(0xFFFFD8E5),
    tertiary = StrawberryTertiaryDark,
    onTertiary = Color(0xFF60283D),
    tertiaryContainer = Color(0xFF7E425A),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = StrawberrySurfaceDark,
    onBackground = Color(0xFFF5DCE5),
    surface = StrawberrySurfaceDark,
    onSurface = Color(0xFFF5DCE5),
    surfaceVariant = Color(0xFF564048),
    onSurfaceVariant = Color(0xFFDCC0CA),
    surfaceTint = StrawberryPrimaryDark,
    inverseSurface = Color(0xFFF5DCE5),
    inverseOnSurface = Color(0xFF3A2C33),
    inversePrimary = StrawberryPrimaryLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA78893),
    outlineVariant = Color(0xFF564048),
    scrim = Color.Black,
    surfaceBright = Color(0xFF49363F),
    surfaceDim = StrawberrySurfaceDark,
    surfaceContainerLowest = Color(0xFF180B11),
    surfaceContainerLow = Color(0xFF281921),
    surfaceContainer = StrawberrySurfaceRaisedDark,
    surfaceContainerHigh = Color(0xFF382730),
    surfaceContainerHighest = Color(0xFF43333C)
)

private val StrawberryMilkLightColorScheme = lightColorScheme(
    primary = StrawberryPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD7E6),
    onPrimaryContainer = Color(0xFF470126),
    secondary = StrawberrySecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD7E4),
    onSecondaryContainer = Color(0xFF421727),
    tertiary = StrawberryTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF4B1830),
    background = StrawberrySurfaceLight,
    onBackground = Color(0xFF28161D),
    surface = StrawberrySurfaceLight,
    onSurface = Color(0xFF28161D),
    surfaceVariant = StrawberrySurfaceSoftLight,
    onSurfaceVariant = Color(0xFF5A414B),
    surfaceTint = StrawberryPrimaryLight,
    inverseSurface = Color(0xFF3B2C33),
    inverseOnSurface = Color(0xFFFEEAF1),
    inversePrimary = StrawberryPrimaryDark,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8D6F7B),
    outlineVariant = Color(0xFFE6BDC9),
    scrim = Color.Black,
    surfaceBright = StrawberrySurfaceLight,
    surfaceDim = Color(0xFFECD5DD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = StrawberrySurfaceRaisedLight,
    surfaceContainer = Color(0xFFFFE6EE),
    surfaceContainerHigh = Color(0xFFFADFE8),
    surfaceContainerHighest = Color(0xFFF4D8E2)
)

private val TropicalCactusDarkColorScheme = darkColorScheme(
    primary = CactusPrimaryDark,
    onPrimary = Color(0xFF482279),
    primaryContainer = Color(0xFF653A97),
    onPrimaryContainer = Color(0xFFF3DAFF),
    secondary = CactusSecondaryDark,
    onSecondary = Color(0xFF5B1667),
    secondaryContainer = Color(0xFF763483),
    onSecondaryContainer = Color(0xFFFED8FF),
    tertiary = CactusTertiaryDark,
    onTertiary = Color(0xFF5F0F49),
    tertiaryContainer = Color(0xFF7B2D64),
    onTertiaryContainer = Color(0xFFFFD8F0),
    background = CactusSurfaceDark,
    onBackground = Color(0xFFF0DDF7),
    surface = CactusSurfaceDark,
    onSurface = Color(0xFFF0DDF7),
    surfaceVariant = Color(0xFF4F4459),
    onSurfaceVariant = Color(0xFFD6C2E2),
    surfaceTint = CactusPrimaryDark,
    inverseSurface = Color(0xFFF0DDF7),
    inverseOnSurface = Color(0xFF312A39),
    inversePrimary = CactusPrimaryLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08CAB),
    outlineVariant = Color(0xFF4F4459),
    scrim = Color.Black,
    surfaceBright = Color(0xFF40384A),
    surfaceDim = CactusSurfaceDark,
    surfaceContainerLowest = Color(0xFF130D18),
    surfaceContainerLow = Color(0xFF231B29),
    surfaceContainer = CactusSurfaceRaisedDark,
    surfaceContainerHigh = Color(0xFF342A3B),
    surfaceContainerHighest = Color(0xFF3F3448)
)

private val TropicalCactusLightColorScheme = lightColorScheme(
    primary = CactusPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1DAFF),
    onPrimaryContainer = Color(0xFF310061),
    secondary = CactusSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD7FB),
    onSecondaryContainer = Color(0xFF380041),
    tertiary = CactusTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF43002E),
    background = CactusSurfaceLight,
    onBackground = Color(0xFF211A28),
    surface = CactusSurfaceLight,
    onSurface = Color(0xFF211A28),
    surfaceVariant = CactusSurfaceSoftLight,
    onSurfaceVariant = Color(0xFF51475A),
    surfaceTint = CactusPrimaryLight,
    inverseSurface = Color(0xFF352F3D),
    inverseOnSurface = Color(0xFFFBEFFF),
    inversePrimary = CactusPrimaryDark,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF83758D),
    outlineVariant = Color(0xFFD5C4E2),
    scrim = Color.Black,
    surfaceBright = CactusSurfaceLight,
    surfaceDim = Color(0xFFE4D8EC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CactusSurfaceRaisedLight,
    surfaceContainer = Color(0xFFEEE6F7),
    surfaceContainerHigh = Color(0xFFE8DFF2),
    surfaceContainerHighest = Color(0xFFE2D9EC)
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
        appPalette == AppPalette.STRAWBERRY_MILK ->
            if (darkTheme) StrawberryMilkDarkColorScheme else StrawberryMilkLightColorScheme
        appPalette == AppPalette.BERRY_YOGURT ->
            if (darkTheme) TropicalCactusDarkColorScheme else TropicalCactusLightColorScheme
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
