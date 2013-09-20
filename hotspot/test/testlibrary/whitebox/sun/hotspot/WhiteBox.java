/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.hotspot;

import java.lang.reflect.Executable;
import java.security.BasicPermission;
import sun.hotspot.parser.DiagnosticCommand;

public class WhiteBox {

  @SuppressWarnings("serial")
  public static class WhiteBoxPermission extends BasicPermission {
    public WhiteBoxPermission(String s) {
      super(s);
    }
  }

  private WhiteBox() {}
  private static final WhiteBox instance = new WhiteBox();
  private static native void registerNatives();

  /**
   * Returns the singleton WhiteBox instance.
   *
   * The returned WhiteBox object should be carefully guarded
   * by the caller, since it can be used to read and write data
   * at arbitrary memory addresses. It must never be passed to
   * untrusted code.
   */
  public synchronized static WhiteBox getWhiteBox() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(new WhiteBoxPermission("getInstance"));
    }
    return instance;
  }

  static {
    registerNatives();
  }

  // Get the maximum heap size supporting COOPs
  public native long getCompressedOopsMaxHeapSize();
  // Arguments
  public native void printHeapSizes();

  // Memory
  public native long getObjectAddress(Object o);
  public native int  getHeapOopSize();

  // Runtime
  // Make sure class name is in the correct format
  public boolean isClassAlive(String name) {
    return isClassAlive0(name.replace('.', '/'));
  }
  private native boolean isClassAlive0(String name);

  // G1
  public native boolean g1InConcurrentMark();
  public native boolean g1IsHumongous(Object o);
  public native long    g1NumFreeRegions();
  public native int     g1RegionSize();
  public native Object[]    parseCommandLine(String commandline, DiagnosticCommand[] args);

  // NMT
  public native long NMTMalloc(long size);
  public native void NMTFree(long mem);
  public native long NMTReserveMemory(long size);
  public native void NMTCommitMemory(long addr, long size);
  public native void NMTUncommitMemory(long addr, long size);
  public native void NMTReleaseMemory(long addr, long size);
  public native boolean NMTWaitForDataMerge();
  public native boolean NMTIsDetailSupported();

  // Compiler
  public native void    deoptimizeAll();
  public        boolean isMethodCompiled(Executable method) {
    return isMethodCompiled(method, false /*not osr*/);
  }
  public native boolean isMethodCompiled(Executable method, boolean isOsr);
  public        boolean isMethodCompilable(Executable method) {
    return isMethodCompilable(method, -1 /*any*/);
  }
  public        boolean isMethodCompilable(Executable method, int compLevel) {
    return isMethodCompilable(method, compLevel, false /*not osr*/);
  }
  public native boolean isMethodCompilable(Executable method, int compLevel, boolean isOsr);
  public native boolean isMethodQueuedForCompilation(Executable method);
  public        int     deoptimizeMethod(Executable method) {
    return deoptimizeMethod(method, false /*not osr*/);
  }
  public native int     deoptimizeMethod(Executable method, boolean isOsr);
  public        void    makeMethodNotCompilable(Executable method) {
    makeMethodNotCompilable(method, -1 /*any*/);
  }
  public        void    makeMethodNotCompilable(Executable method, int compLevel) {
    makeMethodNotCompilable(method, compLevel, false /*not osr*/);
  }
  public native void    makeMethodNotCompilable(Executable method, int compLevel, boolean isOsr);
  public        int     getMethodCompilationLevel(Executable method) {
    return getMethodCompilationLevel(method, false /*not ost*/);
  }
  public native int     getMethodCompilationLevel(Executable method, boolean isOsr);
  public native boolean testSetDontInlineMethod(Executable method, boolean value);
  public        int     getCompileQueuesSize() {
    return getCompileQueueSize(-1 /*any*/);
  }
  public native int     getCompileQueueSize(int compLevel);
  public native boolean testSetForceInlineMethod(Executable method, boolean value);
  public boolean        enqueueMethodForCompilation(Executable method, int compLevel) {
    return enqueueMethodForCompilation(method, compLevel, -1 /*InvocationEntryBci*/);
  }
  public native boolean enqueueMethodForCompilation(Executable method, int compLevel, int entry_bci);
  public native void    clearMethodState(Executable method);
  public native int     getMethodEntryBci(Executable method);

  // Intered strings
  public native boolean isInStringTable(String str);

  // Memory
  public native void readReservedMemory();

  // force Full GC
  public native void fullGC();
}
