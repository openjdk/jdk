/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc.shared;

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

/** <P> A ContiguousSpace describes a heap area. </P>

    <P> Invariant: bottom() and end() are on page_size boundaries and: </P>

    <P> bottom() <= top() <= end() </P>

    <P> top() is inclusive and end() is exclusive. </P> */

public class ContiguousSpace extends VMObject implements LiveRegionsProvider {
  private static AddressField bottomField;
  private static AddressField endField;
  private static AddressField topField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("ContiguousSpace");

    bottomField = type.getAddressField("_bottom");
    endField    = type.getAddressField("_end");
    topField = type.getAddressField("_top");
  }

  public ContiguousSpace(Address addr) {
    super(addr);
  }

  public Address bottom() { return bottomField.getValue(addr); }
  public Address end()    { return endField.getValue(addr);    }
  public Address top()    { return topField.getValue(addr);    }

  /** Support for iteration over heap -- not sure how this will
      interact with GC in reflective system, but necessary for the
      debugging mechanism */
  public OopHandle bottomAsOopHandle() {
    return bottomField.getOopHandle(addr);
  }

  /** Support for iteration over heap -- not sure how this will
      interact with GC in reflective system, but necessary for the
      debugging mechanism */
  public OopHandle nextOopHandle(OopHandle handle, long size) {
    return handle.addOffsetToAsOopHandle(size);
  }

  /** Returned value is in bytes */
  public long capacity() { return end().minus(bottom()); }
  public long used()     { return top().minus(bottom()); }
  public long free()     { return end().minus(top());    }

  public void print() { printOn(System.out); }

  /** In a contiguous space we have a more obvious bound on what parts
      contain objects. */
  public MemRegion usedRegion() {
    return new MemRegion(bottom(), top());
  }

  /** Returns regions of Space where live objects live */
  public List<MemRegion> getLiveRegions() {
    List<MemRegion> res = new ArrayList<>();
    res.add(new MemRegion(bottom(), top()));
    return res;
  }

  /** Testers */
  public boolean contains(Address p) {
    return (bottom().lessThanOrEqual(p) && top().greaterThan(p));
  }

  public void printOn(PrintStream tty) {
    tty.print(" [" + bottom() + "," +
                top() + "," + end() + ")");
    tty.print(" space capacity = ");
    tty.print(capacity());
    tty.print(", ");
    tty.print((double) used() * 100.0/ capacity());
    tty.print(" used");
  }
}
