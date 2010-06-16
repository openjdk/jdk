/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.dbx.*;

// A test of the debugger backend. This should be used to connect to
// the helloWorld.cpp program.

public class TestDebugger {
  // FIXME: make these configurable, i.e., via a dotfile
  private static final String dbxPathName               = "/export/home/kbr/ws/dbx_61/dev/Derived-sparcv9-S2./src/dbx/dbx";
  private static final String[] dbxSvcAgentDSOPathNames =
    new String[] {
      "/export/home/kbr/main/sa_baseline/src/os/solaris/agent/libsvc_agent_dbx.so"
    };

  private static void usage() {
    System.out.println("usage: java TestDebugger [pid]");
    System.out.println("pid must be the process ID of the helloWorld process");
    System.exit(1);
  }

  public static void main(String[] args) {
    try {
      if (args.length != 1) {
        usage();
      }

      int pid = 0;
      try {
        pid = Integer.parseInt(args[0]);
      }
      catch (NumberFormatException e) {
        usage();
      }

      JVMDebugger debugger = new DbxDebuggerLocal(new MachineDescriptionSPARC64Bit(),
                                                  dbxPathName, dbxSvcAgentDSOPathNames, true);

      try {
        debugger.attach(pid);
      }
      catch (DebuggerException e) {
        System.err.print("Error attaching to process ID " + pid + ": ");
        if (e.getMessage() != null) {
          System.err.print(e.getMessage());
        }
        System.err.println();
        System.exit(1);
      }

      // HACK: configure debugger with primitive type sizes to get
      // Java types going
      debugger.configureJavaPrimitiveTypeSizes(1, 1, 2, 8, 4, 4, 8, 2);

      // FIXME: figure out how to canonicalize and/or eliminate
      // loadobject specification
      String loadObjectName = "-";

      //    long strAddr = debugger.lookup("helloWorld", "helloWorldString");
      Address addr = debugger.lookup(loadObjectName, "helloWorldString");
      if (addr == null) {
        System.err.println("Error looking up symbol \"helloWorldString\" in context \"" +
                           loadObjectName + "\"");
        System.exit(1);
      }

      // This is a pointer which points to the start of storage.
      // Dereference it.
      addr = addr.getAddressAt(0);

      // Read the number of bytes we know we need
      int helloWorldLen = 13;
      byte[] data = new byte[helloWorldLen];
      for (int i = 0; i < helloWorldLen; ++i) {
        data[i] = (byte) addr.getCIntegerAt(i, 1, false);
      }

      // Convert to characters
      char[] chars = new char[data.length];
      for (int i = 0; i < data.length; ++i) {
        chars[i] = (char) data[i];
      }
      String helloWorldStr = new String(chars);

      System.out.println("Successfully read string \"" + helloWorldStr + "\" from target process\n");

      // Test all Java data types (see helloWorld.cpp)
      byte   expectedByteValue   = (byte) 132;
      short  expectedShortValue  = (short) 27890;
      int    expectedIntValue    = 1020304050;
      long   expectedLongValue   = 102030405060708090L;
      float  expectedFloatValue  = 35.4F;
      double expectedDoubleValue = 1.23456789;
      byte   byteValue   = 0;
      short  shortValue  = 0;
      int    intValue    = 0;
      long   longValue   = 0;
      float  floatValue  = 0;
      double doubleValue = 0;

      addr = debugger.lookup(loadObjectName, "testByte");
      if (addr == null) {
        System.err.println("Error looking up symbol \"testByte\" in context \"" +
                           loadObjectName + "\"");
        System.exit(1);
      }
      byteValue = addr.getJByteAt(0);
      if (byteValue != expectedByteValue) {
        System.err.println("Error: unexpected byte value (got " +
                           byteValue + ", expected " + expectedByteValue + ")");
        System.exit(1);
      }

      addr = debugger.lookup(loadObjectName, "testShort");
      if (addr == null) {
        System.err.println("Error looking up symbol \"testShort\" in context \"" +
                           loadObjectName + "\"");
        System.exit(1);
      }
      shortValue = addr.getJShortAt(0);
      if (shortValue != expectedShortValue) {
        System.err.println("Error: unexpected short value (got " +
                           shortValue + ", expected " + expectedShortValue + ")");
        System.exit(1);
      }

      addr = debugger.lookup(loadObjectName, "testInt");
      if (addr == null) {
        System.err.println("Error looking up symbol \"testInt\" in context \"" +
                           loadObjectName + "\"");
        System.exit(1);
      }
      intValue = addr.getJIntAt(0);
      if (intValue != expectedIntValue) {
        System.err.println("Error: unexpected int value (got " +
                           intValue + ", expected " + expectedIntValue + ")");
        System.exit(1);
      }

      addr = debugger.lookup(loadObjectName, "testLong");
      if (addr == null) {
        System.err.println("Error looking up symbol \"testLong\" in context \"" +
                           loadObjectName + "\"");
        System.exit(1);
      }
      longValue = addr.getJLongAt(0);
      if (longValue != expectedLongValue) {
        System.err.println("Error: unexpected long value (got " +
                           longValue + ", expected " + expectedLongValue + ")");
        System.exit(1);
      }

      addr = debugger.lookup(loadObjectName, "testFloat");
      if (addr == null) {
        System.err.println("Error looking up symbol \"testFloat\" in context \"" +
                           loadObjectName + "\"");
        System.exit(1);
      }
      floatValue = addr.getJFloatAt(0);
      if (floatValue != expectedFloatValue) {
        System.err.println("Error: unexpected float value (got " +
                           floatValue + ", expected " + expectedFloatValue + ")");
        System.exit(1);
      }

      addr = debugger.lookup(loadObjectName, "testDouble");
      if (addr == null) {
        System.err.println("Error looking up symbol \"testDouble\" in context \"" +
                           loadObjectName + "\"");
        System.exit(1);
      }
      doubleValue = addr.getJDoubleAt(0);
      if (doubleValue != expectedDoubleValue) {
        System.err.println("Error: unexpected double value (got " +
                           doubleValue + ", expected " + expectedDoubleValue + ")");
        System.exit(1);
      }

      System.err.println("All tests passed successfully.");

      debugger.detach();
    }
    catch (AddressException e) {
      System.err.println("Error occurred during test:");
      e.printStackTrace();
      System.exit(1);
    }
  }
}
