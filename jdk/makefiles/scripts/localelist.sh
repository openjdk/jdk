#!/bin/sh

#
# Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

#
# This script is to generate the supported locale list string and replace the
# #LOCALE_LIST# in <ws>/src/share/classes/sun/util/CoreResourceBundleControl.java.
#
# NAWK & SED is passed in as environment variables.
#
LOCALE_LIST=$1
INUT_FILE=$2
OUTPUT_FILE=$3

LOCALES=`(for I in $LOCALE_LIST; do echo $I;done) | sort | uniq`
JAVA_LOCALES=

toJavaLocale()
{
    NewLocale=`echo $1 | $NAWK '
		BEGIN {
		    # The following values have to be consistent with java.util.Locale.
		    javalocales["en"] = "ENGLISH";
		    javalocales["fr"] = "FRENCH";
		    javalocales["de"] = "GERMAN";
		    javalocales["it"] = "ITALIAN";
		    javalocales["ja"] = "JAPANESE";
		    javalocales["ko"] = "KOREAN";
		    javalocales["zh"] = "CHINESE";
		    javalocales["zh_CN"] = "SIMPLIFIED_CHINESE";
		    javalocales["zh_TW"] = "TRADITIONAL_CHINESE";
		    javalocales["fr_FR"] = "FRANCE";
		    javalocales["de_DE"] = "GERMANY";
		    javalocales["it_IT"] = "ITALY";
		    javalocales["ja_JP"] = "JAPAN";
		    javalocales["ko_KR"] = "KOREA";
		    javalocales["en_GB"] = "UK";
		    javalocales["en_US"] = "US";
		    javalocales["en_CA"] = "CANADA";
		    javalocales["fr_CA"] = "CANADA_FRENCH";
		}
		{
		    if ($0 in javalocales) {
			print "	Locale." javalocales[$0];
		    } else {
			split($0, a, "_");
			if (a[3] != "") {
			    print " new Locale(\"" a[1] "\", \"" a[2] "\", \"" a[3] "\")";
			} else if (a[2] != "") {
			    print " new Locale(\"" a[1] "\", \"" a[2] "\")";
			} else {
			    print " new Locale(\"" a[1] "\")";
			}
		    }
		}'`

    JAVA_LOCALES=$JAVA_LOCALES$NewLocale
}

# count the number of locales
counter=0
for i in $LOCALES
do
    counter=`expr $counter + 1`
done

index=0
for locale in $LOCALES
do
    index=`expr $index + 1`;
    if [ $index != $counter ]
    then
	toJavaLocale $locale
	JAVA_LOCALES=$JAVA_LOCALES","
    else
	toJavaLocale $locale
    fi
done

# replace the #LOCALE_LIST# in the precompiled CoreResourceBundleControl.java file.

$SED -e "s@^#warn .*@// -- This file was mechanically generated: Do not edit! -- //@" \
    -e  "s/#LOCALE_LIST#/$JAVA_LOCALES/g" $2 > $3



