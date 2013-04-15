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
 * JDK-8011714: Regexp decimal escape handling still not correct
 *
 * @test
 * @run
 */

// \0 should be interpreted as <NUL> character here
print(/\08/.test("\x008"));
print(/[\08]/.test("8"));
print(/[\08]/.test("\x00"));

// Can't be converted to octal thus encoded as literal char sequence
print(/\8/.exec("\\8"));
print(/[\8]/.exec("\\"));
print(/[\8]/.exec("8"));

// 0471 is too high for an octal escape so it is \047 outside a character class
// and \\471 inside a character class
print(/\471/.exec("\x271"));
print(/[\471]/.exec("1"));
print(/[\471]/.exec("\x27"));

// 0366 is a valid octal escape (246)
print(/\366/.test("\xf6"));
print(/[\366]/.test("\xf6"));
print(/[\366]/.test("\xf6"));

// more tests for conversion of invalid backreferences to octal escapes or literals
print(/(a)(b)(c)(d)\4/.exec("abcdd"));
print(/(a)(b)(c)(d)\4x/.exec("abcddx"));
print(/(a)(b)(c)(d)\47/.exec("abcdd7"));
print(/(a)(b)(c)(d)\47/.exec("abcd\x27"));
print(/(a)(b)(c)(d)\47xyz/.exec("abcd\x27xyz"));
print(/(a)(b)(c)(d)[\47]/.exec("abcd\x27"));
print(/(a)(b)(c)(d)[\47]xyz/.exec("abcd\x27xyz"));
print(/(a)(b)(c)(d)\48/.exec("abcd\x048"));
print(/(a)(b)(c)(d)\48xyz/.exec("abcd\x048xyz"));
print(/(a)(b)(c)(d)[\48]/.exec("abcd\x04"));
print(/(a)(b)(c)(d)[\48]xyz/.exec("abcd\x04xyz"));
print(/(a)(b)(c)(d)\84/.exec("abcd84"));
print(/(a)(b)(c)(d)\84xyz/.exec("abcd84xyz"));
print(/(a)(b)(c)(d)[\84]/.exec("abcd8"));
print(/(a)(b)(c)(d)[\84]xyz/.exec("abcd8xyz"));

