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
 * NASHORN-217 :  getter and setter name in object literal could be string, number, reserved word as well
 *
 * @test
 * @run
 */

var obj = {
   get class() { return 44; },
   get in() { return 'hello' },
   get typeof() { return false; },
   set class(x) { print('class ' + x); },
   set in(x) { print('in ' + x); },
   set typeof(x) { print('typeof ' + x); },
   get 14() { return 14; },
   set 14(x) { print("14 " + x); },
   get 'foo prop'() { return 'foo prop value' },
   set 'foo prop'(x) { print('foo prop ' + x); }
};

print('obj.class ' + obj.class);
print('obj.in ' + obj.in);
print('obj.typeof ' + obj.typeof);
print('obj[14] ' + obj[14]);
print("obj['foo prop'] " + obj['foo prop']);

obj.class = 3;
obj.in = 'world';
obj.typeof = 34;
obj[14] = 34;
obj['foo prop'] = 'new foo prop';
