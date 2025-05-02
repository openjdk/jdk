/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.types.WrongTypeException;
import sun.jvm.hotspot.utilities.*;

public class Mutex extends VMObject {
  private static long          nameFieldOffset;
  private static long          ownerFieldOffset;

  private static AddressField  mutex_array;
  private static int           maxNum;

  private static final long addrSize = VM.getVM().getAddressSize();

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type = db.lookupType("Mutex");

    sun.jvm.hotspot.types.Field nameField = type.getField("_name");
    nameFieldOffset = nameField.getOffset();
    sun.jvm.hotspot.types.Field ownerField = type.getField("_owner");
    ownerFieldOffset = ownerField.getOffset();

    mutex_array = type.getAddressField("_mutex_array");
    maxNum = type.getCIntegerField("_num_mutex").getJInt();
  }

  public Mutex(Address addr) {
    super(addr);
  }

  public String name() { return CStringUtilities.getString(addr.getAddressAt(nameFieldOffset)); }

  public Address owner() { return addr.getAddressAt(ownerFieldOffset); }

  public static Address at(int i) { return mutex_array.getValue().getAddressAt(i * addrSize); }

  public static int maxNum() { return maxNum; }

}
