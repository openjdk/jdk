/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

// Superclass for symbol and string tables.

public class BasicHashtable extends VMObject {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("BasicHashtable");
    tableSizeField = type.getCIntegerField("_table_size");
    bucketsField   = type.getAddressField("_buckets");
    bucketSize = db.lookupType("HashtableBucket").getSize();
  }

  // Fields
  private static CIntegerField tableSizeField;
  private static AddressField  bucketsField;
  private static long bucketSize;

  // Accessors
  protected int tableSize() {
    return (int) tableSizeField.getValue(addr);
  }

  protected BasicHashtableEntry bucket(int i) {
    if (Assert.ASSERTS_ENABLED) {
       Assert.that(i >= 0 && i < tableSize(), "Invalid bucket id");
    }
    Address tmp = bucketsField.getValue(addr);
    tmp = tmp.addOffsetTo(i * bucketSize);
    HashtableBucket bucket = (HashtableBucket) VMObjectFactory.newObject(
                                              HashtableBucket.class, tmp);
    return bucket.getEntry(getHashtableEntryClass());
  }

  // derived class may return Class<? extends BasicHashtableEntry>
  protected Class getHashtableEntryClass() {
    return BasicHashtableEntry.class;
  }

  public BasicHashtable(Address addr) {
    super(addr);
  }
}
