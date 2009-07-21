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
 * @summary new type annotation location: class literals
 * @author Mahmood Ali
 * @compile -source 1.7 ClassLiterals.java
 */

class ClassLiterals {

  public static void main(String[] args) {
    if (String.class != @A String.class) throw new Error();
    if (@A int.class != int.class) throw new Error();
    if (@A int.class != Integer.TYPE) throw new Error();
    if (@A int @B(0) [].class != int[].class) throw new Error();

    if (String[].class != @A String[].class) throw new Error();
    if (String[].class != String @A [].class) throw new Error();
    if (@A int[].class != int[].class) throw new Error();
    if (@A int @B(0) [].class != int[].class) throw new Error();
  }
}

@interface A {}
@interface B { int value(); }
