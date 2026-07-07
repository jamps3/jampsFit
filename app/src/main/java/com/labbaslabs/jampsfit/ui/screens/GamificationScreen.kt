package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.LocalMainViewModel
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.FestivalProgressCard
import com.labbaslabs.jampsfit.ui.components.GamificationCard
import com.labbaslabs.jampsfit.ui.components.WorkoutSummaryCard

@Composable
fun GamificationScreen(state: WatchState, scrollState: ScrollState = rememberScrollState()) {
    val viewModel = LocalMainViewModel.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Progress",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        GamificationCard(state)
        FestivalProgressCard(
            state = state,
            onCreateFestival = { viewModel.createFestival() },
            onSelectFestival = { viewModel.selectFestival(it) },
            onRenameFestival = { id, name -> viewModel.updateFestivalName(id, name) },
            onFestivalImageChange = { id, uri -> viewModel.updateFestivalImage(id, uri) },
            onAttachEventToFestival = { viewModel.attachEventToSelectedFestival(it) },
            onMoveEventToFestival = { eventId, festivalId -> viewModel.attachEventToFestival(eventId, festivalId) },
            onDeleteEvent = { viewModel.deleteEvent(it) }
        )
        WorkoutSummaryCard(state)
    }
}
