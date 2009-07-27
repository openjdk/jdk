/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4906586
 * @summary Missing ambiguity error when two methods are equally specific
 * @author gafter
 *
 * @compile/fail Ambig3.java
 */

class Test<T,E> {
    public void check(T val){
        System.out.println("Second check method being called");
    }
    public E check(E val){
        System.out.println("First check method being called");
        return null;
    }
 }

class Test3 extends Test<String,String> { }

class ParametericMethodsTest3 {
      public void assertion2() {
            Test3 tRef = new Test3();
            tRef.check("");
      }
 }
