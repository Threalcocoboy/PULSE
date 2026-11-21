#!/usr/bin/env bash
set -euo pipefail

tag=$1
[[ "$tag" =~ ^v[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}$ ]]

cd release-output
sha256sum --check --strict SHA256SUMS.txt
read -r expected apk < SHA256SUMS.txt
[[ "$expected" =~ ^[a-f0-9]{64}$ ]]
[[ "$apk" =~ ^PULSE-[0-9.]+-[0-9]+-min28\.apk$ ]]

releases="repos/$GITHUB_REPOSITORY/releases"
matching=$(gh api --paginate "$releases" --jq ".[] | select(.tag_name == \"$tag\") | [.id, .draft] | @tsv")
if [[ -z "$matching" ]]; then
  gh release create "$tag" --repo "$GITHUB_REPOSITORY" --verify-tag --draft \
    --title "PULSE ${tag#v}" --notes-file RELEASE_NOTES.md
  matching=$(gh api --paginate "$releases" --jq ".[] | select(.tag_name == \"$tag\") | [.id, .draft] | @tsv")
fi
[[ "$matching" =~ ^([0-9]+)[[:blank:]]+true$ ]]
release_id=${BASH_REMATCH[1]}

upload() {
  local filename=$1 content_type=$2 checksum=$3 existing
  existing=$(gh api --paginate "$releases/$release_id/assets" \
    --jq ".[] | select(.name == \"$filename\") | .digest")
  if [[ -z "$existing" ]]; then
    curl --fail --silent --show-error --request POST \
      --header "Authorization: Bearer $GH_TOKEN" \
      --header "Content-Type: $content_type" \
      --data-binary "@$filename" \
      "https://uploads.github.com/$releases/$release_id/assets?name=$filename" \
      | jq -e --arg digest "sha256:$checksum" '.digest == $digest'
  else
    test "$existing" = "sha256:$checksum"
  fi
}
read -r checksum_digest _ < <(sha256sum SHA256SUMS.txt)
upload "$apk" application/vnd.android.package-archive "$expected"
upload SHA256SUMS.txt text/plain "$checksum_digest"
