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
 * NASHORN-148 :  arguments element deletion/resurrection does not work as expected
 * 
 * @test
 * @run
 */


function func(x) { 
    print("func.x = " + x);
    print("func.arguments[0] = " + arguments[0]);

    arguments[0] = "world";

    print("func.x = " + x);
    print("func.arguments[0] = " + arguments[0]);

    x = "changed x";
    print("func.x = " + x);
    print("func.arguments[0] = " + arguments[0]);

    // delete arguments[0]
    delete arguments[0]; 
    print("func.x = " + x);
    print("func.arguments[0] = " + arguments[0]);

    // resurrect arguments[0]
    arguments[0] = 42;
    print("func.x = " + x);
    print("func.arguments[0] = " + arguments[0]);

    x = 33;
    print("func.x = " + x);
    print("func.arguments[0] = " + arguments[0]);
}

func(3.14);


// deletion and resurrection of different argument
function func2(x, y) {
   delete arguments[0];
   arguments[0] = 3434;
   print("func2.x = " + x);
   print("func2.arguments[0] = " + arguments[0]);

   print("func2.y = " + y);
   print("func2.arguments[1] = " + arguments[1]);

   y = 54;
   print("func2.y = " + y);
   print("func2.arguments[1] = " + arguments[1]);

   arguments[1] = 67;
   print("func2.y = " + y);
   print("func2.arguments[1] = " + arguments[1]);
}

func2(1, 3);

