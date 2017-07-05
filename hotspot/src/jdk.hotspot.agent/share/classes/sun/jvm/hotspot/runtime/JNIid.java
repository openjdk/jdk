/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;

public class JNIid extends VMObject {
  private static MetadataField holder;
  private static AddressField next;
  private static CIntegerField offset;
  private static MetadataField resolvedMethod;
  private static MetadataField resolvedReceiver;

  private ObjectHeap heap;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {

    // FIXME: JNIid has been removed as part of redesign of JNI method,
    // field ID generation. Please refer to src/share/vm/prims/jniId.hpp/.cpp
    // For now, commenting out the below code.

    /***********************************************************
    Type type = db.lookupType("JNIid");

    holder = type.getOopField("_holder");
    next = type.getAddressField("_next");
    offset = type.getCIntegerField("_offset");
    resolvedMethod = type.getOopField("_resolved_method");
    resolvedReceiver = type.getOopField("_resolved_receiver");
    ***********************************************************/
  }

  public JNIid(Address addr, ObjectHeap heap) {
    super(addr);
    this.heap = heap;
  }

  public JNIid next() {
    Address nextAddr = next.getValue(addr);
    if (nextAddr == null) {
      return null;
    }
    return new JNIid(nextAddr, heap);
  }

  public Klass     holder()           { return (Klass) holder.getValue(addr); }
  public int       offset()           { return (int) offset.getValue(addr); }
  public Method    method() {
    return ((InstanceKlass) holder()).getMethods().at(offset());
  }
  public Method    resolvedMethod()   { return (Method)resolvedMethod.getValue(addr); }
  public Klass     resolvedReceiver() { return (Klass) resolvedReceiver.getValue(addr); }
}
