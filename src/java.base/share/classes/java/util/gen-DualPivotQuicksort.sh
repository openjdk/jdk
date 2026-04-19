#!/bin/bash

javac -d . ../../../../../../make/jdk/src/classes/build/tools/spp/Spp.java

in=DualPivotQuicksort.java.template
out=DualPivotQuicksort.java

rm -rf $out

gen() {
    java build.tools.spp.Spp -nel -Dtype=$1 -DTYPE=$2 -K$3 -K$4 -K$5 -K$6 -i$in -o$out
}

gen ""     ""     Common1  ""                 ""          ""
gen int    INT    AllTypes IntLongFloatDouble IntLong     ""
gen long   LONG   AllTypes IntLongFloatDouble IntLong     ""
gen byte   ""     AllTypes ByteCharShort      ""          Byte
gen char   ""     AllTypes ByteCharShort      CharShort   Char
gen short  ""     AllTypes ByteCharShort      CharShort   Short
gen float  FLOAT  AllTypes IntLongFloatDouble FloatDouble Float
gen double DOUBLE AllTypes IntLongFloatDouble FloatDouble Double
gen ""     ""     Common2  ""                 ""          ""

rm -rf build
