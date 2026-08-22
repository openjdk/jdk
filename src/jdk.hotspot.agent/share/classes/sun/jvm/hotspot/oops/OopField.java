/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.utilities.SystemDictionaryHelper;

// The class for an oop field simply provides access to the value.
public class OopField extends Field {
  public OopField(FieldIdentifier id, long offset, boolean isVMField) {
    super(id, offset, isVMField);
  }

  public OopField(sun.jvm.hotspot.types.OopField vmField, long startOffset) {
    super(new NamedFieldIdentifier(vmField.getName()), vmField.getOffset() + startOffset, true);
  }

  public OopField(InstanceKlass holder, int fieldArrayIndex) {
    super(holder, fieldArrayIndex);
  }

  private Klass getFieldKlass() {
    var sig = getSignature().asString();
    var klsName = sig.substring(1, sig.length() - 1); // extracts L(class name);
    return SystemDictionaryHelper.findInstanceKlass(klsName);
  }

  @Override
  public long getOffset() {
    long ofs = super.getOffset();
    if (isFlat()) {
      // Subtract payload offset because this field is flattened.
      ofs -= ((InlineKlass)getFieldKlass()).members().payloadOffset();
    }
    return ofs;
  }

  public Oop getValue(Oop obj) {
    if (!isVMField() && !obj.isInstance() && !obj.isArray()) {
      throw new InternalError();
    }
    var heap = obj.getHeap();
    if (isFlat()) {
      var handle = obj.getHandle().addOffsetToAsOopHandle(getOffset());
      return heap.newOop(handle, (InlineKlass)getFieldKlass());
    } else {
      return heap.newOop(getValueAsOopHandle(obj));
    }
  }

  /** Debugging support */
  public OopHandle getValueAsOopHandle(Oop obj) {
    if (!isVMField() && !obj.isInstance() && !obj.isArray()) {
      throw new InternalError(obj.toString());
    }

    return VM.getVM().getUniverse().heap().oop_load_at(obj.getHandle(), getOffset());
  }

  public Oop getValue(VMObject obj) {
    return VM.getVM().getObjectHeap().newOop(getValueAsOopHandle(obj));
  }

  /** Debugging support */
  public OopHandle getValueAsOopHandle(VMObject obj) {
    return obj.getAddress().getOopHandleAt(getOffset());
  }

  public void setValue(Oop obj) throws MutationException {
    // Fix this: setOopAt is missing in Address
  }
}
