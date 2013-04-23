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
 * NASHORN-192 :  User defined property setter or getter with extra arguments or lesser argument fails by throwing exception
 * 
 * @test
 * @run
 */

var obj = {};
Object.defineProperty(obj, "foo", {
    get: function(obj) {
        return 'hello';
    }
});

try {
    if (obj.foo !== 'hello') {
        fail("getter with extra argument does not work");
    }
} catch (e) {
    fail("failed to get with " + e);
}

Object.defineProperty(obj, "bar", {
    set: function() {
        // do nothing
    }
});

try {
    obj.bar = 33;
} catch (e) {
    fail("failed to set with " + e);
}

Object.defineProperty(obj, "prop", {
    set: function(obj1, obj2, obj3) {
        this.val = obj1;
    }
}); 

try {
    obj.prop = 33;
    if (obj.val !== 33) {
        fail("set with extra argument does not work");
    }
} catch (e) {
    fail("failed to set with " + e);
}
