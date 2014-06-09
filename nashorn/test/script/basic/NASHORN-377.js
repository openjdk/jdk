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
 * NASHORN-377: Typed arrays.
 *
 * @test
 * @run
 */

var types = [Int8Array,Uint8Array,Uint8ClampedArray,Int16Array,Uint16Array,Int32Array,Uint32Array,Float32Array,Float64Array];

//---------------------------------------------------------------------------
// utility functions
//---------------------------------------------------------------------------
function tohex(d, w) {
  var hex = Number(d).toString(16);
  var pad = (w ? w : 8) - hex.length;
  hex = "00000000".substr(0, pad) + hex;
  return hex;
}

function arrstr(a, n, w) {
  var s = "";
  if (typeof n == "undefined") n = a.length;
  if (typeof w == "undefined") w = a.constructor.BYTES_PER_ELEMENT * 2;
  for (var i = 0; i < n; i++) {
    s += tohex(a[i], w);
  }
  return s;
}
function bufstr(b) {
  if (b.buffer !== undefined) {
    b = b.buffer;
  }
  return arrstr(new Uint8Array(b));
}

function assertFail(f) {
  try {
    f();
  } catch (e) {
    //print(e);
    return;
  }
  throw "assertion failed: expected exception";
}

function assertTrue(f) {
  if (f() !== true) throw "assertion failed: " + f;
}

function isUndefined(x) {
  return typeof x === "undefined";
}

function fillArray(a, start) {
  if (typeof start == "undefined") start = 1;
  for (var i = 0; i < a.length; i++) {
    a[i] = i + start;
  }
  return a;
}

//---------------------------------------------------------------------------
// tests
//---------------------------------------------------------------------------
(function() {
  var b = new ArrayBuffer(8);
  var i8 = new Int8Array(b);
  print(i8.buffer.byteLength, b.byteLength, i8.buffer === b, b.length);
  print(b, i8.buffer, i8);
})();

(function test_attributes() {
  var b = new ArrayBuffer(8);
  for (var i in types) {
    var x = new types[i](b);
    print(x.byteOffset, x.byteLength, x.length, x.constructor.BYTES_PER_ELEMENT);
    assertTrue(function(){ return x.constructor === types[i] });
  }
})();

(function() {
  var b = new ArrayBuffer(8);
  var i8 = new Int8Array(b);
  fillArray(i8, 0x70);

  var i8_2 = new Int8Array(b, 2);
  var i8_2_4 = new Uint8Array(b, 2, 4);

  i8_2_4[3] = 0x80;

  print(arrstr(i8, 8, 2)  + " " + bufstr(i8));
  print(arrstr(i8_2, 6)   + " " + i8_2.byteOffset   + " " + i8_2.byteLength);
  print(arrstr(i8_2_4, 4) + " " + i8_2_4.byteOffset + " " + i8_2_4.byteLength);

  var i8_1_5 = i8.subarray(1, 5);
  i8_2_4.subarray(1, 5);
  print(arrstr(i8_1_5, 4) + " " + i8_1_5.byteOffset + " " + i8_1_5.byteLength);

  print(bufstr(b.slice(1,7)));
})();

(function() {
  var b = new ArrayBuffer(8);
  fillArray(new Int8Array(b), 0x70);
  new Int8Array(b)[5] = 0x80;

  var i32 = new Int32Array(b);
  var u32 = new Uint32Array(b);
  print(arrstr(i32), i32[0], i32[1]);
  i32[1] = 0xfefdfcfb;
  print(arrstr(i32), i32[0], i32[1]);
  print(arrstr(u32), u32[0], u32[1]);

  var pi = 3.1415926;
  var f32 = new Float32Array(b);
  var f64 = new Float64Array(b);
  f32[0] = pi;
  print(bufstr(b), f32.length);
  f64[0] = pi;
  print(bufstr(b), f64.length);
  print(arrstr(u32), u32[0], u32[1]);

  var d = new Int32Array(3);
  d.set(i32,1);
  print(bufstr(d));

  var s = new Int16Array(b);
  var t = new Uint16Array(b);
  print(arrstr(s), arrstr(t));
  s[0] = -1; s[1] = 0x80;
  print(arrstr(s), arrstr(t));
})();

(function enumerate_properties() {
  var i8 = new Int8Array(new ArrayBuffer(8));
  var s = ""; for (var i in i8) { s += i + " "; } print(s.trim());
})();

// check that ScriptObject fallback is still working
// DISABLED because correct behavior is unclear
(function() {
  // NB: firefox will never set any out-of-bounds or non-array values although it does get both from prototype.
  var z = new Uint8Array(4);
  z["asdf"] = "asdf"; print(z["asdf"]);
  z[0x100000000] = "asdf"; print(z[0x100000000]);
  z[-1] = "asdf"; print(z[-1]);

  // v8 and nashorn disagree on out-of-bounds uint32 indices: v8 won't go to the prototype.
  z[0xf0000000] = "asdf"; print(z[0xf0000000]);
  z[0xffffffff] = "asdf"; print(z[0xffffffff]);
  z[0x70000000] = "asdf"; print(z[0x70000000]);

  // this will work in firefox and nashorn (not in v8).
  Uint8Array.prototype[4] = "asdf"; print(z[4]);
});

(function test_exceptions() {
  assertFail(function() { new Int32Array(new ArrayBuffer(7)); });
  assertFail(function() { new Int32Array(new ArrayBuffer(8), 0, 4); });
  assertFail(function() { new Int32Array(new ArrayBuffer(8),-1, 2); });
  assertFail(function() { new Int32Array(new ArrayBuffer(8), 0,-1); });
})();

(function test_subarray() {
  var x = fillArray(new Int8Array(8));
  print(arrstr(x));
  print("subarray(2,4)=" + arrstr(x.subarray(2, 4)), "subarray(-6,-4)=" + arrstr(x.subarray(-6, -4))); // negative index refers from the end of the array
  print(arrstr(x.subarray(-10, -2))); // negative index clamped to 0
  assertTrue(function(){ return arrstr(x.subarray(6, 4)) === ""; }); // negative length clamped to 0
  print(arrstr(x.subarray(1,-1).subarray(1,-1)), arrstr(x.subarray(1,-1).subarray(1,-1).subarray(1,-1))); // subarray of subarray
})();

(function test_slice() {
  var b = new ArrayBuffer(16);
  fillArray(new Int8Array(b));
  print(bufstr(b));
  print("slice(4,8)=" + bufstr(b.slice(4, 8)), "slice(-8,-4)=" + bufstr(b.slice(-8, -4))); // negative index refers from the end of the array
  print(bufstr(b.slice(-20, -4))); // negative index clamped to 0
  assertTrue(function(){ return bufstr(b.slice(8, 4)) === ""; }); // negative length clamped to 0
  print(arrstr(new Int16Array(b.slice(1,-1).slice(2,-1).slice(1,-2).slice(1,-1)))); // slice of slice
})();

(function test_clamped() {
  var a = new Uint8ClampedArray(10);
  a[0] = -17;       // clamped to 0
  a[1] = 4711;      // clamped to 255
  a[2] = 17.5;      // clamped to 18
  a[3] = 16.5;      // clamped to 16
  a[4] = 255.9;     // clamped to 255
  a[5] = Infinity;  // clamped to 255
  a[6] = -Infinity; // clamped to 0
  a[7] = NaN;       // 0
  assertTrue(function(){ return a[0] === 0 && a[1] === 255 && a[2] === 18 && a[3] === 16 && a[4] === 255 && a[5] === 255 && a[6] === 0 && a[7] === 0; });
})();

(function test_out_of_bounds() {
  var a = new Int32Array(10);
  a[10] = 10;
  a[100] = 100;
  a[1000] = 1000;
  assertTrue(function(){ return isUndefined(a[10]) && isUndefined(a[11]) && isUndefined(a[100]) && isUndefined(a[123]) && isUndefined(a[1000]); });
})();

