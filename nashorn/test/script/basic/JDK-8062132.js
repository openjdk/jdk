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
 * 8062132: Nashorn incorrectly binds "this" for constructor created by another function
 *
 * @test
 * @run
 */

function subclass(parentCtor, proto) {
    function C() {
        parentCtor.call(this);
    }

    C.prototype = Object.create(parentCtor.prototype);

    for (var prop in proto) {
        if (proto.hasOwnProperty(prop)) {
            C.prototype[prop] = proto[prop];
        }
    }

    return C;
}

var Parent = function() {
    this.init();
};

Parent.prototype = {
    init: null
};

var Child1 = subclass(Parent, {
    prop1: 1,
    init: function() {
        print('child 1');
    }
});

var Child2 = subclass(Parent, {
    init: function() {
        print('child 2');
    }
});

var Child3 = subclass(Parent, {
    prop1: 1,
    init: function() {
        print('child 3');
    }
});

new Child1();
new Child2();
new Child3();
new Child1();
new Child2();
new Child3();
