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
 * NASHORN-266 : Strict mode functions see scope call 'this' as undefined
 *
 * @test
 * @run
 */


function func() {
    'use strict';
    return this;
}

if (func() !== undefined) {
    fail("#1 'this' should be undefined");
}

if (this.func() !== this) {
    fail("#2 'this' should be global");
}

function func2() {
    return this;
}

if (func2() === undefined) {
    fail("#3 'this' should not be undefined");
}

if (this.func2() !== this) {
    fail("#4 'this' should be global");
}

// strict calling non-strict
function func3() {
    'use strict';

    if (func2() === undefined) {
        fail("#5 'this' should not be undefined");
    }
}

func3();
