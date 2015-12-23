/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.memory;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class LoaderConstraintEntry extends sun.jvm.hotspot.utilities.HashtableEntry {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("LoaderConstraintEntry");
    nameField = type.getAddressField("_name");
    numLoadersField = type.getCIntegerField("_num_loaders");
    maxLoadersField = type.getCIntegerField("_max_loaders");
    loadersField = type.getAddressField("_loaders");
  }

  // Fields
  private static AddressField nameField;
  private static CIntegerField numLoadersField;
  private static CIntegerField maxLoadersField;
  private static AddressField loadersField;

  // Accessors

  public Symbol name() {
    return Symbol.create(nameField.getValue(addr));
  }

  public int numLoaders() {
    return (int) numLoadersField.getValue(addr);
  }

  public int maxLoaders() {
    return (int) maxLoadersField.getValue(addr);
  }

  public Oop initiatingLoader(int i) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(i >= 0 && i < numLoaders(), "invalid index");
    }
    Address loaders = loadersField.getValue(addr);
    OopHandle loader = loaders.addOffsetToAsOopHandle(i * VM.getVM().getOopSize());
    return VM.getVM().getObjectHeap().newOop(loader);
  }

  public LoaderConstraintEntry(Address addr) {
    super(addr);
  }

  /* covariant return type :-(
  public LoaderConstraintEntry next() {
    return (LoaderConstraintEntry) super.next();
  }
  For now, let the caller cast it ..
  */
}
