/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.meta.InvokeTarget;

import java.util.Objects;

public class HotSpotDirectCall extends Call {

    /**
     * Specifies if the target method should be attached to the call site.
     * This permits the compiler to override the bytecode based call site resolution for cases were special semantics are needed.
     * If the call does not originate from the associated bci, {@link jdk.vm.ci.code.BytecodeFrame#duringCall} must not be set.
     */
    public final boolean bind;

    public HotSpotDirectCall(InvokeTarget target, int pcOffset, int size, DebugInfo debugInfo, boolean bind) {
        super(target, pcOffset, size, true, debugInfo);
        this.bind = bind;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HotSpotDirectCall && super.equals(obj)) {
            HotSpotDirectCall that = (HotSpotDirectCall) obj;
            if (this.size == that.size && this.direct == that.direct && Objects.equals(this.target, that.target) && this.bind == that.bind) {
                return true;
            }
        }
        return false;
    }
}
