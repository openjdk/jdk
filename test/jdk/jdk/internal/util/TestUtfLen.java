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
 */

/*
 * @test
 * @bug 8366421
 * @summary Test for ModifiedUtf.utfLen() return type change from int to long to avoid overflow
 * @modules java.base/jdk.internal.classfile.impl
 *          java.base/jdk.internal.util
 * @run main/othervm -Xmx4g --add-opens java.base/jdk.internal.classfile.impl=ALL-UNNAMED TestUtfLen
 */

import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.ClassFile;
import java.lang.reflect.Method;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.UTFDataFormatException;

import jdk.internal.classfile.impl.BufWriterImpl;
import jdk.internal.classfile.impl.ClassFileImpl;
import jdk.internal.util.ModifiedUtf;

public class TestUtfLen {
  private static final String THREE_BYTE = "\u2600";   // 3-byte UTF-8

  public static void main(String[] args) {
    int count = Integer.MAX_VALUE / 3 + 1;
    long expected = 3L * count;
    String largeString = THREE_BYTE.repeat(count);

    long total = ModifiedUtf.utfLen(largeString, 0);
    if (total != expected) {
      throw new RuntimeException("Expected total=" + expected + " but got " + total);
    }

    /**
     * Verifies that the following three methods that call ModifiedUtf.utfLen()
     * correctly handle overflow:
     * - DataOutputStream.writeUTF(String)
     * - BufWriterImpl.writeUtfEntry(String)
     * - ObjectOutputStream.writeUTF(String)
     */
    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
         DataOutputStream dataOut = new DataOutputStream(byteOut)) {
      dataOut.writeUTF(largeString);
      throw new RuntimeException("Expected UTFDataFormatException was not thrown.");
    } catch (UTFDataFormatException e) {
    }

    BufWriterImpl bufWriter = new BufWriterImpl(ConstantPoolBuilder.of(), (ClassFileImpl) ClassFile.of());
    Method writeUtfEntry = bufWriter.getClass().getDeclaredMethod("writeUtfEntry", String.class);
    writeUtfEntry.setAccessible(true);
    try {
      writeUtfEntry.invoke(bufWriter, largeString);
      throw new RuntimeException("Expected IllegalArgumentException was not thrown.");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (!(cause instanceof IllegalArgumentException)) {
        throw new RuntimeException("Expected IllegalArgumentException was not thrown.");
      }
    }

    File tempFile = File.createTempFile("utfLenOverflow", ".dat");
    tempFile.deleteOnExit();
    try (FileOutputStream fos = new FileOutputStream(tempFile);
      ObjectOutputStream objOut = new ObjectOutputStream(fos)){
      objOut.writeUTF(largeString);
    }

    System.out.println("PASSED");
  }
}
