/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8087211: Indirect evals should be strict with -strict option
 *
 * @test
 * @run
 * @option -strict
 */

var global = this;

try {
    // indirect eval call.
    global.eval("x = 34;");
    throw new Error("should have thrown ReferenceError");
} catch (e if e instanceof ReferenceError) {
}


function teststrict() {
    "use strict";
    // strict caller, indirect eval.
    global.eval('public = 1;');
}

try {
    teststrict();
    throw new Error("should have thrown SyntaxError");
} catch (e if e instanceof SyntaxError) {
}

function testnonstrict() {
    // non strict caller, indirect eval.
    global.eval('public = 1;');
}

try {
    testnonstrict();
    throw new Error("should have thrown SyntaxError");
} catch (e if e instanceof SyntaxError) {
}
