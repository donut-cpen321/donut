package com.example.cpen321application.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.ui.WeatherKind
import com.example.cpen321application.core.ui.forecastText
import com.example.cpen321application.core.ui.rainChanceText
import com.example.cpen321application.core.ui.temperatureText
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.weatherKind

// The chance of rain is a little lighter than the temperature
private const val RAIN_ALPHA = 0.85f

/**
 * The forecast at the court as a soft blue 28 dp pill: icon, temperature, chance of rain.
 * TalkBack reads the whole forecast, e.g. "11°C · light rain · 40% chance of rain".
 */
@Composable
fun WeatherPill(forecast: Forecast, modifier: Modifier = Modifier) {
    val donut = DonutTheme.colors
    val spoken = forecastText(forecast)
    Row(
        modifier = modifier
            .height(28.dp)
            .background(donut.infoContainer, CircleShape)
            .padding(start = 8.dp, end = 10.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = painterResource(weatherKind(forecast.description).icon),
            contentDescription = null,
            tint = donut.onInfoContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(text = temperatureText(forecast), style = DonutTextStyles.numericSmall, color = donut.onInfoContainer)
        Text(
            text = rainChanceText(forecast),
            style = DonutTextStyles.numericSmall.copy(fontWeight = FontWeight.SemiBold),
            color = donut.onInfoContainer,
            modifier = Modifier.alpha(RAIN_ALPHA),
        )
    }
}

@get:DrawableRes
private val WeatherKind.icon: Int
    get() = when (this) {
        WeatherKind.CLEAR -> R.drawable.ic_sunny_filled
        WeatherKind.PARTLY_CLOUDY -> R.drawable.ic_partly_cloudy_day_filled
        WeatherKind.CLOUDY -> R.drawable.ic_cloud_filled
        WeatherKind.RAIN -> R.drawable.ic_rainy_filled
        WeatherKind.THUNDER -> R.drawable.ic_thunderstorm_filled
        WeatherKind.SNOW -> R.drawable.ic_weather_snowy_filled
        WeatherKind.FOG -> R.drawable.ic_foggy_filled
    }
