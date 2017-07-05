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
 * @bug 6646019
 * @summary array subscript expressions become top() with -d64
 * @run main/othervm -Xcomp -XX:CompileOnly=Test.test Test
*/


public class Test  {
  final static int i = 2076285318;
  long l = 2;
  short s;

  public static void main(String[] args) {
    Test t = new Test();
    try { t.test(); }
    catch (Throwable e) {
      if (t.l != 5) {
        System.out.println("Fails: " + t.l + " != 5");
      }
    }
  }

  private void test() {
    l = 5;
    l = (new short[(byte)-2])[(byte)(l = i)];
  }
}
