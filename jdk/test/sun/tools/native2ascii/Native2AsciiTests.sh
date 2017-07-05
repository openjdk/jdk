#! /bin/sh -e

#
# Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test
# @bug 4630463 4630971 4636448 4701617 4721296 4710890 6247817
# @summary Tests miscellaneous native2ascii bugfixes and regressions


if [ "${TESTSRC}" = "" ]; then TESTSRC=.; fi
if [ "${TESTJAVA}" = "" ]; then TESTJAVA=$1; shift; fi

case `uname -s` in
  Windows* | CYGWIN*) OS=Windows;;
  SunOS|Linux) OS=Unix;;
esac

N2A=$TESTJAVA/bin/native2ascii

check() {
  bug=$1; shift
  expected=$1; shift
  out=$1; shift

  # Strip carriage returns from output when comparing with n2a test output
  # on win32 systems
  if [ ${OS} = Windows ]; then
     sed -e 's@\\r@@g' $out >$out.1
     sed -e 's@\\r@@g' $expected >$out.expected
  else
     cp $out $out.1
     cp $expected $out.expected
  fi
  if (set -x; diff -c $out.expected $out.1); then
    echo "$bug passed"
  else
    echo "$bug failed"
    exit 1
  fi
}

# Check that native2ascii -reverse with an ISO-8859-1 encoded file works
# as documented. 4630463 fixes a bug in the ISO-8859-1 encoder which
# prevented encoding of valid ISO-8859-1 chars > 0x7f

rm -f x.*
$N2A -reverse -encoding ISO-8859-1 $TESTSRC/A2N_4630463 x.out
check 4630463 $TESTSRC/A2N_4630463.expected x.out

# Take file encoded in ISO-8859-1 with range of chars ,  0x7f < c < 0xff
# invoke native2ascii with input filename and output filename identical
# Ensure that output file is as expected by comparing to expected output.
# 4636448 Fixed bug whereby output file was clobbered if infile and outfile
# referred to same filename.  This bug only applies to Solaris/Linux, since on
# Windows you can't write to a file that's open for reading.

if [ $OS = Unix ]; then
  rm -f x.*
  cp $TESTSRC/N2A_4636448 x.in
  chmod +w x.in
  ls -l x.in
  if $N2A -encoding ISO-8859-1 x.in x.in; then
    check 4636448 $TESTSRC/N2A_4636448.expected x.in
  fi
fi

# Ensure that files containing backslashes adjacent to EOF don't
# hang native2ascii -reverse

rm -f x.*
$N2A -reverse -encoding ISO-8859-1 $TESTSRC/A2N_4630971 x.out
check 4630971 $TESTSRC/A2N_4630971 x.out

# Check reverse (char -> native) encoding of Japanese Halfwidth
# Katakana characters for MS932 (default WinNT Japanese encoding)
# Regression test for bugID 4701617

rm -f x.*
$N2A -reverse -encoding MS932 $TESTSRC/A2N_4701617 x.out
check 4701617 $TESTSRC/A2N_4701617.expected x.out

# for win32 only ensure when output file pre-exists that
# native2ascii tool will simply overwrite with the expected
# output file (fixed bugID 4710890)

if [ OS = Windows ]; then
   rm -f x.*
   cp $TESTSRC/test3 x.in
   chmod a+x x.in
   ls -l x.in
   touch x.out
   $N2A -encoding ISO-8859-1 x.in x.out
   check 4710890 $TESTSRC/test3 x.out
fi

rm -rf x.*
$N2A -reverse $TESTSRC/A2N_6247817 x.out
check 4701617 $TESTSRC/A2N_6247817 x.out

