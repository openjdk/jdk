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
 * JDK-8023368: Instance __proto__ property should exist and be writable.
 *
 * @test
 * @run
 */

// check Object.setPrototypeOf extension rather than using __proto__

// function to force same callsites
function check(obj) {
    print(obj.func());
    print(obj.x);
    print(obj.toString());
}

function Func() {
}

Func.prototype.func = function() {
    return "Func.prototype.func";
}

Func.prototype.x = "hello";

var obj = new Func();
var obj2 = Object.create(obj);

// check direct and indirect __proto__ change
check(obj);
check(obj2);
Object.setPrototypeOf(obj, {
   func: function() {
        return "obj.__proto__.func @ " + __LINE__;
   },
   x: 344
});

check(obj);
check(obj2);

// check indirect (1 and 2 levels) __proto__ function change
Object.setPrototypeOf(Object.getPrototypeOf(obj), {
    toString: function() {
        return "new object.toString";
    }
});

check(obj);
check(obj2);
