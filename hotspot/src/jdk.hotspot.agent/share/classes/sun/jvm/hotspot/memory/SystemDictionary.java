/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;

public class SystemDictionary {
  private static AddressField dictionaryField;
  private static AddressField sharedDictionaryField;
  private static AddressField placeholdersField;
  private static AddressField loaderConstraintTableField;
  private static sun.jvm.hotspot.types.OopField javaSystemLoaderField;

  private static AddressField objectKlassField;
  private static AddressField classLoaderKlassField;
  private static AddressField stringKlassField;
  private static AddressField systemKlassField;
  private static AddressField threadKlassField;
  private static AddressField threadGroupKlassField;
  private static AddressField methodHandleKlassField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("SystemDictionary");

    dictionaryField = type.getAddressField("_dictionary");
    sharedDictionaryField = type.getAddressField("_shared_dictionary");
    placeholdersField = type.getAddressField("_placeholders");
    loaderConstraintTableField = type.getAddressField("_loader_constraints");
    javaSystemLoaderField = type.getOopField("_java_system_loader");

    objectKlassField = type.getAddressField(WK_KLASS("Object_klass"));
    classLoaderKlassField = type.getAddressField(WK_KLASS("ClassLoader_klass"));
    stringKlassField = type.getAddressField(WK_KLASS("String_klass"));
    systemKlassField = type.getAddressField(WK_KLASS("System_klass"));
    threadKlassField = type.getAddressField(WK_KLASS("Thread_klass"));
    threadGroupKlassField = type.getAddressField(WK_KLASS("ThreadGroup_klass"));
    methodHandleKlassField = type.getAddressField(WK_KLASS("MethodHandle_klass"));
  }

  // This WK functions must follow the definitions in systemDictionary.hpp:
  private static String WK_KLASS(String name) {
      //#define WK_KLASS(name) _well_known_klasses[SystemDictionary::WK_KLASS_ENUM_NAME(name)]
      return ("_well_known_klasses[SystemDictionary::"+WK_KLASS_ENUM_NAME(name)+"]");
  }
  private static String WK_KLASS_ENUM_NAME(String kname) {
      //#define WK_KLASS_ENUM_NAME(kname)    kname##_knum
      return (kname+"_knum");
  }

  public Dictionary dictionary() {
    Address tmp = dictionaryField.getValue();
    return (Dictionary) VMObjectFactory.newObject(Dictionary.class, tmp);
  }

  public Dictionary sharedDictionary() {
    Address tmp = sharedDictionaryField.getValue();
    return (Dictionary) VMObjectFactory.newObject(Dictionary.class, tmp);
  }

  public PlaceholderTable placeholders() {
    Address tmp = placeholdersField.getValue();
    return (PlaceholderTable) VMObjectFactory.newObject(PlaceholderTable.class, tmp);
  }

  public LoaderConstraintTable constraints() {
    Address tmp = placeholdersField.getValue();
    return (LoaderConstraintTable) VMObjectFactory.newObject(LoaderConstraintTable.class, tmp);
  }

  // few well known classes -- not all are added here.
  // add more if needed.
  public static InstanceKlass getThreadKlass() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(threadKlassField.getValue());
  }

  public static InstanceKlass getThreadGroupKlass() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(threadGroupKlassField.getValue());
  }

  public static InstanceKlass getObjectKlass() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(objectKlassField.getValue());
  }

  public static InstanceKlass getStringKlass() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(stringKlassField.getValue());
  }

  public static InstanceKlass getClassLoaderKlass() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(classLoaderKlassField.getValue());
  }

  public static InstanceKlass getSystemKlass() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(systemKlassField.getValue());
  }

  public static InstanceKlass getMethodHandleKlass() {
    return (InstanceKlass)Metadata.instantiateWrapperFor(methodHandleKlassField.getValue());
  }

  public InstanceKlass getAbstractOwnableSynchronizerKlass() {
    return (InstanceKlass) find("java/util/concurrent/locks/AbstractOwnableSynchronizer",
                                null, null);
  }

  public static Oop javaSystemLoader() {
    return newOop(javaSystemLoaderField.getValue());
  }

  private static Oop newOop(OopHandle handle) {
    return VM.getVM().getObjectHeap().newOop(handle);
  }

  /** Lookup an already loaded class. If not found null is returned. */
  public Klass find(String className, Oop classLoader, Oop protectionDomain) {
    Symbol sym = VM.getVM().getSymbolTable().probe(className);
    if (sym == null) return null;
    return find(sym, classLoader, protectionDomain);
  }

  /** Lookup an already loaded class. If not found null is returned. */
  public Klass find(Symbol className, Oop classLoader, Oop protectionDomain) {
    Dictionary dict = dictionary();
    long hash = dict.computeHash(className, classLoader);
    int index = dict.hashToIndex(hash);
    return dict.find(index, hash, className, classLoader, protectionDomain);
  }

  /** Interface for iterating through all classes in dictionary */
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
  public void allClassesDo(final ClassVisitor v) {
    ClassVisitor visitor = new ClassVisitor() {
      public void visit(Klass k) {
        for (Klass l = k; l != null; l = l.arrayKlassOrNull()) {
          v.visit(l);
        }
      }
    };
    classesDo(visitor);
    VM.getVM().getUniverse().basicTypeClassesDo(visitor);
  }

  /** Iterate over all klasses in dictionary; just the classes from
      declaring class loaders */
  public void classesDo(ClassVisitor v) {
    dictionary().classesDo(v);
  }

  /** All classes, and their class loaders */
  public void classesDo(ClassAndLoaderVisitor v) {
    dictionary().classesDo(v);
  }

  /** All array classes of primitive type, and their class loaders */
  public void primArrayClassesDo(ClassAndLoaderVisitor v) {
    placeholders().primArrayClassesDo(v);
  }
}
