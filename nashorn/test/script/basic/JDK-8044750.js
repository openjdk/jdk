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

for (var i = 0; i < 20; i++) {
    var obj = {};
    obj.foo = i;
    obj[i] = i;
    func(obj);
}

// pass a 'with' scope object that does not have 'foo'.
// callsite inside func should see __noSuchProperty__
// hook on global scope object.
func({});
