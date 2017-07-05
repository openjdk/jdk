/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.types.basic.*;

/** This class implements the compiler-specific access to the vtbl for
    a given C++ type. */
public class HotSpotSolarisVtblAccess extends BasicVtblAccess {

  public HotSpotSolarisVtblAccess(SymbolLookup symbolLookup,
                                  String[] jvmLibNames) {
    super(symbolLookup, jvmLibNames);
  }

  protected String vtblSymbolForType(Type type) {
    String demangledSymbol = type.getName() + "::__vtbl";
    return mangle(demangledSymbol);
  }

  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  private String mangle(String symbol) {
    String[] parts = symbol.split("::");
    StringBuffer mangled = new StringBuffer("__1c");
    for (int i = 0; i < parts.length; i++) {
      int len = parts[i].length();
      if (len >= 26) {
        mangled.append((char)('a' + (len / 26)));
        len = len % 26;
      }
      mangled.append((char)('A' + len));
      mangled.append(parts[i]);
    }
    mangled.append("_");
    return mangled.toString();
  }
}
