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
 * NASHORN-226 : Error.prototype.toString does not confirm to spec.
 *
 * @test
 * @run
 */

var e = new Error();
if (e.toString() !== "Error") {
    fail("#1 expected 'Error', got " + e.toString());
}

e.message = "";
if (e.toString() !== "Error") {
    fail("#2 expected 'Error', got " + e.toString());
}

e.message = { toString: function() { return '' } };
if (e.toString() !== "Error") {
    fail("#3 expected 'Error', got " + e.toString());
}

e.name = "MyError";
e.message = "";
if (e.toString() !== "MyError") {
    fail("#4 expected 'MyError', got " + e.toString());
}

