/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @compile NoSuchFieldOutputTest.java FieldName.java
 * @compile FieldName1.jasm
 * @run main NoSuchFieldOutputTest
 * @compile FieldName2.jasm
 * @run main NoSuchFieldOutputTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoSuchFieldOutputTest {
  public static void main(java.lang.String[] unused) throws Exception {
      try {
          FieldName fm = new FieldName();
          Object x = FieldName.x;
          String s = "x = " + x;
          throwTestException("Non error output does not match", s);
      } catch (NoSuchFieldError nsfe) {
          Pattern noSuchFieldPattern = Pattern.compile("Class (?<classname>[\\w\\d]+) does not have field '(?<signature>[\\w\\d]+) (?<varname>[\\w\\d]+)'");
          String output = nsfe.getMessage();
          Matcher noSuchFieldMatcher = noSuchFieldPattern.matcher(output);
          if (noSuchFieldMatcher.matches()) {
            String classname = noSuchFieldMatcher.group("classname");
            String signature = noSuchFieldMatcher.group("signature");
            String varname   = noSuchFieldMatcher.group("varname");
            if (!classname.equals("FieldName")) {
              throwTestException("Failed to match class name", output);
            }
            if (!signature.equals("int")) {
              throwTestException("Failed to match type signature", output);
            }
            if (!varname.equals("x")) {
              throwTestException("Failed to match field name", output);
            }
          } else {
            throwTestException("Output does not match", output);
          }
      }
  }
  private static void throwTestException(String reason, String output) throws Exception {
      throw new Exception(reason + " . Stdout is :\n" + output);
  }
}
