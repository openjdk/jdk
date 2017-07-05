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
 * JDK-8030197: Nashorn: Object.defineProperty() can be lured to change fixed NaN property
 *
 * @test
 * @run
 */

function str(n) {
    var a = new Uint8Array(new Float64Array([n]).buffer);
    return Array.apply(null, a).reduceRight(
        function(acc, v){
            return acc + (v < 10 ? "0" : "") + v.toString(16);
        }, "");
}

var o = Object.defineProperty({}, "NaN", { value: NaN })
var str1 = str(o.NaN);
Object.defineProperty(o, "NaN", { value: 0/0 })
var str2 = str(o.NaN);
if (str1 != str2) {
    fail("NaN bit pattern changed");
}
