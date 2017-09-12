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
 * Check global functions and properties.
 *
 * @test
 * @run
 */

print(undefined);
print(typeof(undefined));
print(NaN);
print(typeof(NaN));
print(Infinity);
print(typeof(Infinity));
print(isNaN(NaN));
print(isNaN(1.0));
print(isNaN(0.0));
print(isNaN(Infinity));
print(isFinite(Math.PI));
print(isFinite(Infinity));
print(isFinite(NaN));
print(parseInt("4345"));
print(parseInt("100", 8));
print(parseInt("ffff", 16));
print(parseInt("0xffff"));
print(parseInt("0xffff", 16));
print(parseInt("0xffff", 8)); // should be NaN
print(parseInt("")); // should be NaN
print(parseInt("-")); // should be NaN
print(parseInt("+")); // should be NaN
print(parseInt("0x")); // should be NaN
print(parseInt("0X")); // should be NaN
print(parseInt("3.1415")); // should be "3" - ignore the not understood part
print(parseInt("5654gjhkgjdfgk")); // should be "5654" - ignore invalid tail chars

print(parseFloat("-Infinity"));
print(parseFloat("+Infinity"));
print(parseFloat("+3.14"));
print(parseFloat("-3.14"));
print(parseFloat("2.9e+8"));
print(parseFloat("6.62E-34"));

(function() {

function checkProtoImmutable(obj) {
    var attr = Object.getOwnPropertyDescriptor(this[obj], "prototype");
    if (attr.writable) {
        throw new Error(obj + ".prototype writable");
    }
    if (attr.enumerable) {
        throw new Error(obj + ".prototype enumerable");
    }
    if (attr.configurable) {
        throw new Error(obj + ".prototype configurable");
    }
}

checkProtoImmutable("Array");
checkProtoImmutable("Boolean");
checkProtoImmutable("Date");
checkProtoImmutable("Function");
checkProtoImmutable("Number");
checkProtoImmutable("Object");
checkProtoImmutable("RegExp");
checkProtoImmutable("String");

})();

// none of the built-in global properties are enumerable
for (i in this) {
    print(i);
}
