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
 * Make sure that there are no-enumerable items on standard objects. 
 *
 * @test
 * @run
 */

function enumerate(obj, name) {
    var count = 0;
    for (i in obj) {
        print(i);
        count++;
    }
    print("Enumerable items in " + name + " = " + count);
}

enumerate(Array.prototype, "Array.prototype");
enumerate(Array, "Array");
enumerate(Boolean.prototype, "Boolean.prototype");
enumerate(Boolean, "Boolean");
enumerate(Date.prototype, "Date.prototype");
enumerate(Date, "Date");
enumerate(Function.prototype, "Function.prototype");
enumerate(Function, "Function");
enumerate(JSON, "JSON");
enumerate(Math, "Math");
enumerate(Number.prototype, "Number.prototype");
enumerate(Number, "Number");
enumerate(Object.prototype, "Object.prototype");
enumerate(Object, "Object");
enumerate(String.prototype, "String.prototype");
enumerate(String, "String");
