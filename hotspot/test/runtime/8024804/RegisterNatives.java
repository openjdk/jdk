/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8024804
 * @summary registerNatives() interface resolution should receive IAE
 * @run main RegisterNatives
 */
public class RegisterNatives {
  interface I { void registerNatives(); }
  interface J extends I {}
  static class B implements J { public void registerNatives() { System.out.println("B"); } }
  public static void main(String... args) {
    System.out.println("Regression test for JDK-8024804, crash when InterfaceMethodref resolves to Object.registerNatives\n");
    J val = new B();
    try {
      val.registerNatives();
    } catch (IllegalAccessError e) {
      System.out.println("TEST PASSES - according to current JVM spec, IAE expected\n");
      return;
    }
    System.out.println("TEST FAILS - no IAE resulted\n");
    System.exit(1);
  }
}
