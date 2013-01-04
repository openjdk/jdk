#!/bin/sh
#
# Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ensure that all tests tagged with @test are also tagged with @run

for f in $(find test/script/basic/*.js); do 
    grep @test $f >/dev/null
    TEST=$?
    grep @run $f >/dev/null
    RUN=$?    

    if [ $TEST -eq 0 ] && [ ! $RUN -eq 0 ]; then		
	echo "repairing ${f}..."
	TEMP=$(mktemp /tmp/scratch.XXXXXX)

	#IFS='', -raw flag to preserve white space
	while IFS='' read -r line; do 	    
	    echo $line | grep @test >/dev/null
	    TEST=$?
	    printf "%s\n" "$line" 
	    if [ $TEST -eq 0 ]; then
		printf "%s\n" "$line" | sed s/@test/@run/g 
	    fi	   
	done < $f >$TEMP

	cp $TEMP $f

	rm -fr $TEMP
    fi

done
