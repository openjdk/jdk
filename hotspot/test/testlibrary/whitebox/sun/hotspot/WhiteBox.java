/*
 * Copyright (c) 2012, 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
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

  // Compiler
  public native void    deoptimizeAll();
  public native boolean isMethodCompiled(Method method);
  public native boolean isMethodCompilable(Method method);
  public native boolean isMethodQueuedForCompilation(Method method);
  public native int     deoptimizeMethod(Method method);
  public native void    makeMethodNotCompilable(Method method);
  public native int     getMethodCompilationLevel(Method method);
  public native boolean setDontInlineMethod(Method method, boolean value);
  public native int     getCompileQueuesSize();

  //Intered strings
  public native boolean isInStringTable(String str);

  // force Full GC
  public native void fullGC();
}
