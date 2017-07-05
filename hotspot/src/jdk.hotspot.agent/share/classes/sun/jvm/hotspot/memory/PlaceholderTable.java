/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class PlaceholderTable extends TwoOopHashtable {
  public PlaceholderTable(Address addr) {
    super(addr);
  }

  // this is overriden here so that Hashtable.bucket will return
  // object of PlacholderEntry.class
  protected Class getHashtableEntryClass() {
    return PlaceholderEntry.class;
  }

  /** All array classes of primitive type, and their class loaders */
  public void primArrayClassesDo(SystemDictionary.ClassAndLoaderVisitor v) {
    ObjectHeap heap = VM.getVM().getObjectHeap();
    int tblSize = tableSize();
    for (int index = 0; index < tblSize; index++) {
      for (PlaceholderEntry probe = (PlaceholderEntry) bucket(index); probe != null;
                                          probe = (PlaceholderEntry) probe.next()) {
        Symbol sym = probe.klass();
        // array of primitive arrays are stored in system dictionary as placeholders
        FieldType ft = new FieldType(sym);
        if (ft.isArray()) {
          FieldType.ArrayInfo info = ft.getArrayInfo();
          if (info.elementBasicType() != BasicType.getTObject()) {
            Klass arrayKlass = heap.typeArrayKlassObj(info.elementBasicType());
            arrayKlass = arrayKlass.arrayKlassOrNull(info.dimension());
            v.visit(arrayKlass, probe.loader());
          }
        }
      }
    }
  }
}
