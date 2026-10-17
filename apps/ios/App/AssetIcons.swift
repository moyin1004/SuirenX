import Foundation

struct AssetIcon: Identifiable {
    let key: String
    let label: String
    let image: String
    let category: String
    var id: String { key }
    static let all: [AssetIcon] = [
        AssetIcon(key: "item", label: "物品", image: "fluent_package_3d", category: "life"),
        AssetIcon(key: "devices", label: "台式电脑", image: "fluent_desktop_computer_3d", category: "digital"),
        AssetIcon(key: "laptop", label: "笔记本", image: "fluent_laptop_3d", category: "digital"),
        AssetIcon(key: "phone", label: "手机", image: "fluent_mobile_phone_3d", category: "digital"),
        AssetIcon(key: "tablet", label: "平板", image: "suiren_tablet", category: "digital"),
        AssetIcon(key: "headphones", label: "耳机", image: "fluent_headphone_3d", category: "digital"),
        AssetIcon(key: "watch", label: "手表", image: "fluent_watch_3d", category: "digital"),
        AssetIcon(key: "camera", label: "相机", image: "fluent_camera_3d", category: "digital"),
        AssetIcon(key: "gamepad", label: "游戏设备", image: "fluent_video_game_3d", category: "digital"),
        AssetIcon(key: "keyboard", label: "键盘", image: "fluent_keyboard_3d", category: "digital"),
        AssetIcon(key: "mouse", label: "鼠标", image: "fluent_computer_mouse_3d", category: "digital"),
        AssetIcon(key: "printer", label: "打印机", image: "fluent_printer_3d", category: "digital"),
        AssetIcon(key: "television", label: "电视", image: "fluent_television_3d", category: "digital"),
        AssetIcon(key: "speaker", label: "音箱", image: "fluent_speaker_high_volume_3d", category: "digital"),
        AssetIcon(key: "microphone", label: "麦克风", image: "fluent_microphone_3d", category: "digital"),
        AssetIcon(key: "battery", label: "电池", image: "fluent_battery_3d", category: "digital"),
        AssetIcon(key: "monitor", label: "显示器", image: "suiren_monitor", category: "digital"),
        AssetIcon(key: "power_supply", label: "电源", image: "suiren_power_supply", category: "digital"),
        AssetIcon(key: "hard_drive", label: "硬盘", image: "suiren_hard_drive", category: "digital"),
        AssetIcon(key: "router", label: "路由器", image: "suiren_router", category: "digital"),
        AssetIcon(key: "charger", label: "充电器", image: "suiren_charger", category: "digital"),
        AssetIcon(key: "power_bank", label: "充电宝", image: "suiren_power_bank", category: "digital"),
        AssetIcon(key: "power_strip", label: "插线板", image: "suiren_power_strip", category: "digital"),
        AssetIcon(key: "projector", label: "投影仪", image: "suiren_projector", category: "digital"),
        AssetIcon(key: "nas", label: "NAS", image: "suiren_nas", category: "digital"),
        AssetIcon(key: "mini_pc", label: "迷你主机", image: "suiren_mini_pc", category: "digital"),
        AssetIcon(key: "usb_hub", label: "扩展坞", image: "suiren_usb_hub", category: "digital"),
        AssetIcon(key: "book", label: "书籍", image: "fluent_books_3d", category: "life"),
        AssetIcon(key: "home", label: "房屋", image: "fluent_house_3d", category: "life"),
        AssetIcon(key: "chair", label: "椅子", image: "fluent_chair_3d", category: "life"),
        AssetIcon(key: "sofa", label: "沙发", image: "fluent_couch_and_lamp_3d", category: "life"),
        AssetIcon(key: "lamp", label: "灯泡", image: "fluent_light_bulb_3d", category: "life"),
        AssetIcon(key: "glasses", label: "眼镜", image: "fluent_glasses_3d", category: "life"),
        AssetIcon(key: "shoes", label: "运动鞋", image: "fluent_running_shoe_3d", category: "life"),
        AssetIcon(key: "wrench", label: "扳手", image: "fluent_wrench_3d", category: "life"),
        AssetIcon(key: "air_purifier", label: "空气净化器", image: "suiren_air_purifier", category: "life"),
        AssetIcon(key: "robot_vacuum", label: "扫地机器人", image: "suiren_robot_vacuum", category: "life"),
        AssetIcon(key: "fan", label: "风扇", image: "suiren_fan", category: "life"),
        AssetIcon(key: "humidifier", label: "加湿器", image: "suiren_humidifier", category: "life"),
        AssetIcon(key: "hair_dryer", label: "吹风机", image: "suiren_hair_dryer", category: "life"),
        AssetIcon(key: "bicycle", label: "自行车", image: "fluent_bicycle_3d", category: "travel"),
        AssetIcon(key: "backpack", label: "背包", image: "fluent_backpack_3d", category: "travel"),
        AssetIcon(key: "luggage", label: "行李箱", image: "fluent_luggage_3d", category: "travel"),
        AssetIcon(key: "car", label: "汽车", image: "fluent_automobile_3d", category: "travel"),
        AssetIcon(key: "motorcycle", label: "摩托车", image: "fluent_motorcycle_3d", category: "travel"),
    ]
    static func find(_ key: String) -> AssetIcon { all.first { $0.key == key } ?? all[0] }
}
