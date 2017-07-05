/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;

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
    length      = new CIntField(type.getCIntegerField("_length"), 0);
    headerSize  = type.getSize();
  }

  // Size of the arrayOopDesc
  private static long headerSize;

  // Fields
  private static CIntField length;

  // Accessors for declared fields
  public long getLength() { return length.getValue(this); }

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
    if (Universe.elementTypeShouldBeAligned(type)) {
      return (VM.getVM().isLP64()) ?  alignObjectSize(headerSize)
                                   : VM.getVM().alignUp(headerSize, 8);
    } else {
      return headerSize;
    }
  }

  public boolean isArray()             { return true; }

  public void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
    if (doVMFields) {
      visitor.doCInt(length, true);
    }
  }
}
