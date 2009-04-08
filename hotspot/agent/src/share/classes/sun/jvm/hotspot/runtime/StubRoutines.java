/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.jvm.hotspot.runtime;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.types.*;

/** Very minimal port for now to get frames working */

public class StubRoutines {
  private static AddressField      callStubReturnAddressField;
  private static AddressField      callStubCompiledReturnAddressField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("StubRoutines");

    callStubReturnAddressField = type.getAddressField("_call_stub_return_address");
    // Only some platforms have specific return from compiled to call_stub
    try {
      type = db.lookupType("StubRoutines::x86");
      if (type != null) {
        callStubCompiledReturnAddressField = type.getAddressField("_call_stub_compiled_return");
      }
    } catch (RuntimeException re) {
      callStubCompiledReturnAddressField = null;
    }
    if (callStubCompiledReturnAddressField == null && VM.getVM().getCPU().equals("x86")) {
      throw new InternalError("Missing definition for _call_stub_compiled_return");
    }
  }

  public StubRoutines() {
  }

  public boolean returnsToCallStub(Address returnPC) {
    Address addr = callStubReturnAddressField.getValue();
    boolean result = false;
    if (addr == null) {
      result = (addr == returnPC);
    } else {
      result = addr.equals(returnPC);
    }
    if (result || callStubCompiledReturnAddressField == null ) return result;
    // Could be a return to compiled code return point
    addr = callStubCompiledReturnAddressField.getValue();
    if (addr == null) {
      return (addr == returnPC);
    } else {
      return (addr.equals(returnPC));
    }

  }
}
