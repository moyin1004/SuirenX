// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SuirenX",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "SuirenCore", targets: ["SuirenCore"])],
    targets: [
        .systemLibrary(name: "CSQLite"),
        .target(name: "SuirenCore", dependencies: ["CSQLite"]),
        .testTarget(name: "SuirenCoreTests", dependencies: ["SuirenCore"]),
    ]
)
