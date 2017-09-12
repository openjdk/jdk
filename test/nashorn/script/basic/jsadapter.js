/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
        Assert.assertTrue(this === obj);
        print("getter called for '" + name + "'"); return name;
    },

    __put__: function(name, value) {
        Assert.assertTrue(this === obj);
        print("setter called for '" + name + "' with " + value);
    },

    __call__: function(name, arg1, arg2) {
        Assert.assertTrue(this === obj);
        print("method '" + name + "' called with " + arg1 + ", " + arg2);
    },

    __new__: function(arg1, arg2) {
        Assert.assertTrue(this === obj);
        print("new with " + arg1 + ", " + arg2);
    },

    __getKeys__: function() {
        Assert.assertTrue(this === obj);
        print("__getKeys__ called");
        return [ "foo", "bar" ];
    },

    __getValues__: function() {
        Assert.assertTrue(this === obj);
        print("__getValues__ called");
        return [ "fooval", "barval" ];
    },

    __has__: function(name) {
        Assert.assertTrue(this === obj);
        print("__has__ called with '" + name + "'");
        return name == "js";
    },

    __delete__: function(name) {
        Assert.assertTrue(this === obj);
        print("__delete__ called with '" + name + "'");
        return true;
    },

    __preventExtensions__ : function() {
        Assert.assertTrue(this === obj);
        print("__preventExtensions__ called");
    },

    __freeze__ : function() {
        Assert.assertTrue(this === obj);
        print("__freeze__ called");

    },

    __isFrozen__ : function() {
        Assert.assertTrue(this === obj);
        print("__isFrozen__ called");
        return false;
    },

    __seal__ : function() {
        Assert.assertTrue(this === obj);
        print("__seal__ called");
    },

    __isSealed__ : function() {
        Assert.assertTrue(this === obj);
        print("__isSealed__ called");
        return false;
    },

    __isExtensible__ : function() {
        Assert.assertTrue(this === obj);
        print("__isExtensible__ called");
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

// calls __getKeys__
for (i in obj) {
    print(i);
}

// calls __getValues__
for each (i in obj) {
    print(i);
}

// calls __has__
var x = "foo" in obj;
print(x);

// calls __has__
var y = "js" in obj;
print(y);

// calls __delete__
print(delete obj.prop);

// call __get__ and __set__
print(obj["js"]);
obj["js"] = "javascript";
print(obj["javascript"]);

// call __isExtensible__, __isSealed__, __isFrozen__
print(Object.isExtensible(obj));
print(Object.isSealed(obj));
print(Object.isFrozen(obj));

// call __freeze__, __seal__, __preventExtensions__
Object.freeze(obj);
Object.seal(obj);
Object.preventExtensions(obj);
