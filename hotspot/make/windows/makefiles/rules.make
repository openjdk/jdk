#
# Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

# These are the commands used externally to compile and run.
# The \ are used here for traditional Windows apps and " quoted to get
# past the Unix-like shell:
!ifdef BootStrapDir
RUN_JAVA="$(BootStrapDir)\bin\java"
RUN_JAVAP="$(BootStrapDir)\bin\javap"
RUN_JAVAH="$(BootStrapDir)\bin\javah"
RUN_JAR="$(BootStrapDir)\bin\jar"
COMPILE_JAVAC="$(BootStrapDir)\bin\javac" $(BOOTSTRAP_JAVAC_FLAGS)
COMPILE_RMIC="$(BootStrapDir)\bin\rmic"
BOOT_JAVA_HOME=$(BootStrapDir)
!else
RUN_JAVA=java
RUN_JAVAP=javap
RUN_JAVAH=javah
RUN_JAR=jar
COMPILE_JAVAC=javac $(BOOTSTRAP_JAVAC_FLAGS)
COMPILE_RMIC=rmic
BOOT_JAVA_HOME=
!endif

# Settings for javac
BOOT_SOURCE_LANGUAGE_VERSION=6
BOOT_TARGET_CLASS_VERSION=6
JAVAC_FLAGS=-g -encoding ascii
BOOTSTRAP_JAVAC_FLAGS=$(JAVAC_FLAGS) -source $(BOOT_SOURCE_LANGUAGE_VERSION) -target $(BOOT_TARGET_CLASS_VERSION)

ProjectFile=jvm.vcproj

!if "$(MSC_VER)" == "1200"

VcVersion=VC6
ProjectFile=jvm.dsp

!elseif "$(MSC_VER)" == "1400"

VcVersion=VC8

!elseif "$(MSC_VER)" == "1500"

VcVersion=VC9

!elseif "$(MSC_VER)" == "1600"

VcVersion=VC10
ProjectFile=jvm.vcxproj

!elseif "$(MSC_VER)" == "1700"
# This is VS2012, but it loads VS10 projects just fine (and will
# upgrade them automatically to VS2012 format).

VcVersion=VC10
ProjectFile=jvm.vcxproj

!else

VcVersion=VC7

!endif
