/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8044750: megamorphic getter for scope objects does not call __noSuchProperty__ hook
 *
 * @test
 * @fork
 * @option -Dnashorn.unstable.relink.threshold=16
 * @run
 */

__noSuchProperty__ = function(name) {
    return 1;
}

function func(obj) {
    with(obj) {
        // this "foo" getter site becomes megamorphic
        // due to different 'with' scope objects.
        foo;
    }
}

var LIMIT = 20; // should be more than megamorphic threshold set via @option

for (var i = 0; i < LIMIT; i++) {
    var obj = {};
    obj.foo = i;
    obj[i] = i;
    func(obj);
}

// pass a 'with' scope object that does not have 'foo'.
// callsite inside func should see __noSuchProperty__
// hook on global scope object.
func({});

function checkFoo() {
    with({}) {
        try {
            foo;
            return true;
        } catch (e) {
            return false;
        }
    }
}

var oldNoSuchProperty = this.__noSuchProperty__;
delete this.__noSuchProperty__;

// keep deleting/restorting __noSuchProperty__ alternatively
// to make "foo" access in checkFoo function megamorphic!

for (var i = 0; i < LIMIT; i++) {
    // no __noSuchProperty__ and 'with' scope object has no 'foo'
    delete __noSuchProperty__;
    Assert.assertFalse(checkFoo(), "Expected false in iteration " + i);

    // __noSuchProperty__ is exists but 'with' scope object has no 'foo'
    this.__noSuchProperty__ = oldNoSuchProperty;
    Assert.assertTrue(checkFoo(), "Expected true in iteration " + i);
}
