/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;

public class Hashtable extends BasicHashtable {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    // just to confirm that type exists
    Type type = db.lookupType("IntptrHashtable");
  }

  // derived class may return Class<? extends HashtableEntry>
  protected Class getHashtableEntryClass() {
    return HashtableEntry.class;
  }

  public int hashToIndex(long fullHash) {
    return (int) (fullHash % tableSize());
  }

  public Hashtable(Address addr) {
    super(addr);
  }

  // VM's Hashtable::hash_symbol
  protected static long hashSymbol(byte[] buf) {
    long h = 0;
    int s = 0;
    int len = buf.length;
    // Emulate the unsigned int in java_lang_String::hash_code
    while (len-- > 0) {
      h = 31*h + (0xFFFFFFFFL & buf[s]);
      s++;
    }
    return h & 0xFFFFFFFFL;
  }
}
