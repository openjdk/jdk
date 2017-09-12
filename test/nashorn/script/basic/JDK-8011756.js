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
 * JDK-8011756: Wrong characters supported in RegExp \c escape
 *
 * @test
 * @run
 */


// Invalid control letters should be escaped:
print(/\cı/.test("\x09"));
print(/\cı/.test("\\cı"));

print(/\cſ/.test("\x13"));
print(/\cſ/.test("\\cſ"));

print(/[\cſ]/.test("\x13"));
print(/[\cſ]/.test("\\"));
print(/[\cſ]/.test("c"));
print(/[\cſ]/.test("ſ"));

print(/[\c#]/.test("\\"));
print(/[\c#]/.test("c"));
print(/[\c#]/.test("#"));

// The characters that are supported by other engines are '0'-'9', '_':
print(/[\c0]/.test("\x10"));
print(/[\c1]/.test("\x11"));
print(/[\c2]/.test("\x12"));
print(/[\c3]/.test("\x13"));
print(/[\c4]/.test("\x14"));
print(/[\c5]/.test("\x15"));
print(/[\c6]/.test("\x16"));
print(/[\c7]/.test("\x17"));
print(/[\c8]/.test("\x18"));
print(/[\c9]/.test("\x19"));
print(/[\c_]/.test("\x1F"));
