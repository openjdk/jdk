/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8315916
 * @summary Test early bailout during the creation of graph nodes for the scalarization of array fields, rather than during code generation.
 * @run main/othervm -Xcomp
 *                   -XX:EliminateAllocationArraySizeLimit=60240
 *                   compiler.escapeAnalysis.TestScalarizeBailout
 */

package compiler.escapeAnalysis;

public class TestScalarizeBailout {
  static Object var1;
  public static void main(String[] args) throws Exception {
    var1 = new long[48 * 1024];
    long[] a1 = new long[48 * 1024];
    try {
      // load the class to initialize the static object and trigger the EA
      Class <?> Class37 = Class.forName("compiler.escapeAnalysis.TestScalarizeBailout");
      for (int i = 0; i < a1.length; i++) {
        a1[i] = (i + 0);
      }
    } catch (Exception e){throw new RuntimeException(e);}
  }
}
