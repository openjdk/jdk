/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.interpreter.Bytecodes;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

// An Annotation is an oop containing annotations as described in the class file

public class Annotations extends Metadata {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private ArrayOfU1Array fieldAnnotationsArray;
  private ArrayOfU1Array fieldTypeAnnotationsArray;

  public Annotations(Address addr) {
    super(addr);
  }

  public boolean isAnnotations()            { return true; }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type = db.lookupType("Annotations");
    classAnnotations = type.getAddressField("_class_annotations");
    fieldsAnnotations = type.getAddressField("_fields_annotations");
    classTypeAnnotations = type.getAddressField("_class_type_annotations");
    fieldsTypeAnnotations = type.getAddressField("_fields_type_annotations");
  }

  private static AddressField classAnnotations;
  private static AddressField fieldsAnnotations;
  private static AddressField classTypeAnnotations;
  private static AddressField fieldsTypeAnnotations;

  public U1Array getClassAnnotations() {
    Address addr = classAnnotations.getValue(getAddress());
    return VMObjectFactory.newObject(U1Array.class, addr);
  }

  public U1Array getFieldAnnotations(int fieldIndex) {
    if (fieldAnnotationsArray == null) {
      Address addr = fieldsAnnotations.getValue(getAddress());
      fieldAnnotationsArray = VMObjectFactory.newObject(ArrayOfU1Array.class, addr);
    }
    if (fieldAnnotationsArray != null) {
      Address addr = fieldAnnotationsArray.at(fieldIndex);
      return VMObjectFactory.newObject(U1Array.class, addr);
    } else {
      return null;
    }
  }

  public U1Array getClassTypeAnnotations() {
    Address addr = classTypeAnnotations.getValue(getAddress());
    return VMObjectFactory.newObject(U1Array.class, addr);
  }

  public U1Array getFieldTypeAnnotations(int fieldIndex) {
    if (fieldTypeAnnotationsArray == null) {
      Address addr = fieldsTypeAnnotations.getValue(getAddress());
      fieldTypeAnnotationsArray = VMObjectFactory.newObject(ArrayOfU1Array.class, addr);
    }
    if (fieldTypeAnnotationsArray != null) {
      Address addr = fieldTypeAnnotationsArray.at(fieldIndex);
      return VMObjectFactory.newObject(U1Array.class, addr);
    } else {
      return null;
    }
  }

  public void printValueOn(PrintStream tty) {
      tty.print("Annotations" + "@" + getAddress());
  }
}
