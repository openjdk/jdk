/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

//  ConstantPoolCache : A constant pool cache (ConstantPoolCache).
//  See cpCache.hpp for details about this class.
//
public class ConstantPoolCache extends Metadata {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("ConstantPoolCache");
    constants      = new MetadataField(type.getAddressField("_constant_pool"), 0);
    baseOffset     = type.getSize();
    intSize        = VM.getVM().getObjectHeap().getIntSize();
    resolvedReferences = type.getAddressField("_resolved_references");
    referenceMap   = type.getAddressField("_reference_map");
    resolvedFieldArray = type.getAddressField("_resolved_field_entries");
    resolvedMethodArray = type.getAddressField("_resolved_method_entries");
    resolvedIndyArray = type.getAddressField("_resolved_indy_entries");
  }

  public ConstantPoolCache(Address addr) {
    super(addr);
  }

  public boolean isConstantPoolCache() { return true; }

  private static MetadataField constants;

  private static long baseOffset;
  private static long elementSize;
  private static CIntField length;
  private static long intSize;
  private static AddressField  resolvedReferences;
  private static AddressField  referenceMap;
  private static AddressField  resolvedFieldArray;
  private static AddressField  resolvedMethodArray;
  private static AddressField  resolvedIndyArray;

  public ConstantPool getConstants() { return (ConstantPool) constants.getValue(this); }

  public long getSize() {
    return alignSize(baseOffset);
  }

  public ResolvedIndyEntry getIndyEntryAt(int i) {
    Address addr = resolvedIndyArray.getValue(getAddress());
    ResolvedIndyArray array = new ResolvedIndyArray(addr);
    return array.getAt(i);
  }

  public ResolvedFieldEntry getFieldEntryAt(int i) {
    Address addr = resolvedFieldArray.getValue(getAddress());
    ResolvedFieldArray array = new ResolvedFieldArray(addr);
    return array.getAt(i);
  }

  public ResolvedMethodEntry getMethodEntryAt(int i) {
    Address addr = resolvedMethodArray.getValue(getAddress());
    ResolvedMethodArray array = new ResolvedMethodArray(addr);
    return array.getAt(i);
  }

  public int getIntAt(int entry, int fld) {
    long offset = baseOffset + entry * elementSize + fld * intSize;
    return (int) getAddress().getCIntegerAt(offset, intSize, true );
  }


  public void printValueOn(PrintStream tty) {
    tty.print("ConstantPoolCache for " + getConstants().getPoolHolder().getName().asString() + " address = " + getAddress() + " offset = " + baseOffset);
  }

  public Oop getResolvedReferences() {
    Address handle = resolvedReferences.getValue(getAddress());
    if (handle != null) {
      // Load through the handle
      OopHandle refs = handle.getOopHandleAt(0);
      return VM.getVM().getObjectHeap().newOop(refs);
    }
    return null;
  }

  public U2Array referenceMap() {
    return new U2Array(referenceMap.getValue(getAddress()));
  }
};
