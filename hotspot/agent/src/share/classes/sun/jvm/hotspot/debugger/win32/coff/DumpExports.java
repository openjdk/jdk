/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.win32.coff;

public class DumpExports {
  private static void usage() {
    System.err.println("usage: java DumpExports [.dll name]");
    System.exit(1);
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      usage();
    }

    String filename = args[0];
    COFFFile file   = COFFFileParser.getParser().parse(filename);
    ExportDirectoryTable exports =
      file.getHeader().
        getOptionalHeader().
          getDataDirectories().
            getExportDirectoryTable();
    if (exports == null) {
      System.out.println("No exports found.");
    } else {
      System.out.println(file.getHeader().getNumberOfSections() + " sections in file");
      for (int i = 0; i < file.getHeader().getNumberOfSections(); i++) {
        System.out.println("  Section " + i + ": " + file.getHeader().getSectionHeader(1 + i).getName());
      }

      DataDirectory dir = file.getHeader().getOptionalHeader().getDataDirectories().getExportTable();
      System.out.println("Export table: RVA = 0x" + Integer.toHexString(dir.getRVA()) +
                         ", size = 0x" + Integer.toHexString(dir.getSize()));

      System.out.println("DLL name: " + exports.getDLLName());
      System.out.println("Time/date stamp 0x" + Integer.toHexString(exports.getTimeDateStamp()));
      System.out.println("Major version 0x" + Integer.toHexString(exports.getMajorVersion() & 0xFFFF));
      System.out.println("Minor version 0x" + Integer.toHexString(exports.getMinorVersion() & 0xFFFF));
      System.out.println(exports.getNumberOfNamePointers() + " functions found");
      for (int i = 0; i < exports.getNumberOfNamePointers(); i++) {
        System.out.println("  0x" +
                           Integer.toHexString(exports.getExportAddress(exports.getExportOrdinal(i))) +
                           "  " +
                           (exports.isExportAddressForwarder(exports.getExportOrdinal(i))  ?
                            ("Forwarded to " + exports.getExportAddressForwarder(exports.getExportOrdinal(i))) :
                            exports.getExportName(i)));
      }
    }
  }
}
