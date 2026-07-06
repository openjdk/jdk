#!/bin/bash

javac -d . ../../../../../../make/jdk/src/classes/build/tools/spp/Spp.java

# NoRadixSort | WithRadixSort
use_radix=NoRadixSort

in=DualPivotQuicksort.java.template
out=DualPivotQuicksort.java

rm -rf $out

gen() {
    java build.tools.spp.Spp -nel -Dtype=$1 -DTYPE=$2 -K$3 -K$4 -K$5 -K$6 -K$7 -K$use_radix -i$in -o$out
}

gen ""     ""     Common1  ""                 ""          ""     ""
gen int    INT    AllTypes IntLongFloatDouble IntLong     Int    IntFloat
gen long   LONG   AllTypes IntLongFloatDouble IntLong     Long   LongDouble
gen byte   ""     AllTypes ByteCharShort      ""          Byte   ""
gen char   ""     AllTypes ByteCharShort      CharShort   Char   ""
gen short  ""     AllTypes ByteCharShort      CharShort   Short  ""
gen float  FLOAT  AllTypes IntLongFloatDouble FloatDouble Float  IntFloat
gen double DOUBLE AllTypes IntLongFloatDouble FloatDouble Double LongDouble
gen ""     ""     Common2  ""                 ""          ""     ""

rm -rf build
