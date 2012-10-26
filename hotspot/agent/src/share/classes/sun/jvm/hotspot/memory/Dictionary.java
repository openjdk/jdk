/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class Dictionary extends TwoOopHashtable {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    // just checking that the type exists
    Type type = db.lookupType("Dictionary");
  }

  public Dictionary(Address addr) {
    super(addr);
  }

  // this is overriden here so that Hashtable.bucket will return
  // object of DictionaryEntry.class
  protected Class getHashtableEntryClass() {
    return DictionaryEntry.class;
  }

  /** Iterate over all klasses in dictionary; just the classes from
      declaring class loaders */
  public void classesDo(SystemDictionary.ClassVisitor v) {
    ObjectHeap heap = VM.getVM().getObjectHeap();
    int tblSize = tableSize();
    for (int index = 0; index < tblSize; index++) {
      for (DictionaryEntry probe = (DictionaryEntry) bucket(index); probe != null;
                                             probe = (DictionaryEntry) probe.next()) {
        Klass k = probe.klass();
        if (heap.equal(probe.loader(), ((InstanceKlass) k).getClassLoader())) {
            v.visit(k);
        }
      }
    }
  }

  /** All classes, and their class loaders */
  public void classesDo(SystemDictionary.ClassAndLoaderVisitor v) {
    int tblSize = tableSize();
    for (int index = 0; index < tblSize; index++) {
      for (DictionaryEntry probe = (DictionaryEntry) bucket(index); probe != null;
                                             probe = (DictionaryEntry) probe.next()) {
        Klass k = probe.klass();
        v.visit(k, probe.loader());
      }
    }
  }

  public Klass find(int index, long hash, Symbol className, Oop classLoader, Oop protectionDomain) {
    DictionaryEntry entry = getEntry(index, hash, className, classLoader);
    if (entry != null && entry.isValidProtectionDomain(protectionDomain)) {
      return entry.klass();
    }
    return null;
  }

  // - Internals only below this point

  private DictionaryEntry getEntry(int index, long hash, Symbol className, Oop classLoader) {
    for (DictionaryEntry entry = (DictionaryEntry) bucket(index); entry != null;
                                    entry = (DictionaryEntry) entry.next()) {
      if (entry.hash() == hash && entry.equals(className, classLoader)) {
        return entry;
      }
    }
    return null;
  }
}
