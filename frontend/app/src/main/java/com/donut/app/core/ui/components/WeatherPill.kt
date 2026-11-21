package com.donut.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.donut.app.R
import com.donut.app.core.data.model.Forecast
import com.donut.app.core.ui.WeatherKind
import com.donut.app.core.ui.forecastText
import com.donut.app.core.ui.rainChanceText
import com.donut.app.core.ui.temperatureText
import com.donut.app.core.ui.theme.DonutTextStyles
import com.donut.app.core.ui.theme.DonutTheme
import com.donut.app.core.ui.weatherKind

/**
 * The forecast at the court in one line: icon, temperature, chance of rain (in the info colour).
 * TalkBack reads the whole forecast, e.g. "11°C · light rain · 40% chance of rain".
 */
@Composable
fun WeatherPill(forecast: Forecast, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val spoken = forecastText(forecast)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(weatherKind(forecast.description).icon),
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(18.dp),
        )
        Text(text = temperatureText(forecast), style = DonutTextStyles.numericSmall.copy(fontSize = 13.sp), color = muted)
        Text(
            text = rainChanceText(forecast),
            style = DonutTextStyles.numericSmall.copy(fontSize = 13.sp),
            color = DonutTheme.colors.info,
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
