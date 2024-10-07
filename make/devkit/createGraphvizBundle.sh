#!/bin/bash
#
# Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#
# Create a bundle in the current directory, containing what's needed to run
# the 'dot' program from the graphviz suite by the OpenJDK build.

set -eux

mydir="$(cd -- $(dirname ${BASH_SOURCE[0]}) && pwd)"
me="${mydir}/$(basename ${BASH_SOURCE[0]})"

EXPAT_VERSION="2.6.0"
EXPAT_URL="https://github.com/libexpat/libexpat/releases/download/R_${EXPAT_VERSION//./_}/expat-${EXPAT_VERSION}.tar.gz"
EXPAT_SHA256="a13447b9aa67d7c860783fdf6820f33ebdea996900d6d8bbc50a628f55f099f7"

GRAPHVIZ_VERSION="9.0.0"
GRAPHVIZ_URL="https://gitlab.com/api/v4/projects/4207231/packages/generic/graphviz-releases/${GRAPHVIZ_VERSION}/graphviz-${GRAPHVIZ_VERSION}.tar.xz"
GRAPHVIZ_SHA256="6c9afda06a732af7658c2619ee713d2545818c3ff19b7b8fd48effcd06d57bf6"

uname_s="$(uname -s)"
case ${uname_s} in
    Linux)
        bundle_os="linux"
        shacmd="sha256sum --strict --check -"
        lib_path_var="LD_LIBRARY_PATH"
        ;;
    Darwin)
        bundle_os="macosx"
        shacmd="shasum -a 256 --strict --check -"
        lib_path_var="DYLD_LIBRARY_PATH"
        ;;
    *)
        echo "Unknown OS: ${uname_s}"
        exit 1
        ;;
esac
uname_m="$(uname -m)"
case ${uname_m} in
    aarch64|arm64)
        bundle_cpu="aarch64"
        ;;
    x86_64)
        bundle_cpu="x64"
        ;;
esac
bundle_platform="${bundle_os}_${bundle_cpu}"

build_dir="${mydir}/../../build/graphviz"
download_dir="${build_dir}/download"
install_dir="${build_dir}/result/graphviz-${bundle_platform}-${GRAPHVIZ_VERSION}"
bundle_file="${install_dir}.tar.gz"

expat_dir="${build_dir}/expat"
expat_src_dir="${expat_dir}/src"

graphviz_dir="${build_dir}/graphviz"
graphviz_src_dir="${graphviz_dir}/src"
graphviz_doc_dir="${install_dir}/doc"

mkdir -p "${build_dir}"
cd "${build_dir}"

download_and_unpack() {
    local url="$1"
    local sha256="$2"
    local file="$3"
    local dir="$4"

    mkdir -p "$(dirname "${file}")"
    if [ ! -f "${file}" ]; then
        curl -L -o "${file}" "${url}"
    fi
    echo "${sha256}  ${file}" | ${shacmd}
    if [ ! -d "${dir}" ]; then
        mkdir -p "${dir}"
        tar --extract --file "${file}" --directory "${dir}" --strip-components 1
    fi
}

download_and_unpack "${EXPAT_URL}" "${EXPAT_SHA256}" "${download_dir}/expat.tar.gz" "${expat_src_dir}"
download_and_unpack "${GRAPHVIZ_URL}" "${GRAPHVIZ_SHA256}" "${download_dir}/graphviz.tar.gz" "${graphviz_src_dir}"

(
    cd "${expat_src_dir}"
    ./configure --prefix="${install_dir}"
    make -j install
)

(
    cd "${graphviz_src_dir}"
    ./configure --prefix="${install_dir}" EXPAT_CFLAGS="-I${install_dir}/include" EXPAT_LIBS="-L${install_dir}/lib -lexpat"
    make -j install
)

cat > "${install_dir}/dot" << EOF
#!/bin/bash
# Get an absolute path to this script
this_script_dir="\$(dirname \$0)"
this_script_dir="\$(cd \${this_script_dir} > /dev/null && pwd)"
export ${lib_path_var}="\${this_script_dir}/lib:\${this_script_dir}/lib/graphviz"
exec "\${this_script_dir}/bin/dot" "\$@"
EOF
chmod +x "${install_dir}/dot"
# create config file
"${install_dir}/dot" -c

cp "${me}" "${install_dir}"

tar --create --gzip --file "${bundle_file}" -C "${install_dir}" .
