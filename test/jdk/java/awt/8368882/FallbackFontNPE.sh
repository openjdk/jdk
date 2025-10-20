# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
# Copyright (c) 2025, Azul Systems, Inc. All rights reserved.
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

# @test
# @bug 8368882
# @compile FallbackFontNPE.java
# @requires os.family == "windows"
# @run shell/timeout=400 FallbackFontNPE.sh
# @summary Check if PhysicalFont.getMapper() doesn't throw NPE

if [ -z "${TESTSRC}" ]; then
  echo "TESTSRC undefined: defaulting to ."
  TESTSRC=.
fi

if [ -z "${TESTCLASSES}" ]; then
  echo "TESTCLASSES undefined: defaulting to ."
  TESTCLASSES=.
fi

if [ -z "${TESTJAVA}" ]; then
  echo "TESTJAVA undefined: can't continue."
  exit 1
fi

cd ${TESTSRC}

echo Setup EUDC.tte font
reg_eudc_1252="HKCU\\EUDC\\1252"
echo ...Write ${reg_eudc_1252} record
old_reg_record=""
if Reg QUERY "${reg_eudc_1252}"; then
   old_reg_record=$(cygpath -m ${TESTCLASSES}/eudc_1252.reg)
   Reg EXPORT "${reg_eudc_1252}" ${old_reg_record} /y
   Reg DELETE "${reg_eudc_1252}" /va /f
fi
Reg ADD "${reg_eudc_1252}" /v SystemDefaultEUDCFont /d EUDC.TTE /f

windows_eudc_tte=$(cygpath -m ${WINDIR}/Fonts/EUDC.tte)
delete_eudc_tte=false
if [ ! -f ${windows_eudc_tte} ]; then
  echo ...Copy custom EUDC.tte file
  delete_eudc_tte=true
  cp ${WINDIR}/Fonts/webdings.ttf ${windows_eudc_tte}
fi

echo Setup fallback font
fallbackfontdir=${TESTJAVA}/lib/fonts/fallback
mkdir -p ${fallbackfontdir}
cp ${WINDIR}/Fonts/webdings.ttf ${fallbackfontdir}

echo Run java test FallbackFontNPE
result=0
${TESTJAVA}/bin/java ${TESTVMOPTS} -cp $(cygpath -m ${TESTCLASSES}) FallbackFontNPE || result=1

echo Delete fallback font
rm ${fallbackfontdir}/webdings.ttf

echo Restore registry record
Reg DELETE ${reg_eudc_1252} /f
if [ -f "${old_reg_record}" ]; then
  Reg import ${old_reg_record}
  rm "${old_reg_record}"
fi

if [ ${delete_eudc_tte} = true ]; then
    echo ...Delete test EUDC.tte
    rm "${windows_eudc_tte}"
fi

if [ $result -ne 0 ]; then
      echo "Test fails: exception thrown!"
      exit 1
fi

exit 0

