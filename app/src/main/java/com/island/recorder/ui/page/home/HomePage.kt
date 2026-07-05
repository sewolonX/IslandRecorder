package com.island.recorder.ui.page.home

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.recorder.R
import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import com.island.recorder.ui.theme.IslandTheme
import com.island.recorder.ui.theme.getMiuixAppBarColor
import com.island.recorder.ui.theme.miuixHomeStatusCardColorActivated
import com.island.recorder.ui.theme.miuixHomeStatusCardColorDeactivated
import com.island.recorder.ui.theme.recorderMiuixBlurEffect
import com.island.recorder.ui.theme.rememberMiuixBlurBackdrop
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomePage(
    viewModel: HomeViewModel = koinViewModel(),
    onNavigateToSettings: () -> Unit,
) {
    val uiState = viewModel.state.collectAsStateWithLifecycle().value
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backdrop = rememberMiuixBlurBackdrop(true)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCapability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.recorderMiuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = stringResource(R.string.home_title),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                bottom = 0.dp
            ),
            overscrollEffect = null
        ) {
            item {
                MiuixStatusGrid(
                    selectedAuthorizer = uiState.selectedAuthorizer,
                    rootMode = uiState.capability.rootMode,
                    shizukuMode = uiState.capability.shizukuMode,
                    onClick = onNavigateToSettings
                )
            }

            item {
                SettingsSummaryCard(
                    modifier = Modifier.padding(vertical = 12.dp),
                    settings = uiState.recordingSettings
                )
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun MiuixStatusGrid(
    selectedAuthorizer: Authorizer,
    rootMode: RootMode,
    shizukuMode: ShizukuMode,
    onClick: () -> Unit
) {
    val isActive = when (selectedAuthorizer) {
        Authorizer.Root -> rootMode != RootMode.None
        Authorizer.Shizuku -> shizukuMode == ShizukuMode.Authorized
    }
    val isDark = IslandTheme.isDark
    val containerColor = if (isActive) {
        if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
    } else {
        if (isDark) Color(0xFF381A1A) else Color(0xFFFAEEEE)
    }
    val textContentColor = MiuixTheme.colorScheme.onSurface
    val descTextColor = textContentColor.copy(alpha = 0.8f)
    val authorizerText = stringResource(
        if (selectedAuthorizer == Authorizer.Shizuku) R.string.authorization_method_shizuku else R.string.authorization_method_root
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = containerColor),
            onClick = onClick,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(50.dp, 38.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = if (isActive) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                        tint = if (isActive) miuixHomeStatusCardColorActivated else miuixHomeStatusCardColorDeactivated,
                        contentDescription = null
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp)
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (isActive) stringResource(R.string.authorization_status_active) else stringResource(
                            R.string.authorization_status_inactive
                        ),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textContentColor
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (isActive) stringResource(R.string.authorization_status_active_desc) else stringResource(
                            R.string.authorization_status_inactive_desc
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = descTextColor
                    )
                    Spacer(Modifier.height(36.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = authorizerText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = descTextColor
                    )
                }
            }
        }

    }
}

@Composable
private fun SettingsSummaryCard(
    modifier: Modifier = Modifier,
    settings: RecordingSettings
) {
    val context = LocalContext.current
    val (screenW, screenH) = remember {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        Pair(metrics.widthPixels, metrics.heightPixels)
    }
    val (qw, qh) = settings.videoQuality.computeDimensions(screenW, screenH)
    val qualityLabel = stringResource(
        R.string.quality_label_format,
        stringResource(settings.videoQuality.tierLabelResId), qw, qh
    )

    Card(modifier = modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.label_quality),
            summary = qualityLabel
        )
        BasicComponent(
            title = stringResource(R.string.label_bitrate),
            summary = stringResource(settings.videoBitrate.labelResId)
        )
        BasicComponent(
            title = stringResource(R.string.label_orientation),
            summary = stringResource(settings.screenOrientation.labelResId)
        )
        BasicComponent(
            title = stringResource(R.string.label_audio),
            summary = stringResource(settings.audioSource.labelResId)
        )
        BasicComponent(
            title = stringResource(R.string.label_frame_rate),
            summary = stringResource(settings.frameRate.labelResId)
        )
    }
}
