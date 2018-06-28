/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.phases;

import static org.graalvm.compiler.core.common.GraalOptions.TraceRA;

import org.graalvm.compiler.lir.alloc.lsra.LinearScanPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPhase;
import org.graalvm.compiler.lir.dfa.LocationMarkerPhase;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
import org.graalvm.compiler.lir.stackslotalloc.SimpleStackSlotAllocator;
import org.graalvm.compiler.options.OptionValues;

public class EconomyAllocationStage extends LIRPhaseSuite<AllocationContext> {
    public EconomyAllocationStage(OptionValues options) {
        if (TraceRA.getValue(options)) {
            appendPhase(new TraceRegisterAllocationPhase());
        } else {
            appendPhase(new LinearScanPhase());
        }

        // build frame map
        appendPhase(new SimpleStackSlotAllocator());

        // currently we mark locations only if we do register allocation
        appendPhase(new LocationMarkerPhase());
    }
}
