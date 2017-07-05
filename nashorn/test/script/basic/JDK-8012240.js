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
 * JDK-8012240: Array.prototype.map.call({length: -1, get 0(){throw 0}}, function(){}).length does not throw error
 *
 * @test
 * @run
 */

var in_getter_for_0 = false;

try {
    Array.prototype.map.call(
        {
            length: -1,
            get 0() {
                in_getter_for_0 = true;
                throw 0;
            }
        },
    function(){}).length;
} catch (e) {
    if (e !== 0 || !in_getter_for_0) {
       fail("should have thrown error from getter for '0'th element");
    }
}
