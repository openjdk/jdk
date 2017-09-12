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
 * JDK-8010731: Nashorn exposes internal symbols such as __callee__, __scope__ to scripts
 *
 * @test
 * @run
 */

function checkCallee() {
     var x = arguments[0]; // force __callee__ (renamed as :callee)

     print(__callee__);
}

try {
    checkCallee();
    fail("Should have thrown ReferenceError for __callee__");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("ReferenceError expected, got " + e);
    }
}

function checkScope() {
    var x = 334;

    function inner() {
        var y = x * x;  // force __scope__ (renamed as :scope")
        print(__scope__);
    }

    inner();
}

try {
    checkScope();
    fail("Should have thrown ReferenceError for __scope__");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("ReferenceError expected, got " + e);
    }
}
