#!/bin/bash
#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

# This script installs the JAB tool into it's own local repository and
# puts a wrapper scripts into <source-root>/.jab

mydir="$(dirname "${BASH_SOURCE[0]}")"
myname="$(basename "${BASH_SOURCE[0]}")"

installed_jab_script=${mydir}/../../.jab/jab
install_data=${mydir}/../../.jab/.data

setup_url() {
    if [ -f "~/.config/jab/jab.conf" ]; then
        source ~/.config/jab/jab.conf
    fi

    jab_repository="jdk-virtual"
    jab_organization="jpg/infra/builddeps"
    jab_module="jab"
    jab_revision="2.0-SNAPSHOT"
    jab_ext="jab.sh.gz"

    closed_script="${mydir}/../../closed/conf/jab-install.conf"
    if [ -f "${closed_script}" ]; then
        source "${closed_script}"
    fi

    if [ -n "${JAB_SERVER}" ]; then
        jab_server="${JAB_SERVER}"
    fi
    if [ -n "${JAB_REPOSITORY}" ]; then
        jab_repository="${JAB_REPOSITORY}"
    fi
    if [ -n "${JAB_ORGANIZATION}" ]; then
        jab_organization="${JAB_ORGANIZATION}"
    fi
    if [ -n "${JAB_MODULE}" ]; then
        jab_module="${JAB_MODULE}"
    fi
    if [ -n "${JAB_REVISION}" ]; then
        jab_revision="${JAB_REVISION}"
    fi
    if [ -n "${JAB_EXTENSION}" ]; then
        jab_extension="${JAB_EXTENSION}"
    fi

    if [ -n "${JAB_URL}" ]; then
        jab_url="${JAB_URL}"
        data_string="${jab_url}"
    else
        data_string="${jab_repository}/${jab_organization}/${jab_module}/${jab_revision}/${jab_module}-${jab_revision}.${jab_ext}"
        jab_url="${jab_server}/${data_string}"
    fi
}

install_jab() {
    if [ -z "${jab_server}" -a -z "${JAB_URL}" ]; then
        echo "No jab server or URL provided, set either"
        echo "JAB_SERVER=<base server address>"
        echo "or"
        echo "JAB_URL=<full path to install script>"
        exit 1
    fi

    if command -v curl > /dev/null; then
        getcmd="curl -s"
    elif command -v wget > /dev/null; then
        getcmd="wget --quiet -O -"
    else
        echo "Could not find either curl or wget"
        exit 1
    fi

    if ! command -v gunzip > /dev/null; then
        echo "Could not find gunzip"
        exit 1
    fi

    echo "Downloading JAB bootstrap script"
    mkdir -p "${installed_jab_script%/*}"
    rm -f "${installed_jab_script}.gz"
    ${getcmd} ${jab_url} > "${installed_jab_script}.gz"
    if [ ! -s "${installed_jab_script}.gz" ]; then
        echo "Failed to download ${jab_url}"
        exit 1
    fi
    echo "Extracting JAB bootstrap script"
    rm -f "${installed_jab_script}"
    gunzip "${installed_jab_script}.gz"
    chmod +x "${installed_jab_script}"
    echo "${data_string}" > "${install_data}"
}

# Main body starts here

setup_url

if [ ! -x "${installed_jab_script}" ]; then
    install_jab
elif [ ! -e "${install_data}" ] || [ "${data_string}" != "$(cat "${install_data}")" ]; then
    echo "Install url changed since last time, reinstalling"
    install_jab
fi

${installed_jab_script} "$@"
