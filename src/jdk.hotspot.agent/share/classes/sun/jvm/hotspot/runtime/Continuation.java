/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA.
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

import sun.jvm.hotspot.debugger.Address;


public class Continuation {

    public static boolean isReturnBarrierEntry(Address senderPC) {
        if (!Continuations.enabled()) {
            return false;
        }
        return VM.getVM().getStubRoutines().contReturnBarrier().equals(senderPC);
    }

    public static boolean isSPInContinuation(ContinuationEntry entry, Address sp) {
        return entry.getEntrySP().greaterThan(sp);
    }

    public static ContinuationEntry getContinuationEntryForSP(JavaThread thread, Address sp) {
        ContinuationEntry entry = thread.getContEntry();
        while (entry != null && !isSPInContinuation(entry, sp)) {
            entry = entry.getParent();
        }
        return entry;
    }

    public static Frame continuationBottomSender(JavaThread thread, Frame callee, Address senderSP) {
        ContinuationEntry ce = getContinuationEntryForSP(thread, callee.getSP());
        Frame entry = ce.toFrame();
        if (callee.isInterpretedFrame()) {
            entry.setSP(senderSP); // sp != unextended_sp
        }
        return entry;
    }

}
