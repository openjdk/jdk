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
 * Verify "length" property of functions is arity.
 * Verify that function can be called by apply and call.
 *
 * @test
 * @run
 */

function func(x, y) { print(x); print(y); }

print(func.call);
print("func.call.length = " + func.call.length);

func.call(this, "hello, ", "world");
func.apply(this, [ "hello, " , "world" ]);
func("hello, ", "world");

// extension: you can pass java List to apply

var list = new java.util.ArrayList();
list.add("invokedynamic");
list.add(" is great!");
func.apply(this, list);

print("func.apply.length = " + func.apply.length);

function func2() {
    print("I am func2");
}

func2.apply(this);

func.apply(this);
