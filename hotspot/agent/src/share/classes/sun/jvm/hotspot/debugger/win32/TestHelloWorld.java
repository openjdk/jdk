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

package sun.jvm.hotspot.debugger.win32;

import java.util.*;
import sun.jvm.hotspot.debugger.*;

/** Tests to see whether we can find the "Hello, World" string in a
    target process */

public class TestHelloWorld {
  private static void usage() {
    System.out.println("usage: java TestHelloWorld [pid]");
    System.out.println("pid must be the process ID of the HelloWorldDLL programs");
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

      JVMDebugger debugger = new Win32DebuggerLocal(new MachineDescriptionIntelX86(), true);
      System.err.println("Trying to attach...");
      debugger.attach(pid);
      System.err.println("Attach succeeded.");
      Address addr = debugger.lookup("helloworld.dll", "helloWorldString");
      System.err.println("helloWorldString address = " + addr);
      System.err.println("Trying to detach...");
      if (!debugger.detach()) {
        System.err.println("ERROR: detach failed.");
        System.exit(0);
      }
      System.err.println("Detach succeeded.");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
