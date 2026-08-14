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
 * @summary test a LoadableDescriptors attribute with an invalid entry
 * @enablePreview
 * @compile LDTest.jcod TestIllegalLoadableDescriptors.java
 * @run main runtime.valhalla.inlinetypes.classfileparser.TestIllegalLoadableDescriptors
 */

package runtime.valhalla.inlinetypes.classfileparser;

public class TestIllegalLoadableDescriptors {

  public static void main(String[] args)  throws ClassNotFoundException {
    boolean gotException = false;
      try {
          Class newClass = Class.forName("LDTest");
      } catch (java.lang.ClassFormatError e) {
          gotException = true;
          if (!e.getMessage().contains("Descriptor from LoadableDescriptors attribute at index \"33\" in class LDTest has illegal signature \"[V")) {
              throw new RuntimeException( "Wrong ClassFormatError: " + e.getMessage());
          }
      }
      if (!gotException) {
        throw new RuntimeException("Missing ClassFormatError");
      }
  }

}
