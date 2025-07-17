/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.code;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class UpcallStub extends CodeBlob {

  private static CIntegerField frameDataOffsetField;
  private static AddressField lastJavaFPField;
  private static AddressField lastJavaSPField;
  private static AddressField lastJavaPCField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static void initialize(TypeDataBase db) {
    Type type = db.lookupType("UpcallStub");
    frameDataOffsetField = type.getCIntegerField("_frame_data_offset");

    Type anchorType = db.lookupType("JavaFrameAnchor");
    lastJavaSPField = anchorType.getAddressField("_last_Java_sp");
    lastJavaPCField = anchorType.getAddressField("_last_Java_pc");

    try {
      lastJavaFPField = anchorType.getAddressField("_last_Java_fp");
    } catch (Exception e) {
      // Some platforms (e.g. PPC64) does not have this field.
      lastJavaFPField = null;
    }
  }

  public UpcallStub(Address addr) {
    super(addr);
  }

  protected Address getJavaFrameAnchor(Frame frame) {
    var frameDataOffset = frameDataOffsetField.getValue(addr);
    var frameDataAddr = frame.getUnextendedSP().addOffsetTo(frameDataOffset);
    var frameData = VMObjectFactory.newObject(FrameData.class, frameDataAddr);
    return frameData.getJavaFrameAnchor();
  }

  public Address getLastJavaSP(Frame frame) {
    return lastJavaSPField.getValue(getJavaFrameAnchor(frame));
  }

  public Address getLastJavaFP(Frame frame) {
    return lastJavaFPField == null ? null : lastJavaFPField.getValue(getJavaFrameAnchor(frame));
  }

  public Address getLastJavaPC(Frame frame) {
    return lastJavaPCField.getValue(getJavaFrameAnchor(frame));
  }

  public static class FrameData extends VMObject {

    private static AddressField jfaField;

    static {
      VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
    }

    private static void initialize(TypeDataBase db) {
      Type type = db.lookupType("UpcallStub::FrameData");
      jfaField = type.getAddressField("jfa");
    }

    public FrameData(Address addr) {
      super(addr);
    }

    public Address getJavaFrameAnchor() {
      return addr.addOffsetTo(jfaField.getOffset());
    }

  }

}
