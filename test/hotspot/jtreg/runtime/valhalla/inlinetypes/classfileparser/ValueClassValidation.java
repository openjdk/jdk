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
 * @summary test validation of value classes
 * @enablePreview
 * @compile cfpValueClassValidation.jcod
 * @run main/othervm -Xverify:remote runtime.valhalla.inlinetypes.classfileparser.ValueClassValidation
 */

package runtime.valhalla.inlinetypes.classfileparser;

import javax.management.RuntimeErrorException;

public class ValueClassValidation {
  public static void runTest(String test_name, String cfe_message, String icce_message) throws Exception {
    System.out.println("Testing: " + test_name);
    boolean gotException = false;
    try {
        Class newClass = Class.forName(test_name);
    } catch (java.lang.ClassFormatError e) {
      gotException = true;
      if (cfe_message != null) {
        if (!e.getMessage().contains(cfe_message)) {
            throw new RuntimeException( "Wrong ClassFormatError: " + e.getMessage());
        }
      } else {
        throw new RuntimeException( "Unexpected ClassFormatError: " + e.getMessage());
      }
    } catch (java.lang.IncompatibleClassChangeError e) {
      gotException = true;
      if (icce_message != null) {
        if (!e.getMessage().contains(icce_message)) {
            throw new RuntimeException( "Wrong IncompatibleClassChangeError: " + e.getMessage());
        }
      } else {
        throw new RuntimeException( "Unexpected IncompatibleClassChangeError: " + e.getMessage());
      }
    }
    if (!gotException) {
      if (cfe_message != null) {
        throw new RuntimeException("Missing ClassFormatError in test" + test_name);
      } else if (icce_message != null) {
        throw new RuntimeException("Missing IncompatibleClassChangeError in test" + test_name);
      }
    }
  }

  public static void main(String[] args) throws Exception {

    // Test none of ACC_ABSTRACT, ACC_FINAL or ACC_IDENTITY is illegal.
    runTest("InvalidClassFlags", "Illegal class modifiers in class InvalidClassFlags", null);

    // Test ACC_ABSTRACT without ACC_IDENTITY is legal
    runTest("AbstractValue", null, null);

    // Test ACC_FINAL without ACC_IDENTITY is legal
    runTest("FinalValue", null, null);

    // Test a concrete value class extending an abstract identity class
    runTest("ValueClass", null, "Value type ValueClass has an identity type as supertype");

    // Test a concrete identity class without ACC_IDENTITY but with an older class file version, extending an abstract identity class
    // (Test that the VM fixes missing ACC_IDENTITY in old class files)
    runTest("IdentityClass", null, null);

    // Test a concrete value class extending a concrete (i.e. final) value class
    runTest("ValueClass2", null, "class ValueClass2 cannot inherit from final class FinalValue");

    // Test an abstract value class extending an abstract identity class
    runTest("AbstractValueClass2", null, "Value type AbstractValueClass2 has an identity type as supertype");

    // Test an abstract identity class without ACC_IDENTITY but with an older class file version, extending an abstract identity class
    // (Test that the VM fixes missing ACC_IDENTITY in old class files)
    runTest("AbstractIdentityClass2", null, null);

    // Test an abstract value class extending a concrete (i.e. final) value class
    runTest("AbstractValueClass3", null, "class AbstractValueClass3 cannot inherit from final class FinalValue");

    //Test a concrete class without ACC_IDENTITY but with an older class file version, declaring a field without ACC_STATIC nor ACC_STRICT
    // (Test that the VM fixes missing ACC_IDENTITY in old class files)
    runTest("NotStaticNotStrictInOldClass", null, null);

    // Test a concrete value class with a static synchronized method
    runTest("StaticSynchMethod", null, null);

    // Test a concrete value class with a non-static synchronized method
    runTest("SynchMethod", "Method m in class SynchMethod (not an identity class) has illegal modifiers: 0x21", null);

    // Test an abstract value class with a static synchronized method
    runTest("StaticSynchMethodInAbstractValue", null, null);

    // Test an abstract value class with a non-static synchronized method
    runTest("SynchMethodInAbstractValue", "Method m in class SynchMethodInAbstractValue (not an identity class) has illegal modifiers: 0x21", null);

    // Test a class with a primitive descriptor in its LoadableDescriptors attribute:
    runTest("PrimitiveInLoadableDescriptors", null, null);
  }
}
