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
 * Basic check for Java array element access and array element set.
 *
 * @test
 * @run
 */

(function() {
    var nargs = arguments.length;
    var args = new (Java.type("java.lang.Object[]"))(nargs);
    print(args.length);
    for (var i = 0; i < nargs; i++) {
        var arg = arguments[i];
        args[i] = arg;
        print(i + ' ' + arg + '/' + args[i]);
    }
})(13, 3.14, 'foo');

var z; // undefined

var intArray = new (Java.type("int[]"))(1);
intArray[0] = 10;
print(intArray[0]);
print(intArray.length);
intArray[0] = z;
print(intArray[0]);
intArray[0] = 10.1;
print(intArray[0]);

var boolArray = new (Java.type("boolean[]"))(2);
boolArray[0] = true;
print(boolArray[0]);
print(boolArray[1]);
print(boolArray.length);

var charArray = new (Java.type("char[]"))(1);
charArray[0] = 'j';
print(charArray[0]);
print(charArray.length);


var doubleArray = new (Java.type("double[]"))(1)
doubleArray[0]=z
print(doubleArray[0])
doubleArray[0]=1
print(doubleArray[0])
doubleArray[0]=1.1
print(doubleArray[0])
