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
 * Verify that JSAdapter works as expected.
 *
 * @test
 * @run
 */

var obj = new JSAdapter() {
    __get__: function(name) {
        print("getter called for '" + name + "'"); return name;
    },

    __put__: function(name, value) {
        print("setter called for '" + name + "' with " + value);
    },

    __call__: function(name, arg1, arg2) {
        print("method '" + name + "' called with " + arg1 + ", " + arg2);
    },

    __new__: function(arg1, arg2) {
        print("new with " + arg1 + ", " + arg2);
    },

    __getIds__: function() {
        print("__getIds__ called");
        return [ "foo", "bar" ];
    },

    __getValues__: function() {
        print("__getValues__ called");
        return [ "fooval", "barval" ];
    },

    __has__: function(name) {
        print("__has__ called with '" + name + "'");
        return name == "js";
    },

    __delete__: function(name) {
        print("__delete__ called with '" + name + "'");
        return true;
    }
};

// calls __get__
print(obj.foo);

// calls __put__
obj.foo = 33;

// calls __call__
obj.func("hello", "world");

// calls __new__
new obj("hey!", "it works!");

for (i in obj) {
    print(i);
}

for each (i in obj) {
    print(i);
}

var x = "foo" in obj;
print(x);

var y = "js" in obj;
print(y);

print(delete obj.prop);

print(obj["js"]);
obj["js"] = "javascript";
print(obj["javascript"]);
