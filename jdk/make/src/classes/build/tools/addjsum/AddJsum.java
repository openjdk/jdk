/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package build.tools.addjsum;

import java.io.*;
import java.util.regex.*;

/** Adds a checksum ("jsum") to the end of a text file. The algorithm
    used is known to the JVM and prevents trivial tampering with the
    class list used for class data sharing.
*/

public class AddJsum {
  private static final long JSUM_SEED = 0xCAFEBABEBABECAFEL;

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: java AddJsum [input file name] [output file name]");
      System.exit(1);
    }

    try {
      File inFile  = new File(args[0]);
      File outFile = new File(args[1]);
      BufferedReader reader = new BufferedReader(new FileReader(inFile));
      BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
      Pattern p = Pattern.compile("# [0-9A-Fa-f]*");
      long computedJsum = JSUM_SEED;

      String line = null;
      while ((line = reader.readLine()) != null) {
        if (line.length() > 0 && line.charAt(0) == '#') {
          Matcher m = p.matcher(line);
          if (!m.matches()) {
            writer.write(line);
            writer.newLine();
          }
        } else {
          computedJsum = jsum(computedJsum, line);
          writer.write(line);
          writer.newLine();
        }
      }
      String hex = Long.toHexString(computedJsum);
      int diff = 16 - hex.length();
      for (int i = 0; i < diff; i++) {
        hex = "0" + hex;
      }
      writer.write("# " + hex);
      writer.newLine();
      reader.close();
      writer.close();
    } catch (IOException e) {
      System.err.println("Error reading or writing file");
      throw(e);
    }
  }

  private static long jsum(long start, String str) {
    long h = start;
    int len = str.length();
    for (int i = 0; i < len; i++) {
      char c = str.charAt(i);
      if (c <= ' ') {
        /* Skip spaces and control characters */
        continue;
      }
      h = 31 * h + c;
    }
    return h;
  }
}
