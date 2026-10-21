import Foundation

struct AssetIcon: Identifiable {
    let key: String
    let label: String
    let image: String
    let category: String
    var id: String { key }
    static let all: [AssetIcon] = [
        AssetIcon(key: "item", label: L10n.text("ui.item"), image: "fluent_package_3d", category: "life"),
        AssetIcon(key: "devices", label: L10n.text("ui.desktop_computer"), image: "fluent_desktop_computer_3d", category: "digital"),
        AssetIcon(key: "laptop", label: L10n.text("ui.laptop"), image: "fluent_laptop_3d", category: "digital"),
        AssetIcon(key: "phone", label: L10n.text("ui.phone"), image: "fluent_mobile_phone_3d", category: "digital"),
        AssetIcon(key: "tablet", label: L10n.text("ui.tablet"), image: "suiren_tablet", category: "digital"),
        AssetIcon(key: "headphones", label: L10n.text("ui.headphones"), image: "fluent_headphone_3d", category: "digital"),
        AssetIcon(key: "watch", label: L10n.text("ui.watch"), image: "fluent_watch_3d", category: "digital"),
        AssetIcon(key: "camera", label: L10n.text("ui.camera"), image: "fluent_camera_3d", category: "digital"),
        AssetIcon(key: "gamepad", label: L10n.text("ui.gaming_device"), image: "fluent_video_game_3d", category: "digital"),
        AssetIcon(key: "keyboard", label: L10n.text("ui.keyboard"), image: "fluent_keyboard_3d", category: "digital"),
        AssetIcon(key: "mouse", label: L10n.text("ui.mouse"), image: "fluent_computer_mouse_3d", category: "digital"),
        AssetIcon(key: "printer", label: L10n.text("ui.printer"), image: "fluent_printer_3d", category: "digital"),
        AssetIcon(key: "television", label: L10n.text("ui.television"), image: "fluent_television_3d", category: "digital"),
        AssetIcon(key: "speaker", label: L10n.text("ui.speaker"), image: "fluent_speaker_high_volume_3d", category: "digital"),
        AssetIcon(key: "microphone", label: L10n.text("ui.microphone"), image: "fluent_microphone_3d", category: "digital"),
        AssetIcon(key: "battery", label: L10n.text("ui.battery"), image: "fluent_battery_3d", category: "digital"),
        AssetIcon(key: "monitor", label: L10n.text("ui.monitor"), image: "suiren_monitor", category: "digital"),
        AssetIcon(key: "power_supply", label: L10n.text("ui.power_supply"), image: "suiren_power_supply", category: "digital"),
        AssetIcon(key: "hard_drive", label: L10n.text("ui.hard_drive"), image: "suiren_hard_drive", category: "digital"),
        AssetIcon(key: "router", label: L10n.text("ui.router"), image: "suiren_router", category: "digital"),
        AssetIcon(key: "charger", label: L10n.text("ui.charger"), image: "suiren_charger", category: "digital"),
        AssetIcon(key: "power_bank", label: L10n.text("ui.power_bank"), image: "suiren_power_bank", category: "digital"),
        AssetIcon(key: "power_strip", label: L10n.text("ui.power_strip"), image: "suiren_power_strip", category: "digital"),
        AssetIcon(key: "projector", label: L10n.text("ui.projector"), image: "suiren_projector", category: "digital"),
        AssetIcon(key: "nas", label: "NAS", image: "suiren_nas", category: "digital"),
        AssetIcon(key: "mini_pc", label: L10n.text("ui.mini_pc"), image: "suiren_mini_pc", category: "digital"),
        AssetIcon(key: "usb_hub", label: L10n.text("ui.usb_hub"), image: "suiren_usb_hub", category: "digital"),
        AssetIcon(key: "book", label: L10n.text("ui.books"), image: "fluent_books_3d", category: "life"),
        AssetIcon(key: "home", label: L10n.text("ui.house"), image: "fluent_house_3d", category: "life"),
        AssetIcon(key: "chair", label: L10n.text("ui.chair"), image: "fluent_chair_3d", category: "life"),
        AssetIcon(key: "sofa", label: L10n.text("ui.sofa"), image: "fluent_couch_and_lamp_3d", category: "life"),
        AssetIcon(key: "lamp", label: L10n.text("ui.light_bulb"), image: "fluent_light_bulb_3d", category: "life"),
        AssetIcon(key: "glasses", label: L10n.text("ui.glasses"), image: "fluent_glasses_3d", category: "life"),
        AssetIcon(key: "shoes", label: L10n.text("ui.running_shoes"), image: "fluent_running_shoe_3d", category: "life"),
        AssetIcon(key: "wrench", label: L10n.text("ui.wrench"), image: "fluent_wrench_3d", category: "life"),
        AssetIcon(key: "air_purifier", label: L10n.text("ui.air_purifier"), image: "suiren_air_purifier", category: "life"),
        AssetIcon(key: "robot_vacuum", label: L10n.text("ui.robot_vacuum"), image: "suiren_robot_vacuum", category: "life"),
        AssetIcon(key: "fan", label: L10n.text("ui.fan"), image: "suiren_fan", category: "life"),
        AssetIcon(key: "humidifier", label: L10n.text("ui.humidifier"), image: "suiren_humidifier", category: "life"),
        AssetIcon(key: "hair_dryer", label: L10n.text("ui.hair_dryer"), image: "suiren_hair_dryer", category: "life"),
        AssetIcon(key: "bicycle", label: L10n.text("ui.bicycle"), image: "fluent_bicycle_3d", category: "travel"),
        AssetIcon(key: "backpack", label: L10n.text("ui.backpack"), image: "fluent_backpack_3d", category: "travel"),
        AssetIcon(key: "luggage", label: L10n.text("ui.luggage"), image: "fluent_luggage_3d", category: "travel"),
        AssetIcon(key: "car", label: L10n.text("ui.car"), image: "fluent_automobile_3d", category: "travel"),
        AssetIcon(key: "motorcycle", label: L10n.text("ui.motorcycle"), image: "fluent_motorcycle_3d", category: "travel"),
    ]
    static func find(_ key: String) -> AssetIcon { all.first { $0.key == key } ?? all[0] }
}
