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
 * JDK-8023650: Regexp m flag does not recognize CRNL or CR
 *
 * @test
 * @run
 */

if (!/^Connection: close$/m.test('\r\n\r\nConnection: close\r\n\r\n')) {
    throw new Error();
}

if (!/^Connection: close$/m.test('\n\nConnection: close\n\n')) {
    throw new Error();
}

if (!/^Connection: close$/m.test('\r\rConnection: close\r\r')) {
    throw new Error();
}

if (!/^Connection: close$/m.test('\u2028\u2028Connection: close\u2028\u2028')) {
    throw new Error();
}

if (!/^Connection: close$/m.test('\u2029\u2029Connection: close\u2029\u2029')) {
    throw new Error();
}

var result = /a(.*)/.exec("a\r");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

result = /a(.*)/m.exec("a\r");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

result = /a(.*)/.exec("a\n");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

result = /a(.*)/m.exec("a\n");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

result = /a(.*)/.exec("a\r\n");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

result = /a(.*)/m.exec("a\r\n");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

result = /a(.*)/.exec("a\u2028");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

result = /a(.*)/m.exec("a\u2029");
if (!result || result[0] != 'a' || result[1] != '') {
    throw new Error();
}

if (/a$/.test("a\n")) {
    throw new Error();
}

if (/a$/.test("a\r")) {
    throw new Error();
}

if (/a$/.test("a\r\n")) {
    throw new Error();
}

if (/a$/.test("a\u2028")) {
    throw new Error();
}

if (/a$/.test("a\u2029")) {
    throw new Error();
}
