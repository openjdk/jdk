/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class SymbolTable extends sun.jvm.hotspot.utilities.Hashtable {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("SymbolTable");
    theTableField  = type.getAddressField("_the_table");
    sharedTableField = type.getAddressField("_shared_table");
    type = db.lookupType("RehashableSymbolHashtable");
    seedField = type.getCIntegerField("_seed");
  }

  // Fields
  private static AddressField theTableField;
  private static AddressField sharedTableField;
  private static CIntegerField seedField;

  private CompactHashTable sharedTable;

  // Accessors
  public static SymbolTable getTheTable() {
    Address tmp = theTableField.getValue();
    SymbolTable table = (SymbolTable) VMObjectFactory.newObject(SymbolTable.class, tmp);
    Address shared = sharedTableField.getStaticFieldAddress();
    table.sharedTable = (CompactHashTable)VMObjectFactory.newObject(CompactHashTable.class, shared);
    return table;
  }

  public static long getSeed() {
      return (long) seedField.getValue();
  }

  public static boolean useAlternateHashcode() {
      if (getSeed() != 0) {
          return true;
      }
      return false;
  }

  public SymbolTable(Address addr) {
    super(addr);
  }

  /** Clone of VM's "temporary" probe routine, as the SA currently
      does not support mutation so lookup() would have no effect
      anyway. Returns null if the given string is not in the symbol
      table. */
  public Symbol probe(String name) {
    try {
      return probe(toModifiedUTF8Bytes(name));
    } catch (IOException e) {
      return null;
    }
  }

  /** Clone of VM's "temporary" probe routine, as the SA currently
      does not support mutation so lookup() would have no effect
      anyway. Searches the regular symbol table and the shared symbol
      table. Null is returned if the given name is not found in both
      tables. */
  public Symbol probe(byte[] name) {
    long hashValue = hashSymbol(name);

    // shared table does not use alternate hashing algorithm,
    // it always uses the same original hash code.
    Symbol s = sharedTable.probe(name, hashValue);
    if (s != null) {
      return s;
    }

    if (useAlternateHashcode()) {
        hashValue = AltHashing.murmur3_32(getSeed(), name);
    }

    for (HashtableEntry e = (HashtableEntry) bucket(hashToIndex(hashValue)); e != null; e = (HashtableEntry) e.next()) {
      if (e.hash() == hashValue) {
         Symbol sym = Symbol.create(e.literalValue());
         if (sym.equals(name)) {
           return sym;
         }
      }
    }

    return null;
  }

  public interface SymbolVisitor {
    public void visit(Symbol sym);
  }

  public void symbolsDo(SymbolVisitor visitor) {
    int numBuckets = tableSize();
    for (int i = 0; i < numBuckets; i++) {
      for (HashtableEntry e = (HashtableEntry) bucket(i); e != null;
           e = (HashtableEntry) e.next()) {
        visitor.visit(Symbol.create(e.literalValue()));
      }
    }
  }

  private static byte[] toModifiedUTF8Bytes(String name) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeUTF(name);
    dos.flush();
    byte[] buf = baos.toByteArray();
    byte[] res = new byte[buf.length - 2];
    // skip the length part
    System.arraycopy(buf, 2, res, 0, res.length);
    return res;
  }
}
