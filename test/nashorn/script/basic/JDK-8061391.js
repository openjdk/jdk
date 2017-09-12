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
 * JDK-8061391 - Checks that the optimistic builtin for concat is semantically
 * correct.
 *
 * @test
 * @run
 */

var maxJavaInt = 0x7fffffff;

var ia = [1, 2, 3, 4];
var la = [maxJavaInt + 1000, maxJavaInt + 2000, maxJavaInt + 3000, maxJavaInt + 4000];
var da = [1.1, 2.2, 3.3, 4.4];
var oa = ["one", "two", "three", "four"];  

var aa = [ia, la, da, oa];

function concats() {
    print("shared callsite");

    print(ia);
    print(la);
    print(da);
    print(oa);
    print(aa);
    
    for (var i = 0; i < aa.length; i++) {
	print(aa[i].concat(aa[i][0]));
	for (var j = 0; j < aa.length ; j++) {
	    print(aa[i].concat(aa[j]));
	}
    }
}

function concats_inline() {
    print("separate callsites");

    print(ia);
    print(la);
    print(da);
    print(oa);
    print(aa);
    
    print(aa[0].concat(aa[0]));
    print(aa[0].concat(aa[1]));
    print(aa[0].concat(aa[2]));
    print(aa[0].concat(aa[3]));
    print(aa[0].concat(aa[0][0]));    

    print(aa[1].concat(aa[0]));
    print(aa[1].concat(aa[1]));
    print(aa[1].concat(aa[2]));
    print(aa[1].concat(aa[3]));
    print(aa[1].concat(aa[1][0]));    

    print(aa[2].concat(aa[0]));
    print(aa[2].concat(aa[1]));
    print(aa[2].concat(aa[2]));
    print(aa[2].concat(aa[3]));
    print(aa[2].concat(aa[2][0]));    

    print(aa[3].concat(aa[0]));
    print(aa[3].concat(aa[1]));
    print(aa[3].concat(aa[2]));
    print(aa[3].concat(aa[3]));
    print(aa[3].concat(aa[3][0]));    
}

concats();
concats_inline();

print();
var oldia = ia.slice(0); //clone ia
print("oldia = " + oldia);
ia[10] = "sparse";
print("oldia = " + oldia);

print();
print("Redoing with sparse arrays");

concats();
concats_inline();

ia = oldia;
print("Restored ia = " + ia);

function concat_expand() {
    print("concat type expansion");
    print(ia.concat(la));
    print(ia.concat(da));
    print(ia.concat(oa));
    print(la.concat(ia));
    print(la.concat(da));
    print(la.concat(oa));
    print(da.concat(ia));
    print(da.concat(la));
    print(da.concat(oa));
}

print();
concat_expand();

print();

function concat_varargs() {
    print("concat varargs");
    print(ia.concat(la)); //fast
    print(ia.concat(la, da, oa)); //slow
    var slow = ia.concat(1, maxJavaInt * 2, 4711.17, function() { print("hello, world") }); //slow
    print(slow);
    return slow;
}

var slow = concat_varargs();

print();
print("sanity checks");
slow.map(
	 function(elem) {
	     if (elem instanceof Function) {
		 elem();
	     } else {
		 print((typeof elem) + " = " + elem);
	     }
	 });

print(ia.concat({key: "value"}));
print(ia.concat({key: "value"}, {key2: "value2"}));
