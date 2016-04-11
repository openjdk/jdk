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

javac -d . ../../../../jdk/make/src/classes/build/tools/spp/Spp.java

SPP=build.tools.spp.Spp

# Generates unsafe access tests for objects and all primitive types
# $1 = package name to Unsafe, sun.misc | jdk.internal.misc
# $2 = test class qualifier name, SunMisc | JdkInternalMisc
# $3 = module name containing the Unsafe class, for @modules
function generate {
    package=$1
    Qualifier=$2
    module=$3

    for type in boolean byte short char int long float double Object
    do
      Type="$(tr '[:lower:]' '[:upper:]' <<< ${type:0:1})${type:1}"
      args="-K$type -Dtype=$type -DType=$Type"

      case $type in
        Object|int|long)
          args="$args -KCAS -KOrdered"
          ;;
      esac

      case $type in
        int|long)
          args="$args -KAtomicAdd"
          ;;
      esac

      case $type in
        short|char|int|long)
          args="$args -KUnaligned"
          ;;
      esac

      case $type in
        boolean)
          value1=true
          value2=false
          value3=false
          ;;
        byte)
          value1=(byte)1
          value2=(byte)2
          value3=(byte)3
          ;;
        short)
          value1=(short)1
          value2=(short)2
          value3=(short)3
          ;;
        char)
          value1=\'a\'
          value2=\'b\'
          value3=\'c\'
          ;;
        int)
          value1=1
          value2=2
          value3=3
          ;;
        long)
          value1=1L
          value2=2L
          value3=3L
          ;;
        float)
          value1=1.0f
          value2=2.0f
          value3=3.0f
          ;;
        double)
          value1=1.0d
          value2=2.0d
          value3=3.0d
          ;;
        Object)
          value1=\"foo\"
          value2=\"bar\"
          value3=\"baz\"
          ;;
      esac

      args="$args -Dvalue1=$value1 -Dvalue2=$value2 -Dvalue3=$value3"

      echo $args
      java $SPP -nel -K$Qualifier -Dpackage=$package -DQualifier=$Qualifier -Dmodule=$module \
          $args < X-UnsafeAccessTest.java.template > ${Qualifier}UnsafeAccessTest${Type}.java
    done
}

generate sun.misc SunMisc jdk.unsupported
generate jdk.internal.misc JdkInternalMisc java.base

rm -fr build
