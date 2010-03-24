#! /bin/sh

#
# Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# Generate concrete buffer classes

# Required environment variables
#   NAWK SED SPP    To invoke tools
#   TYPE            Primitive type
#   SRC             Source file
#   DST             Destination file
#
# Optional environment variables
#   RW              Mutability: R(ead only), W(ritable)
#   BO              Byte order: B(ig), L(ittle), S(wapped), U(nswapped)
#   BIN             Defined => generate binary-data access methods

type=$TYPE
rw=$RW
rwkey=XX

case $type in
  char)  fulltype=character;;
  int)   fulltype=integer;;
  *)     fulltype=$type;;
esac

case $type in
  byte)           LBPV=0;;
  char | short)   LBPV=1;;
  int | float)    LBPV=2;;
  long | double)  LBPV=3;;
esac

case $type in 
  float|double) floatingPointOrIntegralType=floatingPointType;;
  *)            floatingPointOrIntegralType=integralType;;
esac

typesAndBits() {

  type="$1"; BO="$2"
  memtype=$type; swaptype=$type; frombits=; tobits=

  case $type in
    float)   memtype=int
             if [ x$BO != xU ]; then
	       swaptype=int
	       fromBits=Float.intBitsToFloat
	       toBits=Float.floatToRawIntBits
	     fi;;
    double)  memtype=long
             if [ x$BO != xU ]; then
	       swaptype=long
	       fromBits=Double.longBitsToDouble
	       toBits=Double.doubleToRawLongBits
	     fi;;
  esac

  echo memtype=$memtype swaptype=$swaptype fromBits=$fromBits toBits=$toBits

  echo $type $fulltype $memtype $swaptype \
  | $NAWK '{ type = $1; fulltype = $2; memtype = $3; swaptype = $4;
	     x = substr(type, 1, 1);
	     Type = toupper(x) substr(type, 2);
	     Fulltype = toupper(x) substr(fulltype, 2);
	     Memtype = toupper(substr(memtype, 1, 1)) substr(memtype, 2);
	     Swaptype = toupper(substr(swaptype, 1, 1)) substr(swaptype, 2);
	     printf("Type=%s x=%s Fulltype=%s Memtype=%s Swaptype=%s ",
		    Type, x, Fulltype, Memtype, Swaptype); }'

  echo "swap=`if [ x$BO = xS ]; then echo Bits.swap; fi`"

}

eval `typesAndBits $type $BO`

a=`if [ $type = int ]; then echo an; else echo a; fi`
A=`if [ $type = int ]; then echo An; else echo A; fi`

if [ "x$rw" = xR ]; then rwkey=ro; else rwkey=rw; fi

set -e

$SPP <$SRC >$DST \
  -K$type \
  -K$floatingPointOrIntegralType \
  -Dtype=$type \
  -DType=$Type \
  -Dfulltype=$fulltype \
  -DFulltype=$Fulltype \
  -Dx=$x \
  -Dmemtype=$memtype \
  -DMemtype=$Memtype \
  -DSwaptype=$Swaptype \
  -DfromBits=$fromBits \
  -DtoBits=$toBits \
  -DLG_BYTES_PER_VALUE=$LBPV \
  -DBYTES_PER_VALUE="(1 << $LBPV)" \
  -DBO=$BO \
  -Dswap=$swap \
  -DRW=$rw \
  -K$rwkey \
  -Da=$a \
  -DA=$A \
  -Kbo$BO

if [ $BIN ]; then

  genBinOps() {
    type="$1"
    Type=`echo $1 | $NAWK '{ print toupper(substr($1, 1, 1)) substr($1, 2) }'`
    fulltype="$2"
    LBPV="$3"
    nbytes="$4"
    nbytesButOne="$5"
    a=`if [ $type = int ]; then echo an; else echo a; fi`
    src=$6
    eval `typesAndBits $type`
    $SPP <$src \
      -Dtype=$type \
      -DType=$Type \
      -Dfulltype=$fulltype \
      -Dmemtype=$memtype \
      -DMemtype=$Memtype \
      -DfromBits=$fromBits \
      -DtoBits=$toBits \
      -DLG_BYTES_PER_VALUE=$LBPV \
      -DBYTES_PER_VALUE="(1 << $LBPV)" \
      -Dnbytes=$nbytes \
      -DnbytesButOne=$nbytesButOne \
      -DRW=$rw \
      -K$rwkey \
      -Da=$a \
      -be
  }

  mv $DST $DST.tmp
  sed -e '/#BIN/,$d' <$DST.tmp >$DST
  rm -f $DST.tmp
  binops=`dirname $SRC`/`basename $SRC .java.template`-bin.java.template
  genBinOps char character 1 two one $binops >>$DST
  genBinOps short short 1 two one $binops >>$DST
  genBinOps int integer 2 four three $binops >>$DST
  genBinOps long long 3 eight seven $binops >>$DST
  genBinOps float float 2 four three $binops >>$DST
  genBinOps double double 3 eight seven $binops >>$DST
  echo '}' >>$DST

fi
