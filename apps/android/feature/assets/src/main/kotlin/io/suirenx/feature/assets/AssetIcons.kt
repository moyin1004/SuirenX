package io.suirenx.feature.assets

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

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
    AssetIconOption("item", "copy_item", R.drawable.fluent_package_3d, "life"),
    AssetIconOption("devices", "copy_desktop_computer", R.drawable.fluent_desktop_computer_3d, "digital"),
    AssetIconOption("laptop", "copy_laptop", R.drawable.fluent_laptop_3d, "digital"),
    AssetIconOption("phone", "copy_phone", R.drawable.fluent_mobile_phone_3d, "digital"),
    AssetIconOption("tablet", "copy_tablet", R.drawable.suiren_tablet, "digital"),
    AssetIconOption("headphones", "copy_headphones", R.drawable.fluent_headphone_3d, "digital"),
    AssetIconOption("watch", "copy_watch", R.drawable.fluent_watch_3d, "digital"),
    AssetIconOption("camera", "copy_camera", R.drawable.fluent_camera_3d, "digital"),
    AssetIconOption("gamepad", "copy_gaming_device", R.drawable.fluent_video_game_3d, "digital"),
    AssetIconOption("keyboard", "copy_keyboard", R.drawable.fluent_keyboard_3d, "digital"),
    AssetIconOption("mouse", "copy_mouse", R.drawable.fluent_computer_mouse_3d, "digital"),
    AssetIconOption("printer", "copy_printer", R.drawable.fluent_printer_3d, "digital"),
    AssetIconOption("television", "copy_tv", R.drawable.fluent_television_3d, "digital"),
    AssetIconOption("speaker", "copy_speaker", R.drawable.fluent_speaker_high_volume_3d, "digital"),
    AssetIconOption("microphone", "copy_microphone", R.drawable.fluent_microphone_3d, "digital"),
    AssetIconOption("battery", "copy_battery", R.drawable.fluent_battery_3d, "digital"),
    AssetIconOption("monitor", "copy_monitor", R.drawable.suiren_monitor, "digital"),
    AssetIconOption("power_supply", "copy_power_supply", R.drawable.suiren_power_supply, "digital"),
    AssetIconOption("hard_drive", "copy_hard_drive", R.drawable.suiren_hard_drive, "digital"),
    AssetIconOption("router", "copy_router", R.drawable.suiren_router, "digital"),
    AssetIconOption("charger", "copy_charger", R.drawable.suiren_charger, "digital"),
    AssetIconOption("power_bank", "copy_power_bank", R.drawable.suiren_power_bank, "digital"),
    AssetIconOption("power_strip", "copy_power_strip", R.drawable.suiren_power_strip, "digital"),
    AssetIconOption("projector", "copy_projector", R.drawable.suiren_projector, "digital"),
    AssetIconOption("nas", "NAS", R.drawable.suiren_nas, "digital"),
    AssetIconOption("mini_pc", "copy_mini_pc", R.drawable.suiren_mini_pc, "digital"),
    AssetIconOption("usb_hub", "copy_usb_hub", R.drawable.suiren_usb_hub, "digital"),
    AssetIconOption("book", "copy_books", R.drawable.fluent_books_3d, "life"),
    AssetIconOption("home", "copy_home", R.drawable.fluent_house_3d, "life"),
    AssetIconOption("chair", "copy_chair", R.drawable.fluent_chair_3d, "life"),
    AssetIconOption("sofa", "copy_sofa", R.drawable.fluent_couch_and_lamp_3d, "life"),
    AssetIconOption("lamp", "copy_light_bulb", R.drawable.fluent_light_bulb_3d, "life"),
    AssetIconOption("glasses", "copy_glasses", R.drawable.fluent_glasses_3d, "life"),
    AssetIconOption("shoes", "copy_running_shoes", R.drawable.fluent_running_shoe_3d, "life"),
    AssetIconOption("wrench", "copy_wrench", R.drawable.fluent_wrench_3d, "life"),
    AssetIconOption("air_purifier", "copy_air_purifier", R.drawable.suiren_air_purifier, "life"),
    AssetIconOption("robot_vacuum", "copy_robot_vacuum", R.drawable.suiren_robot_vacuum, "life"),
    AssetIconOption("fan", "copy_fan", R.drawable.suiren_fan, "life"),
    AssetIconOption("humidifier", "copy_humidifier", R.drawable.suiren_humidifier, "life"),
    AssetIconOption("hair_dryer", "copy_hair_dryer", R.drawable.suiren_hair_dryer, "life"),
    AssetIconOption("bicycle", "copy_bicycle", R.drawable.fluent_bicycle_3d, "travel"),
    AssetIconOption("backpack", "copy_backpack", R.drawable.fluent_backpack_3d, "travel"),
    AssetIconOption("luggage", "copy_luggage", R.drawable.fluent_luggage_3d, "travel"),
    AssetIconOption("car", "copy_car", R.drawable.fluent_automobile_3d, "travel"),
    AssetIconOption("motorcycle", "copy_motorcycle", R.drawable.fluent_motorcycle_3d, "travel"),
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
    val categories = listOf("all" to uiText(CopyR.string.copy_all), "digital" to uiText(CopyR.string.copy_electronics), "life" to uiText(CopyR.string.copy_everyday), "travel" to uiText(CopyR.string.copy_travel))
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
                TextButton(onClick = onDismiss, modifier = Modifier.width(64.dp)) { Text(uiText(CopyR.string.copy_cancel)) }
                Text(uiText(CopyR.string.copy_choose_asset_icon), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 16.sp)
                Button(
                    onClick = { onSelected(draftKey) },
                    modifier = Modifier.width(64.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(uiText(CopyR.string.copy_ok), maxLines = 1)
                }
            }
            Text(uiText(CopyR.string.copy_select_an_icon_then_tap_ok), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val optionDescription = uiText(option.label)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                onClick = { draftKey = option.key },
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(54.dp).semantics {
                                    contentDescription = optionDescription
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
