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
 * JDK-8071928: Instance properties with getters returning wrong values
 *
 * @test
 * @run
 */


var types = {};

function Type() {}

Type.prototype.getName = function() {
    return this._name;
};

function defineType(init) {
    return Object.create(Type.prototype, {
        _name: { get: function() { return init.name; } }
    });
}

types.A = defineType({ name: 'A' });
types.B = defineType({ name: 'B' });
types.C = defineType({ name: 'C' });
types.D = defineType({ name: 'D' });

var keys = Object.keys(types);
for (var i = 0; i < keys.length; i++) {
    var t = types[keys[i]];
    if (t.getName() != keys[i]) {
        throw 'wrong name for ' + keys[i] + ': ' + t.getName();
    }
}
