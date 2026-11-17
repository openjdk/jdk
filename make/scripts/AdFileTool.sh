#!/usr/bin/env bash
#
# Copyright (c) 2026 IBM Corporation. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

set -euo pipefail

die() {
  printf '%s: ERROR: %s\n' "$(basename "$0")" "$*" >&2
  exit 2
}

usage() {
  cat >&2 <<EOF
Usage:
  $(basename "$0") verify <ad-file>...
  $(basename "$0") regenerate <ad-file>...

Modes:
  verify
      Verify that generated content matches the checked-in AD files.

  regenerate
      Regenerate the specified AD files.
EOF
  exit 2
}

#
# This tool relies on the explicit generation markers.
#
# M4 source files referenced by generation markers must use the naming
# convention:
#
#   *_ad.m4
#
# Generated content must be delimited by markers of one of the forms:
#
#   BEGIN File generated automatically from <file>_ad.m4
#   END File generated automatically from <file>_ad.m4
#
# or
#
#   BEGIN Section generated automatically from <file>_ad.m4
#   END Section generated automatically from <file>_ad.m4
#
# The corresponding *_ad.m4 file is expected to reside in the same
# directory as the AD file being processed.
#
# AD files without generation markers are not managed by this tool.
#
# In verify mode, generated output must match the checked-in AD file.
# In regenerate mode, generated output is written back to the AD file.
#

extract_m4_source_file() {
  sed -n 's/.*generated automatically from \(.*_ad\.m4\).*/\1/p' <<< "$1"
}

MODE="${1:-}"
[[ -n "$MODE" ]] || usage
shift

case "$MODE" in
  verify|regenerate)
    ;;
  *)
    usage
    ;;
esac

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

[[ $# -gt 0 ]] || die "$MODE mode requires one or more AD files"

adfiles=("$@")

for adfile in "${adfiles[@]}"; do
  [[ -f "$adfile" ]] || die "AD file not found: $adfile"
  [[ "$adfile" == *.ad ]] || die "Expected an AD file, got: $adfile"

  dir=$(dirname "$adfile")

  file_markers=$(grep -c \
    "BEGIN File generated automatically from" \
    "$adfile" || true)

  section_markers=$(grep -c \
    "BEGIN Section generated automatically from" \
    "$adfile" || true)

  if [[ "$file_markers" -eq 0 && "$section_markers" -eq 0 ]]; then
    continue
  fi

  [[ "$file_markers" -le 1 ]] || die "Multiple file-generation markers found in '$adfile'"

  while IFS= read -r marker; do
    [[ -n "$marker" ]] || continue

    m4base=$(extract_m4_source_file "$marker")

    [[ -n "$m4base" ]] || die "Malformed generation marker in '$adfile': $marker"

    m4file="$dir/$m4base"

    [[ -f "$m4file" ]] || die "Missing M4 source file '$m4file' for '$adfile'"

    generated="$tmpdir/$(basename "$adfile").generated"

    m4 "$m4file" > "$generated"

    if [[ "$MODE" == "verify" ]]; then
      if ! cmp -s "$generated" "$adfile"; then
        die "Generated output differs from '$adfile'"
      fi
    else
      if ! cmp -s "$generated" "$adfile"; then
        mv "$generated" "$adfile"
      fi
    fi

  done < <(
    grep "BEGIN File generated automatically from" "$adfile" || true
  )

  while IFS= read -r marker; do
    [[ -n "$marker" ]] || continue

    m4base=$(extract_m4_source_file "$marker")

    [[ -n "$m4base" ]] || die "Malformed generation marker in '$adfile': $marker"

    begin="BEGIN Section generated automatically from $m4base"
    end="END Section generated automatically from $m4base"

    m4file="$dir/$m4base"

    [[ -f "$m4file" ]] || die "Missing M4 source file '$m4file' for '$adfile'"

    section="$tmpdir/${m4base}.section"
    generated="$tmpdir/${m4base}.generated"

    m4 "$m4file" |
    awk \
      -v b="$begin" \
      -v e="$end" '
        index($0, b) { in_section = 1 }
        in_section { print }
        index($0, e) { exit }
      ' > "$section"

    [[ -s "$section" ]] || die "Failed to extract section '$begin' from '$m4file'"

    if ! awk \
      -v sec="$section" \
      -v b="$begin" \
      -v e="$end" '
      BEGIN {
        while ((getline line < sec) > 0) {
          repl[++n] = line
        }
        close(sec)
      }

      {
        if (!inside) {
          if (index($0, b)) {
            for (i = 1; i <= n; i++) {
              print repl[i]
            }
            inside = 1
            replaced = 1
          } else {
            print
          }
          next
        }

        if (index($0, e)) {
          inside = 0
        }
      }

      END {
        if (!replaced) {
          exit 1
        }
      }
    ' "$adfile" > "$generated"; then
      die "Failed to replace section '$begin' in '$adfile'"
    fi

    if [[ "$MODE" == "verify" ]]; then
      if ! cmp -s "$generated" "$adfile"; then
        die "Generated section '$m4base' differs from '$adfile'"
      fi
    else
      if ! cmp -s "$generated" "$adfile"; then
        mv "$generated" "$adfile"
      fi
    fi

  done < <(
    grep "BEGIN Section generated automatically from" "$adfile" || true
  )

done

exit 0