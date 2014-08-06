/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 */

import sun.hotspot.WhiteBox;

class AllocateBeyondMetaspaceSize {
  public static Object dummy;

  public static void main(String [] args) {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: <MetaspaceSize> <YoungGenSize>");
    }

    long metaspaceSize = Long.parseLong(args[0]);
    long youngGenSize = Long.parseLong(args[1]);

    run(metaspaceSize, youngGenSize);
  }

  private static void run(long metaspaceSize, long youngGenSize) {
    WhiteBox wb = WhiteBox.getWhiteBox();

    long allocationBeyondMetaspaceSize  = metaspaceSize * 2;
    long metaspace = wb.allocateMetaspace(null, allocationBeyondMetaspaceSize);

    triggerYoungGC(youngGenSize);

    wb.freeMetaspace(null, metaspace, metaspace);
  }

  private static void triggerYoungGC(long youngGenSize) {
    long approxAllocSize = 32 * 1024;
    long numAllocations  = 2 * youngGenSize / approxAllocSize;

    for (long i = 0; i < numAllocations; i++) {
      dummy = new byte[(int)approxAllocSize];
    }
  }
}
