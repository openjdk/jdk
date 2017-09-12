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
 * JDK-8013131: Various compatibility issues in String.prototype.split()
 *
 * @test
 * @run
 */


// Make sure limit is honored with undefined/empty separator
print(JSON.stringify("aa".split(undefined, 0)));
print(JSON.stringify("abc".split("", 1)));

// Make sure limit is honored with capture groups
print(JSON.stringify("aa".split(/(a)/, 1)));
print(JSON.stringify("aa".split(/(a)/, 2)));
print(JSON.stringify("aa".split(/((a))/, 1)));
print(JSON.stringify("aa".split(/((a))/, 2)));

// Empty capture group at end of string should be ignored
print(JSON.stringify("aaa".split(/((?:))/)));

// Tests below are to make sure that split does not read or write lastIndex property
var r = /a/;
r.lastIndex = {
    valueOf: function(){throw 2}
};
print(JSON.stringify("aa".split(r)));

r = /a/g;
r.lastIndex = 100;
print(JSON.stringify("aa".split(r)));
print(r.lastIndex);

r = /((?:))/g;
r.lastIndex = 100;
print(JSON.stringify("aaa".split(r)));
print(r.lastIndex);

// Make sure lastIndex is not updated on non-global regexp
r = /a/;
r.lastIndex = 100;
print(JSON.stringify(r.exec("aaa")));
print(r.lastIndex);
