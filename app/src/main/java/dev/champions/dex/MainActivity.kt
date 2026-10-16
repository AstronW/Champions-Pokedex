package dev.champions.dex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { val model: DexViewModel = viewModel(); DexApp(model) }
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
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
        val dex = model.dex
        if (dex == null) {
            Column(Modifier.fillMaxSize().background(pal.bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                BallMark(Modifier.size(56.dp), pal.accent); Spacer(Modifier.height(24.dp))
                if (model.error != null) { Text(model.error!!, color = pal.text); GhostButton("重试", model::load) }
                else { Text("正在打开图鉴", style = MaterialTheme.typography.titleMedium, color = pal.text)
                    val bar = Modifier.padding(top = 20.dp).width(120.dp); val progress = model.loadProgress
                    if (progress != null) LinearProgressIndicator({ progress }, bar, color = pal.accent, trackColor = pal.n(300), drawStopIndicator = {})
                    else LinearProgressIndicator(bar, color = pal.accent, trackColor = pal.n(300)) }
            }
            return@DexTheme
        }
        var root by rememberSaveable { mutableStateOf(DexRoot.POKEMON) }
        var stack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var nextEntry by rememberSaveable { mutableIntStateOf(0) }
        val rootStates = rememberSaveableStateHolder()
        val detailStates = rememberSaveableStateHolder()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var toast by remember { mutableStateOf<Pair<Int, String>?>(null) }
        LaunchedEffect(toast) { if (toast != null) { delay(1600); toast = null } }
        fun open(key: String) {
            if (stack.lastOrNull()?.substringAfter('#') == key) return
            val updated = openDetailEntry(stack, "${nextEntry++}#$key")
            (stack - updated.toSet()).forEach { detailStates.removeState(it) }
            stack = updated
        }
        fun back() {
            stack.lastOrNull()?.let { detailStates.removeState(it) }
            stack = stack.dropLast(1)
        }
        fun openDrawer() { scope.launch { drawer.open() } }
        // Back from another top-level page returns to the catalogue first, rather than leaving the app.
        BackHandler(stack.isEmpty() && root != DexRoot.POKEMON && !drawer.isOpen) { root = DexRoot.POKEMON }
        BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
        BackHandler(stack.isNotEmpty() && !drawer.isOpen) { back() }
        CompositionLocalProvider(LocalToast provides { message -> toast = (toast?.first ?: 0) + 1 to message }) {
            ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = stack.isEmpty() || drawer.isOpen, scrimColor = pal.n(900).copy(alpha = .45f), drawerContent = {
                // Switch pages first and let that heavy first frame finish while the drawer is still open, then slide the drawer
                // away over the page's own fade-in. Closing first left a pause after the drawer had gone before anything appeared.
                suspend fun closeDrawer() {
                    repeat(2) { withFrameNanos { } }
                    if (model.reduceMotion) drawer.snapTo(DrawerValue.Closed) else drawer.close()
                }
                NavigationDrawerContent(dex, model, root, onPick = { item -> root = item; scope.launch { closeDrawer() } },
                    onSettings = { open("settings"); scope.launch { closeDrawer() } })
            }) {
                Box(Modifier.fillMaxSize().background(pal.bg).safeDrawingPadding()) {
                    Box(if (stack.isNotEmpty()) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                        val pageDuration = if (model.reduceMotion) 0 else 260
                        AnimatedContent(targetState = root, modifier = Modifier.fillMaxSize(), transitionSpec = {
                            (fadeIn(tween(pageDuration)) + slideInVertically(tween(pageDuration)) { it / 24 }) togetherWith
                                fadeOut(tween(pageDuration / 2))
                        }, label = "page") { current ->
                        rootStates.SaveableStateProvider(current.name) {
                            when (current) {
                                DexRoot.POKEMON -> PokemonListScreen(dex, model, ::openDrawer, ::open)
                                DexRoot.MOVES -> MoveListScreen(dex, model, ::openDrawer, ::open)
                                DexRoot.ABILITIES -> AbilityListScreen(dex, model, ::openDrawer, ::open)
                                DexRoot.STATUSES -> StatusListScreen(dex, ::openDrawer, ::open)
                                DexRoot.TYPES -> TypeChartScreen(::openDrawer)
                                DexRoot.CALCULATOR -> CalculatorScreen(dex, model, onNav = ::openDrawer, isRoot = true, onOpen = ::open)
                                DexRoot.SPEED -> SpeedScreen(dex, model, ::openDrawer, ::open)
                                DexRoot.TEAM -> TeamScreen(dex, model, ::openDrawer, ::open)
                                DexRoot.FAVORITES -> FavoritesScreen(dex, model, ::openDrawer, ::open)
                            }
                        }
                        }
                    }
                    val duration = if (model.reduceMotion) 0 else 220
                    AnimatedContent(targetState = stack.lastOrNull(), modifier = Modifier.fillMaxSize(), transitionSpec = {
                        val direction = if (isReturningToDetail(initialState, targetState)) -1 else 1
                        ((fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { direction * it / 12 }) togetherWith
                            (fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { -direction * it / 12 })).using(null)
                    }, label = "navigation") { entry ->
                        if (entry != null) detailStates.SaveableStateProvider(entry) {
                            val route = entry.substringAfter('#')
                            Box(Modifier.fillMaxSize().background(pal.bg).testTag("detail:$route")) {
                                when {
                                    route == "settings" -> SettingsScreen(model, dex, ::back)
                                    route == "calculator-license" -> CalculatorLicense(::back)
                                    route.startsWith("calculator:") -> CalculatorScreen(dex, model, initialId = route.substringAfter(':'), onNav = ::back, isRoot = false, onOpen = ::open)
                                    else -> DetailScreen(route, dex, model, ::back, ::open)
                                }
                            }
                        }
                    }
                    ToastHost(toast?.second, Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp))
                }
            }
        }
    }
}

@Composable
private fun ToastHost(message: String?, modifier: Modifier) {
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    val alpha by animateFloatAsState(if (message != null) 1f else 0f, tween(if (LocalReduceMotion.current) 0 else 250), label = "toast")
    if (alpha > 0f) Text(last, modifier.alpha(alpha).semantics { liveRegion = LiveRegionMode.Polite }.clip(PillShape).background(pal.n(900)).padding(horizontal = 18.dp, vertical = 10.dp),
        color = pal.n(100), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NavigationDrawerContent(dex: Dex, model: DexViewModel, current: DexRoot, onPick: (DexRoot) -> Unit, onSettings: () -> Unit) {
    val counts = mapOf(DexRoot.POKEMON to "${dex.speciesCount}", DexRoot.MOVES to "${dex.availableMoves.size}",
        DexRoot.ABILITIES to "${dex.abilities.size}", DexRoot.STATUSES to "${dex.mechanics.statuses.size}", DexRoot.TEAM to "${model.team.count { it != null }}/6",
        DexRoot.FAVORITES to "${model.favorites.size}")
    ModalDrawerSheet(Modifier.width(304.dp), drawerShape = RoundedCornerShape(topEnd = 44.dp, bottomEnd = 44.dp), drawerContainerColor = pal.bg, drawerContentColor = pal.text) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 6.dp)) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = (-14).dp).size(64.dp).clip(CircleShape).background(pal.g(200)))
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-52).dp, y = 28.dp).size(22.dp).clip(CircleShape).background(pal.a(300)))
                Column {
                    Text("POKÉMON CHAMPIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = pal.a(700))
                    Text("对战图鉴", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 28.sp, lineHeight = 31.sp)
                }
            }
            DexRoot.entries.groupBy { it.group }.forEach { (group, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(group, Modifier.padding(start = 12.dp, bottom = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = pal.n(600))
                    items.forEach { item -> DrawerItem(item.title, counts[item].orEmpty(), item == current) { onPick(item) } }
                    if (group == "我的") DrawerItem("设置与关于", "", false, onSettings)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("外观", Modifier.padding(start = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = pal.n(600))
                PillSegment(Appearance.entries.map { it.label }, model.appearance.ordinal, { model.changeAppearance(Appearance.entries[it]) }, height = 40.dp)
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(PillShape).background(if (selected) pal.a(200) else pal.bg)
        .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 17.sp, color = if (selected) pal.a(900) else pal.text)
        Text(count, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(600))
    }
}
