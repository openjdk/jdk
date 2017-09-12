/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;

public class BasicHashtableEntry extends VMObject {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("BasicHashtableEntry<mtInternal>");
    hashField      = type.getCIntegerField("_hash");
    nextField      = type.getAddressField("_next");
  }

  // Fields
  private static CIntegerField hashField;
  private static AddressField  nextField;

  // Accessors
  public long hash() {
    return hashField.getValue(addr) & 0xFFFFFFFFL;
  }

  private long nextAddressValue() {
    Debugger dbg = VM.getVM().getDebugger();
    Address nextValue = nextField.getValue(addr);
    return (nextValue != null) ? dbg.getAddressValue(nextValue) : 0L;
  }

  public boolean isShared() {
    return (nextAddressValue() & 1L) != 0;
  }

  public BasicHashtableEntry next() {
    Address nextValue = nextField.getValue(addr);
    Address next = (nextValue != null)? nextValue.andWithMask(-2L) : null;
    // using this.getClass so that return type will be as expected in
    // subclass context. But, because we can't use covariant return type
    // caller has to use this next and cast the result to correct type.
    return (BasicHashtableEntry) VMObjectFactory.newObject(this.getClass(), next);
  }

  public BasicHashtableEntry(Address addr) {
    super(addr);
  }
}
