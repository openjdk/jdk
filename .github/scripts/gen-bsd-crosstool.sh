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

# Writes a set of <triple>-clang wrappers that drive the host clang at a BSD
# sysroot, plus the llvm binutils under the names the build looks for.  A
# wrapper rather than a set of flags because configure records the compiler
# as one word and passes it around that way.
#
# Usage: gen-bsd-crosstool.sh <os> <triple> <sysroot> <bindir>

set -eu

os="$1"
triple="$2"
sysroot="$3"
bindir="$4"
mkdir -p "$bindir"

fixups="$bindir/bsd-clang-fixups.h"
cat > "$fixups" <<'H'
/* The BSD headers ask for these gcc-internal macros; clang defines neither
   the minima nor the sig_atomic_t type, only the maxima. */
#ifndef __WCHAR_MIN__
#define __WCHAR_MIN__ (-__WCHAR_MAX__ - 1)
#endif
#ifndef __WINT_MIN__
#define __WINT_MIN__ 0U
#endif
#ifndef __SIG_ATOMIC_TYPE__
#define __SIG_ATOMIC_TYPE__ int
#endif
#ifndef __SIG_ATOMIC_MIN__
#define __SIG_ATOMIC_MIN__ (-__SIG_ATOMIC_MAX__ - 1)
#endif
H

# NetBSD and DragonFly ship libstdc++, and neither puts its headers where
# clang looks by default.  NetBSD keeps them together under /usr/include/g++;
# DragonFly splits them, with the headers proper under /usr/include/c++/<ver>
# and the target-specific bits/c++config.h off in /usr/libdata/gcc<ver>.
# Getting that wrong is not an error -- clang falls back to the host's headers
# and compiles Ubuntu's declarations against DragonFly's system headers, which
# comes apart much later as exception specifications that do not match.
# FreeBSD and OpenBSD ship libc++, which clang finds by itself.
case "$os" in
  netbsd)
    cxx_extra="-stdlib=libstdc++ -isystem $sysroot/usr/include/g++"
    rt_extra="--rtlib=libgcc"
    link_extra="-lgcc"
    ;;
  dragonfly)
    cxxdir=$(ls -d "$sysroot"/usr/include/c++/*/ | sort -V | tail -1)
    cxxdir=${cxxdir%/}
    gccdir=$(ls -d "$sysroot"/usr/libdata/gcc*/ | sort -V | tail -1)
    gccdir=${gccdir%/}
    cxx_extra="-stdlib=libstdc++ -nostdinc++ -isystem $cxxdir \
        -isystem $cxxdir/backward -isystem $gccdir"
    rt_extra="--rtlib=libgcc"
    link_extra="-lgcc"
    ;;
  openbsd)
    # iconv is a package there rather than part of the C library, and a
    # package unpacks under usr/local, which clang does not search.
    cxx_extra=""
    rt_extra=""
    link_extra=""
    common_extra="-isystem $sysroot/usr/local/include -L$sysroot/usr/local/lib"
    ;;
  *)
    cxx_extra=""
    rt_extra=""
    link_extra=""
    ;;
esac
: "${common_extra:=}"

for tool in clang clang++; do
  case "$tool" in
    clang++) extra="$cxx_extra" ;;
    *)       extra="" ;;
  esac
  cat > "$bindir/$triple-$tool" <<W
#!/bin/sh
# -Wno-unused-command-line-argument: the linker flags below are passed on
# every invocation, including the compile-only ones, and the JDK builds
# with warnings as errors.
exec /usr/bin/$tool --target=$triple --sysroot=$sysroot \\
  -Wno-unused-command-line-argument \\
  $rt_extra -fuse-ld=lld $common_extra $extra \\
  -include $fixups "\$@" $link_extra
W
  chmod +x "$bindir/$triple-$tool"
done

# Wrappers rather than symlinks: the LLVM binutils pick their mode out of
# the name they are invoked by, and the FreeBSD triple has a version in it,
# so llvm-ar called as x86_64-unknown-freebsd15.1-ar reads the ".1-ar" as a
# suffix and refuses -- "error: not ranlib, ar, lib or dlltool".
for tool in ar ranlib strip objcopy nm objdump; do
  printf '#!/bin/sh\nexec /usr/bin/llvm-%s "$@"\n' "$tool" > "$bindir/$triple-$tool"
  chmod +x "$bindir/$triple-$tool"
done

# Prove the wrapper links before configure spends ten minutes finding out.
tmp=$(mktemp -d)
printf 'int main(void){return 0;}\n' > "$tmp/probe.c"
"$bindir/$triple-clang" "$tmp/probe.c" -o "$tmp/probe"
file "$tmp/probe"
rm -rf "$tmp"
