/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

 package sun.jvm.hotspot.oops;

 import sun.jvm.hotspot.debugger.Address;
 import sun.jvm.hotspot.runtime.VM;
 import sun.jvm.hotspot.types.Type;
 import sun.jvm.hotspot.types.TypeDataBase;
 import sun.jvm.hotspot.types.WrongTypeException;
 import sun.jvm.hotspot.utilities.GenericArray;
 import sun.jvm.hotspot.utilities.Observable;
 import sun.jvm.hotspot.utilities.Observer;

 public class ResolvedMethodArray extends GenericArray {
     static {
         VM.registerVMInitializedObserver(new Observer() {
             public void update(Observable o, Object data) {
                 initialize(VM.getVM().getTypeDataBase());
             }
         });
     }

     private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
         elemType = db.lookupType("ResolvedMethodEntry");

         Type type = db.lookupType("Array<ResolvedMethodEntry>");
         dataFieldOffset = type.getAddressField("_data").getOffset();
     }

     private static long dataFieldOffset;
     protected static Type elemType;

     public ResolvedMethodArray(Address addr) {
         super(addr, dataFieldOffset);
     }

     public ResolvedMethodEntry getAt(int index) {
         if (index < 0 || index >= length()) throw new ArrayIndexOutOfBoundsException(index + " " + length());

         Type elemType = getElemType();

         Address data = getAddress().addOffsetTo(dataFieldOffset);
         long elemSize = elemType.getSize();

         return new ResolvedMethodEntry(data.addOffsetTo(index* elemSize));
     }

     public Type getElemType() {
         return elemType;
     }
 }
