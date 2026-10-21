package webui

import (
	"os/exec"
	"testing"
)

func TestLocalizationRuntime(t *testing.T) {
	node, err := exec.LookPath("node")
	if err != nil {
		t.Skip("Node.js is required for the Web localization runtime tests")
	}
	if output, err := exec.Command(node, "--test", "i18n.test.cjs").CombinedOutput(); err != nil {
		t.Fatalf("Web localization runtime: %v\n%s", err, output)
	}
}
