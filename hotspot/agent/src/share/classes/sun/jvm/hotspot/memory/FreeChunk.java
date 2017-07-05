/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.memory;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.runtime.*;

public class FreeChunk extends VMObject {
   static {
      VM.registerVMInitializedObserver(new Observer() {
         public void update(Observable o, Object data) {
            initialize(VM.getVM().getTypeDataBase());
         }
      });
   }

   private static synchronized void initialize(TypeDataBase db) {
      Type type = db.lookupType("FreeChunk");
      nextField = type.getAddressField("_next");
      prevField = type.getAddressField("_prev");
      sizeField = type.getCIntegerField("_size");
   }

   // Fields
   private static AddressField nextField;
   private static AddressField prevField;
   private static CIntegerField sizeField;

   // Accessors
   public FreeChunk next() {
      return (FreeChunk) VMObjectFactory.newObject(FreeChunk.class, nextField.getValue(addr));
   }

   public FreeChunk prev() {
      Address prev = prevField.getValue(addr).andWithMask(~0x3);
      return (FreeChunk) VMObjectFactory.newObject(FreeChunk.class, prev);
   }

   public long size() {
      return sizeField.getValue(addr);
   }

   public FreeChunk(Address addr) {
      super(addr);
   }

   public static boolean secondWordIndicatesFreeChunk(long word) {
      return (word & 0x1L) == 0x1L;
   }

   public boolean isFree() {
      Debugger dbg = VM.getVM().getDebugger();
      Address prev = prevField.getValue(addr);
      return secondWordIndicatesFreeChunk(dbg.getAddressValue(prev));
   }
}
