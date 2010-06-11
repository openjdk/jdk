/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.tools.*;
import sun.jvm.hotspot.runtime.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;

/**
This is a sanity checking tool for Serviceability Agent. To use this class,
refer to sasanity.sh script in the current directory.
*/

public class SASanityChecker extends Tool {
  private static final String saJarName;
  private static final Map c2types;

  static {
     saJarName = System.getProperty("SASanityChecker.SAJarName", "sa-jdi.jar");
     c2types = new HashMap();
     Object value = new Object();
     c2types.put("sun.jvm.hotspot.code.ExceptionBlob", value);
     c2types.put("sun.jvm.hotspot.code.DeoptimizationBlob", value);
     c2types.put("sun.jvm.hotspot.code.UncommonTrapBlob", value);

  }

  public void run() {
     String classPath = System.getProperty("java.class.path");
     StringTokenizer st = new StringTokenizer(classPath, File.pathSeparator);
     String saJarPath = null;
     while (st.hasMoreTokens()) {
        saJarPath = st.nextToken();
        if (saJarPath.endsWith(saJarName)) {
           break;
        }
     }

     if (saJarPath == null) {
        throw new RuntimeException(saJarName + " is not the CLASSPATH");
     }

     String cpuDot = "." + VM.getVM().getCPU() + ".";
     String platformDot = "." + VM.getVM().getOS() + "_" + VM.getVM().getCPU() + ".";
     boolean isClient = VM.getVM().isClientCompiler();

     try {
        FileInputStream fis = new FileInputStream(saJarPath);
        JarInputStream jis = new JarInputStream(fis);
        JarEntry je = null;
        while ( (je = jis.getNextJarEntry()) != null) {
           String entryName = je.getName();
           int dotClassIndex = entryName.indexOf(".class");
           if (dotClassIndex == -1) {
              // skip non-.class stuff
              continue;
           }

           entryName = entryName.substring(0, dotClassIndex).replace('/', '.');

           // skip debugger, asm classes, type classes and jdi binding classes
           if (entryName.startsWith("sun.jvm.hotspot.debugger.") ||
               entryName.startsWith("sun.jvm.hotspot.asm.") ||
               entryName.startsWith("sun.jvm.hotspot.type.") ||
               entryName.startsWith("sun.jvm.hotspot.jdi.") ) {
              continue;
           }

           String runtimePkgPrefix = "sun.jvm.hotspot.runtime.";
           int runtimeIndex = entryName.indexOf(runtimePkgPrefix);
           if (runtimeIndex != -1) {
              // look for further dot. if there, it has to be sub-package.
              // in runtime sub-packages include only current platform classes.
              if (entryName.substring(runtimePkgPrefix.length() + 1, entryName.length()).indexOf('.') != -1) {
                 if (entryName.indexOf(cpuDot) == -1 &&
                     entryName.indexOf(platformDot) == -1) {
                    continue;
                 }
              }
           }

           if (isClient) {
              if (c2types.get(entryName) != null) {
                 continue;
              }
           } else {
              if (entryName.equals("sun.jvm.hotspot.c1.Runtime1")) {
                 continue;
              }
           }

           System.out.println("checking " + entryName + " ..");
           // force init of the class to uncover any vmStructs mismatch
           Class.forName(entryName);
        }
     } catch (Exception exp) {
        System.out.println();
        System.out.println("FAILED");
        System.out.println();
        throw new RuntimeException(exp.getMessage());
     }
     System.out.println();
     System.out.println("PASSED");
     System.out.println();
  }

  public static void main(String[] args) {
     SASanityChecker checker = new SASanityChecker();
     checker.start(args);
     checker.stop();
  }
}
