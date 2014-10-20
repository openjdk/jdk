/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8060011: Concatenating an array and converting it to Java gives wrong result
 *
 * @test
 * @run
 */


function compareAsJavaArrays(a1, a2) {
    var ja1 = Java.to(a1);
    var ja2 = Java.to(a2);
    if (ja1.length !== ja2.length) {
        throw "different length";
    }
    for (var i = 0; i < ja1.length; i++) {
        if (ja1[i] !== ja2[i]) {
            throw "different element at " + i;
        }
    }
    if (java.util.Arrays.toString(ja1) !== java.util.Arrays.toString(ja2)) {
        throw "different string representation";
    }
}

compareAsJavaArrays([0, 1, 2, 3],
                    [0].concat([1, 2, 3]));
compareAsJavaArrays([1000000000, 2000000000, 3000000000, 4000000000],
                    [1000000000].concat([2000000000, 3000000000, 4000000000]));
compareAsJavaArrays([0.5, 1.5, 2.5, 3.5],
                    [0.5].concat([1.5, 2.5, 3.5]));
compareAsJavaArrays(["0", "1", "2", "3"],
                    ["0"].concat(["1", "2", "3"]));



