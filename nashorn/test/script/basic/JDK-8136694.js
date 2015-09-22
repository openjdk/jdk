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
 * JDK-8136694: Megemorphic scope access does not throw ReferenceError when property is missing
 *
 * @test
 * @fork
 * @option -Dnashorn.unstable.relink.threshold=16
 * @run
 */

function checkFoo() {
    try {
        // The 'foo' access becomes megamorphic
        foo;
        return true;
    } catch (e) {
        return false;
    }
}


// Similar check for 'with' blocks as well.
function checkFooInWith() {
    with({}) {
        try {
            // The 'foo' access becomes megamorphic
            foo;
            return true;
        } catch (e) {
            return false;
        }
    }
}

function loop(checker) {
    // LIMIT has to be more than the megamorphic threashold
    // set via @option in this test header!
    var LIMIT = 20;
    for (var i = 0; i < LIMIT; i++) {
        // make sure global has no "foo"
        delete foo;
        Assert.assertFalse(checker(), "Expected false in interation " + i);

        // now add 'foo' in global
        foo = 44;
        Assert.assertTrue(checker(), "Expected true in interation " + i);
    }
}


loop(checkFoo);
loop(checkFooInWith);
