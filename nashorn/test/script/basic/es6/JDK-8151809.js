/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8151809: ES6 Map/Set insertion with existing keys changes iteration order
 *
 * @test
 * @run
 * @option --language=es6
 */

function assertSetIteratorResult(result, expectedDone, expectedValue) {
    Assert.assertEquals(result.done, expectedDone);
    Assert.assertEquals(result.value, expectedValue);
}

function assertMapIteratorResult(result, expectedDone, expectedKey, expectedValue) {
    Assert.assertEquals(result.done, expectedDone);
    if (expectedDone) {
        Assert.assertEquals(result.value, undefined);
    } else {
        Assert.assertEquals(result.value[0], expectedKey);
        Assert.assertEquals(result.value[1], expectedValue);
    }
}

let set = new Set(["foo", "bar", "foo"]);
let iter = set[Symbol.iterator]();
assertSetIteratorResult(iter.next(), false, "foo");
assertSetIteratorResult(iter.next(), false, "bar");
assertSetIteratorResult(iter.next(), true);

set.add ("foo");
iter = set[Symbol.iterator]();
assertSetIteratorResult(iter.next(), false, "foo", false);
assertSetIteratorResult(iter.next(), false, "bar", false);
assertSetIteratorResult(iter.next(), true);

set.delete("foo");
set.add ("foo");
assertSetIteratorResult(iter.next(), true);
iter = set[Symbol.iterator]();
assertSetIteratorResult(iter.next(), false, "bar", false);
assertSetIteratorResult(iter.next(), false, "foo", false);
assertSetIteratorResult(iter.next(), true);


let map = new Map([["foo", 1], ["bar", 2], ["foo", 3]]);
iter = map[Symbol.iterator]();
assertMapIteratorResult(iter.next(), false, "foo", 3);
assertMapIteratorResult(iter.next(), false, "bar", 2);
assertMapIteratorResult(iter.next(), true);


map.set("foo", 4);
iter = map[Symbol.iterator]();
assertMapIteratorResult(iter.next(), false, "foo", 4);
assertMapIteratorResult(iter.next(), false, "bar", 2);
assertMapIteratorResult(iter.next(), true);

map.delete("foo");
map.set("foo", 5);
assertMapIteratorResult(iter.next(), true);
iter = map[Symbol.iterator]();
assertMapIteratorResult(iter.next(), false, "bar", 2);
assertMapIteratorResult(iter.next(), false, "foo", 5);
assertMapIteratorResult(iter.next(), true);
