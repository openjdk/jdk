/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.classfile.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;

public class ClassLoaderDataGraph {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type = db.lookupType("ClassLoaderDataGraph");

    headField = type.getAddressField("_head");
  }

  private static AddressField headField;

  public ClassLoaderData getClassLoaderGraphHead() {
    return ClassLoaderData.instantiateWrapperFor(headField.getValue());
  }

  /** Lookup an already loaded class in any class loader. */
  public Klass find(String className) {
    Symbol sym = VM.getVM().getSymbolTable().probe(className);
    if (sym == null) return null;
    for (ClassLoaderData cld = getClassLoaderGraphHead(); cld != null; cld = cld.next()) {
        Klass k = cld.find(sym);
        if (k != null) {
            return k;
        }
    }
    return null;
  }

  /** Interface for iterating through all classes. */
  public static interface ClassVisitor {
    public void visit(Klass k);
  }

  /** Interface for iterating through all classes and their class
      loaders in dictionary */
  public static interface ClassAndLoaderVisitor {
    public void visit(Klass k, Oop loader);
  }

  /** Iterate over all klasses - including object, primitive
      array klasses */
  public void classesDo(ClassVisitor v) {
    for (ClassLoaderData cld = getClassLoaderGraphHead(); cld != null; cld = cld.next()) {
        cld.classesDo(v);
    }
  }

  /** Iterate over all klasses - including object, primitive
      array klasses, pass initiating loader. */
  public void allEntriesDo(ClassAndLoaderVisitor v) {
    for (ClassLoaderData cld = getClassLoaderGraphHead(); cld != null; cld = cld.next()) {
        cld.allEntriesDo(v);
    }
  }
}
