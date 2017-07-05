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
 * JDK-8013874: Function argument's prototype seem cached and wrongly reused
 *
 * @test
 * @run
 */

function deepEqual(actual, expected) {
    print("deepEqual: " + (actual.prop === expected.prop) + ", prop: " + expected.prop);
}

var proto = {};
var other = {
    prop: 0
};

function NameBuilder(first, last) {
    this.first = first;
    this.last = last;
    return this;
}

NameBuilder.prototype = proto;

function NameBuilder2(first, last) {
    this.first = first;
    this.last = last;
    return this;
}

NameBuilder2.prototype = proto;

var nb1 = new NameBuilder('Ryan', 'Dahl');
var nb2 = new NameBuilder2('Ryan', 'Dahl');

print("In loader, nb1.prop === nb2.prop " + (nb1.prop === nb2.prop));
deepEqual(nb1, nb2);

NameBuilder2.prototype = other;
nb2 = new NameBuilder2('Ryan', 'Dahl');

print("In loader, nb1.prop === nb2.prop " + (nb1.prop === nb2.prop));
deepEqual(nb1, nb2);
