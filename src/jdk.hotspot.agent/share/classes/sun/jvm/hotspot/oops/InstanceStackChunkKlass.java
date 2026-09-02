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

  @Override
  public long getObjectSize(Oop object) {
    // Mirrors InstanceStackChunkKlass::oop_size in the VM, in bytes.
    long stackSizeInWords = ((IntField) findField("size", "I")).getValue(object);
    return instanceSize(stackSizeInWords);
  }

  private long instanceSize(long stackSizeInWords) {
    long sizeInWords = getSizeHelper() + stackSizeInWords + gcDataSize(stackSizeInWords);
    return Oop.alignObjectSize(sizeInWords * VM.getVM().getAddressSize());
  }

  private static long gcDataSize(long stackSizeInWords) {
    return bitmapSize(stackSizeInWords);
  }

  private static long bitmapSize(long stackSizeInWords) {
    long bitsPerWord = VM.getVM().getBytesPerWord() * 8L;
    return bitmapSizeInBits(stackSizeInWords) / bitsPerWord;
  }

  private static long bitmapSizeInBits(long stackSizeInWords) {
    VM vm = VM.getVM();
    // Need one bit per potential narrowOop* or oop* address.
    long bitsPerWord = vm.getBytesPerWord() * 8L;
    long sizeInBits = stackSizeInWords * (vm.getBytesPerWord() / vm.getHeapOopSize());
    return vm.alignUp(sizeInBits, bitsPerWord);
  }

  @Override
  public void iterateNonStaticFields(OopVisitor visitor, Oop obj) {
    super.iterateNonStaticFields(visitor, obj);
    iterateStackOops(visitor, obj);
  }

  // findField only sees the Java fields, this covers the ones injected by the VM.
  private Field findInjectedField(String name, String sig) {
    for (int i = getJavaFieldsCount(); i < getAllFieldsCount(); i++) {
      if (getFieldName(i).equals(name) && getFieldSignature(i).equals(sig)) {
        return getFieldByIndex(i);
      }
    }
    return null;
  }

  // Visits the oops in the copied stack, mirroring the bitmap path of
  // oop_oop_iterate_stack in the VM.
  public void iterateStackOops(OopVisitor visitor, Oop obj) {
    byte flags = ((ByteField) findInjectedField("flags", "B")).getValue(obj);
    if ((flags & 0x10) == 0) {   // FLAG_HAS_BITMAP, only set once the GC transforms the chunk
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
