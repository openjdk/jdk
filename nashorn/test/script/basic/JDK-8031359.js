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
 * JDK-8031359: Invocable.getInterface() works incorrectly if interface has default methods
 *
 * @test
 * @run
 */

var func = new java.util.function.Function() {
    apply: function(arg) {
        print("func called with " + arg);
        return arg.toUpperCase();
    }
};

// Function.andThen is a default method
func.andThen(func)("hello");

// Function.compose is another default method
func.compose(new java.util.function.Function() {
    apply: function(arg) {
        print("compose called with " + arg);
        return arg.charAt(0);
    }
})("hello");

var func2 = new java.util.function.Function() {
    apply: function(arg) {
        print("I am func2: " + arg);
        return arg;
    },

    andThen: function(func) {
        print("This is my andThen!");
        return func;
    }
};

func2.apply("hello");
func2.andThen(func);
