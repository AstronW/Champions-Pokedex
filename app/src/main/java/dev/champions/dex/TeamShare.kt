package dev.champions.dex

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import dev.champions.dex.components.*

/** Preview of the team image with its two outlets: the system share sheet, or a copy kept on the device. */
@Composable
fun AnimatedVisibilityScope.TeamShareSheet(sets: List<MemberSet>, dex: Dex, onClose: () -> Unit) {
    val context = LocalContext.current; val toast = LocalToast.current; val scope = rememberCoroutineScope()
    val image = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    fun run(block: suspend (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            // The preview is the image: its recorded drawing is rendered again at full resolution.
            try { block(image.toImageBitmap().asAndroidBitmap()) } catch (_: Exception) { toast("图片生成失败，请重试") } finally { busy = false }
        }
    }
    // Before Android 10 a gallery write needs a storage permission, so the user picks a location instead.
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) run { bitmap -> toast(if (withContext(Dispatchers.IO) { writeImage(context, uri, bitmap) }) "已保存" else "保存失败，请重试") }
    }
    MenuPanel("队伍构建", pal.a(700), "分享队伍图片", true, onClose) {
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("保存到本地", {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) run { bitmap ->
                        toast(if (withContext(Dispatchers.IO) { saveToPictures(context, bitmap) }) "已保存到相册 · Pictures/$ImageFolder" else "保存失败，请重试")
                    } else createDocument.launch(imageName())
                }, Modifier.weight(1f), height = 44.dp)
                PrimaryButton("分享", { run { bitmap -> context.startActivity(withContext(Dispatchers.IO) { shareIntent(context, bitmap) }) } }, Modifier.weight(1f), height = 44.dp)
            }
        }
        item(key = "image") {
            // Every device lays the image out at the same width and font scale, so the export looks the same everywhere;
            // only the preview shrinks to fit a narrow screen.
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val scale = (maxWidth / ShareWidth).coerceAtMost(1f)
                val frame = RoundedCornerShape(20.dp)
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
                    TeamShareCard(sets, dex, Modifier.scaledToFit(ShareWidth, scale).clip(frame).border(1.dp, pal.divider, frame)
                        .drawWithContent { image.record { this@drawWithContent.drawContent() }; drawLayer(image) }.testTag("team-share-image"))
                }
            }
        }
        item(key = "note") { Text("图片按当前外观生成，展示每位成员的形态、道具、特性、能力调整与招式。", fontSize = 11.sp, color = pal.n(700)) }
    }
}

private val ShareWidth = 380.dp
/** One size for every move: the longest learnable names (6 characters) fit a move cell at [ShareWidth]. */
private val MoveIcon = 14.dp
private val MoveText = 9.sp

/** Lays the content out at [width] and draws it scaled by [scale], occupying only the scaled space. */
private fun Modifier.scaledToFit(width: Dp, scale: Float) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixedWidth(width.roundToPx()))
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
    }
}

/** The shared image: every member's form, item, ability, stat adjustment and moves on a solid page background. */
@Composable
private fun TeamShareCard(sets: List<MemberSet>, dex: Dex, modifier: Modifier) {
    Column(modifier.background(pal.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Kicker("POKÉMON CHAMPIONS", pal.a(700), spacing = 1.4.sp)
                Text("我的队伍", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 26.sp, lineHeight = 30.sp, color = pal.text)
            }
            Text("${sets.size} 只 · Lv.50", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        }
        sets.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ShareTile(it, dex, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text("Champions 对战图鉴", fontSize = 10.sp, color = pal.n(600))
    }
}

@Composable
private fun ShareTile(set: MemberSet, dex: Dex, modifier: Modifier) {
    val p = set.form; val m = set.member
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(typeTone(p.types.first()).blob).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PokemonArt(p, Modifier.size(48.dp))
            Text(p.displayName, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = pal.text, maxLines = 2)
        }
        // Two named types need the tile's full width.
        TypeTagRow(p.types, fontSize = 10.sp, icon = 16.dp, vertical = 2.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            set.item?.let { AssetImage("items/${it.sprite}.png", null, Modifier.size(18.dp)) }
            FitText("${set.item?.name ?: "无道具"} · ${dex.abilitiesById[set.ability]?.name ?: "—"}", Modifier.weight(1f))
        }
        FitText(if (m.up.isNotEmpty() && m.down.isNotEmpty()) "+${statLabel(m.up)}  −${statLabel(m.down)}" else "无能力调整", color = pal.n(800))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (set.moves.isEmpty()) FitText("未选择招式", color = pal.n(700))
            set.moves.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pair.forEach { move ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            TypeTag(move.type, showName = false, icon = MoveIcon)
                            Text(move.name, fontSize = MoveText, lineHeight = 12.sp, color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** One line that shrinks to fit, so the image never cuts a name short. */
@Composable
private fun FitText(text: String, modifier: Modifier = Modifier, color: Color = pal.text, size: TextUnit = 11.sp, weight: FontWeight = FontWeight.SemiBold) =
    BasicText(text, modifier, TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = BodyFont), maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = size, stepSize = .5.sp))
private const val ImageFolder = "ChampionsDex"

private fun imageName() = "champions-team-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".png"

/** A recorded layer may come back as a GPU bitmap, which must be read back before encoding. */
private fun Bitmap.writePng(out: OutputStream) {
    (if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this).compress(Bitmap.CompressFormat.PNG, 100, out)
}

/** Android 10+ lets an app add its own pictures to the gallery without a permission. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToPictures(context: Context, bitmap: Bitmap): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, imageName()); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ImageFolder"); put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
    return runCatching {
        resolver.openOutputStream(uri)!!.use(bitmap::writePng)
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }.onFailure { resolver.delete(uri, null, null) }.isSuccess
}

private fun writeImage(context: Context, uri: Uri, bitmap: Bitmap) =
    runCatching { context.contentResolver.openOutputStream(uri)!!.use(bitmap::writePng) }.isSuccess

/** The image goes through the app's cache and a read-only content URI, so the receiving app sees only this file. */
private fun shareIntent(context: Context, bitmap: Bitmap): Intent {
    val file = File(context.cacheDir, "shared").apply { mkdirs() }.resolve("champions-team.png")
    file.outputStream().use(bitmap::writePng)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri(null, uri) }
    return Intent.createChooser(send, "分享队伍")
}
