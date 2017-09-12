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
 * Basic checks for Object.preventExtensible.
 *
 * @test
 * @run
 */

// after preventExtenssions, we can't add new properties but
// can modifiy existing properties.
var obj = { num: 45.0 };

print("extensible? " + Object.isExtensible(obj));
Object.preventExtensions(obj);
print("extensible? " + Object.isExtensible(obj));

obj['bar'] = "hello";
print(obj.bar);
obj.num = Math.PI;
print(obj.num);

obj.foo = 55;
print(obj.foo);
obj.num = Math.E;
print(obj.num);
