package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ExportCsvIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ExportCsv",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 15f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(12f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(4f)
        quadToRelative(0f, 1.1f, -1.1f, 1.1f)
        horizontalLineToRelative(-12f)
        quadToRelative(-1.1f, 0f, -1.1f, -1.1f)
        close()
        moveTo(11f, 16f)
        verticalLineTo(6.8f)
        lineToRelative(-3.6f, 3.6f)
        lineToRelative(-1.4f, -1.4f)
        lineToRelative(6f, -6f)
        lineToRelative(6f, 6f)
        lineToRelative(-1.4f, 1.4f)
        lineToRelative(-3.6f, -3.6f)
        verticalLineTo(16f)
        horizontalLineToRelative(-2f)
        close()
    }.build()
