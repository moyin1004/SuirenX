#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
expected='hz version v0.9.7'
if [[ "$(hz --version)" != "$expected" ]]; then
  echo "Install the pinned generator: go install github.com/cloudwego/hertz/cmd/hz@v0.9.7" >&2
  exit 1
fi
include="$(go env GOPATH)/pkg/mod/github.com/cloudwego/hertz/cmd/hz@v0.9.7/protobuf/api"
hz update --idl ../../api/proto/suirenx/m5/v1/m5.proto \
  -I ../../api/proto -I "$include" \
  --module github.com/moyin1004/suirenx/services/api --out_dir . --unset_omitempty
protoc -I ../../api/proto -I "$include" \
  --descriptor_set_out=/tmp/suirenx-api.pb ../../api/proto/suirenx/asset/v1/asset.proto
protoc -I ../../api/proto -I "$include" \
  --descriptor_set_out=/tmp/suirenx-m5.pb ../../api/proto/suirenx/m5/v1/m5.proto
