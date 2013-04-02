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
 * NASHORN-58
 *
 * @test
 * @run
 */

function test1() {
    var x = 1;
    try { 
	print('try'); 
	x = 2; 
    } catch(e) {
	print('catch');
    } finally { 
	print('finally');
	x = 3; 
    }
    print(x);
}

function test2() {
    var x = 1;
    try {
	print('try');
    } finally {
	print('finally');
	x = 2;
    }
    print(x);
}

function test3() {
    try {
	return 2;
    } finally {
	return 3;
    }
}

function test4() {
    try {
	x = 1;
	print(x);
	try {
	    x = 2;
	    print(x);
	} finally {
	    x = 3;
	    print(x)
	    try {
		x = 4;
		print(x);
	    } finally {
		x = 5;
		print(x);
	    }
	}
	print(x)
    } finally {
	x = 6;
	print(x);
    }
    print(x);
}

function test5() {
    try {
	x = 1;
	print(x);
	try {
	    x = 2;
	    print(x);
	} finally {
	    x = 3;
	    print(x)
	    try {
		x = 4;
		print(x);
	    } finally {
		x = 5;
		return x;
	    }
	}
	print(x)
    } finally {
	x = 6;
	return x;
    }
}

function test6() {
    try {
	throw new Error("testing");
    } catch (ex) {
	print(ex);
	return;
    } finally {
	print("finally");
    }
}

function test7() {
    var e = new Error("no message");
    var i = 3;
    try {
        throw e;
    } catch (ex) {
    } finally {
        i++;
    }
    if (i != 4) {
	print("FAIL");
    }
    print("SUCCESS");
}


test1();
test2();
print(test3());
test4();
print(test5())
test6();
test7();

