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
 * JDK-8009230: Nashorn rejects extended RegExp syntax accepted by all major JS engines
 *
 * @test
 * @run
 */


// Invalid ControlEscape/IdentityEscape character treated as literal.
print(/\z/.exec("z"));  // Invalid escape, same as /z/
// Incomplete/Invalid ControlEscape treated as "\\c"
print(/\c/.exec("\\c"));  // same as /\\c/
print(/\c2/.exec("\\c2"));  // same as /\\c2/
print(/\C/.exec("C"));  // same as /C/
print(/\C2/.exec("C2"));  // same as /C2/
// Incomplete HexEscapeSequence escape treated as "x".
print(/\x/.exec("x"));  // incomplete x-escape
print(/\x1/.exec("x1"));  // incomplete x-escape
print(/\x1z/.exec("x1z"));  // incomplete x-escape
// Incomplete UnicodeEscapeSequence escape treated as "u".
print(/\u/.exec("u"));  // incomplete u-escape
print(/\uz/.exec("uz"));  // incomplete u-escape
print(/\u1/.exec("u1"));  // incomplete u-escape
print(/\u1z/.exec("u1z"));  // incomplete u-escape
print(/\u12/.exec("u12"));  // incomplete u-escape
print(/\u12z/.exec("u12z"));  // incomplete u-escape
print(/\u123/.exec("u123"));  // incomplete u-escape
print(/\u123z/.exec("u123z"));  // incomplete u-escape
// Bad quantifier range:
print(/x{z/.exec("x{z"));  // same as /x\{z/
print(/x{1z/.exec("x{1z"));  // same as /x\{1z/
print(/x{1,z/.exec("x{1,z"));  // same as /x\{1,z/
print(/x{1,2z/.exec("x{1,2z"));  // same as /x\{1,2z/
print(/x{10000,20000z/.exec("x{10000,20000z"));  // same as /x\{10000,20000z/
// Notice: It needs arbitrary lookahead to determine the invalidity,
// except Mozilla that limits the numbers.

// Zero-initialized Octal escapes.
/\012/;    // same as /\x0a/

// Nonexisting back-references smaller than 8 treated as octal escapes:
print(/\5/.exec("\u0005"));  // same as /\x05/
print(/\7/.exec("\u0007"));  // same as /\x07/
print(/\8/.exec("\u0008"));  // does not match

// Invalid PatternCharacter accepted unescaped
print(/]/.exec("]"));
print(/{/.exec("{"));
print(/}/.exec("}"));

// Bad escapes also inside CharacterClass.
print(/[\z]/.exec("z"));
print(/[\c]/.exec("c"));
print(/[\c2]/.exec("c"));
print(/[\x]/.exec("x"));
print(/[\x1]/.exec("x1"));
print(/[\x1z]/.exec("x1z"));
print(/[\u]/.exec("u"));
print(/[\uz]/.exec("u"));
print(/[\u1]/.exec("u"));
print(/[\u1z]/.exec("u"));
print(/[\u12]/.exec("u"));
print(/[\u12z]/.exec("u"));
print(/[\u123]/.exec("u"));
print(/[\u123z]/.exec("u"));
print(/[\012]/.exec("0"));
print(/[\5]/.exec("5"));
// And in addition:
print(/[\B]/.exec("B"));
print(/()()[\2]/.exec(""));  // Valid backreference should be invalid.
