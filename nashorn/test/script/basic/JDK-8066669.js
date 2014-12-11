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
 * JDK-8066669: dust.js performance regression caused by primitive field conversion
 *
 * @test
 * @run
 */

// Make sure index access on Java objects is working as expected.
var map = new java.util.HashMap();

map["foo"] = "bar";
map[1] = 2;
map[false] = true;
map[null] = 0;

print(map);

var keys =  map.keySet().iterator();

while(keys.hasNext()) {
    var key = keys.next();
    print(typeof key, key);
}

print(typeof map["foo"], map["foo"]);
print(typeof map[1], map[1]);
print(typeof map[false], map[false]);
print(typeof map[null], map[null]);

print(map.foo);
print(map.false);
print(map.null);

map.foo = "baz";
print(map);
