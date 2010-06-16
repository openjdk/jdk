/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.bugspot;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;

/** Helper class for locating a program counter. Indicates the
    confidence of the find. */

public class PCFinder {
  public static final int LOW_CONFIDENCE = 1;
  public static final int HIGH_CONFIDENCE = 2;

  public static class Info {
    private String name;
    private long   offset;
    private int    confidence;

    public Info(String name, long offset, int confidence) {
      this.name = name;
      this.offset = offset;
      this.confidence = confidence;
    }

    /** May be null */
    public String getName()       { return name;       }

    /** If this is -1, a symbol could not be found, and the offset
        should not be shown */
    public long   getOffset()     { return offset;     }

    /** PCFinder.LOW_CONFIDENCE or PCFinder.HIGH_CONFIDENCE */
    public int    getConfidence() { return confidence; }
  }

  /** Passed loadobject may be null in which case the returned Info
      object has low confidence */
  public static Info findPC(Address pc, LoadObject lo, CDebugger dbg) {
    if (lo == null) {
      return new Info(null, -1, LOW_CONFIDENCE);
    }

    // First try debug info
    BlockSym sym = lo.debugInfoForPC(pc);
    while (sym != null) {
      if (sym.isFunction()) {
        // Highest confidence
        return new Info(sym.toString(), pc.minus(sym.getAddress()), HIGH_CONFIDENCE);
      }
    }

    // Now try looking up symbol in loadobject

    // FIXME: must add support for mapfiles on Win32 and try looking
    // up there first if possible. Should we hide that behind
    // LoadObject.closestSymbolToPC and have the ClosestSymbol return
    // confidence? I think so. On Solaris there is no notion of a
    // mapfile, and the confidence for closestSymbolToPC will be high
    // instead of low.

    int confidence = HIGH_CONFIDENCE;

    ClosestSymbol cs = lo.closestSymbolToPC(pc);
    if (cs != null) {
      // FIXME: currently low confidence (only on Win32)
      return new Info(cs.getName() + "()", cs.getOffset(), LOW_CONFIDENCE);
    }

    // Unknown location
    return new Info(dbg.getNameOfFile(lo.getName()).toUpperCase() +
                    "! " + pc + "()", -1, HIGH_CONFIDENCE);
  }
}
