/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8048071: eval within 'with' statement does not use correct scope if with scope expression has a copy of eval
 *
 * @test
 * @run
 */

function func() {
   var x = 1;
   with ({ eval: this.eval }) {
      eval("var x = 23");
   }

   return x;
}

print(func());
print("typeof x? " + typeof x);

print((function(global){
    var x = 1;
    with(global) {
        eval("eval('var x=0')");
    }
    return x;
})(this));
print("typeof x? " + typeof x);

print((function(global){
   var x = 1;
   with({eval:  global.eval}) {
       eval("eval('var x=0')");
   }
   return x;
})(this));
print("typeof x? " + typeof x);

// not-builtin eval cases

(function () {
   function eval(str) {
      print("local eval called: " + str);
      print(this);
   }

   with({}) {
     eval("hello");
   }
})();

(function () {
   with({
    eval:function(str) {
       print("with's eval called: " + str);
       print("this = " + this);
       print("this.foo = " + this.foo);
    },
    foo: 42
   }) {
     eval("hello")
   }
})();
