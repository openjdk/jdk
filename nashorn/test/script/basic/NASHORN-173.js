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
 * NASHORN-173 : radix is ignored in Number.toString(radix).
 * @test
 * @run
 */

function printNumber(n) {
    print(n);
    print(n.toString(10));
    print(n.toString(undefined));
    print(n.toString(2));
    print(n.toString(5));
    print(n.toString(8));
    print(n.toString(12));
    print(n.toString(16));
    print(n.toString(21));
    print(n.toString(32));
    print(n.toString(36));
    try {
      n.toString(0);
    } catch (e) {
      print(e.name);
    }
    try {
      n.toString(37);
    } catch (e) {
      print(e.name);
    }
}

printNumber(0);
printNumber(1);
printNumber(-10);
printNumber(255);
printNumber(255.5);
printNumber(255.55);
printNumber(4988883874234);
printNumber(342234.2);
printNumber(-32423423.2342);
printNumber(837423.234765892);
printNumber(2342344660903453345345);
printNumber(-0.138789879520123);
printNumber(10.5);
printNumber(-10.22);
printNumber(-32.44);
printNumber(0.435235);
printNumber(0.5);
printNumber(5);
printNumber(7.5);
