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

/*
 * Test that JSAdapter linking works as expected under receiver and/or
 * adaptee change.
 *
 * @test
 * @run
 */

var js1 = new JSAdapter() {
    __get__: function(name) {
        Assert.assertTrue(this === js1);
        return "js1->" + name;
    }
};

var js2 = new JSAdapter() {
    __get__: function(name) {
        Assert.assertTrue(this === js2);
        return "js2->" + name;
    }
};

var js3 = new JSAdapter() {
    __get__: function(name) {
        Assert.assertTrue(this === js3);
        return "js3->" + name;
    }
};

var handler = {
    __get__: function(name) {
        return "handler->" + name;
    }
};

var js4 = new JSAdapter(handler);

var arr = [ js1, js2, js3, js4, js1, js2, js3, js4 ];
for (i in arr) {
    if (i == 7) {
        handler.__get__ = function(name) {
            return "all-new-handler->" + name;
        }
    }
    print(arr[i].foo);
}

