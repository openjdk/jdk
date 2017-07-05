/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class CompactHashTable extends VMObject {
  static {
    VM.registerVMInitializedObserver(new Observer() {
      public void update(Observable o, Object data) {
        initialize(VM.getVM().getTypeDataBase());
      }
    });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type = db.lookupType("SymbolCompactHashTable");
    baseAddressField = type.getAddressField("_base_address");
    bucketCountField = type.getCIntegerField("_bucket_count");
    tableEndOffsetField = type.getCIntegerField("_table_end_offset");
    bucketsField = type.getAddressField("_buckets");
    uintSize = db.lookupType("juint").getSize();
  }

  // Fields
  private static CIntegerField bucketCountField;
  private static CIntegerField tableEndOffsetField;
  private static AddressField  baseAddressField;
  private static AddressField  bucketsField;
  private static long uintSize;

  private static int BUCKET_OFFSET_MASK = 0x3FFFFFFF;
  private static int BUCKET_TYPE_SHIFT = 30;
  private static int COMPACT_BUCKET_TYPE = 1;

  public CompactHashTable(Address addr) {
    super(addr);
  }

  private int bucketCount() {
    return (int)bucketCountField.getValue(addr);
  }

  private int tableEndOffset() {
    return (int)tableEndOffsetField.getValue(addr);
  }

  private boolean isCompactBucket(int bucket_info) {
    return (bucket_info >> BUCKET_TYPE_SHIFT) == COMPACT_BUCKET_TYPE;
  }

  private int bucketOffset(int bucket_info) {
    return bucket_info & BUCKET_OFFSET_MASK;
  }

  public Symbol probe(byte[] name, long hash) {

    if (bucketCount() == 0) {
      // The table is invalid, so don't try to lookup
      return null;
    }

    long    symOffset;
    Symbol  sym;
    Address baseAddress = baseAddressField.getValue(addr);
    Address bucket = bucketsField.getValue(addr);
    Address bucketEnd = bucket;
    long index = hash % bucketCount();
    int bucketInfo = (int)bucket.getCIntegerAt(index * uintSize, uintSize, true);
    int bucketOffset = bucketOffset(bucketInfo);
    int nextBucketInfo = (int)bucket.getCIntegerAt((index+1) * uintSize, uintSize, true);
    int nextBucketOffset = bucketOffset(nextBucketInfo);

    bucket = bucket.addOffsetTo(bucketOffset * uintSize);

    if (isCompactBucket(bucketInfo)) {
      symOffset = bucket.getCIntegerAt(0, uintSize, true);
      sym = Symbol.create(baseAddress.addOffsetTo(symOffset));
      if (sym.equals(name)) {
        return sym;
      }
    } else {
      bucketEnd = bucket.addOffsetTo(nextBucketOffset * uintSize);
      while (bucket.lessThan(bucketEnd)) {
        long symHash = bucket.getCIntegerAt(0, uintSize, true);
        if (symHash == hash) {
          symOffset = bucket.getCIntegerAt(uintSize, uintSize, true);
          Address symAddr = baseAddress.addOffsetTo(symOffset);
          sym = Symbol.create(symAddr);
          if (sym.equals(name)) {
            return sym;
          }
        }
        bucket = bucket.addOffsetTo(2 * uintSize);
      }
    }
    return null;
  }
}
