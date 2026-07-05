package com.island.recorder.ui.page.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.island.recorder.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun MiuixHomeRoute(
    viewModel: MiuixHomeViewModel = koinViewModel(),
    onNavigateToSettings: () -> Unit
) {
    MiuixHomePage(
        viewModel = viewModel,
        title = stringResource(R.string.home_title),
        outerPadding = PaddingValues(0.dp),
        onNavigateToSettings = onNavigateToSettings,
        modifier = Modifier.fillMaxSize(),
    )
}
