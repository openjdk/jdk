/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.classfile;

import java.io.PrintStream;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;

public class ClassLoaderData extends VMObject {
  static {
    VM.registerVMInitializedObserver(new java.util.Observer() {
        public void update(java.util.Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("ClassLoaderData");
    classLoaderField = type.getAddressField("_class_loader");
    nextField = type.getAddressField("_next");
    klassesField = new MetadataField(type.getAddressField("_klasses"), 0);
    isAnonymousField = new CIntField(type.getCIntegerField("_is_anonymous"), 0);
    dictionaryField = type.getAddressField("_dictionary");
  }

  private static AddressField   classLoaderField;
  private static AddressField nextField;
  private static MetadataField  klassesField;
  private static CIntField isAnonymousField;
  private static AddressField dictionaryField;

  public ClassLoaderData(Address addr) {
    super(addr);
  }

  public Dictionary dictionary() {
      Address tmp = dictionaryField.getValue();
      return (Dictionary) VMObjectFactory.newObject(Dictionary.class, tmp);
  }

  public static ClassLoaderData instantiateWrapperFor(Address addr) {
    if (addr == null) {
      return null;
    }
    return new ClassLoaderData(addr);
  }

  public Oop getClassLoader() {
    Address handle = classLoaderField.getValue(getAddress());
    if (handle != null) {
      // Load through the handle
      OopHandle refs = handle.getOopHandleAt(0);
      return (Instance)VM.getVM().getObjectHeap().newOop(refs);
    }
    return null;
  }

  public boolean getIsAnonymous() {
    return isAnonymousField.getValue(this) != 0;
  }

  public ClassLoaderData next() {
    return instantiateWrapperFor(nextField.getValue(getAddress()));
  }

  public Klass getKlasses()    { return (Klass)klassesField.getValue(this);  }

  /** Lookup an already loaded class. If not found null is returned. */
  public Klass find(Symbol className) {
    for (Klass l = getKlasses(); l != null; l = l.getNextLinkKlass()) {
        if (className.equals(l.getName())) {
            return l;
        }
    }
    return null;
  }

  /** Iterate over all klasses - including object, primitive
      array klasses */
  public void classesDo(ClassLoaderDataGraph.ClassVisitor v) {
      for (Klass l = getKlasses(); l != null; l = l.getNextLinkKlass()) {
          v.visit(l);
      }
  }

  /** Iterate over all klasses in the dictionary, including initiating loader. */
  public void allEntriesDo(ClassLoaderDataGraph.ClassAndLoaderVisitor v) {
      for (Klass l = getKlasses(); l != null; l = l.getNextLinkKlass()) {
          dictionary().allEntriesDo(v, getClassLoader());
      }
  }
}
