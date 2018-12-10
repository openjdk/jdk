/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;
import java.util.Observable;
import java.util.Observer;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.gc.cms.CMSHeap;
import sun.jvm.hotspot.gc.epsilon.EpsilonHeap;
import sun.jvm.hotspot.gc.g1.G1CollectedHeap;
import sun.jvm.hotspot.gc.parallel.ParallelScavengeHeap;
import sun.jvm.hotspot.gc.serial.SerialHeap;
import sun.jvm.hotspot.gc.shared.CollectedHeap;
import sun.jvm.hotspot.gc.shenandoah.ShenandoahHeap;
import sun.jvm.hotspot.gc.z.ZCollectedHeap;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.runtime.BasicType;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VirtualConstructor;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;


public class Universe {
  private static AddressField collectedHeapField;
  private static VirtualConstructor heapConstructor;
  private static sun.jvm.hotspot.types.OopField mainThreadGroupField;
  private static sun.jvm.hotspot.types.OopField systemThreadGroupField;

  private static AddressField narrowOopBaseField;
  private static CIntegerField narrowOopShiftField;
  private static AddressField narrowKlassBaseField;
  private static CIntegerField narrowKlassShiftField;

  public enum NARROW_OOP_MODE {
    UnscaledNarrowOop,
    ZeroBasedNarrowOop,
    HeapBasedNarrowOop
  }

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static boolean typeExists(TypeDataBase db, String type) {
      try {
          db.lookupType(type);
      } catch (RuntimeException e) {
          return false;
      }
      return true;
  }

  private static void addHeapTypeIfInDB(TypeDataBase db, Class heapClass) {
      String heapName = heapClass.getSimpleName();
      if (typeExists(db, heapName)) {
          heapConstructor.addMapping(heapName, heapClass);
      }
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("Universe");

    collectedHeapField = type.getAddressField("_collectedHeap");

    heapConstructor = new VirtualConstructor(db);
    addHeapTypeIfInDB(db, CMSHeap.class);
    addHeapTypeIfInDB(db, SerialHeap.class);
    addHeapTypeIfInDB(db, ParallelScavengeHeap.class);
    addHeapTypeIfInDB(db, G1CollectedHeap.class);
    addHeapTypeIfInDB(db, EpsilonHeap.class);
    addHeapTypeIfInDB(db, ZCollectedHeap.class);
    addHeapTypeIfInDB(db, ShenandoahHeap.class);

    mainThreadGroupField   = type.getOopField("_main_thread_group");
    systemThreadGroupField = type.getOopField("_system_thread_group");

    narrowOopBaseField = type.getAddressField("_narrow_oop._base");
    narrowOopShiftField = type.getCIntegerField("_narrow_oop._shift");
    narrowKlassBaseField = type.getAddressField("_narrow_klass._base");
    narrowKlassShiftField = type.getCIntegerField("_narrow_klass._shift");

    UniverseExt.initialize(heapConstructor);
  }

  public Universe() {
  }
  public static String narrowOopModeToString(NARROW_OOP_MODE mode) {
    switch (mode) {
    case UnscaledNarrowOop:
      return "32-bits Oops";
    case ZeroBasedNarrowOop:
      return "zero based Compressed Oops";
    case HeapBasedNarrowOop:
      return "Compressed Oops with base";
    }
    return "";
  }
  public CollectedHeap heap() {
    return (CollectedHeap) heapConstructor.instantiateWrapperFor(collectedHeapField.getValue());
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

  public static long getNarrowKlassBase() {
    if (narrowKlassBaseField.getValue() == null) {
      return 0;
    } else {
      return narrowKlassBaseField.getValue().minus(null);
    }
  }

  public static int getNarrowKlassShift() {
    return (int)narrowKlassShiftField.getValue();
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
