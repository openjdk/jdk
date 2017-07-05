#!/bin/bash

javac -d . ../../../../jdk/make/src/classes/build/tools/spp/Spp.java

SPP=build.tools.spp.Spp

# Generates unsafe access tests for objects and all primitive types
# $1 = package name to Unsafe, sun.misc | jdk.internal.misc
# $2 = test class qualifier name, SunMisc | JdkInternalMisc
function generate {
    package=$1
    Qualifier=$2

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

      java $SPP -nel -K$Qualifier -Dpackage=$package -DQualifier=$Qualifier \
          $args < X-UnsafeAccessTest.java.template > ${Qualifier}UnsafeAccessTest${Type}.java
    done
}

generate sun.misc SunMisc
generate jdk.internal.misc JdkInternalMisc

rm -fr build