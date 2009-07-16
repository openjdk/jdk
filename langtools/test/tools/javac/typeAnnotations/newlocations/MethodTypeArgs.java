/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6843077
 * @summary new type annotation location: method type args
 * @author Mahmood Ali
 * @compile -source 1.7 MethodTypeArgs.java
 */

class MethodTypeArgs {
  void oneArg() {
    this.<@A String>newList();
    this.<@A MyList<@B(0) String>>newList();

    MethodTypeArgs.<@A String>newList();
    MethodTypeArgs.<@A MyList<@B(0) String>>newList();
  }

  void twoArg() {
    this.<String, String>newMap();
    this.<@A String, @B(0) MyList<@A String>>newMap();

    MethodTypeArgs.<String, String>newMap();
    MethodTypeArgs.<@A String, @B(0) MyList<@A String>>newMap();
  }

  void withArraysIn() {
    this.<String[]>newList();
    this.<@A String @B(0) [] @A []>newList();

    this.<@A String[], @B(0) MyList<@A String> @A []>newMap();
  }

  static <E> void newList() { }
  static <K, V> void newMap() { }
}

class MyList<E> { }
@interface A { }
@interface B { int value(); }
