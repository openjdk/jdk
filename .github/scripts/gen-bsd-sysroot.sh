#!/bin/bash
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

# Fetches a sysroot for one of the BSDs from that operating system's own
# distribution sets.  There is no debootstrap here: each BSD publishes its
# base system as a handful of tarballs, and the pieces the JDK needs are the
# base system, the compiler support files and, where the X11 headers are
# packaged apart from the rest, those too.
#
# Usage: gen-bsd-sysroot.sh <netbsd|freebsd|openbsd|dragonfly> <directory>

set -eu

os="$1"
sysroot="$2"
mkdir -p "$sysroot"

# --max-time so that a mirror that accepts the connection and then stops
# sending fails the job rather than sitting there until the six hour
# runner limit.
fetch() {
  echo "fetching $2"
  curl -fsSL --retry 3 --retry-delay 5 --retry-all-errors \
      --max-time 1800 --speed-limit 10240 --speed-time 120 -o "$1" "$2"
  ls -l "$1"
}

extract() {
  echo "extracting $1"
  sudo tar xf "$1" -C "$sysroot" "${@:2}"
}

case "$os" in
  netbsd)
    # comp holds the headers and the static libraries.  The X11 sets are not
    # fetched: the build is headless, see below.
    base=https://cdn.netbsd.org/pub/NetBSD/NetBSD-11.0/amd64/binary/sets
    for set in base comp; do
      fetch "$set.tar.xz" "$base/$set.tar.xz"
      extract "$set.tar.xz"
    done
    ;;

  freebsd)
    # FreeBSD puts the whole base system in one base.txz.
    base=https://download.freebsd.org/releases/amd64/15.1-RELEASE
    fetch base.txz "$base/base.txz"
    extract base.txz
    ;;

  openbsd)
    # OpenBSD numbers its sets after the release: base79.tgz for 7.9, with
    # comp79 carrying the headers.
    base=https://cdn.openbsd.org/pub/OpenBSD/7.9/amd64
    for set in base79 comp79; do
      fetch "$set.tgz" "$base/$set.tgz"
      extract "$set.tgz"
    done
    # Alone among these systems, OpenBSD has no iconv in its C library, and
    # java.instrument and libjdwp include <iconv.h> outright.  It comes from
    # a package, which unpacks under usr/local.
    # A package's paths are relative to /usr/local, so it needs its own
    # destination rather than the sysroot root.
    fetch libiconv.tgz \
        https://cdn.openbsd.org/pub/OpenBSD/7.9/packages/amd64/libiconv-1.19.tgz
    sudo mkdir -p "$sysroot/usr/local"
    echo "extracting libiconv.tgz into usr/local"
    sudo tar xf libiconv.tgz -C "$sysroot/usr/local"
    ;;

  dragonfly)
    # DragonFly publishes no distribution sets: the release is an ISO or a
    # disk image and nothing else.  The ISO is cd9660 and libarchive reads
    # that directly, so the headers and libraries come straight out of it
    # without a loop mount.
    base=https://mirror-master.dragonflybsd.org/iso-images
    fetch dfly.iso.bz2 "$base/dfly-x86_64-6.4.2_REL.iso.bz2"
    bunzip2 dfly.iso.bz2
    # No usr/libdata/ldscripts: the ISO does not carry it, and lld does not
    # read linker scripts from the sysroot anyway.
    # usr/libdata carries bits/c++config.h, which sits apart from the rest
    # of the libstdc++ headers on DragonFly.
    bsdtar -xf dfly.iso -C "$sysroot" usr/include usr/lib usr/libdata lib
    rm -f dfly.iso
    ;;

  *)
    echo "gen-bsd-sysroot.sh: unknown target $os" >&2
    exit 1
    ;;
esac

sudo chown -R "$USER" "$sysroot"


# No BSD carries X11 in its base system either -- NetBSD and OpenBSD ship it
# as separate sets, the others leave it to ports -- so the build is configured
# headless and none of it is fetched.  What that gives up is the X11 half of
# java.desktop; everything else, hotspot included, still gets compiled.
#
# Neither cups nor fontconfig is part of any BSD base system, and the JDK
# needs their headers alone: both are opened with dlopen at run time.  The
# Linux ones say the same thing.
for h in cups fontconfig; do
  if [ ! -d "$sysroot/usr/include/$h" ] && [ -d "/usr/include/$h" ]; then
    cp -R "/usr/include/$h" "$sysroot/usr/include/"
  fi
done

# Drop what the build never reads, so that the cache entry stays small.
rm -rf "$sysroot"/{dev,proc,var,tmp,root,home} 2>/dev/null || true
rm -rf "$sysroot"/usr/{sbin,share,libexec} 2>/dev/null || true
rm -rf "$sysroot"/{sbin,bin,rescue} 2>/dev/null || true

# Every one of these systems keeps its linker symlinks absolute --
# /usr/lib/libc.so points at /lib/libc.so.N -- and a symlink is resolved by
# the kernel, which knows nothing about --sysroot.  Left alone they reach out
# of the sysroot and into the host, and the link either fails or, worse,
# succeeds against the wrong libc.  Make them relative.
#
# After the pruning above, not before: a distribution set carries directories
# the build never looks at and whose modes stop even the owner walking them
# (var/spool/ftp/hidden, dev), and those are gone by now.
sudo chmod -R u+rwX "$sysroot"
find "$sysroot" -type l | while read -r link; do
  target=$(readlink "$link")
  case "$target" in
    /*) ;;
    *) continue ;;
  esac
  up=$(dirname "${link#$sysroot/}" | awk -F/ '{ for (i = 1; i <= NF; i++) printf "../" }')
  ln -sf "${up}${target#/}" "$link"
done

# OpenBSD ships no unversioned libfoo.so.  Its own linker scans the directory
# and takes the highest libfoo.so.N.M it finds; lld does not, so -lc++abi
# falls through to libc++abi.a and the link of a shared object dies on
#
#   ld.lld: error: relocation R_X86_64_PC32 cannot be used against symbol
#     '__cxa_new_handler'; recompile with -fPIC
#
# because the archive is not built PIC.  Make the symlinks lld expects.
for dir in "$sysroot"/usr/lib "$sysroot"/lib; do
  [ -d "$dir" ] || continue
  for so in "$dir"/lib*.so.*; do
    [ -e "$so" ] || continue
    stem=${so%%.so.*}
    [ -e "$stem.so" ] && continue
    newest=$(ls -1 "$stem".so.* 2>/dev/null | sort -V | tail -1)
    ln -sf "$(basename "$newest")" "$stem.so"
  done
done

ls -d "$sysroot/usr/include" "$sysroot/usr/lib"
