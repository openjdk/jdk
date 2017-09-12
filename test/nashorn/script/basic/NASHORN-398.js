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
 * NASHORN-398 : Should not be able to set non-existing indexed property if object is not extensible
 *
 * @test
 * @run
 */

function funcNonStrict(array) {
    var obj = array? [] : {};
    Object.preventExtensions(obj);
    obj[0] = 12;
    if (obj[0] === 12) {
        fail("#1 obj[0] has been set");
    }

    if (obj.hasOwnProperty("0")) {
        fail("#2 has property '0'");
    }
}

funcNonStrict(true);
funcNonStrict(false);

function funcStrict(array) {
    'use strict';

    var obj = array? [] : {};
    Object.preventExtensions(obj);
    try {
        obj[0] = 12;
        fail("#3 should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#4 TypeError expected, got " + e);
        }
    }

    if (obj[0] === 12) {
        fail("#5 obj[0] has been set");
    }

    if (obj.hasOwnProperty("0")) {
        fail("has property '0'");
    }
}

funcStrict(true);
funcStrict(false);

