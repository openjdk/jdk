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
 * NASHORN-296 : load messes file name in some cases
 *
 * @test
 * @run
 */

function test(name) {
    try {
        load({ script: 'throw new Error()', name: name });
    } catch(e) {
        // normalize windows path separator to URL style
        var actual = e.getStackTrace()[0].fileName;
        if (actual !== name) {
            fail("expected file name to be " + name +
                 ", actually got file name " + actual);
        }
    }
}

// test inexistent file
test("com/oracle/node/sample.js");

// test filename without file:/ prefix
try {
    throw new Error();
} catch (e) {
    test(e.getStackTrace()[0].fileName.substring(6));
}

