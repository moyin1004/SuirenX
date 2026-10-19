// Package suirenx exposes product metadata shared by the native apps and server.
package suirenx

import (
	_ "embed"
	"fmt"
	"regexp"
	"strings"
)

//go:embed version.xcconfig
var versionConfig string

var productVersion = parseProductVersion(versionConfig)

// ProductVersion returns MARKETING_VERSION frozen into this binary at build time.
func ProductVersion() string { return productVersion }

func parseProductVersion(config string) string {
	var version string
	var found bool
	for _, line := range strings.Split(config, "\n") {
		key, value, ok := strings.Cut(line, "=")
		if !ok || strings.TrimSpace(key) != "MARKETING_VERSION" {
			continue
		}
		if found {
			panic("duplicate MARKETING_VERSION in version.xcconfig")
		}
		found = true
		version = strings.TrimSpace(value)
	}
	if !regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`).MatchString(version) {
		panic(fmt.Sprintf("invalid MARKETING_VERSION in version.xcconfig: %q", version))
	}
	return version
}
