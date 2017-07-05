#
# Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
#

!include local.make

all: checkCL checkLink

checkCL:
	@ if "$(MSC_VER)" NEQ "1600" if "$(MSC_VER)" NEQ "1700" if "$(MSC_VER)" NEQ "1800" \
	echo *** WARNING *** Unsupported cl.exe version detected: $(MSC_VER) ($(RAW_MSC_VER)), only 1600/1700/1800 (Visual Studio 2010/2012/2013) are supported.

checkLink:
	@ if "$(LD_VER)" NEQ "1000" if "$(LD_VER)" NEQ "1100" if "$(LD_VER)" NEQ "1200" \
	echo *** WARNING *** Unsupported link.exe version detected: $(LD_VER) ($(RAW_LD_VER)), only 1000/1100/1200 (Visual Studio 2010/2012/2013) are supported.
