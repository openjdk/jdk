/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc.parallel;

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;

public abstract class ImmutableSpace extends VMObject {
   static {
      VM.registerVMInitializedObserver(new Observer() {
         public void update(Observable o, Object data) {
            initialize(VM.getVM().getTypeDataBase());
         }
      });
   }

   private static synchronized void initialize(TypeDataBase db) {
      Type type = db.lookupType("ImmutableSpace");
      bottomField = type.getAddressField("_bottom");
      endField    = type.getAddressField("_end");
   }

   public ImmutableSpace(Address addr) {
      super(addr);
   }

   // Fields
   private static AddressField bottomField;
   private static AddressField endField;

   // Accessors
   public Address   bottom()       { return bottomField.getValue(addr); }
   public Address   end()          { return endField.getValue(addr);    }

   /** Returns a subregion of the space containing all the objects in
      the space. */
   public MemRegion usedRegion() {
      return new MemRegion(bottom(), end());
   }

   /** Support for iteration over heap -- not sure how this will
      interact with GC in reflective system, but necessary for the
      debugging mechanism */
   public OopHandle bottomAsOopHandle() {
      return bottomField.getOopHandle(addr);
   }

   /** returns all MemRegions where live objects are */
   public abstract List/*<MemRegion>*/ getLiveRegions();

   /** Returned value is in bytes */
   public long capacity() { return end().minus(bottom()); }

   public abstract long used();

   /** Testers */
   public boolean contains(Address p) {
      return (bottom().lessThanOrEqual(p) && end().greaterThan(p));
   }

   public void print() { printOn(System.out); }
   public abstract void printOn(PrintStream tty);
}
