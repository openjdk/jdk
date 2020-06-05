/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

            private final Map<Integer, Set<Value>> baseDerivedRefs;

            BasePointersSet() {
                baseDerivedRefs = new HashMap<>();
            }

            private BasePointersSet(BasePointersSet other) {
                // Deep copy.
                baseDerivedRefs = new HashMap<>(other.baseDerivedRefs.size());
                for (Map.Entry<Integer, Set<Value>> entry : other.baseDerivedRefs.entrySet()) {
                    Set<Value> s = new HashSet<>(entry.getValue());
                    baseDerivedRefs.put(entry.getKey(), s);
                }
            }

            @Override
            public Marker.BasePointersSet copy() {
                return new BasePointersSet(this);
            }

            // Verify that there is no base includes derivedRef already.
            // The single derivedRef maps to multiple bases case can not happen.
            private boolean verifyDerivedRefs(Value derivedRef, int base) {
                for (Map.Entry<Integer, Set<Value>> entry : baseDerivedRefs.entrySet()) {
                    Set<Value> s = entry.getValue();
                    if (s.contains(derivedRef) && base != entry.getKey()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void put(Value v) {
                Variable base = (Variable) v.getValueKind(LIRKind.class).getDerivedReferenceBase();
                assert !base.getValueKind(LIRKind.class).isValue();

                Set<Value> derivedRefs = baseDerivedRefs.get(base.index);
                assert verifyDerivedRefs(v, base.index);
                if (derivedRefs == null) {
                    HashSet<Value> s = new HashSet<>();
                    s.add(v);
                    baseDerivedRefs.put(base.index, s);
                } else {
                    derivedRefs.add(v);
                }
            }

            @Override
            public void putAll(BasePointersSet v) {
                for (Map.Entry<Integer, Set<Value>> entry : v.baseDerivedRefs.entrySet()) {
                    Integer k = entry.getKey();
                    Set<Value> derivedRefsOther = entry.getValue();
                    Set<Value> derivedRefs = baseDerivedRefs.get(k);
                    if (derivedRefs == null) {
                        // Deep copy.
                        Set<Value> s = new HashSet<>(derivedRefsOther);
                        baseDerivedRefs.put(k, s);
                    } else {
                        derivedRefs.addAll(derivedRefsOther);
                    }
                }
            }

            @Override
            public void remove(Value v) {
                Variable base = (Variable) v.getValueKind(LIRKind.class).getDerivedReferenceBase();
                assert !base.getValueKind(LIRKind.class).isValue();
                Set<Value> derivedRefs = baseDerivedRefs.get(base.index);
                // Just mark the base pointer as null if no derived references exist.
                if (derivedRefs == null) {
                    return;
                }

                // Remove the value from the derived reference set if the set exists.
                if (derivedRefs.contains(v)) {
                    derivedRefs.remove(v);
                    if (derivedRefs.isEmpty()) {
                        baseDerivedRefs.remove(base.index);
                    }
                }
            }

            private IndexedValueMap getMap() {
                IndexedValueMap result = new IndexedValueMap();
                for (Set<Value> entry : baseDerivedRefs.values()) {
                    if (entry.isEmpty()) {
                        continue;
                    }
                    Value v = entry.iterator().next();
                    Variable base = (Variable) v.getValueKind(LIRKind.class).getDerivedReferenceBase();
                    result.put(base.index, base);
                }
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Marker.BasePointersSet) {
                    BasePointersSet other = (BasePointersSet) obj;
                    return baseDerivedRefs.equals(other.baseDerivedRefs);
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("[BasePointersSet] baseDerivedRefs map: {");

                boolean mapHaveElement = false;
                for (Map.Entry<Integer, Set<Value>> entry : baseDerivedRefs.entrySet()) {
                    sb.append(entry.getKey());
                    sb.append(": (");

                    boolean setHaveElement = false;
                    for (Value v : entry.getValue()) {
                        sb.append(v + ",");
                        setHaveElement = true;
                    }
                    if (setHaveElement) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    sb.append("),");
                    mapHaveElement = true;
                }
                if (mapHaveElement) {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append("}");
                return sb.toString();
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
            info.setLiveBasePointers(values.getMap());
        }

    }
}
