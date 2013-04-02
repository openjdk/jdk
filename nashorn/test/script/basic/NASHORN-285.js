/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * NASHORN-258 : loops with continues could be marked terminal
 *
 * @test
 * @run
 */

function do_not_loop_forever() {   
    var sum = 0;
    for (var i = 0; i < 4711; i++) {            
	sum += i;
	if (i >= 0) {
            continue;                                                           
        }  
	return sum;
    }  
    return sum;
}


function still_tag_terminal() {
    var sum = 0;
    for (var i = 0; i < 4711; i++) {            
	sum += i;
	for (var j = 0; j < 4712; j++) {
	    sum += j;
	    if (j & 1) {
		continue;
	    }
	}
	return sum;
    }  
    return sum;
}

print(do_not_loop_forever());
print(still_tag_terminal());
