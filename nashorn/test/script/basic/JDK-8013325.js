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
 * JDK-8013325: function named 'arguments' should still access arguments object within itself.
 * Its parent should however see the function and not an arguments object.
 *
 * @test
 * @run
 */

function x() {
  // x doesn't see an arguments object as it has a nested function with that name
  // so it'll invoke the function.
  arguments("a", "b", "c");
  
  function arguments(x, y, z) {
      // The function 'arguments' OTOH can't see itself; if it uses the 
      // identifier 'arguments', it'll see its own arguments object.
      print(arguments)
      print(x + " " + y + " " + z)
  }
}
x()
