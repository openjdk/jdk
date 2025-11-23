#!/bin/bash
#
# Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

# For manual invocation.
# You can regenerate the source files,
# and you can clean them up.
# FIXME: Move this script under $REPO/make/gensrc/
list_mech_gen() {
    ( # List MG files physically present
      grep -il 'mechanically generated.*do not edit' *.java
      # List MG files currently deleted (via --clean)
      hg status -nd .
    ) | grep '.Vector\.java$'
}
case $* in
'')             CLASS_FILTER='*';;
--generate*)    CLASS_FILTER=${2-'*'};;
--clean)        MG=$(list_mech_gen); set -x; rm -f $MG; exit;;
--revert)       MG=$(list_mech_gen); set -x; hg revert $MG; exit;;
--list)         list_mech_gen; exit;;
--help|*)       echo "Usage: $0 [--generate [file] | --clean | --revert | --list]"; exit 1;;
esac

javac -d . ../../../../../../../make/jdk/src/classes/build/tools/spp/Spp.java

SPP=build.tools.spp.Spp

typeprefix=

globalArgs=""
#globalArgs="$globalArgs -KextraOverrides"

for type in byte short int long float double float16
do

  Type="$(tr '[:lower:]' '[:upper:]' <<< ${type:0:1})${type:1}"
  TYPE="$(tr '[:lower:]' '[:upper:]' <<< ${type})"

  case $type in
    float16)
       type=short
       TYPE=SHORT
       ;;
  esac

  args=$globalArgs
  args="$args -K$type -Dtype=$type -DType=$Type -DTYPE=$TYPE"

  Boxtype=$Type
  Wideboxtype=$Boxtype
  ElemLayout=$Type

  kind=BITWISE

  bitstype=$type
  maskbitstype=$type
  Bitstype=$Type
  Boxbitstype=$Boxtype

  fptype=$type
  Fptype=$Type
  Boxfptype=$Boxtype
  carriertype=$type
  Carriertype=$Type
  elemtype=$type
  FPtype=$type
  fallbacktype=$type

  case $Type in
    Byte)
      Wideboxtype=Integer
      sizeInBytes=1
      laneType=VECTOR_LANE_TYPE_BYTE
      lanebitsType=VECTOR_LANE_TYPE_BYTE
      args="$args -KbyteOrShort"
      ;;
    Short)
      fptype=Float16
      Fptype=Float16
      Boxfptype=Float16
      Wideboxtype=Integer
      sizeInBytes=2
      laneType=VECTOR_LANE_TYPE_SHORT
      lanebitsType=VECTOR_LANE_TYPE_SHORT
      args="$args -KbyteOrShort"
      ;;
    Int)
      Boxtype=Integer
      Carriertype=Integer
      Wideboxtype=Integer
      Boxbitstype=Integer
      fptype=float
      Fptype=Float
      Boxfptype=Float
      sizeInBytes=4
      laneType=VECTOR_LANE_TYPE_INT
      lanebitsType=VECTOR_LANE_TYPE_INT
      args="$args -KintOrLong -KintOrFP -KintOrFloat"
      ;;
    Long)
      fptype=double
      Fptype=Double
      Boxfptype=Double
      sizeInBytes=8
      laneType=VECTOR_LANE_TYPE_LONG
      lanebitsType=VECTOR_LANE_TYPE_LONG
      args="$args -KintOrLong -KlongOrDouble"
      ;;
    Float)
      kind=FP
      bitstype=int
      maskbitstype=int
      Bitstype=Int
      Boxbitstype=Integer
      sizeInBytes=4
      laneType=VECTOR_LANE_TYPE_FLOAT
      lanebitsType=VECTOR_LANE_TYPE_INT
      args="$args -KintOrFP -KintOrFloat"
      FPtype=FP32
      ;;
    Double)
      kind=FP
      bitstype=long
      maskbitstype=long
      Bitstype=Long
      Boxbitstype=Long
      sizeInBytes=8
      laneType=VECTOR_LANE_TYPE_DOUBLE
      lanebitsType=VECTOR_LANE_TYPE_LONG
      args="$args -KintOrFP -KlongOrDouble"
      FPtype=FP64
      ;;
    Float16)
      kind=FP
      bitstype=short
      maskbitstype=short
      Bitstype=Short
      Boxbitstype=Short
      sizeInBytes=2
      carriertype=short
      Carriertype=Short
      FPtype=FP16
      Boxtype=Float16
      elemtype=Float16
      ElemLayout=Short
      laneType=VECTOR_LANE_TYPE_FLOAT16
      lanebitsType=VECTOR_LANE_TYPE_SHORT
      fallbacktype=float
      args="$args -KbyteOrShort -KshortOrFP -KshortOrFloat16"
      ;;
  esac


  args="$args -K$FPtype -K$kind -DlaneType=$laneType -DlanebitsType=$lanebitsType -Dfallbacktype=$fallbacktype -DBoxtype=$Boxtype -DWideboxtype=$Wideboxtype"
  args="$args -DElemLayout=$ElemLayout -Dbitstype=$bitstype -Dmaskbitstype=$maskbitstype -DBitstype=$Bitstype -DBoxbitstype=$Boxbitstype"
  args="$args -Dfptype=$fptype -DFptype=$Fptype -DBoxfptype=$Boxfptype"
  args="$args -DsizeInBytes=$sizeInBytes"
  args="$args -Dcarriertype=$carriertype -Delemtype=$elemtype -DCarriertype=$Carriertype"

  abstractvectortype=${typeprefix}${Type}Vector
  abstractbitsvectortype=${typeprefix}Vector${Bitstype}
  abstractfpvectortype=${typeprefix}${Fptype}Vector
  args="$args -Dabstractvectortype=$abstractvectortype -Dabstractbitsvectortype=$abstractbitsvectortype -Dabstractfpvectortype=$abstractfpvectortype"
  case $abstractvectortype in
  $CLASS_FILTER)
    echo $abstractvectortype.java : $args
    rm -f $abstractvectortype.java
    java $SPP -nel $args \
       -iX-Vector.java.template \
       -o$abstractvectortype.java
    [ -f $abstractvectortype.java ] || exit 1

    if [ VAR_OS_ENV==windows.cygwin ]; then
      tr -d '\r' < $abstractvectortype.java > temp
      mv temp $abstractvectortype.java
    fi
  esac

  old_args="$args"
  for bits in 64 128 256 512 Max
  do
    vectortype=${typeprefix}${Type}Vector${bits}
    masktype=${typeprefix}${Type}Mask${bits}
    shuffletype=${typeprefix}${Type}Shuffle${bits}
    bitsvectortype=${typeprefix}${Bitstype}Vector${bits}
    fpvectortype=${typeprefix}${Fptype}Vector${bits}
    vectorindexbits=$((bits * 4 / sizeInBytes))

    numLanes=$((bits / (sizeInBytes * 8)))
    if [[ "${numLanes}" == "1" ]]; then
        lanes=1L
    elif [[ "${numLanes}" == "2" ]]; then
        lanes=2L
    elif [[ "${numLanes}" == "4" ]]; then
        lanes=4L
    elif [[ "${numLanes}" == "8" ]]; then
        lanes=8L
    elif [[ "${numLanes}" == "16" ]]; then
        lanes=16L
    elif [[ "${numLanes}" == "32" ]]; then
        lanes=32L
    elif [[ "${numLanes}" == "64" ]]; then
        lanes=64L
    fi;

    if [[ "${bits}" == "Max" ]]; then
        vectorindextype="vix.getClass()"
    else
        vectorindextype="IntVector${vectorindexbits}.class"
    fi;

    BITS=$bits
    case $bits in
      Max)
        BITS=MAX
        ;;
    esac

    shape=S${bits}Bit
    Shape=S_${bits}_BIT
    args="$old_args"
    args="$args -K$lanes -K$bits"
    if [[ "${vectortype}" == "IntVectorMax" ]]; then
      args="$args -KintAndMax"
    fi
    bitargs="$args -Dbits=$bits -DBITS=$BITS -Dvectortype=$vectortype -Dmasktype=$masktype -Dshuffletype=$shuffletype -Dbitsvectortype=$bitsvectortype -Dfpvectortype=$fpvectortype -Dvectorindextype=$vectorindextype -Dshape=$shape -DShape=$Shape"

    case $vectortype in
    $CLASS_FILTER)
      echo $vectortype.java : $bitargs
      rm -f $vectortype.java
      java $SPP -nel $bitargs \
         -iX-VectorBits.java.template \
         -o$vectortype.java
      [ -f $vectortype.java ] || exit 1

      if [ VAR_OS_ENV==windows.cygwin ]; then
        tr -d  '\r' < $vectortype.java > temp
        mv temp $vectortype.java
      fi
    esac
  done

done

rm -fr build

