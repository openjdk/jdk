/*
* Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * apply_to_call1.js - do one apply to call specialization, then override, apply and make sure it reverts (i.e. stops
 * calling call)
 *
 * @test
 * @run
 */

print("start");

var x = {
    a : 0,
    b : 0,
    c : 0,
    initialize : function(x,y,z) {
    this.a = x;
    this.b = y;
    this.c = z;
    }
};

function test() {
    x.initialize.apply(x, arguments);
}

test(4711,23,17);
print(x.a);
print(x.b);
print(x.c);

print("Overwriting apply now");

x.initialize = {
    apply : function() {
    for (var i=0;i<arguments.length;i++) {
        print("I am not who you think " + arguments[i]);
    }
    x.a = arguments[1][0];
    }
};

test(4712);
print(x.a);
