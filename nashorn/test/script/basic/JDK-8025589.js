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
 * JDK-8025589:  Array.prototype.shift should only copy defined elements in generic mode
 *
 * @test
 * @run
 */

var s = {length: 4, 2: 1};
Array.prototype.shift.call(s);

if (s.length != 3) {
    fail("s.length != 3");
}
if (0 in s) {
    fail("0 in s");
}
if (s.hasOwnProperty(0)) {
    fail("s.hasOwnProperty(0)");
}
if (s[1] !== 1) {
    fail("s[1] !== 1");
}
if (2 in s) {
    fail("2 in s");
}
if (s.hasOwnProperty(2)) {
    fail("s.hasOwnProperty(2)");
}
