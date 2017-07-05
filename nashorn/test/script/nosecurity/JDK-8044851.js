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
 * JDK-8044851: nashorn properties leak memory
 *
 * @test
 * @run
 * @option -Dnashorn.debug=true
 * @fork
 */

function printProperty(value, property) {
    print(value, property.getKey(), property.isSpill() ? "spill" : "field", property.getSlot());
}

var obj = {}, i, name;

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    obj[name] = 'a' + i;
    printProperty(obj[name], Debug.map(obj).findProperty(name));
}
print();

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    delete obj[name];
}

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    obj[name] = 'b' + i;
    printProperty(obj[name], Debug.map(obj).findProperty(name));
}
print();

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    Object.defineProperty(obj, name, {get: function() {return i;}, set: function(v) {}, configurable: true});
    printProperty(obj[name], Debug.map(obj).findProperty(name));
}
print();

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    delete obj[name];
}

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    obj[name] = 'c' + i;
    printProperty(obj[name], Debug.map(obj).findProperty(name));
}
print();

for (i = 7; i > -1; --i) {
    name = 'property' + i;
    delete obj[name];
}

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    obj[name] = 'd' + i;
    printProperty(obj[name], Debug.map(obj).findProperty(name));
}
print();

for (i = 0; i < 8; ++i) {
    name = 'property' + i;
    Object.defineProperty(obj, name, {get: function() {return i;}, set: function(v) {}});
    printProperty(obj[name], Debug.map(obj).findProperty(name));
}
