#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

function prepare_help_system {
    AC_CHECK_PROGS(PKGHANDLER, apt-get yum port pkgutil pkgadd)
}
	
function help_on_build_dependency {
    # Print a helpful message on how to acquire the necessary build dependency.
    # $1 is the help tag: freetyp2, cups, pulse, alsa etc
    MISSING_DEPENDENCY=$1
    PKGHANDLER_COMMAND=

    case $PKGHANDLER in
	apt-get)
                apt_help     $MISSING_DEPENDENCY ;;
    yum)
                yum_help     $MISSING_DEPENDENCY ;;
	port)
                port_help    $MISSING_DEPENDENCY ;;
	pkgutil)
                pkgutil_help $MISSING_DEPENDENCY ;;
	pkgadd)
                pkgadd_help  $MISSING_DEPENDENCY ;;
    * )
      break ;;
    esac

    if test "x$PKGHANDLER_COMMAND" != x; then
        HELP_MSG="You might be able to fix this by running '$PKGHANDLER_COMMAND'."
    fi
}

function apt_help {
    case $1 in
    devkit)
        PKGHANDLER_COMMAND="sudo apt-get install build-essential" ;;
    openjdk)
        PKGHANDLER_COMMAND="sudo apt-get install openjdk-7-jdk" ;;
    alsa)
        PKGHANDLER_COMMAND="sudo apt-get install libasound2-dev" ;;
    cups)
        PKGHANDLER_COMMAND="sudo apt-get install libcups2-dev" ;;
    freetype2)
        PKGHANDLER_COMMAND="sudo apt-get install libfreetype6-dev" ;;
    pulse)
        PKGHANDLER_COMMAND="sudo apt-get install libpulse-dev" ;;
    x11)
        PKGHANDLER_COMMAND="sudo apt-get install libX11-dev libxext-dev libxrender-dev libxtst-dev" ;;
    ccache)
        PKGHANDLER_COMMAND="sudo apt-get install ccache" ;;
    * )
       break ;;
    esac
}

function yum_help {
    case $1 in
    devkit)
        PKGHANDLER_COMMAND="sudo yum groupinstall \"Development Tools\"" ;;
    openjdk)
        PKGHANDLER_COMMAND="sudo yum install java-1.7.0-openjdk" ;;
    alsa)
        PKGHANDLER_COMMAND="sudo yum install alsa-lib-devel" ;;
    cups)
        PKGHANDLER_COMMAND="sudo yum install cups-devel" ;;
    freetype2)
        PKGHANDLER_COMMAND="sudo yum install freetype2-devel" ;;
    pulse)
        PKGHANDLER_COMMAND="sudo yum install pulseaudio-libs-devel" ;;
    x11)
        PKGHANDLER_COMMAND="sudo yum install libXtst-devel" ;;
    ccache)
        PKGHANDLER_COMMAND="sudo yum install ccache" ;;
    * )
       break ;;
    esac
}

function port_help {
    PKGHANDLER_COMMAND=""
}

function pkgutil_help {
    PKGHANDLER_COMMAND=""
}

function pkgadd_help {
    PKGHANDLER_COMMAND=""
}
