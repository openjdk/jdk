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
 * NASHORN-385 : JSAdapter throws AssertionError when adaptee does not define hook methods like __get__, __put__ etc.
 *
 * @test
 * @run
 */

var proto = {};
var o = new JSAdapter(proto);

function checkGetter() {
    print("o.foo => " + o.foo);
}

function checkSetter() {
    o.bar = 44;
}

function checkCall() {
    try {
        o.func();
    } catch (e) {
        print(e);
    }
}

checkGetter();
checkSetter();
checkCall();

proto.__get__ = function(name) {
    print("in __get__: " + name);
    return name;
};

proto.__put__ = function(name) {
    print("in __put__: " + name);
}

proto.__call__ = function(name) {
    print("in __call__: " + name);
}

checkGetter();
checkSetter();
checkCall();
