#!/bin/bash

javac -d . ../../../../../../make/jdk/src/classes/build/tools/spp/Spp.java

in=DualPivotQuicksort.java.template
out=DualPivotQuicksort.java

rm -rf $out

gen() {
    java build.tools.spp.Spp -nel -Dtype=$1 -DTYPE=$2 -K$3 -K$4 -K$5 -K$6 -K$7 -i$in -o$out
}

gen ""     ""     Common1  ""            ""                 ""          ""
gen int    INT    AllTypes AllExceptByte IntLongFloatDouble IntLong     ""
gen long   LONG   AllTypes AllExceptByte IntLongFloatDouble IntLong     ""
gen byte   ""     AllTypes ""            ""                 ""          Byte
gen char   ""     AllTypes AllExceptByte ""                 CharShort   Char
gen short  ""     AllTypes AllExceptByte ""                 CharShort   Short
gen float  FLOAT  AllTypes AllExceptByte IntLongFloatDouble FloatDouble Float
gen double DOUBLE AllTypes AllExceptByte IntLongFloatDouble FloatDouble Double
gen ""     ""     Common2  ""            ""                 ""          ""

rm -rf build
