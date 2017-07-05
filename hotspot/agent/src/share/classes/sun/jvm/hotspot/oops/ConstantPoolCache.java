/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

// A ConstantPool is an array containing class constants
// as described in the class file

public class ConstantPoolCache extends Array {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("constantPoolCacheOopDesc");
    constants      = new OopField(type.getOopField("_constant_pool"), 0);
    baseOffset     = type.getSize();

    Type elType    = db.lookupType("ConstantPoolCacheEntry");
    elementSize    = elType.getSize();
  }

  ConstantPoolCache(OopHandle handle, ObjectHeap heap) {
    super(handle, heap);
  }

  public boolean isConstantPoolCache() { return true; }

  private static OopField constants;

  private static long baseOffset;
  private static long elementSize;

  public ConstantPool getConstants() { return (ConstantPool) constants.getValue(this); }

  public long getObjectSize() {
    return alignObjectSize(baseOffset + getLength() * elementSize);
  }

  public ConstantPoolCacheEntry getEntryAt(int i) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(0 <= i && i < getLength(), "index out of bounds");
    }
    return new ConstantPoolCacheEntry(this, i);
  }

  public int getIntAt(int entry, int fld) {
    //alignObjectSize ?
    long offset = baseOffset + /*alignObjectSize*/entry * elementSize + fld* getHeap().getIntSize();
    return (int) getHandle().getCIntegerAt(offset, getHeap().getIntSize(), true );
  }


  public void printValueOn(PrintStream tty) {
    tty.print("ConstantPoolCache for " + getConstants().getPoolHolder().getName().asString());
  }

  public void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
    if (doVMFields) {
      visitor.doOop(constants, true);
      for (int i = 0; i < getLength(); i++) {
        ConstantPoolCacheEntry entry = getEntryAt(i);
        entry.iterateFields(visitor);
      }
    }
  }
};
