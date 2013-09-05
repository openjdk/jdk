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
 * JDK-8023531: new RegExp('').toString() should return '/(?:)/'
 *
 * @test
 * @run
 */

if (new RegExp("").toString() !== "/(?:)/") {
    throw new Error();
} else if (!(new RegExp("").test(""))) {
    throw new Error();
}

if (new RegExp("", "g").toString() !== "/(?:)/g") {
    throw new Error();
} else if (!(new RegExp("", "g").test(""))) {
    throw new Error();
}

if (new RegExp("", "i").toString() !== "/(?:)/i") {
    throw new Error();
} else if (!(new RegExp("", "i").test(""))) {
    throw new Error();
}

if (new RegExp("", "m").toString() !== "/(?:)/m") {
    throw new Error();
} else if (!(new RegExp("", "m").test(""))) {
    throw new Error();
}

if (RegExp("").toString() !== "/(?:)/") {
    throw new Error();
} else if (!RegExp("").test("")) {
    throw new Error();
}

if (RegExp("", "g").toString() !== "/(?:)/g") {
    throw new Error();
} else if (!RegExp("", "g").test("")) {
    throw new Error();
}

if (RegExp("", "i").toString() !== "/(?:)/i") {
    throw new Error();
} else if (!RegExp("", "i").test("")) {
    throw new Error();
}

if (RegExp("", "m").toString() !== "/(?:)/m") {
    throw new Error();
} else if (!RegExp("", "m").test("")) {
    throw new Error();
}

var re = /abc/;
re.compile("");
if (re.toString() !== "/(?:)/") {
    throw new Error();
} else if (!re.test("")) {
    throw new Error();
}

re.compile("", "g");
if (re.toString() !== "/(?:)/g") {
    throw new Error();
} else if (!re.test("")) {
    throw new Error();
}

re.compile("", "i");
if (re.toString() !== "/(?:)/i") {
    throw new Error();
} else if (!re.test("")) {
    throw new Error();
}

re.compile("", "m");
if (re.toString() !== "/(?:)/m") {
    throw new Error();
} else if (!re.test("")) {
    throw new Error();
}
