/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
/*
 * @test
 * @summary test that illegal field modifiers are detected correctly
 * @enablePreview
 * @compile fieldModifiersTest.jcod
 * @run main/othervm -Xverify:remote runtime.valhalla.inlinetypes.classfileparser.IllegalFieldModifiers
 */

package runtime.valhalla.inlinetypes.classfileparser;


public class IllegalFieldModifiers {

  public static void runTest(String test_name, String message) throws Exception {
      System.out.println("Testing: " + test_name);
      boolean gotException = false;
      try {
          Class newClass = Class.forName(test_name);
      } catch (java.lang.ClassFormatError e) {
          gotException = true;
          if (!e.getMessage().contains(message)) {
              throw new RuntimeException( "Wrong ClassFormatError: " + e.getMessage());
          }
      }
      if (!gotException) {
        throw new RuntimeException("Missing ClassFormatError in test " + test_name);
      }
  }

  public static void main(String[] args) throws Exception {

    // Test that ACC_FINAL with ACC_VOLATILE is illegal.
    runTest("FinalAndVolatile", "Illegal field modifiers (fields cannot be final and volatile) in class FinalAndVolatile: 0x850");

    // Test that ACC_STATIC with ACC_STRICT is illegal.
    // runTest("StrictAndStatic", "Illegal field modifiers (field cannot be strict and static) in class StrictAndStatic: 0x808");

    // Test that ACC_STRICT without ACC_FINAL is illegal.
    // runTest("StrictNotFinal", "Illegal field modifiers (strict field must be final) in class StrictNotFinal: 0x800");

    // Test that a concrete value class cannot have field without ACC_STATIC or ACC_STRICT
    runTest("NotStaticNotStrict", "Illegal field modifiers (value class fields must be either non-static final and strict, or static) in class NotStaticNotStrict: 0x10");

    // Test that an abstract value class cannot have field without ACC_STATIC or ACC_STRICT
    runTest("NotStaticNotStrictInAbstract", "Illegal field modifiers (value class fields must be either non-static final and strict, or static) in class NotStaticNotStrictInAbstract: 0x10");

  }

}
