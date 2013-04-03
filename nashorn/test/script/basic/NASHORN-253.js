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
 * NASHORN-253 :
 *
 * @test
 * @run
 */

try {
    foo();
    fail("#1 expected error!!");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("#2 expected ReferenceError, got " + e);
    }
}

try {
    this.foo();
    fail("#3 expected error!!");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#4 expected ReferenceError, got " + e);
    }
}

try {
    new foo();
    fail("#5 expected error!!");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("#6 expected ReferenceError, got " + e);
    }
}

try {
    new this.foo();
    fail("#7 expected error!!");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#8 expected ReferenceError, got " + e);
    }
}

try {
    var x = foo;
    fail("#9 expected error!!");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("#10 expected ReferenceError, got " + e);
    }
}
