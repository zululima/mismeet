#!/usr/bin/env bash
# Store builds come from an annotated tag <platform>-<version>-<build>, such as android-0.1.0-1,
# made on a clean tree, so every published binary names the exact source it was built from.
#
#   scripts/release.sh version <android|ios>   print the tag the current version maps to
#   scripts/release.sh tag <android|ios>       tag HEAD with it and push the tag to origin
#   scripts/release.sh check <android|ios>     fail unless HEAD is that tag and the tree is clean
#   scripts/release.sh archive-ios             check, then archive the iOS app
#   scripts/release.sh upload-ios              check, archive and upload to App Store Connect, which
#                                              offers the build in TestFlight and for App Store review
#   scripts/release.sh upload-android          check, build the bundle and release it to the closed
#                                              testing track through the Play Developer API, then
#                                              publish the APK as apk-android does
#   scripts/release.sh upload-android production
#                                              check, build the bundle and put it in a production
#                                              draft, rolled out in Play Console, then publish the APK
#   scripts/release.sh apk-android             publish Play's universal APK of the current Android
#                                              tag as the GitHub release the README links
set -euo pipefail

root="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"

# The App Store Connect API key that the git-ignored ios/.asc-key names, the .p8 file on its first
# line and the issuer id on its second. Without it, xcodebuild uses the Apple Account in Xcode,
# which signs out now and then.
asc_key=()
if [ -f "$root/ios/.asc-key" ]; then
  p8="$(sed -n 1p "$root/ios/.asc-key")"
  asc_key=(-authenticationKeyPath "$p8" -authenticationKeyID "$(basename "$p8" .p8 | sed 's/^AuthKey_//')"
    -authenticationKeyIssuerID "$(sed -n 2p "$root/ios/.asc-key")")
fi

version() {
  case "$1" in
    android)
      local gradle="$root/android/app/build.gradle.kts"
      local name code
      name="$(sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' "$gradle")"
      code="$(sed -nE 's/^ *versionCode = ([0-9]+).*/\1/p' "$gradle")"
      echo "android-$name-$code"
      ;;
    ios)
      local spec="$root/ios/project.yml"
      local name build
      name="$(sed -nE 's/^ *MARKETING_VERSION: "?([^"]+)"?.*/\1/p' "$spec")"
      build="$(sed -nE 's/^ *CURRENT_PROJECT_VERSION: "?([^"]+)"?.*/\1/p' "$spec")"
      echo "ios-$name-$build"
      ;;
    *)
      echo "platform must be android or ios" >&2
      exit 2
      ;;
  esac
}

check() {
  local tag
  tag="$(version "$1")"
  if [ -n "$(git -C "$root" status --porcelain)" ]; then
    echo "The tree has uncommitted changes; a store build must come from a clean tag." >&2
    exit 1
  fi
  if ! git -C "$root" rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "No tag $tag. Run: scripts/release.sh tag $1" >&2
    exit 1
  fi
  if [ "$(git -C "$root" rev-parse "$tag^{commit}")" != "$(git -C "$root" rev-parse HEAD)" ]; then
    echo "HEAD is not $tag. Run: git switch --detach $tag" >&2
    exit 1
  fi
  echo "$tag"
}

tag() {
  local tag
  tag="$(version "$1")"
  if [ -n "$(git -C "$root" status --porcelain)" ]; then
    echo "Commit or stash the changes first; the tag must name a clean tree." >&2
    exit 1
  fi
  if git -C "$root" rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "$tag exists already. Raise the version or build number first." >&2
    exit 1
  fi
  git -C "$root" fetch -q origin
  if ! git -C "$root" merge-base --is-ancestor HEAD "origin/$(git -C "$root" rev-parse --abbrev-ref origin/HEAD | cut -d/ -f2)"; then
    echo "HEAD is not on origin's default branch yet. Push it first." >&2
    exit 1
  fi
  git -C "$root" tag -a "$tag" -m "Store build $tag"
  git -C "$root" push -q origin "refs/tags/$tag"
  echo "$tag"
}

archive_ios() {
  local tag
  tag="$(check ios)"
  xcodegen generate --spec "$root/ios/project.yml" >/dev/null
  xcodebuild -project "$root/ios/Mismeet.xcodeproj" -scheme Mismeet -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$root/ios/build/$tag.xcarchive" \
    -allowProvisioningUpdates ${asc_key[@]+"${asc_key[@]}"} DEVELOPMENT_TEAM="$(cat "$root/ios/.team-id")" \
    archive | xcbeautify >&2
  echo "$root/ios/build/$tag.xcarchive"
}

upload_ios() {
  local archive options
  archive="$(archive_ios)"
  options="$root/ios/build/ExportOptions.plist"
  # manageAppVersionAndBuildNumber stays off so the uploaded build keeps the tagged numbers.
  cat > "$options" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key>
  <string>app-store-connect</string>
  <key>destination</key>
  <string>upload</string>
  <key>teamID</key>
  <string>$(cat "$root/ios/.team-id")</string>
  <key>signingStyle</key>
  <string>automatic</string>
  <key>manageAppVersionAndBuildNumber</key>
  <false/>
  <key>uploadSymbols</key>
  <true/>
</dict>
</plist>
PLIST
  xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist "$options" \
    -exportPath "$root/ios/build/export" -allowProvisioningUpdates ${asc_key[@]+"${asc_key[@]}"} | xcbeautify
  echo "Uploaded $(basename "$archive" .xcarchive). It shows in App Store Connect > TestFlight once processed."
}

b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

# An OAuth access token for the service account whose JSON key android/.play-key names.
play_token() {
  local key="$1" now header claims sig
  now="$(date +%s)"
  header="$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)"
  claims="$(jq -cn --arg iss "$(jq -r .client_email "$key")" --argjson iat "$now" \
    '{iss: $iss, scope: "https://www.googleapis.com/auth/androidpublisher",
      aud: "https://oauth2.googleapis.com/token", iat: $iat, exp: ($iat + 3600)}' | b64url)"
  sig="$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign <(jq -r .private_key "$key") | b64url)"
  curl -fsS https://oauth2.googleapis.com/token \
    -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer -d assertion="$header.$claims.$sig" \
    | jq -r .access_token
}

upload_android() {
  local tag key token api upload edit code track status
  case "${1:-}" in
    "") track=alpha status=completed ;;
    production) track=production status=draft ;;
    *) echo "upload-android takes no argument or production" >&2; exit 2 ;;
  esac
  tag="$(check android)"
  key="$(cat "$root/android/.play-key")"
  "$root/android/gradlew" -q -p "$root/android" bundleRelease >&2
  token="$(play_token "$key")"
  api="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/app.mismeet.android"
  upload="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/app.mismeet.android"
  edit="$(curl -fsS -X POST -H "Authorization: Bearer $token" "$api/edits" | jq -r .id)"
  code="$(curl -fsS -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/octet-stream' \
    --data-binary @"$root/android/app/build/outputs/bundle/release/app-release.aab" \
    "$upload/edits/$edit/bundles?uploadType=media" | jq -r .versionCode)"
  # Closed testing's own name is alpha, whatever Play Console calls it.
  curl -fsS -X PUT -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "$(jq -cn --arg name "$tag" --arg code "$code" --arg status "$status" '{releases: [{name: $name, versionCodes: [$code], status: $status}]}')" \
    "$api/edits/$edit/tracks/$track" >/dev/null
  curl -fsS -X POST -H "Authorization: Bearer $token" "$api/edits/$edit:commit" >/dev/null
  if [ "$track" = alpha ]; then
    echo "Uploaded $tag to closed testing. Play sends it for review; Publishing overview shows the state."
  else
    echo "Uploaded $tag as a production draft. Roll it out under Production in Play Console."
  fi
  publish_apk "$tag" "$token"
}

# Play's universal APK is signed with the app signing key, as every install from Play is, so a
# phone that took the APK can later update from Play with its key and contacts kept.
publish_apk() {
  local tag="$1" token="$2" code api id dir tries=0
  code="${tag##*-}"
  api="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/app.mismeet.android/generatedApks/$code"
  until id="$(curl -fsS -H "Authorization: Bearer $token" "$api" \
    | jq -er '.generatedApks[0].generatedUniversalApk.downloadId // empty | @uri')"; do
    tries=$((tries + 1))
    if [ "$tries" -ge 12 ]; then
      echo "Play has no universal APK of $tag yet. Run later: scripts/release.sh apk-android" >&2
      exit 1
    fi
    sleep 10
  done
  dir="$(mktemp -d)"
  curl -fsS -L -H "Authorization: Bearer $token" -o "$dir/mismeet.apk" "$api/downloads/$id:download?alt=media"
  gh release create "$tag" "$dir/mismeet.apk" --repo zululima/mismeet --verify-tag --latest \
    --title "Android ${tag#android-}" \
    --notes "Google Play's signed APK of $tag, for Android 10 and later. Changes are in CHANGELOG.md."
  rm -r "$dir"
  echo "Published https://github.com/zululima/mismeet/releases/tag/$tag"
}

apk_android() {
  local tag
  tag="$(version android)"
  if ! git -C "$root" rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "No tag $tag. Upload it first: scripts/release.sh upload-android" >&2
    exit 1
  fi
  publish_apk "$tag" "$(play_token "$(cat "$root/android/.play-key")")"
}

case "${1:-}" in
  version) version "${2:-}" ;;
  tag) tag "${2:-}" ;;
  check) check "${2:-}" ;;
  archive-ios) archive_ios ;;
  upload-ios) upload_ios ;;
  upload-android) upload_android "${2:-}" ;;
  apk-android) apk_android ;;
  *) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
