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
 * NASHORN-261 : All 'eval' calls assumed to be global builtin eval calls
 *
 * @test
 * @run
 */

// call eval with no args
if (eval() !== undefined) {
    fail("eval with no arg should return undefined");
}

// call eval with extra (ignored) args
function func() {
    'use strict';

    try {
        // we pass hidden args after first arg which is code. Make sure
        // those stay intact even when user passes extra args.
        // Example: strict mode flag is passed as hidden arg...

        eval("eval = 3", "hello", "world", "nashorn");
        fail("SyntaxError expected!");
    } catch (e) {
        if (! (e instanceof SyntaxError)) {
            fail("SyntaxError expected, got " + e);
        }
    }
}

func();

// try calling 'eval' -- but from with scope rather than builtin one
with ( {
    eval: function() {
        if (arguments.length != 1) {
            fail("arguments.length !== 1, it is " + arguments.length);
            for (i in arguments) { print(arguments[i]); }
        }
    }
}) {

    eval("hello");
}

// finally, overwrite builtin 'eval'
var reached = false;
eval = function() {
    reached = true;
    for (i in arguments) {
        print(arguments[i]);
    }
}

// pass no args to our overwritten eval
// the new eval should not print anything (no hidden args passed)
eval();

// make sure our modified eval was called
if (! reached) {
    fail("modified eval was not called");
}
