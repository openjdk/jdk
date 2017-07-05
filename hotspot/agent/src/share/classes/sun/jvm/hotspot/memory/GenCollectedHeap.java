/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.gc_interface.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class GenCollectedHeap extends SharedHeap {
  private static CIntegerField nGensField;
  private static long gensOffset;
  private static AddressField genSpecsField;

  private static GenerationFactory genFactory;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("GenCollectedHeap");

    nGensField = type.getCIntegerField("_n_gens");
    gensOffset = type.getField("_gens").getOffset();
    genSpecsField = type.getAddressField("_gen_specs");

    genFactory = new GenerationFactory();
  }

  public GenCollectedHeap(Address addr) {
    super(addr);
  }

  public int nGens() {
    return (int) nGensField.getValue(addr);
  }

  public Generation getGen(int i) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that((i >= 0) && (i < nGens()), "Index " + i +
                  " out of range (should be between 0 and " + nGens() + ")");
    }

    if ((i < 0) || (i >= nGens())) {
      return null;
    }

    Address genAddr = addr.getAddressAt(gensOffset +
                                        (i * VM.getVM().getAddressSize()));
    return genFactory.newObject(addr.getAddressAt(gensOffset +
                                                  (i * VM.getVM().getAddressSize())));
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

  /** Package-private access to GenerationSpecs */
  GenerationSpec spec(int level) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that((level >= 0) && (level < nGens()), "Index " + level +
                  " out of range (should be between 0 and " + nGens() + ")");
    }

    if ((level < 0) || (level >= nGens())) {
      return null;
    }

    Address ptrList = genSpecsField.getValue(addr);
    if (ptrList == null) {
      return null;
    }
    return (GenerationSpec)
      VMObjectFactory.newObject(GenerationSpec.class,
                                ptrList.getAddressAt(level * VM.getVM().getAddressSize()));
  }

  public CollectedHeapName kind() {
    return CollectedHeapName.GEN_COLLECTED_HEAP;
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
