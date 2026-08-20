/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

// An InstanceKlass is the VM level representation of a Java class.

public class InstanceStackChunkKlass extends InstanceKlass {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    // Just make sure it's there for now
    Type type = db.lookupType("InstanceStackChunkKlass");
  }

  public InstanceStackChunkKlass(Address addr) {
    super(addr);
  }

  public long getObjectSize(Oop object) {
    // Mirrors InstanceStackChunkKlass::oop_size in the VM.
    long stackSizeInWords = ((IntField) findField("size", "I")).getValue(object);
    VM vm = VM.getVM();
    long bitsPerWord = vm.getBytesPerWord() * 8L;
    long bitmapBits = stackSizeInWords * (vm.getBytesPerWord() / vm.getHeapOopSize());
    bitmapBits = (bitmapBits + bitsPerWord - 1) & ~(bitsPerWord - 1);
    long gcDataInWords = bitmapBits / bitsPerWord;
    long sizeInWords = getSizeHelper() + stackSizeInWords + gcDataInWords;
    return Oop.alignObjectSize(sizeInWords * vm.getAddressSize());
  }

  public void iterateNonStaticFields(OopVisitor visitor, Oop obj) {
    super.iterateNonStaticFields(visitor, obj);
    // Visit the oops in the copied stack. Mirrors the bitmap path of
    // oop_oop_iterate_stack in the VM. Chunks the GC has not transformed
    // yet have no bitmap and their frames are not visited here.
    byte flags = ((ByteField) findField("flags", "B")).getValue(obj);
    if ((flags & 0x10) == 0) {   // FLAG_HAS_BITMAP
      return;
    }
    VM vm = VM.getVM();
    long wordSize = vm.getAddressSize();
    long oopSize = vm.getHeapOopSize();
    long stackSizeInWords = ((IntField) findField("size", "I")).getValue(obj);
    long headerBytes = getSizeHelper() * wordSize;
    long bitmapBytes = headerBytes + stackSizeInWords * wordSize;
    long bitsPerWord = wordSize * 8L;
    long slotCount = stackSizeInWords * (wordSize / oopSize);
    Address base = obj.getHandle();
    for (long w = 0; w * bitsPerWord < slotCount; w++) {
      long word = base.getCIntegerAt(bitmapBytes + w * wordSize, wordSize, true);
      if (word == 0) {
        continue;
      }
      for (long b = 0; b < bitsPerWord; b++) {
        long index = w * bitsPerWord + b;
        if (index >= slotCount) {
          break;
        }
        if (((word >>> b) & 1) == 0) {
          continue;
        }
        long offset = headerBytes + index * oopSize;
        OopField field;
        if (vm.isCompressedOopsEnabled()) {
          field = new NarrowOopField(new IndexableFieldIdentifier((int) index), offset, false);
        } else {
          field = new OopField(new IndexableFieldIdentifier((int) index), offset, false);
        }
        visitor.doOop(field, false);
      }
    }
  }
}
