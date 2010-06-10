/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;

public class CMSCollector extends VMObject {
  private static long markBitMapFieldOffset;

  public CMSCollector(Address addr) {
    super(addr);
  }

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("CMSCollector");
    markBitMapFieldOffset = type.getField("_markBitMap").getOffset();
  }

  //Accessing mark bitmap
  public CMSBitMap markBitMap() {
   return (CMSBitMap) VMObjectFactory.newObject(
                                CMSBitMap.class,
                                addr.addOffsetTo(markBitMapFieldOffset));
  }

  public long blockSizeUsingPrintezisBits(Address addr) {
    CMSBitMap markBitMap = markBitMap();
    long addressSize = VM.getVM().getAddressSize();
    if ( markBitMap.isMarked(addr) &&  markBitMap.isMarked(addr.addOffsetTo(1*addressSize)) ) {
       System.err.println("Printezis bits are set...");
      Address nextOneAddr = markBitMap.getNextMarkedWordAddress(addr.addOffsetTo(2*addressSize));
      //return size in bytes
      long size =  (nextOneAddr.addOffsetTo(1*addressSize)).minus(addr);
      return size;
    } else {
     //missing Printezis marks
     System.err.println("Missing Printszis marks...");
     return -1;
    }

  }
}
