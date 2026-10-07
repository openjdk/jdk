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
  private static int metadataWordsAtBottom;

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
    metadataWordsAtBottom = db.lookupIntConstant("frame::metadata_words_at_bottom").intValue();
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

  // Visit the bitmap range from sp to the end of the stack, then the lock
  // stack, the way oop_oop_iterate_stack does in the VM.
  public void iterateStackOops(OopVisitor visitor, Oop obj) {
    if (!hasBitmap(obj)) {
      return;
    }
    VM vm = VM.getVM();
    long wordSize = vm.getAddressSize();
    long oopSize = vm.getHeapOopSize();
    long slotsPerWord = wordSize / oopSize;
    long stackSizeInWords = ((IntField) findField("size", "I")).getValue(obj);
    long sp = ((IntField) findField("sp", "I")).getValue(obj);
    long headerBytes = getSizeHelper() * wordSize;
    long bitmapBytes = headerBytes + stackSizeInWords * wordSize;
    long bitsPerWord = wordSize * 8L;
    long slotCount = stackSizeInWords * slotsPerWord;
    // the saved frame pointer below sp can hold an oop, so start where the VM starts
    long firstSlot = Math.max(0L, sp - metadataWordsAtBottom) * slotsPerWord;
    Address base = obj.getHandle();
    for (long w = firstSlot / bitsPerWord; w * bitsPerWord < slotCount; w++) {
      long word = base.getCIntegerAt(bitmapBytes + w * wordSize, wordSize, true);
      if (word == 0) {
        continue;
      }
      for (long b = 0; b < bitsPerWord; b++) {
        long index = w * bitsPerWord + b;
        if (index >= slotCount) {
          break;
        }
        if (index < firstSlot || ((word >>> b) & 1) == 0) {
          continue;
        }
        visitStackOop(visitor, index, headerBytes + index * oopSize);
      }
    }
    // lock stack entries sit at the start of the stack, one machine word each
    int lockStackSize = Byte.toUnsignedInt(((ByteField) findInjectedField("lockStackSize", "B")).getValue(obj));
    for (int i = 0; i < lockStackSize; i++) {
      visitStackOop(visitor, i * slotsPerWord, headerBytes + i * wordSize);
    }
  }

  public boolean hasBitmap(Oop obj) {
    byte flags = ((ByteField) findInjectedField("flags", "B")).getValue(obj);
    return (flags & 0x10) != 0;   // FLAG_HAS_BITMAP, only set once the GC transforms the chunk
  }

  private void visitStackOop(OopVisitor visitor, long index, long offset) {
    FieldIdentifier id = new IndexableFieldIdentifier((int) index);
    OopField field = VM.getVM().isCompressedOopsEnabled()
        ? new NarrowSlotField(id, offset) : new SlotField(id, offset);
    visitor.doOop(field, false);
  }

  // Stack slots have no field info behind them, so the flat field checks answer here.
  private static class SlotField extends OopField {
    SlotField(FieldIdentifier id, long offset) {
      super(id, offset, false);
    }
    @Override
    public boolean isFlat() {
      return false;
    }
    @Override
    public boolean hasNullMarker() {
      return false;
    }
  }

  private static class NarrowSlotField extends NarrowOopField {
    NarrowSlotField(FieldIdentifier id, long offset) {
      super(id, offset, false);
    }
    @Override
    public boolean isFlat() {
      return false;
    }
    @Override
    public boolean hasNullMarker() {
      return false;
    }
  }
}
