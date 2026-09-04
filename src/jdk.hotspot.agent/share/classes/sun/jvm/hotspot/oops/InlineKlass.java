/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

// An InlineKlass is the VM level representation of a flattenable Java class.

public class InlineKlass extends InstanceKlass {

  public static class Members extends VMObject {

    private static CIntField payloadOffsetField;
    private static CIntField nullMarkerOffsetField;

    static {
      VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
      Type type = db.lookupType("InlineKlass::Members");
      payloadOffsetField = new CIntField(type.getCIntegerField("_payload_offset"), 0);
      nullMarkerOffsetField = new CIntField(type.getCIntegerField("_null_marker_offset"), 0);
    }

    public Members(Address addr) {
      super(addr);
    }

    public int payloadOffset() {
      return (int)payloadOffsetField.getValue(this);
    }

    public int nullMarkerOffset() {
      return (int)nullMarkerOffsetField.getValue(this);
    }

  }

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    // Just make sure it's there for now
    Type type = db.lookupType("InlineKlass");
  }

  public InlineKlass(Address addr) {
    super(addr);
  }

  public Members members() {
    return VMObjectFactory.newObject(Members.class, getAdrInlineKlassMembers());
  }

  public int nullMarkerOffset() {
    return members().nullMarkerOffset();
  }

  public int payloadOffset() {
    return members().payloadOffset();
  }

  public int nullMarkerOffsetInPayload() {
    return nullMarkerOffset() - payloadOffset();
  }

  public OopHandle nullMarkerAddress(Address payload) {
    // "payload" might be OopHandle (e.g. when it comes from OopField), then
    // we cannot use addOffsetTo() because it is not allowed due to prevent
    // interior object pointers. Hence addOffsetToAsOopHandle() is called here.
    return payload.addOffsetToAsOopHandle(nullMarkerOffsetInPayload());
  }

  public boolean isPayloadMarkedAsNull(Address payload) {
    return nullMarkerAddress(payload).getJByteAt(0) == (byte)0;
  }

}
