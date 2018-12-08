/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.dfa;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase;
import org.graalvm.compiler.lir.util.IndexedValueMap;
import org.graalvm.compiler.lir.util.ValueSet;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Record all derived reference base pointers in a frame state.
 */
public final class MarkBasePointersPhase extends AllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        new Marker(lirGenRes.getLIR(), null).build();
    }

    private static final class Marker extends LocationMarker<Marker.BasePointersSet> {

        private final class BasePointersSet extends ValueSet<Marker.BasePointersSet> {

            private final IndexedValueMap variables;

            BasePointersSet() {
                variables = new IndexedValueMap();
            }

            private BasePointersSet(BasePointersSet s) {
                variables = new IndexedValueMap(s.variables);
            }

            @Override
            public Marker.BasePointersSet copy() {
                return new BasePointersSet(this);
            }

            @Override
            public void put(Value v) {
                Variable base = (Variable) v.getValueKind(LIRKind.class).getDerivedReferenceBase();
                assert !base.getValueKind(LIRKind.class).isValue();
                variables.put(base.index, base);
            }

            @Override
            public void putAll(BasePointersSet v) {
                variables.putAll(v.variables);
            }

            @Override
            public void remove(Value v) {
                Variable base = (Variable) v.getValueKind(LIRKind.class).getDerivedReferenceBase();
                assert !base.getValueKind(LIRKind.class).isValue();
                variables.put(base.index, null);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Marker.BasePointersSet) {
                    BasePointersSet other = (BasePointersSet) obj;
                    return variables.equals(other.variables);
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() {
                throw new UnsupportedOperationException();
            }
        }

        private Marker(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
        }

        @Override
        protected Marker.BasePointersSet newLiveValueSet() {
            return new BasePointersSet();
        }

        @Override
        protected boolean shouldProcessValue(Value operand) {
            ValueKind<?> kind = operand.getValueKind();
            if (kind instanceof LIRKind) {
                return ((LIRKind) kind).isDerivedReference();
            } else {
                return false;
            }
        }

        @Override
        protected void processState(LIRInstruction op, LIRFrameState info, BasePointersSet values) {
            info.setLiveBasePointers(new IndexedValueMap(values.variables));
        }
    }
}
