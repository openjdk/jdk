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
 * JDK-8134609: Allow constructors with same prototoype map to share the allocator map
 *
 * @test
 * @run
 * @fork
 * @option -Dnashorn.debug
 */

function createProto(members) {
    function P() {
        for (var id in members) {
            if (members.hasOwnProperty(id)) {
                this[id] = members[id];
            }
        }
        return this;
    }
    return new P();
}

function createSubclass(prototype, members) {
    function C() {
        for (var id in members) {
            if (members.hasOwnProperty(id)) {
                this[id] = members[id];
            }
        }
        return this;
    }

    C.prototype = prototype;

    return new C();
}

function assertP1(object, value) {
    Assert.assertTrue(object.p1 === value);
}

// First prototype will have non-shared proto-map. Second and third will be shared.
var proto0 = createProto({p1: 0, p2: 1});
var proto1 = createProto({p1: 1, p2: 2});
var proto2 = createProto({p1: 2, p2: 3});

Assert.assertTrue(Debug.map(proto1) === Debug.map(proto2));

assertP1(proto1, 1);
assertP1(proto2, 2);

// First instantiation will have a non-shared prototype map, from the second one
// maps will be shared until a different proto map comes along.
var child0 = createSubclass(proto1, {c1: 1, c2: 2});
var child1 = createSubclass(proto2, {c1: 2, c2: 3});
var child2 = createSubclass(proto1, {c1: 3, c2: 4});
var child3 = createSubclass(proto2, {c1: 1, c2: 2});
var child4 = createSubclass(proto0, {c1: 3, c2: 2});

Assert.assertTrue(Debug.map(child1) === Debug.map(child2));
Assert.assertTrue(Debug.map(child1) === Debug.map(child3));
Assert.assertTrue(Debug.map(child3) !== Debug.map(child4));

assertP1(child1, 2);
assertP1(child2, 1);
assertP1(child3, 2);
assertP1(child4, 0);

Assert.assertTrue(delete proto2.p1);

assertP1(child3, undefined);
assertP1(child2, 1);
Assert.assertTrue(Debug.map(child1) !== Debug.map(child3));
