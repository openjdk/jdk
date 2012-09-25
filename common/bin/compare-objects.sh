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
# ./common/bin/compare-objects.sh old_jdk_build_dir new_jdk_build_dir
#
# Compares object files
#

if [ "x$1" = "x-h" ] || [ "x$1" = "x--help" ] || [ "x$1" == "x" ]; then
    echo "bash ./common/bin/compare-objects.sh old_jdk_build_dir new_jdk_build_dir <pattern>"
    echo ""
    echo "Compare object files"
    echo ""
    exit 10
fi

#######
#
# List of files (grep patterns) that are ignored
# 
# 1) hotspot object files
IGNORE="-e hotspot"

# 2) various build artifacts: sizer.32.o sizer.64.o dummyodbc.o
#    these are produced during build and then e.g run to produce other data
#    i.e not directly put into build => safe to ignore
IGNORE="${IGNORE} -e sizer.32.o -e sizer.64.o"
IGNORE="${IGNORE} -e dummyodbc.o"
IGNORE="${IGNORE} -e genSolarisConstants.o"
IGNORE="${IGNORE} -e genUnixConstants.o"

OLD="$1"
NEW="$2"
shift; shift
PATTERN="$*"

if [ -f $NEW/spec.sh ]; then
    . $NEW/spec.sh
elif [ -f $NEW/../../spec.sh ]; then
    . $NEW/../../spec.sh
elif [ -f $OLD/spec.sh ]; then
    . $OLD/spec.sh
elif [ -f $OLD/../../spec.sh ]; then
    . $OLD/../../spec.sh
else
    echo "Unable to find spec.sh"
    echo "Giving up"
    exit 1
fi

export COMPARE_ROOT=/tmp/cimages.$USER/objects
mkdir -p $COMPARE_ROOT

(${CD} $OLD && ${FIND} . -name '*.o') > $COMPARE_ROOT/list.old
(${CD} $NEW && ${FIND} . -name '*.o') > $COMPARE_ROOT/list.new

# On macosx JobjC is build in both i386 and x86_64 variant (universial binary)
#   but new build only builds the x86_64
# Remove the 386 variants from comparison...to avoid "false" positives
${GREP} -v 'JObjC.dst/Objects-normal/i386' $COMPARE_ROOT/list.old > $COMPARE_ROOT/list.old.new
${CP} $COMPARE_ROOT/list.old $COMPARE_ROOT/list.old.full
${CP} $COMPARE_ROOT/list.old.new $COMPARE_ROOT/list.old

findnew() {
    arg_1=$1
    arg_2=$2

    # special case 1 unpack-cmd => unpackexe
    arg_1=`${ECHO} $arg_1 | ${SED} 's!unpack-cmd!unpackexe!g'`
    arg_2=`${ECHO} $arg_2 | ${SED} 's!unpack-cmd!unpackexe!g'`

    # special case 2 /JObjC.dst/ => /libjobjc/
    arg_1=`${ECHO} $arg_1 | ${SED} 's!/JObjC.dst/!/libjobjc/!g'`
    arg_2=`${ECHO} $arg_2 | ${SED} 's!/JObjC.dst/!/libjobjc/!g'`

    full=`${ECHO} $arg_1 | ${SED} 's!\.!\\\.!g'`
    medium=`${ECHO} $arg_1 | ${SED} 's!.*/\([^/]*/[^/]*\)!\1!'`
    short=`${ECHO} $arg_2 | ${SED} 's!\.!\\\.!g'`
    if [ "`${GREP} -c "/$full" $COMPARE_ROOT/list.new`" -eq 1 ]
    then
	${ECHO} $NEW/$arg_1
	return
    fi

    if [ "`${GREP} -c "$medium" $COMPARE_ROOT/list.new`" -eq 1 ]
    then
	${GREP} "$medium" $COMPARE_ROOT/list.new
	return
    fi

    if [ "`${GREP} -c "/$short" $COMPARE_ROOT/list.new`" -eq 1 ]
    then
	${GREP} "/$short" $COMPARE_ROOT/list.new
	return
    fi

    # old style has "dir" before obj{64}
    dir=`${ECHO} $arg_1 | ${SED} 's!.*/\([^/]*\)/obj[64]*.*!\1!g'`
    if [ -n "$dir" -a "$dir" != "$arg_1" ]
    then
	if [ "`${GREP} $dir $COMPARE_ROOT/list.new | ${GREP} -c "/$short"`" -eq 1 ]
	then
	    ${GREP} $dir $COMPARE_ROOT/list.new | ${GREP} "/$short"
	    return
	fi

	# Try with lib$dir/
	if [ "`${GREP} "lib$dir/" $COMPARE_ROOT/list.new | ${GREP} -c "/$short"`" -eq 1 ]
	then
	    ${GREP} "lib$dir/" $COMPARE_ROOT/list.new | ${GREP} "/$short"
	    return
	fi

	# Try with $dir_objs
	if [ "`${GREP} "${dir}_objs" $COMPARE_ROOT/list.new | ${GREP} -c "/$short"`" -eq 1 ]
	then
	    ${GREP} "${dir}_objs" $COMPARE_ROOT/list.new | ${GREP} "/$short"
	    return
	fi
    fi

    # check for some specifics...
    for i in demo hotspot jobjc
    do
	if [ "`${ECHO} $full | ${GREP} -c $i`" -gt 0 ]
	then
	    if [ "`${GREP} $i $COMPARE_ROOT/list.new | ${GREP} -c "/$short"`" -eq 1 ]
	    then
		${GREP} $i $COMPARE_ROOT/list.new | ${GREP} "/$short"
		return
	    fi
	fi
    done

    # check for specific demo
    demo=`${ECHO} $arg_1 | ${SED} 's!.*/demo/jvmti/\([^/]*\)/.*!\1!g'`
    if [ -n "$demo" -a "$dir" != "$demo" ]
    then
	if [ "`${GREP} $demo $COMPARE_ROOT/list.new | ${GREP} -c "/$short"`" -eq 1 ]
	then
	    ${GREP} $demo $COMPARE_ROOT/list.new | ${GREP} "/$short"
	    return
	fi
    fi

    return
}

compare() {
    old=$1
    new=$2
    ${DIFF} $old $new > /dev/null
    res=$?
    if [ $res -eq 0 ]
    then
	${ECHO} 0
	return
    fi

    # check if stripped objects gives equality
    ${CP} $old $COMPARE_ROOT/`basename $old`.old
    ${CP} $new $COMPARE_ROOT/`basename $old`.new
    ${POST_STRIP_CMD} $COMPARE_ROOT/`basename $old`.old $COMPARE_ROOT/`basename $old`.new > /dev/null 2>&1
    ${DIFF} $COMPARE_ROOT/`basename $old`.old $COMPARE_ROOT/`basename $old`.new > /dev/null
    res=$?
    ${RM} $COMPARE_ROOT/`basename $old`.old $COMPARE_ROOT/`basename $old`.new
    if [ $res -eq 0 ]
    then
	${ECHO} S
	return
    fi

    name=`basename $1 | ${SED} 's!\.o!!'`
    cntold=`strings $old | ${GREP} -c $name`
    cntnew=`strings $new | ${GREP} -c $name`
    
    if [ $cntold -gt 0 -a $cntnew -gt 0 ]
    then
	${ECHO} F
	return
    fi

    ${ECHO} 1
}

for F in `${CAT} $COMPARE_ROOT/list.old`
do
    if [ "${IGNORE}" ] && [ "`${ECHO} $F | ${GREP} ${IGNORE}`" ]
    then
	#
	# skip ignored files
        #
	continue;
    fi

    if [ "$PATTERN" ] && [ `${ECHO} $F | ${GREP} -c $PATTERN` -eq 0 ]
    then
	continue;
    fi

    f=`basename $F`
    o=$OLD/$F
    n=`findnew $F $f`

    if [ "$n" ]
    then	
	n="$NEW/$n"
	${ECHO} `compare $o $n` : $f : $o : $n
    else
	${ECHO} "- : $f : $o "
    fi
done
