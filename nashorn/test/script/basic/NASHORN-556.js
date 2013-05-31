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

/*
 * NASHORN-556: Need more tests to exercise code in jdk.nashorn.internal.runtime.array package.
 *
 * @test
 * @run
 */

function p() {
  var s = "";
  for each (var i in arguments) {
    s += ((i !== undefined) ? i : "") + ",";
  }
  s = s.length != 0 ? s.substr(0, s.length - 1) : s;
  print(s);
}

function assertEq(expected, actual) {
  if (actual !== expected && !(isNaN(actual) && isNaN(expected))) {
    throw "expected=" + expected + " actual=" + actual;
  }
}

function f1() {
  // (NoTypeArrayData)
  var empty = {};
  empty.length = 10;
  Java.to(empty);
  delete empty[0];
  Array.prototype.slice.call(empty, 0, 1);
  Array.prototype.pop.call(empty);
  Array.prototype.shift.call(empty);
  empty = {};
  empty[0] = eval("84") >>> 1; assertEq(42, empty[0]);
  empty = {};
  Array.prototype.unshift.call(empty, "x"); assertEq("x", empty[0]);
  empty = {};
  empty[0] = 8.4; assertEq(8.4, empty[0]);
}

function f2() {
  // DeletedArrayFilter
  var deleted = [,1,,2,,3,,4,,];
  assertEq(2, Java.to(deleted)[3]);
  assertEq(undefined, deleted.pop());
  assertEq(4, deleted.pop());
  deleted.unshift(5);
  p.apply(null, deleted);
  assertEq(5, deleted.shift());
  print(deleted.slice(0,3), deleted.slice(1,7));
  assertEq(1, deleted[3] >>> 1);
  deleted[3] = eval("84") >>> 1; assertEq(42, deleted[3]);
  p.apply(null, deleted);
}

function f3() {
  // DeletedRangeArrayFilter
  var delrange = [1,2,3,,,,,,,,,,];
  Java.to(delrange);
  delrange.unshift(4);
  p.apply(null, delrange);
  print(delrange.slice(1,3), delrange.slice(2,6));
  assertEq(4, delrange.shift());
}

function f4() {
  // NumberArrayData
  var num = [1.1,2.2,3.3,4.4,5.5];
  Java.to(num);
  assertEq(2, num[3] >>> 1);
  assertEq(5, num[4] | 0);
  assertEq(5.5, num.pop());
  num.unshift(13.37);
  print(num.slice(1,4));
  assertEq(13.37, num.shift());
  p.apply(null, num);
  num.length = 20;
  delete num[0];
  num[0] = eval("14") >>> 1;
}

function f5() {
  // ObjectArrayData
  var obj = [2,"two",3.14,"pi",14,"fourteen"];
  Java.to(obj);
  assertEq(-12.86, obj[2] - 16);
  assertEq(7, obj[4] >>> 1);
  obj.unshift("one");
  obj[0] = 1.3;
  obj[0] = eval("14") >>> 1;
  assertEq(7, obj.shift());
  p.apply(null, obj);
}

function f6() {
  // SparseArrayData
  var sparse = [9,8,7,6,5,4,3,2,1];
  sparse[0x76543210] = 84;
  assertEq(42, sparse[0x76543210] >>> 1);
  assertEq(42, sparse[0x76543210] - 42);
  assertEq(85, sparse[0x76543210] | 1);
  sparse[0x76543210] = 7.2;
  sparse[0x76543210] = eval("84") >>> 1;
  sparse.unshift(10);
  print(sparse.slice(0,12));
  print(sparse.slice(0x76543209, 0x76543213));
  assertEq(10, sparse.shift());
  assertEq(42, sparse.pop());
  sparse.length = 1024*1024;
  sparse.push(sparse.length);
  delete sparse[sparse.length-1];
  //print(Java.to(sparse).length);
  (function(){}).apply(null, sparse);
}

function f7() {
  // UndefinedArrayFilter
  var undef = [1,2,3,4,5,undefined,7,8,9,19];
  Java.to(undef);
  assertEq(4, undef[8] >>> 1);
  var tmp = undef[9] >>> 1;
  undef[8] = tmp;
  undef.unshift(21);
  print(undef.slice(0, 4), undef.slice(4, 5));
  assertEq(21, undef.shift());
  undef.push(20);
  assertEq(20, undef.pop());
  assertEq(19, undef.pop());
  p.apply(null, undef);
  undef.length = 20;
}

function f8() {
  // LongArrayData
  var j = Java.from(Java.to([23,37,42,86,47], "long[]"));
  Java.to(j);
  p.apply(null, j);
  assertEq(43, j[3] >>> 1);
  assertEq(36, j[4] - 11);
  j.unshift(eval("14") >>> 1);
  print(j.slice(0,4));
  assertEq(7, j.shift());
  assertEq(47, j.pop());
  j.push("asdf");
  j = Java.from(Java.to([23,37,42,86,47], "long[]"));
  j.length = 3;
  j[0] = 13;
  j = Java.from(Java.to([23,37,42,86,47], "long[]"));
  delete j[0];
  j = Java.from(Java.to([23,37,42,86,47], "long[]"));
  j.length = 20;
  j[0] = 13.37;
}

function f9() {
  // FrozenArrayFilter
  var a1 = [10,11,12,13,14,15];
  Object.freeze(a1);
  assertEq(true, Object.isFrozen(a1));
  Object.getOwnPropertyDescriptor(a1, 0);
  a1[1] = 1;
  a1[2] = eval("14") >>> 1;
  a1[3] = 3.14;
  a1[4] = "asdf";
  print(a1.slice(1,4));
  a1.length = 20;
}

function f10() {
  // SealedArrayFilter
  var a1 = [10,11,12,13,14,15];
  Object.seal(a1);
  assertEq(true, Object.isSealed(a1));
  Object.getOwnPropertyDescriptor(a1, 0);
  a1[1] = 1;
  a1[2] = eval("14") >>> 1;
  a1[3] = 3.14;
  a1[4] = "asdf";
  print(a1.slice(1,4));
  delete a1[0];
  a1.length = 20;
}

f1();
f2();
f3();
f4();
f5();
f6();
f7();
f8();
f9();
f10();

