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
 * NASHORN-258 : Broken slot assignments to non constant members of multidimensional arrays in OP=
 *
 * @test
 * @run
 */

function test3(a) {
    for (i = 0; i < a.length ; i++) {
    for (j = 0; j < a[i].length ; j++) {
        for (k = 0; k < a[i][j].length ; k++) {
        a[i][j][k] *= 8;
        }
    }
    }
}

function test3local(a) {
    for (var i = 0; i < a.length ; i++) {
    for (var j = 0; j < a[i].length ; j++) {
        for (var k = 0; k < a[i][j].length ; k++) {
        a[i][j][k] *= 8;
        }
    }
    }
}

var array = [ [[1,1,1],[1,1,1],[1,1,1]],
          [[1,1,1],[1,1,1],[1,1,1]],
          [[1,1,1],[1,1,1],[1,1,1]] ];

test3(array);
print(array);

test3local(array);
print(array);

function outer() {

    var array2 = [ [[1,1,1],[1,1,1],[1,1,1]],
           [[1,1,1],[1,1,1],[1,1,1]],
           [[1,1,1],[1,1,1],[1,1,1]] ];

    var f =  function inner() {
    for (var i = 0; i < array2.length ; i++) {
        for (var j = 0; j < array2[i].length ; j++) {
        array2[i][j][2] *= 8;
        }
    }
    };

    f();
    print(array2);
}

outer();
