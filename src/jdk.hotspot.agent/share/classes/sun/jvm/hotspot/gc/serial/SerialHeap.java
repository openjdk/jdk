/*
 * Copyright (c) 2017, 2024, Red Hat, Inc. and/or its affiliates.
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

package sun.jvm.hotspot.gc.serial;

import java.io.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.gc.shared.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class SerialHeap extends CollectedHeap {

  public SerialHeap(Address addr) {
    super(addr);
  }

  public CollectedHeapName kind() {
    return CollectedHeapName.SERIAL;
  }

  private static AddressField youngGenField;
  private static AddressField oldGenField;

  private static GenerationFactory genFactory;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("SerialHeap");

    youngGenField = type.getAddressField("_young_gen");
    oldGenField = type.getAddressField("_old_gen");

    genFactory = new GenerationFactory();
  }

  public int nGens() {
    return 2; // Young + Old
  }

  public Generation getGen(int i) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that((i == 0) || (i == 1), "Index " + i +
                  " out of range (should be 0 or 1)");
    }

    switch (i) {
    case 0:
      return genFactory.newObject(youngGenField.getValue(addr));
    case 1:
      return genFactory.newObject(oldGenField.getValue(addr));
    default:
      // no generation for i, and assertions disabled.
      return null;
    }
  }

  public boolean isIn(Address a) {
    for (int i = 0; i < nGens(); i++) {
      Generation gen = getGen(i);
      if (gen.isIn(a)) {
        return true;
      }
    }

    return false;
  }

  public long capacity() {
    long capacity = 0;
    for (int i = 0; i < nGens(); i++) {
      capacity += getGen(i).capacity();
    }
    return capacity;
  }

  public long used() {
    long used = 0;
    for (int i = 0; i < nGens(); i++) {
      used += getGen(i).used();
    }
    return used;
  }

  public void liveRegionsIterate(LiveRegionsClosure closure) {
    // Run through all generations, obtaining bottom-top pairs.
    for (int i = 0; i < nGens(); i++) {
      Generation gen = getGen(i);
      gen.liveRegionsIterate(closure);
    }
  }

  public void printOn(PrintStream tty) {
    for (int i = 0; i < nGens(); i++) {
      tty.print("Gen " + i + ": ");
      getGen(i).printOn(tty);
      tty.println("Invocations: " + getGen(i).invocations());
      tty.println();
    }
  }
}
