#!/bin/bash

javac -d . ../../../../../make/jdk/src/classes/build/tools/spp/Spp.java

in=Sorting.java.template
out=Sorting.java

rm -rf $out

gen() {
    java build.tools.spp.Spp -nel -Dtype=$1 -DType=$2 -K$3 -K$4 -K$5 -K$6 -i$in -o$out
}

gen ""     ""        Common1  ""                 ""                 ""
gen int    Integer   AllTypes IntLongFloatDouble IntLongFloatDouble ""
gen long   Long      AllTypes IntLongFloatDouble IntLongFloatDouble ""
gen byte   Byte      AllTypes ByteCharShort      ""                 ""
gen char   Character AllTypes ByteCharShort      ""                 ""
gen short  Short     AllTypes ByteCharShort      ""                 ""
gen float  Float     AllTypes IntLongFloatDouble FloatDouble        Float
gen double Double    AllTypes IntLongFloatDouble FloatDouble        Double
gen ""     ""        Common2  ""                 ""                 ""

rm -rf build
