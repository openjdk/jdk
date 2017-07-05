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
 * NASHORN-215 : Exception parameter should be local to the catch block.
 *
 * @test
 * @run
 */

var exp = new Error("Error!");
try {
   throw exp;
} catch (e) {
   print("1 " + e.message);
   try {
      throw new Error("Nested error");
   } catch (e) {
      print("2 " + e.message);
   }
   print("3 " + e.message);
};

try {
   print(e);
   print("should not reach here");
} catch (e1) {
   print("4 success");
}

function f() {
    var x = 4;
    try {
       throw exp;
    } catch (e) {
       print("5 " + e.message);
       (function() {
           try {
              throw new Error("Nested error.");
           } catch (e) {
              try {
                print("6 " + e.message);
                throw new Error("error in catch");
              } catch (e) {
                 print("7 " + e.message);
              }
              (function() { print("8 " + e.message); })();
              print("9 " + e.message);
           }
          print("a " + e.message);
       })();
       print("b " + e.message);
    };
    return function() { return x; }();
}
f();

try {
  throw "asdf1";
} catch (ex) {
  (function() {
    var o = {};
    with (o) {
      print(ex);
    }
  })();
  try {
    throw "asdf2";
  } catch (ex) {
    (function() {
      var o = {};
      with (o) {
        print(ex);
        ex = "asdf3";
      }
    })();
    print(ex);
  }
  print(ex);
}
