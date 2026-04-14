/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, Red Hat Inc.
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

package sun.jvm.hotspot.debugger.linux.aarch64;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.aarch64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.aarch64.*;

public final class LinuxAARCH64CFrame extends BasicCFrame {
   public LinuxAARCH64CFrame(LinuxDebugger dbg, Address sp, Address fp, Address pc) {
      super(dbg.getCDebugger());
      this.sp = sp;
      this.fp = fp;
      this.pc = pc;
      this.dbg = dbg;
   }

   // override base class impl to avoid ELF parsing
   public ClosestSymbol closestSymbolToPC() {
      // try native lookup in debugger.
      return dbg.lookup(dbg.getAddressValue(pc()));
   }

   public Address pc() {
      return pc;
   }

   public Address localVariableBase() {
      return fp;
   }

   @Override
   public CFrame sender(ThreadProxy thread) {
      return sender(thread, null, null, null);
   }

   @Override
   public CFrame sender(ThreadProxy thread, Address nextSP, Address nextFP, Address nextPC) {
      // Check fp
      // Skip if both nextFP and nextPC are given - do not need to load from fp.
      if (nextFP == null && nextPC == null) {
        if (fp == null) {
          return null;
        }

        // Check alignment of fp
        if (dbg.getAddressValue(fp) % (2 * ADDRESS_SIZE) != 0) {
          return null;
        }
      }

      if (nextFP == null) {
        nextFP = fp.getAddressAt(0 * ADDRESS_SIZE);
      }
      if (nextFP == null) {
        return null;
      }

      if (nextPC == null) {
        nextPC  = fp.getAddressAt(1 * ADDRESS_SIZE);
      }
      if (nextPC == null) {
        return null;
      }

      if (nextSP == null) {
        CodeCache cc = VM.getVM().getCodeCache();
        CodeBlob currentBlob = cc.findBlobUnsafe(pc());

        // This case is different from HotSpot. See JDK-8371194 for details.
        if (currentBlob != null && (currentBlob.isContinuationStub() || currentBlob.isNativeMethod())) {
          // Use FP since it should always be valid for these cases.
          // TODO: These should be walked as Frames not CFrames.
          nextSP = fp.addOffsetTo(2 * ADDRESS_SIZE);
        } else {
          CodeBlob codeBlob = cc.findBlobUnsafe(nextPC);
          boolean useCodeBlob = codeBlob != null && codeBlob.getFrameSize() > 0;
          nextSP = useCodeBlob ? nextFP.addOffsetTo((2 * ADDRESS_SIZE) - codeBlob.getFrameSize()) : nextFP;
        }
      }
      if (nextSP == null) {
        return null;
      }

      return new LinuxAARCH64CFrame(dbg, nextSP, nextFP, nextPC);
   }

   @Override
   public Frame toFrame() {
     return new AARCH64Frame(sp, fp, pc);
   }

   // package/class internals only
   private static final int ADDRESS_SIZE = 8;
   private Address pc;
   private Address sp;
   private Address fp;
   private LinuxDebugger dbg;
}
