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
 * NASHORN-275 : strict eval receives wrong 'this' object
 *
 * @test
 * @run
 */

var global = this;

function func() {
    "use strict";

    if (eval("this") === global) {
        fail("#1 strict eval gets 'global' as 'this'");
    }

    if (eval ("typeof this") !== 'undefined') {
        fail("#2 typeof this is not undefined in strict eval");
    }
}

func();

var global = this;
if (eval("\"use strict\";this") !== this) {
    fail("#3 strict mode eval receives wrong 'this'");
}

var obj = {
  func: function() {
      if (eval('"use strict"; this') !== obj) {
          fail("#4 strict mode eval receives wrong 'this'");
      }
  }
};

obj.func();

function func2() {
   'use strict';
   return eval('this');
}

func2.call(null);
if (func2.call(null) !== null) {
    fail("#5 strict mode eval receives wrong 'this'");
}

if (func2.call('hello') !== 'hello') {
    fail("#6 strict mode eval receives wrong 'this'");
}

// indirect eval
var my_eval = eval;
if (my_eval("'use strict'; this; ") !== this) {
    fail("#7 strict mode eval receives wrong 'this'");
}

