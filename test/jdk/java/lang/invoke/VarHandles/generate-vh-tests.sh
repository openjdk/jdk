#!/bin/bash

#
# Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

# Toggle Bash strict mode
set -euo pipefail

# Switch to the directory this script is located in
cd "$(dirname "$0")"

SPP="../../../../../../make/jdk/src/classes/build/tools/spp/Spp.java"

# Generates variable handle tests for objects and all primitive types
# This is likely to be a temporary testing approach as it may be more
# desirable to generate code using ASM which will allow more flexibility
# in the kinds of tests that are generated.

for type in boolean byte short char int long float double String Value NullRestrictedValue
do
  Type="$(tr '[:lower:]' '[:upper:]' <<< ${type:0:1})${type:1}"
  args="-K$type -Dtype=$type -DType=$Type"

  args="$args -KCAS"

  case $type in
    byte|short|char|int|long|float|double)
      args="$args -KAtomicAdd"
      ;;
  esac

  case $type in
    boolean|byte|short|char|int|long)
      args="$args -KBitwise"
      ;;
  esac

 # Object = objects of identity or value class
 # Value = value class 
  case $type in
    String)
      args="$args -KObject"
      ;;
    Value)
      args="$args -KObject -KValue"
      ;;
    NullRestrictedValue)
      args="$args -KObject -KValue -KNullRestricted"
      ;;
  esac

  wrong_primitive_type=boolean

  case $type in
    boolean)
      value1=true
      value2=false
      value3=false
      wrong_primitive_type=int
      ;;
    byte)
      value1=(byte)0x01
      value2=(byte)0x23
      value3=(byte)0x45
      ;;
    short)
      value1=(short)0x0123
      value2=(short)0x4567
      value3=(short)0x89AB
      ;;
    char)
      value1=\'\\\\u0123\'
      value2=\'\\\\u4567\'
      value3=\'\\\\u89AB\'
      ;;
    int)
      value1=0x01234567
      value2=0x89ABCDEF
      value3=0xCAFEBABE
      ;;
    long)
      value1=0x0123456789ABCDEFL
      value2=0xCAFEBABECAFEBABEL
      value3=0xDEADBEEFDEADBEEFL
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
    String)
      value1=\"foo\"
      value2=\"bar\"
      value3=\"baz\"
      ;;
    Value)
      value1="Value.getInstance(10)"
      value2="Value.getInstance(20)"
      value3="Value.getInstance(30)"
      ;;
    NullRestrictedValue)
      value1="NullRestrictedValue.of((byte)20,(short)1854)"
      value2="NullRestrictedValue.of((byte)-42,(short)1854)"
      value3="NullRestrictedValue.of((byte)20,(short)-31083)"
  esac

  args="$args -Dvalue1=$value1 -Dvalue2=$value2 -Dvalue3=$value3 -Dwrong_primitive_type=$wrong_primitive_type"

  echo $args
  out=VarHandleTestAccess${Type}.java
  rm -f $out
  java $SPP -nel $args -iX-VarHandleTestAccess.java.template -o$out
  out=VarHandleTestMethodHandleAccess${Type}.java
  rm -f $out
  java $SPP -nel $args -iX-VarHandleTestMethodHandleAccess.java.template -o$out
  out=VarHandleTestMethodType${Type}.java
  rm -f $out
  java $SPP -nel $args -iX-VarHandleTestMethodType.java.template -o$out
done

for type in short char int long float double
do
  Type="$(tr '[:lower:]' '[:upper:]' <<< ${type:0:1})${type:1}"
  args="-K$type -Dtype=$type -DType=$Type"

  BoxType=$Type
  case $type in
    char)
      BoxType=Character
      ;;
    int)
      BoxType=Integer
      ;;
  esac
  args="$args -DBoxType=$BoxType"

  case $type in
    int|long|float|double)
      args="$args -KCAS"
      ;;
  esac

  case $type in
    int|long)
      args="$args -KAtomicAdd"
      ;;
  esac

  case $type in
    int|long)
      args="$args -KBitwise"
      ;;
  esac

  # The value of `value3` is chosen such that when added to `value1` or `value2`
  # it will result in carrying of bits over to the next byte, thereby detecting
  # possible errors in endianness conversion e.g. if say for atomic addition the
  # augend is incorrectly processed
  case $type in
    short)
      value1=(short)0x0102
      value2=(short)0x1112
      value3=(short)0xFFFE
      ;;
    char)
      value1=(char)0x0102
      value2=(char)0x1112
      value3=(char)0xFFFE
      ;;
    int)
      value1=0x01020304
      value2=0x11121314
      value3=0xFFFEFDFC
      ;;
    long)
      value1=0x0102030405060708L
      value2=0x1112131415161718L
      value3=0xFFFEFDFCFBFAF9F8L
      ;;
    float)
      value1=0x01020304
      value2=0x11121314
      value3=0xFFFEFDFC
      ;;
    double)
      value1=0x0102030405060708L
      value2=0x1112131415161718L
      value3=0xFFFEFDFCFBFAF9F8L
      ;;
  esac

  args="$args -Dvalue1=$value1 -Dvalue2=$value2 -Dvalue3=$value3"

  echo $args
  out=VarHandleTestByteArrayAs${Type}.java
  rm -f $out
  java $SPP -nel $args -iX-VarHandleTestByteArrayView.java.template -o$out
done
