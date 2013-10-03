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
 * JDK-8024255: When a keyword is used as object property name, the property can not be deleted
 *
 * @test
 * @run
 */

function check(obj, name) {
    var desc = Object.getOwnPropertyDescriptor(obj, name);
    if (! desc.configurable) {
        fail("Property " + name + " is not configurable");
    }

    if (! (delete obj[name])) {
        fail("Property " + name + " can not be deleted");
    }
}

var obj = { 
    default: 344,
    in: 'hello', 
    if: false,
    class: 4.223
}

for (var p in obj) {
    check(obj, p);
}
