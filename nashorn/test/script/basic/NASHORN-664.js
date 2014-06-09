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
 * NASHORN-664 : Can not set nashorn extension properties such as fileName, lineNumber and columnNumber of Error objects
 *
 * @test
 * @run
 */

try {
    throw new Error();
} catch (e) {
    e.fileName = "foo";
    if (e.fileName !== 'foo') {
        fail("can not set e.fileName");
    }
    e.lineNumber = 100;
    if (e.lineNumber !== 100) {
        fail("can not set e.lineNumber");
    }
    e.columnNumber = 33;
    if (e.columnNumber !== 33) {
        fail("can not set e.columnNumber");
    }
    e.stack = "mystack";
    if (e.stack !== 'mystack') {
        fail("can not set e.stack");
    }
}
