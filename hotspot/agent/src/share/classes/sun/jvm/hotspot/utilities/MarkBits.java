/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.gc_interface.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

/** Helper class which covers the reserved area of the heap with an
    (object-external) set of mark bits, used for GC-like scans through
    the heap like liveness analysis. */

public class MarkBits {
  public MarkBits(CollectedHeap heap) {
    MemRegion reserved = heap.reservedRegion();
    // Must cover "reserved" with one bit for each OopHandle
    start = reserved.start();
    end   = reserved.end();
    long numOopHandles = end.minus(start) / VM.getVM().getOopSize();
    // FIXME: will have trouble with larger heap sizes
    bits = new BitMap((int) numOopHandles);
  }

  public void clear() {
    bits.clear();
  }

  /** Returns true if a mark was newly placed for the given Oop, or
      false if the Oop was already marked. If the Oop happens to lie
      outside the heap (should not happen), prints a warning and
      returns false. */
  public boolean mark(Oop obj) {
    if (obj == null) {
      System.err.println("MarkBits: WARNING: null object, ignoring");
      return false;
    }

    OopHandle handle = obj.getHandle();
    // FIXME: will have trouble with larger heap sizes
    long idx = handle.minus(start) / VM.getVM().getOopSize();
    if ((idx < 0) || (idx >= bits.size())) {
      System.err.println("MarkBits: WARNING: object " + handle + " outside of heap, ignoring");
      return false;
    }
    int intIdx = (int) idx;
    if (bits.at(intIdx)) {
      return false; // already marked
    }
    bits.atPut(intIdx, true);
    return true;
  }

  /** Forces clearing of a given mark bit. */
  public void clear(Oop obj) {
    OopHandle handle = obj.getHandle();
    // FIXME: will have trouble with larger heap sizes
    long idx = handle.minus(start) / VM.getVM().getOopSize();
    if ((idx < 0) || (idx >= bits.size())) {
      System.err.println("MarkBits: WARNING: object " + handle + " outside of heap, ignoring");
      return;
    }
    int intIdx = (int) idx;
    bits.atPut(intIdx, false);
  }

  private BitMap  bits;
  private Address start;
  private Address end;
}
