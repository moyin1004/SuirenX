package io.suirenx.feature.assets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AssetIconOption(
    val key: String,
    val label: String,
    @DrawableRes val image: Int,
    val category: String,
)

// Stable persisted keys, with fixed Fluent Emoji 3D images and original product vectors bundled offline.
// Source: microsoft/fluentui-emoji at 1ffb34c752ecf5d402f04cfb4b392c77f57c54bc.
// MIT copyright and permission notice is packaged in assets/licenses/FluentEmoji-MIT.txt.
internal val assetIconOptions = listOf(
    AssetIconOption("item", "物品", R.drawable.fluent_package_3d, "life"),
    AssetIconOption("devices", "台式电脑", R.drawable.fluent_desktop_computer_3d, "digital"),
    AssetIconOption("laptop", "笔记本", R.drawable.fluent_laptop_3d, "digital"),
    AssetIconOption("phone", "手机", R.drawable.fluent_mobile_phone_3d, "digital"),
    AssetIconOption("tablet", "平板", R.drawable.suiren_tablet, "digital"),
    AssetIconOption("headphones", "耳机", R.drawable.fluent_headphone_3d, "digital"),
    AssetIconOption("watch", "手表", R.drawable.fluent_watch_3d, "digital"),
    AssetIconOption("camera", "相机", R.drawable.fluent_camera_3d, "digital"),
    AssetIconOption("gamepad", "游戏设备", R.drawable.fluent_video_game_3d, "digital"),
    AssetIconOption("keyboard", "键盘", R.drawable.fluent_keyboard_3d, "digital"),
    AssetIconOption("mouse", "鼠标", R.drawable.fluent_computer_mouse_3d, "digital"),
    AssetIconOption("printer", "打印机", R.drawable.fluent_printer_3d, "digital"),
    AssetIconOption("television", "电视", R.drawable.fluent_television_3d, "digital"),
    AssetIconOption("speaker", "音箱", R.drawable.fluent_speaker_high_volume_3d, "digital"),
    AssetIconOption("microphone", "麦克风", R.drawable.fluent_microphone_3d, "digital"),
    AssetIconOption("battery", "电池", R.drawable.fluent_battery_3d, "digital"),
    AssetIconOption("monitor", "显示器", R.drawable.suiren_monitor, "digital"),
    AssetIconOption("power_supply", "电源", R.drawable.suiren_power_supply, "digital"),
    AssetIconOption("hard_drive", "硬盘", R.drawable.suiren_hard_drive, "digital"),
    AssetIconOption("router", "路由器", R.drawable.suiren_router, "digital"),
    AssetIconOption("charger", "充电器", R.drawable.suiren_charger, "digital"),
    AssetIconOption("power_bank", "充电宝", R.drawable.suiren_power_bank, "digital"),
    AssetIconOption("power_strip", "插线板", R.drawable.suiren_power_strip, "digital"),
    AssetIconOption("projector", "投影仪", R.drawable.suiren_projector, "digital"),
    AssetIconOption("nas", "NAS", R.drawable.suiren_nas, "digital"),
    AssetIconOption("mini_pc", "迷你主机", R.drawable.suiren_mini_pc, "digital"),
    AssetIconOption("usb_hub", "扩展坞", R.drawable.suiren_usb_hub, "digital"),
    AssetIconOption("book", "书籍", R.drawable.fluent_books_3d, "life"),
    AssetIconOption("home", "房屋", R.drawable.fluent_house_3d, "life"),
    AssetIconOption("chair", "椅子", R.drawable.fluent_chair_3d, "life"),
    AssetIconOption("sofa", "沙发", R.drawable.fluent_couch_and_lamp_3d, "life"),
    AssetIconOption("lamp", "灯泡", R.drawable.fluent_light_bulb_3d, "life"),
    AssetIconOption("glasses", "眼镜", R.drawable.fluent_glasses_3d, "life"),
    AssetIconOption("shoes", "运动鞋", R.drawable.fluent_running_shoe_3d, "life"),
    AssetIconOption("wrench", "扳手", R.drawable.fluent_wrench_3d, "life"),
    AssetIconOption("air_purifier", "空气净化器", R.drawable.suiren_air_purifier, "life"),
    AssetIconOption("robot_vacuum", "扫地机器人", R.drawable.suiren_robot_vacuum, "life"),
    AssetIconOption("fan", "风扇", R.drawable.suiren_fan, "life"),
    AssetIconOption("humidifier", "加湿器", R.drawable.suiren_humidifier, "life"),
    AssetIconOption("hair_dryer", "吹风机", R.drawable.suiren_hair_dryer, "life"),
    AssetIconOption("bicycle", "自行车", R.drawable.fluent_bicycle_3d, "travel"),
    AssetIconOption("backpack", "背包", R.drawable.fluent_backpack_3d, "travel"),
    AssetIconOption("luggage", "行李箱", R.drawable.fluent_luggage_3d, "travel"),
    AssetIconOption("car", "汽车", R.drawable.fluent_automobile_3d, "travel"),
    AssetIconOption("motorcycle", "摩托车", R.drawable.fluent_motorcycle_3d, "travel"),
)

// Unknown keys remain stored; only their visual representation falls back.
internal fun assetIconOption(key: String): AssetIconOption =
    assetIconOptions.firstOrNull { it.key == key } ?: assetIconOptions.first()

@Composable
internal fun AssetCategoryIcon(
    option: AssetIconOption,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 28.dp,
    cornerRadius: Dp = 14.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(option.image),
            contentDescription = contentDescription,
            modifier = Modifier.size(glyphSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssetIconPicker(
    selectedKey: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftKey by rememberSaveable(selectedKey) { mutableStateOf(selectedKey) }
    var category by rememberSaveable { mutableStateOf("all") }
    val categories = listOf("all" to "全部", "digital" to "数码", "life" to "生活", "travel" to "出行")
    val visible = assetIconOptions.filter { category == "all" || it.category == category }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.width(64.dp)) { Text("取消") }
                Text("选择资产图标", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 16.sp)
                Button(
                    onClick = { onSelected(draftKey) },
                    modifier = Modifier.width(64.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("确定", maxLines = 1)
                }
            }
            Text("选中后点击确定。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { (key, label) ->
                    Box(
                        modifier = Modifier.height(48.dp).widthIn(min = 48.dp).selectable(
                            selected = category == key,
                            role = Role.Tab,
                            onClick = { category = key },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            color = if (category == key) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(label, textAlign = TextAlign.Center, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (maxWidth >= 292.dp) 5 else 4),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(visible, key = { it.key }) { option ->
                        val isSelected = draftKey == option.key
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                onClick = { draftKey = option.key },
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(54.dp).semantics {
                                    contentDescription = option.label
                                    selected = isSelected
                                    role = Role.RadioButton
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(painterResource(option.image), contentDescription = null, modifier = Modifier.size(40.dp))
                                    if (isSelected) {
                                        Text("✓", modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
