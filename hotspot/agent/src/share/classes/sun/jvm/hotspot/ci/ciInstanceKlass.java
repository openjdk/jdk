/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.ci;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.SystemDictionary;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.types.WrongTypeException;

public class ciInstanceKlass extends ciKlass {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("ciInstanceKlass");
    initStateField = new CIntField(type.getCIntegerField("_init_state"), 0);
    isSharedField = new CIntField(type.getCIntegerField("_is_shared"), 0);
    CLASS_STATE_LINKED = db.lookupIntConstant("instanceKlass::linked").intValue();
    CLASS_STATE_FULLY_INITIALIZED = db.lookupIntConstant("instanceKlass::fully_initialized").intValue();
  }

  private static CIntField initStateField;
  private static CIntField isSharedField;
  private static int CLASS_STATE_LINKED;
  private static int CLASS_STATE_FULLY_INITIALIZED;

  public ciInstanceKlass(Address addr) {
    super(addr);
  }

  public int initState() {
    int initState = (int)initStateField.getValue(getAddress());
    if (isShared() && initState < CLASS_STATE_LINKED) {
      InstanceKlass ik = (InstanceKlass)getOop();
      initState = ik.getInitStateAsInt();
    }
    return initState;
  }

  public boolean isShared() {
    return isSharedField.getValue(getAddress()) != 0;
  }

  public boolean isLinked() {
    return initState() >= CLASS_STATE_LINKED;
  }

  public boolean isInitialized() {
    return initState() == CLASS_STATE_FULLY_INITIALIZED;
  }
}
