package dev.champions.dex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.champions.dex.components.BallMark
import dev.champions.dex.components.DexModalDrawer
import dev.champions.dex.components.GhostButton
import dev.champions.dex.components.LocalSheetHost
import dev.champions.dex.components.LocalToast
import dev.champions.dex.components.PillSegment
import dev.champions.dex.components.PillShape
import dev.champions.dex.components.SheetHost
import dev.champions.dex.components.SheetHostState
import dev.champions.dex.components.rememberDexDrawerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: DexViewModel = viewModel()
            DexApp(model)
        }
    }
}

@Composable
fun DexApp(model: DexViewModel) {
    DexTheme(model.appearance, model.reduceMotion) {
        val dark = LocalDark.current
        val view = LocalView.current
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        val dex = model.dex
        if (dex == null) {
            Column(
                Modifier.fillMaxSize().background(pal.bg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BallMark(Modifier.size(56.dp), pal.accent)
                Spacer(Modifier.height(24.dp))
                if (model.error != null) {
                    Text(model.error!!, color = pal.text)
                    GhostButton("重试", model::load)
                } else {
                    Text("正在打开图鉴", style = MaterialTheme.typography.titleMedium, color = pal.text)
                    val bar = Modifier.padding(top = 20.dp).width(120.dp)
                    val progress = model.loadProgress
                    if (progress !=
                        null
                    ) {
                        LinearProgressIndicator({ progress }, bar, color = pal.accent, trackColor = pal.n(300), drawStopIndicator = {})
                    } else {
                        LinearProgressIndicator(bar, color = pal.accent, trackColor = pal.n(300))
                    }
                }
            }
            return@DexTheme
        }
        var root by rememberSaveable { mutableStateOf(DexRoot.POKEMON) }
        // Detail routes above the catalogue; Navigation 3 keeps each entry's saved state and clears it once popped.
        var details by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        val backStack = remember(details) { listOf<DexKey>(HomeKey) + details.map(::DetailKey) }
        val drawer = rememberDexDrawerState()
        val sheets = remember { SheetHostState() }
        val scope = rememberCoroutineScope()
        // The catalogue page fades and rises in after a switch; it is a layer property, so the page itself never re-lays out.
        var pageIn by remember { mutableFloatStateOf(1f) }
        var pageAnimation by remember { mutableStateOf<Job?>(null) }
        var toast by remember { mutableStateOf<Pair<Int, String>?>(null) }
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(1600.milliseconds)
                toast = null
            }
        }

        fun open(route: String) {
            details = openDetailEntry(details, route)
        }

        fun back() {
            details = details.dropLast(1)
        }

        fun openDrawer() {
            scope.launch { drawer.open() }
        }

        /**
         * Shows [item] at once, with the old page dropped rather than cross-faded, so the new page's heavy first frame
         * is the only work in it. Only after that frame has been drawn do the page's fade-in and the drawer's slide start,
         * so neither animation begins with a dropped frame.
         */
        fun switchRoot(item: DexRoot) {
            pageAnimation?.cancel()
            pageIn = 1f
            if (item == root) {
                scope.launch { drawer.close() }
                return
            }
            root = item
            if (!model.reduceMotion) pageIn = 0f
            pageAnimation =
                scope.launch {
                    repeat(2) { withFrameNanos { } }
                    launch { drawer.close() }
                    if (pageIn < 1f) animate(pageIn, 1f, animationSpec = tween(ENTER_MS, easing = EnterEasing)) { v, _ -> pageIn = v }
                }
        }
        // Back from another top-level page returns to the catalogue first, rather than leaving the app.
        BackHandler(details.isEmpty() && root != DexRoot.POKEMON && !drawer.isOpen) { switchRoot(DexRoot.POKEMON) }
        BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
        CompositionLocalProvider(
            LocalToast provides { message -> toast = (toast?.first ?: 0) + 1 to message },
            LocalSheetHost provides sheets,
        ) {
            DexModalDrawer(
                drawer,
                gesturesEnabled = details.isEmpty() || drawer.isOpen,
                drawerContent = {
                    NavigationDrawerContent(
                        dex,
                        model,
                        root,
                        onPick = ::switchRoot,
                        onSettings = {
                            open("settings")
                            // Same order as a page switch: the detail's first frame lands before the drawer starts to move.
                            scope.launch {
                                repeat(2) { withFrameNanos { } }
                                drawer.close()
                            }
                        },
                    )
                },
            ) {
                Box(Modifier.fillMaxSize().background(pal.bg)) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        val duration = if (model.reduceMotion) 0 else 220
                        NavDisplay(
                            backStack,
                            Modifier.fillMaxSize(),
                            // A drawer still closing over a just-opened page takes the back press first.
                            onBack = { if (drawer.isOpen) scope.launch { drawer.close() } else back() },
                            transitionSpec = { detailSlide(1, duration) },
                            popTransitionSpec = { detailSlide(-1, duration) },
                            predictivePopTransitionSpec = { detailSlide(-1, duration) },
                        ) { key ->
                            when (key) {
                                HomeKey -> {
                                    NavEntry(key, contentKey = HOME_CONTENT_KEY) {
                                        val rootStates = rememberSaveableStateHolder()
                                        val rise = with(LocalDensity.current) { 24.dp.toPx() }
                                        Box(
                                            Modifier.fillMaxSize().background(pal.bg).graphicsLayer {
                                                alpha = pageIn
                                                translationY = (1f - pageIn) * rise
                                            },
                                        ) {
                                            val current = root
                                            key(current) {
                                                rootStates.SaveableStateProvider(current.name) {
                                                    when (current) {
                                                        DexRoot.POKEMON -> {
                                                            PokemonListScreen(dex, model, ::openDrawer, ::open)
                                                        }

                                                        DexRoot.MOVES -> {
                                                            MoveListScreen(dex, model, ::openDrawer, ::open)
                                                        }

                                                        DexRoot.ABILITIES -> {
                                                            AbilityListScreen(dex, model, ::openDrawer, ::open)
                                                        }

                                                        DexRoot.STATUSES -> {
                                                            StatusListScreen(dex, ::openDrawer, ::open)
                                                        }

                                                        DexRoot.TYPES -> {
                                                            TypeChartScreen(::openDrawer)
                                                        }

                                                        DexRoot.CALCULATOR -> {
                                                            CalculatorScreen(
                                                                dex,
                                                                model,
                                                                onNav = ::openDrawer,
                                                                isRoot = true,
                                                                onOpen = ::open,
                                                            )
                                                        }

                                                        DexRoot.SPEED -> {
                                                            SpeedScreen(dex, model, ::openDrawer, ::open)
                                                        }

                                                        DexRoot.TEAM -> {
                                                            TeamScreen(dex, model, ::openDrawer, ::open)
                                                        }

                                                        DexRoot.FAVORITES -> {
                                                            FavoritesScreen(dex, model, ::openDrawer, ::open)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                is DetailKey -> {
                                    NavEntry(key, contentKey = key.route) {
                                        val route = key.route
                                        Box(Modifier.fillMaxSize().background(pal.bg).testTag("detail:$route")) {
                                            when {
                                                route == "settings" -> {
                                                    SettingsScreen(model, dex, ::back)
                                                }

                                                route == "calculator-license" -> {
                                                    CalculatorLicense(::back)
                                                }

                                                route.startsWith(
                                                    "calculator:",
                                                ) -> {
                                                    CalculatorScreen(
                                                        dex,
                                                        model,
                                                        initialId = route.substringAfter(':'),
                                                        onNav = ::back,
                                                        isRoot = false,
                                                        onOpen = ::open,
                                                    )
                                                }

                                                else -> {
                                                    DetailScreen(route, dex, model, ::back, ::open)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SheetHost(sheets)
                    ToastHost(toast?.second, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 104.dp))
                }
            }
        }
    }
}

private fun Scene<DexKey>.isHome() = entries.lastOrNull()?.contentKey == HOME_CONTENT_KEY

/** The catalogue stays still beneath a detail sliding over or away from it; details slide past each other. */
private fun AnimatedContentTransitionScope<Scene<DexKey>>.detailSlide(
    direction: Int,
    duration: Int,
): ContentTransform {
    val enter =
        if (targetState.isHome()) {
            EnterTransition.None
        } else {
            fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { direction * it / 12 }
        }
    val exit =
        if (initialState.isHome()) {
            ExitTransition.KeepUntilTransitionsFinished
        } else {
            fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { -direction * it / 12 }
        }
    return (enter togetherWith exit).using(null)
}

@Composable
private fun ToastHost(
    message: String?,
    modifier: Modifier,
) {
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    val alpha by animateFloatAsState(if (message != null) 1f else 0f, tween(if (LocalReduceMotion.current) 0 else 250), label = "toast")
    if (alpha >
        0f
    ) {
        Text(
            last,
            modifier
                .alpha(alpha)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }.clip(PillShape)
                .background(pal.n(900))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            color = pal.n(100),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NavigationDrawerContent(
    dex: Dex,
    model: DexViewModel,
    current: DexRoot,
    onPick: (DexRoot) -> Unit,
    onSettings: () -> Unit,
) {
    val counts =
        mapOf(
            DexRoot.POKEMON to "${dex.speciesCount}",
            DexRoot.MOVES to "${dex.availableMoves.size}",
            DexRoot.ABILITIES to "${dex.abilities.size}",
            DexRoot.STATUSES to "${dex.mechanics.statuses.size}",
            DexRoot.TEAM to "${model.team.count { it != null }}/6",
            DexRoot.FAVORITES to "${model.favorites.size}",
        )
    ModalDrawerSheet(
        Modifier.width(304.dp),
        drawerShape = RoundedCornerShape(topEnd = 44.dp, bottomEnd = 44.dp),
        drawerContainerColor = pal.bg,
        drawerContentColor = pal.text,
    ) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 6.dp)) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = (-14).dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(pal.g(200)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-52).dp, y = 28.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(pal.a(300)),
                )
                Column {
                    Text("POKÉMON CHAMPIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = pal.a(700))
                    Text("对战图鉴", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 28.sp, lineHeight = 31.sp)
                }
            }
            DexRoot.entries.groupBy { it.group }.forEach { (group, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        group,
                        Modifier.padding(start = 12.dp, bottom = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = pal.n(600),
                    )
                    items.forEach { item -> DrawerItem(item.title, counts[item].orEmpty(), item == current) { onPick(item) } }
                    if (group == "我的") DrawerItem("设置与关于", "", false, onSettings)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "外观",
                    Modifier.padding(start = 8.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = pal.n(600),
                )
                PillSegment(
                    Appearance.entries.map {
                        it.label
                    },
                    model.appearance.ordinal,
                    { model.changeAppearance(Appearance.entries[it]) },
                    height = 40.dp,
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    count: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(PillShape)
            .background(if (selected) pal.a(200) else pal.bg)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontFamily = HeadingFont,
            fontWeight = HeadingWeight,
            fontSize = 17.sp,
            color = if (selected) pal.a(900) else pal.text,
        )
        Text(count, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(600))
    }
}
