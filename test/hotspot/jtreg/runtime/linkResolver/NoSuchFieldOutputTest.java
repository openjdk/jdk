/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8298065
 * @summary Test output of NoSuchFieldError when field signature does not match
 * @compile NoSuchFieldPrimitive.jasm NoSuchFieldReference.jasm TestClass.java
 * @compile NoSuchFieldArray.jasm NoSuchFieldMultiArray.jasm
 * @run main NoSuchFieldOutputTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Tests the output text of NoSuchFieldError
public class NoSuchFieldOutputTest {

  public static void main(java.lang.String[] unused) throws Exception {
    try {
      Class.forName("NoSuchFieldPrimitive").newInstance();
    } catch (NoSuchFieldError nsfe) {
      testNoSuchFieldOutput(nsfe, "primitive");
    }
    try {
      Class.forName("NoSuchFieldReference").newInstance();
    } catch (NoSuchFieldError nsfe) {
      testNoSuchFieldOutput(nsfe, "reference");
    }
    try {
      Class.forName("NoSuchFieldArray").newInstance();
    } catch (NoSuchFieldError nsfe) {
      testNoSuchFieldOutput(nsfe, "array");
    }
    try {
      Class.forName("NoSuchFieldMultiArray").newInstance();
    } catch (NoSuchFieldError nsfe) {
      testNoSuchFieldOutput(nsfe, "multiArray");
    }
  }

  private static void testNoSuchFieldOutput(NoSuchFieldError nsfe, String testType) throws Exception {
    Pattern noSuchFieldPattern = Pattern.compile("Class (?<classname>[\\w\\d]+) does not have member field '(?<signature>[\\S]+) (?<varname>[\\w\\d]+)'");
    String output = nsfe.getMessage();
    Matcher noSuchFieldMatcher = noSuchFieldPattern.matcher(output);
    if (noSuchFieldMatcher.matches()) {
      switch (testType) {
      case "primitive":
        checkOutputGroups(noSuchFieldMatcher, output, "NoSuchFieldPrimitive", "int", "x");
        break;
      case "reference":
        checkOutputGroups(noSuchFieldMatcher, output, "NoSuchFieldReference", "TestClass", "y");
        break;
      case "array":
        checkOutputGroups(noSuchFieldMatcher, output, "NoSuchFieldArray", "char[]", "z");
        break;
      case "multiArray":
        checkOutputGroups(noSuchFieldMatcher, output, "NoSuchFieldMultiArray", "double[][]", "a");
        break;
      default:
        throwTestException("No matching test", output);
      }
    } else {
      throwTestException("Output format does not match", output);
    }
    System.out.println(output);
  }

  private static void checkOutputGroups(Matcher noSuchFieldMatcher, String output,
                                 String testClass, String testSig, String testVar) throws Exception {
    String classname = noSuchFieldMatcher.group("classname");
    String signature = noSuchFieldMatcher.group("signature");
    String varname   = noSuchFieldMatcher.group("varname");
    if (!classname.equals(testClass)) {
      throwTestException("Failed to match class name", output);
    }
    if (!signature.equals(testSig)) {
      throwTestException("Failed to match type signature", output);
    }
    if (!varname.equals(testVar)) {
      throwTestException("Failed to match field name", output);
    }
  }

  private static void throwTestException(String reason, String output) throws Exception {
      throw new Exception(reason + " . Stdout is :\n" + output);
  }
}
