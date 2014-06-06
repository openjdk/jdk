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
 * NASHORN-36 : Not all line terminators are accepted for single line comments.
 *
 * @test
 * @run
 */

// refer to ECMA section 7.3 Line Terminators
// Table 3 Line Terminator Characters.

// Because line comment ends with any of line terminators
// the following evals should set global 'x' with values.

eval("// line comment\u000A x = 1;");
print(x); // should print 1

eval("// line comment\u000D x = 2;");
print(x); // should print 2

eval("// line comment\u2028 x = 3;");
print(x); // should print 3

eval("// line comment\u2029 x = 4;");
print(x); // should print 4

