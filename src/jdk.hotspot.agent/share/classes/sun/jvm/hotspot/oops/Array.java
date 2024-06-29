/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

// Array is an abstract superclass for TypeArray and ObjArray

public class Array extends Oop {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  Array(OopHandle handle, ObjectHeap heap) {
    super(handle, heap);
  }

  private static void initialize(TypeDataBase db) throws WrongTypeException {
    Type type   = db.lookupType("arrayOopDesc");
    typeSize    = (int)type.getSize();
  }

  // Size of the arrayOopDesc
  private static long headerSize=0;
  private static long lengthOffsetInBytes=0;
  private static long typeSize;

  // Check whether an element of a arrayOop with the given type must be
  // aligned 0 mod 8.  The arrayOop itself must be aligned at least this
  // strongly.
  private static boolean elementTypeShouldBeAligned(BasicType type) {
    if (VM.getVM().isLP64()) {
      if (type == BasicType.T_OBJECT || type == BasicType.T_ARRAY) {
        return !VM.getVM().isCompressedOopsEnabled();
      }
    }
    return type == BasicType.T_DOUBLE || type == BasicType.T_LONG;
  }

  private static long headerSizeInBytes() {
    if (headerSize != 0) {
      return headerSize;
    }
    headerSize = lengthOffsetInBytes() + VM.getVM().getIntSize();
    return headerSize;
  }

  private static long lengthOffsetInBytes() {
    if (lengthOffsetInBytes != 0) {
      return lengthOffsetInBytes;
    }
    if (VM.getVM().isCompressedKlassPointersEnabled()) {
      lengthOffsetInBytes = typeSize - VM.getVM().getIntSize();
    } else {
      lengthOffsetInBytes = typeSize;
    }
    return lengthOffsetInBytes;
  }

  // Accessors for declared fields
  public long getLength() {
    boolean isUnsigned = true;
    return this.getHandle().getCIntegerAt(lengthOffsetInBytes(), VM.getVM().getIntSize(), isUnsigned);
  }

  public long getObjectSize() {
    ArrayKlass klass = (ArrayKlass) getKlass();
    // We have to fetch the length of the array, shift (multiply) it
    // appropriately, up to wordSize, add the header, and align to
    // object size.
    long s = getLength() << klass.getLog2ElementSize();
    s += klass.getArrayHeaderInBytes();
    s = Oop.alignObjectSize(s);
    return s;
  }

  public static long baseOffsetInBytes(BasicType type) {
    long typeSizeInBytes = headerSizeInBytes();
    if (elementTypeShouldBeAligned(type)) {
      VM vm = VM.getVM();
      return vm.alignUp(typeSizeInBytes, vm.getVM().getHeapWordSize());
    } else {
      return typeSizeInBytes;
    }
  }

  public boolean isArray()             { return true; }

  public void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
  }
}
