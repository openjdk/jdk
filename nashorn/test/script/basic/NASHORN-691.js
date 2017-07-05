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
 * NASHORN-691 :  Get rid of function id guard
 *
 * @test
 * @run
 */

function func(x) {
    return function(name) {
        return x;
    }
};

var delegate = {
   __get__: func(33)
};

var x = new JSAdapter(delegate);

function f(o) {
   print('o.foo = ' + o.foo);
}

f(x);

// now change __get__
delegate.__get__ = func(44);
f(x);

var obj = {};
obj.__noSuchProperty__ = func(3);
f(obj);
obj.__noSuchProperty__ = func("hello");
f(obj);
