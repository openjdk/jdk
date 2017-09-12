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
 * Basic String constructor tests.
 *
 * @test
 * @run
 */

var s = new String("javascript");
print(s.length);

// construct from char codes
print("fromCharCode.length = " + String.fromCharCode.length);
var x = String.fromCharCode(65, 66, 67, 68);
print(x);

// print(String.prototype.concat);
print("concat.length = " + String.prototype.concat.length);
print(s.concat(" is great!"));

print("slice.length = " + String.prototype.slice.length);
print("hello world".slice(-6));
print("hello world".slice(7, 10));

print("substring.length = " + String.prototype.substring.length);
print(s.substring(0, 4));
print(s.substring(4));
print(s.substring(10, 4));

print("toLowerCase.length = " + String.prototype.toLowerCase.length);
print("JAVA".toLowerCase());
print("JAVA".toLocaleLowerCase());
print("toUpperCase.length = " + String.prototype.toUpperCase.length);
print("javascript".toUpperCase());
print("javascript".toLocaleUpperCase());

print("localeCompare.length = " + String.prototype.localeCompare.length);
print("java".localeCompare("JAVA"));
print("java".localeCompare("java"));

print("trim.length = " + String.prototype.trim.length);
print(" java ".trim());
print("java".trim());

print("hello".indexOf("l"));
print("hello".lastIndexOf("l"));

// we can call java.lang.String methods on JS Strings.

print("hello".endsWith("lo"));
print("hello".startsWith("hell"));
print("hello".startsWith("lo"));
print("hello".endsWith("hell"));

// we can access string's characters with array-like indexing..

print("hello"[0]);
print("hello"[2]);
print("hello"[100]);  // undefined

print('foo' === 'foo');
print('foo' !== 'foo');
print('' === '');
print('' !== '');

