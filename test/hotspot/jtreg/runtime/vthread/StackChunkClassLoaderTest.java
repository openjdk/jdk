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
 * @test StackChunkClassLoaderTest
 * @summary Test that a different jdk.internal.vm.StackChunk can be loaded by non-null class loader
 * @library /test/lib
 * @compile StackChunk.java
 * @run main/othervm StackChunkClassLoaderTest
 */

import java.lang.reflect.Method;
import java.io.FileInputStream;
import java.io.File;

public class StackChunkClassLoaderTest extends ClassLoader {

    public String loaderName;

    StackChunkClassLoaderTest(String name) {
        this.loaderName = name;
    }

    // Get data for pre-compiled class file to load.
    public byte[] getClassData(String name) {
        try {
            String classDir = System.getProperty("test.classes");
            String tempName = name.replaceAll("\\.", "/");
            return new FileInputStream(classDir + File.separator + tempName + ".class").readAllBytes();
        } catch (Exception e) {
              return null;
        }
    }

    public Class loadClass(String name) throws ClassNotFoundException {
        if (!name.contains("StackChunk")) {
            return super.loadClass(name);
        }

        byte[] data = getClassData(name);
        System.out.println("name is " + name);
        return defineClass(name, data, 0, data.length);
    }

  public static void main(java.lang.String[] unused) throws Exception {
      ClassLoader cl = new StackChunkClassLoaderTest("StackChunkClassLoaderTest");
      Class<?> c = Class.forName("jdk.internal.vm.StackChunk", true, cl);
      Object obj = c.getDeclaredConstructor().newInstance();
      System.gc();
      java.lang.reflect.Method m = c.getMethod("print");
      m.invoke(obj);
      Method mi = c.getMethod("getI");
      Object val = mi.invoke(obj);
      if (((Integer)val).intValue() != 55) {
          throw new RuntimeException("Test failed, StackChunk object corrupt");
      }
  }
}
