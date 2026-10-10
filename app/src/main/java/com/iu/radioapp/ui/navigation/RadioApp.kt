package com.iu.radioapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.iu.radioapp.R
import com.iu.radioapp.ui.host.HostEntryScreen
import com.iu.radioapp.ui.nowplaying.NowPlayingRoute
import com.iu.radioapp.ui.rating.RatingTabsRoute
import com.iu.radioapp.ui.request.SongRequestRoute as SongRequestScreenRoute
import com.iu.radioapp.ui.requests.MyRequestsRoute as MyRequestsScreenRoute
import com.iu.radioapp.ui.search.TrackSearchRoute as TrackSearchScreenRoute
import kotlin.reflect.KClass

object NavTags {
    const val HOST_ENTRY = "nav_host_entry"
    fun tab(area: TopLevel) = "nav_tab_${area.name}"
}

enum class TopLevel(val route: Any, @StringRes val label: Int, @DrawableRes val icon: Int) {
    PROGRAM(ProgramRoute, R.string.nav_program, R.drawable.ic_nav_program),
    REQUESTS(MyRequestsRoute, R.string.nav_requests, R.drawable.ic_nav_requests),
    RATING(RatingRoute, R.string.nav_rating, R.drawable.ic_nav_rating),
}

// The request form has no area of its own: it stays in the one it was opened from.
private val areaOf: Map<KClass<*>, TopLevel> = mapOf(
    ProgramRoute::class to TopLevel.PROGRAM,
    MyRequestsRoute::class to TopLevel.REQUESTS,
    TrackSearchRoute::class to TopLevel.REQUESTS,
    RatingRoute::class to TopLevel.RATING,
)

private val titleOf: Map<KClass<*>, Int> = mapOf(
    ProgramRoute::class to R.string.title_now_playing,
    MyRequestsRoute::class to R.string.title_my_requests,
    TrackSearchRoute::class to R.string.title_search,
    SongRequestRoute::class to R.string.title_song_request,
    RatingRoute::class to R.string.title_rating,
    HostEntryRoute::class to R.string.title_host_entry,
)

private fun NavDestination.routeClass(): KClass<*>? = titleOf.keys.firstOrNull { hasRoute(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val routeClass = backStackEntry?.destination?.routeClass()
    var lastArea by rememberSaveable { mutableStateOf(TopLevel.PROGRAM) }
    val area = routeClass?.let { areaOf[it] } ?: lastArea
    LaunchedEffect(area) { lastArea = area }
    val isTopLevel = TopLevel.entries.any { routeClass == it.route::class }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(routeClass?.let { titleOf[it] } ?: R.string.app_name)) },
                navigationIcon = {
                    if (!isTopLevel && navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (routeClass != HostEntryRoute::class) {
                        TextButton(
                            onClick = { navController.navigate(HostEntryRoute) { launchSingleTop = true } },
                            modifier = Modifier.testTag(NavTags.HOST_ENTRY),
                        ) { Text(stringResource(R.string.action_host)) }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevel.entries.forEach { topLevel ->
                    NavigationBarItem(
                        selected = area == topLevel,
                        onClick = { navController.navigateToTopLevel(topLevel) },
                        icon = { Icon(painterResource(topLevel.icon), contentDescription = null) },
                        label = { Text(stringResource(topLevel.label)) },
                        modifier = Modifier.testTag(NavTags.tab(topLevel)),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = ProgramRoute, modifier = Modifier.padding(padding)) {
            composable<ProgramRoute> {
                NowPlayingRoute(onRequestTrack = { navController.navigate(SongRequestRoute.of(it)) })
            }
            composable<MyRequestsRoute> {
                MyRequestsScreenRoute(onFindTrack = { navController.navigate(TrackSearchRoute) })
            }
            composable<TrackSearchRoute> {
                TrackSearchScreenRoute(onTrackSelected = { navController.navigate(SongRequestRoute.of(it)) })
            }
            composable<SongRequestRoute> { entry ->
                SongRequestScreenRoute(
                    track = entry.toRoute<SongRequestRoute>().toTrack(),
                    onShowMyRequests = { navController.showMyRequestsAfterForm() },
                )
            }
            composable<RatingRoute> { RatingTabsRoute() }
            composable<HostEntryRoute> { HostEntryScreen() }
        }
    }
}

// Each area keeps its own back stack and its ViewModels (and with them the rating session lock).
private fun NavHostController.navigateToTopLevel(topLevel: TopLevel) {
    navigate(topLevel.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// The finished form leaves the stack; restoring the saved requests stack would only bring it back.
private fun NavHostController.showMyRequestsAfterForm() {
    popBackStack()
    if (popBackStack<MyRequestsRoute>(inclusive = false)) return
    navigate(MyRequestsRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}
