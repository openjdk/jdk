#!/bin/bash
#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#

# MANUAL
#
# ./common/bin/compareimages.sh old_jdk_image new_jdk_image
#
# Compare the directory structure.
# Compare the filenames in the directories.
# Compare the contents of the zip archives
# Compare the contents of the jar archives
# Compare the native libraries
# Compare the native executables
# Compare the remaining files
#
# ./common/bin/compareimages.sh old_jdk_image new_jdk_image [zips jars libs execs other]
#
# Compare only the selected subset of the images.
#
# ./common/bin/compareimages.sh old_jdk_image new_jdk_image CodePointIM.jar
#
# Compare only the CodePointIM.jar file
# Can be used to compare zips, libraries and executables.
#

if [ "x$1" = "x-h" ] || [ "x$1" = "x--help" ] || [ "x$1" == "x" ]; then
    echo "bash ./common/bin/compareimages.sh old_jdk_image new_jdk_image"
    echo ""
    echo "Compare the directory structure."
    echo "Compare the filenames in the directories."
    echo "Compare the contents of the zip archives"
    echo "Compare the contents of the jar archives"
    echo "Compare the native libraries"
    echo "Compare the native executables"
    echo "Compare the remaining files"
    echo ""
    echo "bash ./common/bin/compareimages.sh old_jdk_image new_jdk_image [zips jars libs execs other]"
    echo ""
    echo "Compare only the selected subset of the images."
    echo ""
    echo "bash ./common/bin/compareimages.sh old_jdk_image new_jdk_image CodePointIM.jar"
    echo ""
    echo "Compare only the CodePointIM.jar file"
    echo "Can be used to compare zips, libraries and executables."
    exit 10
fi

OLD="$1"
NEW="$2"
CMD="$3"

DIFF_RESULT=0

CMP_ZIPS=false
CMP_JARS=false
CMP_LIBS=false
CMP_EXECS=false
CMP_OTHER=false

FILTER="cat"

if [ -n "$CMD" ]; then
  case "$CMD" in
    zips)
          CMP_ZIPS=true
      ;;
    jars)
          CMP_JARS=true
      ;;
    libs)
          CMP_LIBS=true
      ;;
    execs)
          CMP_EXECS=true
      ;;
    other)
          CMP_OTHER=true
      ;;
    *)
          CMP_ZIPS=true
          CMP_JARS=true
          CMP_LIBS=true
          CMP_EXECS=true
          CMP_OTHER=true
          FILTER="grep $3"
      ;;
  esac
else
    CMP_ZIPS=true
    CMP_JARS=true
    CMP_LIBS=true
    CMP_EXECS=true
    CMP_OTHER=true
fi

DIFFJARZIP="/bin/bash `dirname $0`/diffjarzip.sh"
DIFFLIB="/bin/bash `dirname $0`/difflib.sh"
DIFFEXEC="/bin/bash `dirname $0`/diffexec.sh"
export COMPARE_ROOT=/tmp/cimages.$USER
mkdir -p $COMPARE_ROOT

# Load the correct exception list.
case "`uname -s`" in
    Linux)
        . `dirname $0`/exception_list_linux
        ;;
esac

echo
echo Comparing $OLD to $NEW
echo

(cd $OLD && find . -type d | sort > $COMPARE_ROOT/from_dirs)
(cd $NEW && find . -type d | sort > $COMPARE_ROOT/to_dirs)

echo -n Directory structure...
if diff $COMPARE_ROOT/from_dirs $COMPARE_ROOT/to_dirs > /dev/null; then
    echo Identical!
else
    echo Differences found.
    DIFF_RESULT=1
    # Differences in directories found.
    ONLY_OLD=$(diff $COMPARE_ROOT/from_dirs $COMPARE_ROOT/to_dirs | grep '<')
    if [ "$ONLY_OLD" ]; then
        echo Only in $OLD
        echo $ONLY_OLD | sed 's|< ./|\t|g' | sed 's/ /\n/g'
    fi
    # Differences in directories found.
    ONLY_NEW=$(diff $COMPARE_ROOT/from_dirs $COMPARE_ROOT/to_dirs | grep '>')
    if [ "$ONLY_NEW" ]; then
        echo Only in $NEW
        echo $ONLY_NEW | sed 's|> ./|\t|g' | sed 's/ /\n/g'
    fi
fi

(cd $OLD && find . -type f | sort > $COMPARE_ROOT/from_files)
(cd $NEW && find . -type f | sort > $COMPARE_ROOT/to_files)

echo -n File names...
if diff $COMPARE_ROOT/from_files $COMPARE_ROOT/to_files > /dev/null; then
    echo Identical!
else
    echo Differences found.
    DIFF_RESULT=1
    # Differences in directories found.
    ONLY_OLD=$(diff $COMPARE_ROOT/from_files $COMPARE_ROOT/to_files | grep '<')
    if [ "$ONLY_OLD" ]; then
        echo Only in $OLD
        echo "$ONLY_OLD" | sed 's|< ./|    |g'
    fi
    # Differences in directories found.
    ONLY_NEW=$(diff $COMPARE_ROOT/from_files $COMPARE_ROOT/to_files | grep '>')
    if [ "$ONLY_NEW" ]; then
        echo Only in $NEW
        echo "$ONLY_NEW" | sed 's|> ./|    |g'
    fi
fi

echo -n Permissions...
found=""
for f in `cd $OLD && find . -type f`
do
    if [ ! -f ${OLD}/$f ]; then continue; fi
    if [ ! -f ${NEW}/$f ]; then continue; fi
    OP=`ls -l ${OLD}/$f | awk '{printf("%.10s\n", $1);}'`
    NP=`ls -l ${NEW}/$f | awk '{printf("%.10s\n", $1);}'`
    if [ "$OP" != "$NP" ]
    then
	if [ -z "$found" ]; then echo ; found="yes"; fi
	printf "\told: ${OP} new: ${NP}\t$f\n"
    fi

    OF=`cd ${OLD} && file $f`
    NF=`cd ${NEW} && file $f`
    if [ "$f" = "./src.zip" ]
    then
	if [ "`echo $OF | grep -ic zip`" -gt 0 -a "`echo $NF | grep -ic zip`" -gt 0 ]
	then
	    # the way we produces zip-files make it so that directories are stored in old file
	    # but not in new (only files with full-path)
	    # this makes file-5.09 report them as different
	    continue;
	fi
    fi

    if [ "$OF" != "$NF" ]
    then
	if [ -z "$found" ]; then echo ; found="yes"; fi
	printf "\tFILE: old: ${OF} new: ${NF}\t$f\n"
    fi
done
if [ -z "$found" ]; then echo ; found="yes"; fi

GENERAL_FILES=$(cd $OLD && find . -type f ! -name "*.so" ! -name "*.jar" ! -name "*.zip" \
                                  ! -name "*.debuginfo" ! -name "*.dylib" ! -name "jexec" \
                                  ! -name "ct.sym" ! -name "*.diz" \
                              | grep -v "./bin/"  | sort | $FILTER)
echo General files...
for f in $GENERAL_FILES
do
    if [ -e $NEW/$f ]; then
        DIFF_OUT=$(diff $OLD/$f $NEW/$f 2>&1)
        if [ -n "$DIFF_OUT" ]; then
            echo $f
            echo "$DIFF_OUT"
        fi
    fi
done


if [ "x$CMP_ZIPS" == "xtrue" ]; then
    ZIPS=$(cd $OLD && find . -type f -name "*.zip" | sort | $FILTER)

    if [ -n "$ZIPS" ]; then
        echo Zip files...

        for f in $ZIPS
        do
            $DIFFJARZIP $OLD/$f $NEW/$f $OLD $NEW 
            if [ "$?" != "0" ]; then
                DIFF_RESULT=1
            fi
        done
   fi        
fi    

if [ "x$CMP_JARS" == "xtrue" ]; then
    JARS=$(cd $OLD && find . -type f -name "*.jar" -o -name "ct.sym" | sort | $FILTER)

    if [ -n "$JARS" ]; then
        echo Jar files...

        for f in $JARS
        do
            DIFFJAR_OUTPUT=`$DIFFJARZIP $OLD/$f $NEW/$f $OLD $NEW`
            DIFFJAR_RESULT=$?
            if [ "$DIFFJAR_RESULT" != "0" ]; then
                for diff in $LIST_DIFF_JAR; do
                    DIFFJAR_OUTPUT=`echo "$DIFFJAR_OUTPUT" | grep -v "$diff"`
                done
                if [ "`echo "$DIFFJAR_OUTPUT" | grep -v "Differing files in"`" != "" ]; then
                    DIFF_RESULT=1
                    echo "$DIFFJAR_OUTPUT"
                fi
            fi
        done
    fi
fi

if [ "x$FILTER" != "xcat" ]; then
    VIEW=view
else
    VIEW=
fi

if [ "x$CMP_LIBS" == "xtrue" ]; then
    LIBS=$(cd $OLD && find . -name 'lib*.so' -o -name '*.dylib' -o -name '*.dll' | sort | $FILTER)

    if [ -n "$LIBS" ]; then
        echo Libraries...
        for f in $LIBS
        do
            DIFFLIB_OUTPUT=`$DIFFLIB $OLD/$f $NEW/$f $OLD $NEW $VIEW`
            DIFFLIB_RESULT=$?
            if [ "$DIFFLIB_RESULT" = "0" ]; then
                :
                #echo "OK: $DIFFLIB_OUTPUT"
            elif [ "$DIFFLIB_RESULT" = "2" ] && [[ "$LIST_DIFF_SIZE $LIST_DIFF_BYTE" == *"${f:2}"* ]]; then
                :
                #echo "OK: $DIFFLIB_OUTPUT"
            elif [ "$DIFFLIB_RESULT" = "1" ] && [[ "$LIST_DIFF_BYTE" == *"${f:2}"* ]]; then
                :
                #echo "OK: $DIFFLIB_OUTPUT"
            else
                echo "$DIFFLIB_OUTPUT"
                DIFF_RESULT=1
            fi
        done
    fi
fi

if [ "x$CMP_EXECS" == "xtrue" ]; then
    if [ $OSTYPE == "cygwin" ]; then
        EXECS=$(cd $OLD && find . -type f -name '*.exe' | sort | $FILTER)
    else
        EXECS=$(cd $OLD && find . -type f -perm -100 \! \( -name '*.so' -o -name '*.dylib' -o -name '*.dll' \) | sort | $FILTER)
    fi


    if [ -n "$EXECS" ]; then
        echo Executables...

        for f in $EXECS
        do
            DIFFEXEC_OUTPUT=`$DIFFEXEC $OLD/$f $NEW/$f $OLD $NEW $VIEW`
            DIFFEXEC_RESULT=$?
            if [ "$DIFFEXEC_RESULT" = "0" ]; then
                :
                #echo "OK: $DIFFEXEC_OUTPUT"
            elif [ "$DIFFEXEC_RESULT" = "2" ] && [[ "$LIST_DIFF_SIZE $LIST_DIFF_BYTE" == *"${f:2}"* ]]; then
                :
                #echo "OK: $DIFFEXEC_OUTPUT"
            elif [ "$DIFFEXEC_RESULT" = "1" ] && [[ "$LIST_DIFF_BYTE" == *"${f:2}"* ]]; then
                :
                #echo "OK: $DIFFEXEC_OUTPUT"
            else
                echo "$DIFFEXEC_OUTPUT"
                DIFF_RESULT=1
            fi
        done
    fi
fi

exit $DIFF_RESULT
