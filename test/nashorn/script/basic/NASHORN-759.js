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

/*
 * NASHORN-759 - Efficient string concatenation with + operator
 *
 * @test
 * @run
 */

// Check invalidation of callsite for non-string CharSequence
function checkProps(s) {
    print(typeof s);
    print(typeof s.length);
    print(s.charCodeAt === String.prototype.charCodeAt);
    print();
}

var s1 = "foo";
var s2 = "bar";
var s3 = s1 + s2 + s1 + s2;
var s4 = new java.lang.StringBuilder("abc");
checkProps(s1);
checkProps(s2);
checkProps(s3);
checkProps(s4);

// Rebuild string and compare to original
function rebuildString(s) {
    var buf = s.split("");
    print(buf);
    while (buf.length > 1) {
        var result = [];
        buf.reduce(function(previous, current, index) {
            if (previous) {
                result.push(previous + current);
                return null;
            } else if (index === buf.length - 1) {
                result.push(current);
                return null;
            } else {
                return current;
            }
        });
        buf = result;
        print(buf);
    }
    return buf[0];
}

var n1 = "The quick gray nashorn jumps over the lazy zebra.";
var n2 = rebuildString(n1);
print(n1, n2, n2.length, n1 == n2, n1 === n2, n1 < n2, n1 <= n2, n1 > n2, n1 >= n2);
checkProps(n2);

var n3 = rebuildString(rebuildString(n2));
print(n3, n3.length, n1 == n3, n2 == n3, n1 === n3, n2 === n3);
checkProps(n3);
