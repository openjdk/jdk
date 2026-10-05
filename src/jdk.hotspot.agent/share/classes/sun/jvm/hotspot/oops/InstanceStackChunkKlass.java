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
}
