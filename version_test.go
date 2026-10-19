package suirenx

import "testing"

func TestParseProductVersion(t *testing.T) {
	for _, version := range []string{"0.1.3", "0.1.4", "12.34.56"} {
		config := "// MARKETING_VERSION = 9.9.9\n MARKETING_VERSION = " + version + "\nCURRENT_PROJECT_VERSION = 1.0.4\n#include? \"ignored-local-file\"\n"
		if got := parseProductVersion(config); got != version {
			t.Fatalf("parseProductVersion() = %q, want %q", got, version)
		}
	}
}

func TestInvalidProductVersionFailsClosed(t *testing.T) {
	for _, config := range []string{"", "MARKETING_VERSION = ", "MARKETING_VERSION = 1.2", "MARKETING_VERSION = <script>", "MARKETING_VERSION = 1.2.3\nMARKETING_VERSION = 1.2.4", "MARKETING_VERSION = \nMARKETING_VERSION = 1.2.4"} {
		t.Run(config, func(t *testing.T) {
			defer func() {
				if recover() == nil {
					t.Fatal("invalid config did not panic")
				}
			}()
			parseProductVersion(config)
		})
	}
}
