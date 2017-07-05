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
 * JDK-8012191: noSuchProperty can't cope with vararg functions
 *
 * @test
 * @run
 */

// ClassCastException: Cannot cast java.lang.String to [Ljava.lang.Object; 
__noSuchProperty__ = function() {
    print("obj.__noSuchProperty__ invoked for " + arguments[0]);
}

nonExistent;

// related issue was seen in JSAdapter __get__, __set__ too
var obj = new JSAdapter() {
    __put__: function() {
        print("JSAdapter.__put__");
        print(arguments[0]);
        print(arguments[1]);
    },

    __get__: function() {
        print("JSAdapter.__get__");
        print(arguments[0]);
    }
};

obj.x = 343;
obj.y
