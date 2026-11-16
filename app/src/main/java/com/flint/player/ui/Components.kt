// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pill button with custom colors that squares off while pressed. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val corner by animateIntAsState(
        targetValue = if (pressed) 22 else 50,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "pillCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 650f),
        label = "pillScale"
    )

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(RoundedCornerShape(percent = corner.coerceIn(0, 50)))
            .background(container)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Rounded dialog drawn inside the app window, so the status bar never reappears. */
@Composable
fun ExpressiveDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissText: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    ModalOverlay(onDismiss = onDismiss) {
        val scheme = MaterialTheme.colorScheme
        val pop = rememberPopProgress()

        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .popScale(pop)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(36.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = glassBorder()
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .popFade(pop)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                content()
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillButton(
                        text = dismissText,
                        onClick = onDismiss,
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer
                    )
                    if (confirmText != null) {
                        PillButton(
                            text = confirmText,
                            onClick = onConfirm,
                            container = if (destructive) scheme.error else scheme.primary,
                            content = if (destructive) scheme.onError else scheme.onPrimary,
                            enabled = confirmEnabled
                        )
                    }
                }
            }
        }
    }
}

/** Pill-shaped text field. */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

/** Empty / permission screen with a bouncy tonal icon. */
@Composable
fun EmptyPlaceholder(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(RoundedCornerShape(32.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}