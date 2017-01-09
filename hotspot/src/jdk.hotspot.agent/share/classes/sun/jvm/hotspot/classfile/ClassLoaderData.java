/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;

public class ClassLoaderData extends VMObject {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("ClassLoaderData");
    classLoaderField = type.getOopField("_class_loader");
    nextField = type.getAddressField("_next");
    klassesField = type.getAddressField("_klasses");
    isAnonymousField = new CIntField(type.getCIntegerField("_is_anonymous"), 0);
  }

  private static sun.jvm.hotspot.types.OopField classLoaderField;
  private static AddressField nextField;
  private static AddressField klassesField;
  private static CIntField isAnonymousField;

  public ClassLoaderData(Address addr) {
    super(addr);
  }

  public static ClassLoaderData instantiateWrapperFor(Address addr) {
    if (addr == null) {
      return null;
    }
    return new ClassLoaderData(addr);
  }

  public Oop getClassLoader() {
    return VM.getVM().getObjectHeap().newOop(classLoaderField.getValue(getAddress()));
  }

  public boolean getIsAnonymous() {
    return isAnonymousField.getValue(this) != 0;
  }

  public ClassLoaderData next() {
    return instantiateWrapperFor(nextField.getValue(getAddress()));
  }

  public Klass getKlasses() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(klassesField.getValue(getAddress()));
  }
}
