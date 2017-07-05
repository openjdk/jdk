/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.tools;

import java.io.PrintStream;
import java.util.Hashtable;
import sun.jvm.hotspot.*;
import sun.jvm.hotspot.bugspot.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.debugger.*;

// generic command line or GUI tool.
// override run & code main as shown below.

public abstract class Tool implements Runnable {
   private BugSpotAgent agent;
   private int debugeeType;

   // debugeeType is one of constants below
   protected static final int DEBUGEE_PID    = 0;
   protected static final int DEBUGEE_CORE   = 1;
   protected static final int DEBUGEE_REMOTE = 2;

   public String getName() {
      return getClass().getName();
   }

   protected boolean needsJavaPrefix() {
      return true;
   }

   // whether this tool requires debuggee to be java process or core?
   protected boolean requiresVM() {
      return true;
   }

   protected void setAgent(BugSpotAgent a) {
      agent = a;
   }

   protected void setDebugeeType(int dt) {
      debugeeType = dt;
   }

   protected BugSpotAgent getAgent() {
      return agent;
   }

   protected int getDebugeeType() {
      return debugeeType;
   }

   protected void printUsage() {
      String name = null;
      if (needsJavaPrefix()) {
         name = "java " + getName();
      } else {
         name = getName();
      }
      System.out.println("Usage: " + name + " [option] <pid>");
      System.out.println("\t\t(to connect to a live java process)");
      System.out.println("   or " + name + " [option] <executable> <core>");
      System.out.println("\t\t(to connect to a core file)");
      System.out.println("   or " + name + " [option] [server_id@]<remote server IP or hostname>");
      System.out.println("\t\t(to connect to a remote debug server)");
      System.out.println();
      System.out.println("where option must be one of:");
      printFlagsUsage();
   }

   protected void printFlagsUsage() {
       System.out.println("    -h | -help\tto print this help message");
   }

   protected void usage() {
      printUsage();
      System.exit(1);
   }

   /*
      Derived class main should be of the following form:

      public static void main(String[] args) {
         <derived class> obj = new <derived class>;
         obj.start(args);
      }

   */

   protected void stop() {
      if (agent != null) {
         agent.detach();
         System.exit(0);
      }
   }

   protected void start(String[] args) {
      if ((args.length < 1) || (args.length > 2)) {
         usage();
      }

      // Attempt to handle -h or -help or some invalid flag
      if (args[0].startsWith("-")) {
          usage();
      }

      PrintStream err = System.err;

      int pid = 0;
      String coreFileName   = null;
      String executableName = null;
      String remoteServer   = null;

      switch (args.length) {
        case 1:
           try {
              pid = Integer.parseInt(args[0]);
              debugeeType = DEBUGEE_PID;
           } catch (NumberFormatException e) {
              // try remote server
              remoteServer = args[0];
              debugeeType  = DEBUGEE_REMOTE;
           }
           break;

        case 2:
           executableName = args[0];
           coreFileName   = args[1];
           debugeeType    = DEBUGEE_CORE;
           break;

        default:
           usage();
      }

      agent = new BugSpotAgent();
      try {
        switch (debugeeType) {
          case DEBUGEE_PID:
             err.println("Attaching to process ID " + pid + ", please wait...");
             agent.attach(pid);
             break;

          case DEBUGEE_CORE:
             err.println("Attaching to core " + coreFileName +
                         " from executable " + executableName + ", please wait...");
             agent.attach(executableName, coreFileName);
             break;

          case DEBUGEE_REMOTE:
             err.println("Attaching to remote server " + remoteServer + ", please wait...");
             agent.attach(remoteServer);
             break;
        }
      }
      catch (DebuggerException e) {
        switch (debugeeType) {
          case DEBUGEE_PID:
             err.print("Error attaching to process: ");
             break;

          case DEBUGEE_CORE:
             err.print("Error attaching to core file: ");
             break;

          case DEBUGEE_REMOTE:
             err.print("Error attaching to remote server: ");
             break;
        }
        if (e.getMessage() != null) {
          err.print(e.getMessage());
        }
        err.println();
        System.exit(1);
      }

      err.println("Debugger attached successfully.");

      boolean isJava = agent.isJavaMode();
      if (isJava) {
         VM vm = VM.getVM();
         if (vm.isCore()) {
           err.println("Core build detected.");
         } else if (vm.isClientCompiler()) {
           err.println("Client compiler detected.");
         } else if (vm.isServerCompiler()) {
           err.println("Server compiler detected.");
         } else {
           throw new RuntimeException("Fatal error: " +
                                 "should have been able to detect core/C1/C2 build");
         }

         String version = vm.getVMRelease();
         if (version != null) {
            err.print("JVM version is ");
            err.println(version);
         }

         run();
      } else { // not a java process or core
         if (requiresVM()) {
            err.println(getName() + " requires a java VM process/core!");
         } else {
            run();
         }
      }
   }
}
