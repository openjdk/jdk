/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime.ia64;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.ia64.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.interpreter.*;
import sun.jvm.hotspot.runtime.*;

/** <P> Should be able to be used on all ia64 platforms we support
    (Win32, Linux) to implement JavaThread's
    "currentFrameGuess()" functionality. Input is an IA64ThreadContext;
    output is SP, FP, and PC for an IA64Frame. Instantiation of the
    IA64Frame is left to the caller, since we may need to subclass
    IA64Frame to support signal handler frames on Unix platforms. </P>

    <P> This is pretty much impossible on ia64.
    </P> */

public class IA64CurrentFrameGuess {
  private IA64ThreadContext context;
  private JavaThread       thread;
  private Address          spFound;
  private Address          fpFound;
  private Address          pcFound;

  private static final boolean DEBUG = false;

  public IA64CurrentFrameGuess(IA64ThreadContext context,
                              JavaThread thread) {
    this.context = context;
    this.thread  = thread;
  }

  /** Returns false if not able to find a frame within a reasonable range. */
  public boolean run(long regionInBytesToSearch) {
    /*
      Without using the stack walking library this is not possible on ia64.
      There is also the issue of walking dynamic code where there is no
      stack walking info generated.
    */
    return false;
  }

  public Address getSP() { return null; }
  public Address getFP() { return null; }
  /** May be null if getting values from thread-local storage; take
      care to call the correct IA64Frame constructor to recover this if
      necessary */
  public Address getPC() { return null; }

  private void setValues(Address sp, Address fp, Address pc) {
    spFound = sp;
    fpFound = fp;
    pcFound = pc;
  }
}
