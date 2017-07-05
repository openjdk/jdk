/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8074693: Different instances of same function use same allocator map
 *
 * @test
 * @run
 */

var lib = {};

lib.mixin = function(target, source) {
    for (var p in source) {
        if (source.hasOwnProperty(p) && !target.hasOwnProperty(p)) {
            target.prototype[p] = source[p];
        }
    }
};

lib.declare = function(def) {
    var className = def.name;

    lib[className] = function() {
        this.init.apply(this, arguments);
    };

    lib.mixin(lib[className], def.members);
};


lib.declare({
    name: "ClassA",
    members: {
        init : function () {
            print("init A called");
        }
    }
});

lib.declare({
    name: "ClassB",
    members: {
        util : function () {
            print("util called")
        },
        init : function() {
            print("init B called");
        }
    }
});

var objA = new lib.ClassA();
var objB = new lib.ClassB();
