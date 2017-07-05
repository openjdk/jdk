#!/bin/bash

javac -d . ../../../../../make/src/classes/build/tools/spp/Spp.java

SPP=build.tools.spp.Spp

# Generates variable handle tests for objects and all primitive types
# This is likely to be a temporary testing approach as it may be more
# desirable to generate code using ASM which will allow more flexibility
# in the kinds of tests that are generated.

for type in boolean byte short char int long float double String
do
  Type="$(tr '[:lower:]' '[:upper:]' <<< ${type:0:1})${type:1}"
  args="-K$type -Dtype=$type -DType=$Type"

  case $type in
    String|int|long)
      args="$args -KCAS"
      ;;
  esac

  case $type in
    int|long)
      args="$args -KAtomicAdd"
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
    String)
      value1=\"foo\"
      value2=\"bar\"
      value3=\"baz\"
      ;;
  esac

  args="$args -Dvalue1=$value1 -Dvalue2=$value2 -Dvalue3=$value3 -Dwrong_primitive_type=$wrong_primitive_type"

  echo $args
  java $SPP -nel $args < X-VarHandleTestAccess.java.template > VarHandleTestAccess${Type}.java
  java $SPP -nel $args < X-VarHandleTestMethodHandleAccess.java.template > VarHandleTestMethodHandleAccess${Type}.java
  java $SPP -nel $args < X-VarHandleTestMethodType.java.template > VarHandleTestMethodType${Type}.java
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
    short)
      value1=(short)0x0102
      value2=(short)0x1112
      value3=(short)0x2122
      ;;
    char)
      value1=(char)0x0102
      value2=(char)0x1112
      value3=(char)0x2122
      ;;
    int)
      value1=0x01020304
      value2=0x11121314
      value3=0x21222324
      ;;
    long)
      value1=0x0102030405060708L
      value2=0x1112131415161718L
      value3=0x2122232425262728L
      ;;
    float)
      value1=0x01020304
      value2=0x11121314
      value3=0x21222324
      ;;
    double)
      value1=0x0102030405060708L
      value2=0x1112131415161718L
      value3=0x2122232425262728L
      ;;
  esac

  args="$args -Dvalue1=$value1 -Dvalue2=$value2 -Dvalue3=$value3"

  echo $args
  java $SPP -nel $args < X-VarHandleTestByteArrayView.java.template > VarHandleTestByteArrayAs${Type}.java
done

rm -fr build
