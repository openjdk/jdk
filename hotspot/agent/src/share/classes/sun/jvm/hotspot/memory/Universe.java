/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.jvm.hotspot.memory;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.gc_interface.*;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;


public class Universe {
  private static AddressField collectedHeapField;
  private static VirtualConstructor heapConstructor;
  private static sun.jvm.hotspot.types.OopField mainThreadGroupField;
  private static sun.jvm.hotspot.types.OopField systemThreadGroupField;

  // single dimensional primitive array klasses
  private static sun.jvm.hotspot.types.OopField boolArrayKlassObjField;
  private static sun.jvm.hotspot.types.OopField byteArrayKlassObjField;
  private static sun.jvm.hotspot.types.OopField charArrayKlassObjField;
  private static sun.jvm.hotspot.types.OopField intArrayKlassObjField;
  private static sun.jvm.hotspot.types.OopField shortArrayKlassObjField;
  private static sun.jvm.hotspot.types.OopField longArrayKlassObjField;
  private static sun.jvm.hotspot.types.OopField singleArrayKlassObjField;
  private static sun.jvm.hotspot.types.OopField doubleArrayKlassObjField;

  // system obj array klass object
  private static sun.jvm.hotspot.types.OopField systemObjArrayKlassObjField;

  private static AddressField narrowOopBaseField;
  private static CIntegerField narrowOopShiftField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("Universe");

    collectedHeapField = type.getAddressField("_collectedHeap");

    heapConstructor = new VirtualConstructor(db);
    heapConstructor.addMapping("GenCollectedHeap", GenCollectedHeap.class);
    heapConstructor.addMapping("ParallelScavengeHeap", ParallelScavengeHeap.class);

    mainThreadGroupField   = type.getOopField("_main_thread_group");
    systemThreadGroupField = type.getOopField("_system_thread_group");

    boolArrayKlassObjField = type.getOopField("_boolArrayKlassObj");
    byteArrayKlassObjField = type.getOopField("_byteArrayKlassObj");
    charArrayKlassObjField = type.getOopField("_charArrayKlassObj");
    intArrayKlassObjField = type.getOopField("_intArrayKlassObj");
    shortArrayKlassObjField = type.getOopField("_shortArrayKlassObj");
    longArrayKlassObjField = type.getOopField("_longArrayKlassObj");
    singleArrayKlassObjField = type.getOopField("_singleArrayKlassObj");
    doubleArrayKlassObjField = type.getOopField("_doubleArrayKlassObj");

    systemObjArrayKlassObjField = type.getOopField("_systemObjArrayKlassObj");

    narrowOopBaseField = type.getAddressField("_narrow_oop._base");
    narrowOopShiftField = type.getCIntegerField("_narrow_oop._shift");
  }

  public Universe() {
  }

  public CollectedHeap heap() {
    try {
      return (CollectedHeap) heapConstructor.instantiateWrapperFor(collectedHeapField.getValue());
    } catch (WrongTypeException e) {
      return new CollectedHeap(collectedHeapField.getValue());
    }
  }

  public static long getNarrowOopBase() {
    if (narrowOopBaseField.getValue() == null) {
      return 0;
    } else {
      return narrowOopBaseField.getValue().minus(null);
    }
  }

  public static int getNarrowOopShift() {
    return (int)narrowOopShiftField.getValue();
  }

  /** Returns "TRUE" iff "p" points into the allocated area of the heap. */
  public boolean isIn(Address p) {
    return heap().isIn(p);
  }

  /** Returns "TRUE" iff "p" points into the reserved area of the heap. */
  public boolean isInReserved(Address p) {
    return heap().isInReserved(p);
  }

  private Oop newOop(OopHandle handle) {
    return VM.getVM().getObjectHeap().newOop(handle);
  }

  public Oop mainThreadGroup() {
    return newOop(mainThreadGroupField.getValue());
  }

  public Oop systemThreadGroup() {
    return newOop(systemThreadGroupField.getValue());
  }

  public Oop systemObjArrayKlassObj() {
    return newOop(systemObjArrayKlassObjField.getValue());
  }

  // iterate through the single dimensional primitive array klasses
  // refer to basic_type_classes_do(void f(klassOop)) in universe.cpp
  public void basicTypeClassesDo(SystemDictionary.ClassVisitor visitor) {
    visitor.visit((Klass)newOop(boolArrayKlassObjField.getValue()));
    visitor.visit((Klass)newOop(byteArrayKlassObjField.getValue()));
    visitor.visit((Klass)newOop(charArrayKlassObjField.getValue()));
    visitor.visit((Klass)newOop(intArrayKlassObjField.getValue()));
    visitor.visit((Klass)newOop(shortArrayKlassObjField.getValue()));
    visitor.visit((Klass)newOop(longArrayKlassObjField.getValue()));
    visitor.visit((Klass)newOop(singleArrayKlassObjField.getValue()));
    visitor.visit((Klass)newOop(doubleArrayKlassObjField.getValue()));
  }

  public void print() { printOn(System.out); }
  public void printOn(PrintStream tty) {
    heap().printOn(tty);
  }

  // Check whether an element of a typeArrayOop with the given type must be
  // aligned 0 mod 8.  The typeArrayOop itself must be aligned at least this
  // strongly.
  public static boolean elementTypeShouldBeAligned(BasicType type) {
    return type == BasicType.T_DOUBLE || type == BasicType.T_LONG;
  }

  // Check whether an object field (static/non-static) of the given type must be
  // aligned 0 mod 8.
  public static boolean fieldTypeShouldBeAligned(BasicType type) {
    return type == BasicType.T_DOUBLE || type == BasicType.T_LONG;
  }
}
