/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.interpreter;

import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.utilities.*;

public class BytecodeStore extends BytecodeLoadStore {
  BytecodeStore(Method method, int bci) {
    super(method, bci);
  }

  public void verify() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(isValid(), "check store");
    }
  }

  public boolean isValid() {
    int jcode = javaCode();
    switch (jcode) {
       case Bytecodes._istore:
       case Bytecodes._lstore:
       case Bytecodes._fstore:
       case Bytecodes._dstore:
       case Bytecodes._astore:
          return true;
       default:
          return false;
    }
  }

  public static BytecodeStore at(Method method, int bci) {
    BytecodeStore b = new BytecodeStore(method, bci);
    if (Assert.ASSERTS_ENABLED) {
      b.verify();
    }
    return b;
  }

  /** Like at, but returns null if the BCI is not at store  */
  public static BytecodeStore atCheck(Method method, int bci) {
    BytecodeStore b = new BytecodeStore(method, bci);
    return (b.isValid() ? b : null);
  }

  public static BytecodeStore at(BytecodeStream bcs) {
    return new BytecodeStore(bcs.method(), bcs.bci());
  }
}
