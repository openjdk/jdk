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
 * JDK-8047959: bindings created for declarations in eval code are not mutable
 *
 * @test
 * @run
 */

eval("var x=10;");
print('delete x? ' + delete x);
print('typeof x = ' + typeof x);

eval("function f() {}");
print('delete f? ' + delete f);
print('typeof f = ' + typeof f);

var foo = 223;
print('delete foo? ' + delete foo);
print('typeof foo = ' + typeof foo);

function func() {}
print('delete func? ' + delete func);
print('typeof func = ' + typeof func);

eval("var foo = 33;");
print("delete foo? " + delete foo);
print("typeof foo? " + typeof foo);
print("foo = " + foo);

var x = "global";
(function(){
    eval("var x='local'");
    print("x in function = "+ x);
    print("delete x? = " + delete x);
    print("x after delete = " + x);
})();
print("x = " + x);
