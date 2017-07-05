/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;

// ArrayKlass is the abstract class for all array classes

public class ArrayKlass extends Klass {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type          = db.lookupType("ArrayKlass");
    dimension          = new CIntField(type.getCIntegerField("_dimension"), 0);
    higherDimension    = new MetadataField(type.getAddressField("_higher_dimension"), 0);
    lowerDimension     = new MetadataField(type.getAddressField("_lower_dimension"), 0);
    vtableLen          = new CIntField(type.getCIntegerField("_vtable_len"), 0);
    componentMirror    = new OopField(type.getOopField("_component_mirror"), 0);
    javaLangCloneableName = null;
    javaLangObjectName = null;
    javaIoSerializableName = null;
  }

  public ArrayKlass(Address addr) {
    super(addr);
  }

  private static CIntField dimension;
  private static MetadataField  higherDimension;
  private static MetadataField  lowerDimension;
  private static CIntField vtableLen;
  private static OopField  componentMirror;

  public Klass getJavaSuper() {
    SystemDictionary sysDict = VM.getVM().getSystemDictionary();
    return sysDict.getObjectKlass();
  }

  public long  getDimension()       { return         dimension.getValue(this); }
  public Klass getHigherDimension() { return (Klass) higherDimension.getValue(this); }
  public Klass getLowerDimension()  { return (Klass) lowerDimension.getValue(this); }
  public long  getVtableLen()       { return         vtableLen.getValue(this); }
  public Oop   getComponentMirror() { return         componentMirror.getValue(this); }

  // constant class names - javaLangCloneable, javaIoSerializable, javaLangObject
  // Initialized lazily to avoid initialization ordering dependencies between ArrayKlass and SymbolTable
  private static Symbol javaLangCloneableName;
  private static Symbol javaLangObjectName;
  private static Symbol javaIoSerializableName;
  private static Symbol javaLangCloneableName() {
    if (javaLangCloneableName == null) {
      javaLangCloneableName = VM.getVM().getSymbolTable().probe("java/lang/Cloneable");
    }
    return javaLangCloneableName;
  }

  private static Symbol javaLangObjectName() {
    if (javaLangObjectName == null) {
      javaLangObjectName = VM.getVM().getSymbolTable().probe("java/lang/Object");
    }
    return javaLangObjectName;
  }

  private static Symbol javaIoSerializableName() {
    if (javaIoSerializableName == null) {
      javaIoSerializableName = VM.getVM().getSymbolTable().probe("java/io/Serializable");
    }
    return javaIoSerializableName;
  }

  public int getClassStatus() {
     return JVMDIClassStatus.VERIFIED | JVMDIClassStatus.PREPARED | JVMDIClassStatus.INITIALIZED;
  }

  public long computeModifierFlags() {
     return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
  }

  public long getArrayHeaderInBytes() {
    return Bits.maskBits(getLayoutHelper() >> LH_HEADER_SIZE_SHIFT, 0xFF);
  }

  public int getLog2ElementSize() {
    return Bits.maskBits(getLayoutHelper() >> LH_LOG2_ELEMENT_SIZE_SHIFT, 0xFF);
  }

  public int getElementType() {
    return Bits.maskBits(getLayoutHelper() >> LH_ELEMENT_TYPE_SHIFT, 0xFF);
  }

  boolean computeSubtypeOf(Klass k) {
    // An array is a subtype of Serializable, Clonable, and Object
    Symbol name = k.getName();
    if (name != null && (name.equals(javaIoSerializableName()) ||
                         name.equals(javaLangCloneableName()) ||
                         name.equals(javaLangObjectName()))) {
      return true;
    } else {
      return false;
    }
  }

  public void printValueOn(PrintStream tty) {
    tty.print("ArrayKlass");
  }

  public void iterateFields(MetadataVisitor visitor) {
    super.iterateFields(visitor);
      visitor.doCInt(dimension, true);
    visitor.doMetadata(higherDimension, true);
    visitor.doMetadata(lowerDimension, true);
      visitor.doCInt(vtableLen, true);
      visitor.doOop(componentMirror, true);
    }
  }
