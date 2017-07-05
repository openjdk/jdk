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
 * JDK-8051778: support bind on all Nashorn callables
 *
 * @test
 * @run
 */

var bind = Function.prototype.bind;

// Bind a POJO method
var l = new java.util.ArrayList();
var l_add_foo = bind.call(l.add, l, "foo");
l_add_foo();
print("l=" + l);

// Bind a BoundCallable
var l_add = bind.call(l.add, l);
var l_add_foo2 = bind.call(l_add, null, "foo2");
l_add_foo2();
print("l=" + l);

// Bind a POJO method retrieved from one instance to a different but 
// compatible instance.
var l2 = new java.util.ArrayList();
var l2_size = bind.call(l.size, l2);
print("l2_size()=" + l2_size());

// Bind a Java type object (used as a constructor).
var construct_two = bind.call(java.lang.Integer, null, 2);
print("Bound Integer(2) constructor: " + new construct_two())

// Bind a @FunctionalInterface proxying to an object literal. NOTE: the 
// expected value of this.a is always "original" and never "bound". This
// might seem counterintuitive, but we are not binding the apply()
// function of the object literal that defines the BiFunction behaviour,
// we are binding the SAM proxy object instead, and it is always
// forwarding to the apply() function with "this" set to the object
// literal. Basically, binding "this" for SAM proxies is useless; only
// binding arguments makes sense.
var f1 = new java.util.function.BiFunction() {
    apply: function(x, y) {
        return "BiFunction with literal: " + this.a + ", " + x + ", " + y;
    },
    a: "unbound"
};
print((bind.call(f1, {a: "bound"}))(1, 2))
print((bind.call(f1, {a: "bound"}, 3))(4))
print((bind.call(f1, {a: "bound"}, 5, 6))())

// Bind a @FunctionalInterface proxying to a function. With the same 
// reasoning as above (binding the proxy vs. binding the JS function), 
// the value of this.a will always be undefined, and never "bound".
var f2 = new java.util.function.BiFunction(
    function(x, y) {
        return "BiFunction with function: " + this.a + ", " + x + ", " + y;
    }
);
print((bind.call(f2, {a: "bound"}))(7, 8))
print((bind.call(f2, {a: "bound"}, 9))(10))
print((bind.call(f2, {a: "bound"}, 11, 12))())
