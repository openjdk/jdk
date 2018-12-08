/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.ArrayList;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;

import jdk.vm.ci.code.Location;
import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotReferenceMap;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public final class HotSpotReferenceMapBuilder extends ReferenceMapBuilder {

    private int maxRegisterSize;

    private final ArrayList<Value> objectValues;
    private int objectCount;

    private final int totalFrameSize;
    private final int maxOopMapStackOffset;
    private final int uncompressedReferenceSize;

    public HotSpotReferenceMapBuilder(int totalFrameSize, int maxOopMapStackOffset, int uncompressedReferenceSize) {
        this.uncompressedReferenceSize = uncompressedReferenceSize;
        this.objectValues = new ArrayList<>();
        this.objectCount = 0;
        this.maxOopMapStackOffset = maxOopMapStackOffset;
        this.totalFrameSize = totalFrameSize;
    }

    @Override
    public void addLiveValue(Value v) {
        if (isJavaConstant(v)) {
            return;
        }
        LIRKind lirKind = (LIRKind) v.getValueKind();
        if (!lirKind.isValue()) {
            objectValues.add(v);
            if (lirKind.isUnknownReference()) {
                objectCount++;
            } else {
                objectCount += lirKind.getReferenceCount();
            }
        }
        if (isRegister(v)) {
            int size = lirKind.getPlatformKind().getSizeInBytes();
            if (size > maxRegisterSize) {
                maxRegisterSize = size;
            }
        }
    }

    private static final Location[] NO_LOCATIONS = {};
    private static final int[] NO_SIZES = {};

    @Override
    public ReferenceMap finish(LIRFrameState state) {
        Location[] objects;
        Location[] derivedBase;
        int[] sizeInBytes;
        if (objectCount == 0) {
            objects = NO_LOCATIONS;
            derivedBase = NO_LOCATIONS;
            sizeInBytes = NO_SIZES;
        } else {
            objects = new Location[objectCount];
            derivedBase = new Location[objectCount];
            sizeInBytes = new int[objectCount];
        }
        int idx = 0;
        for (Value obj : objectValues) {
            LIRKind kind = (LIRKind) obj.getValueKind();
            int bytes = bytesPerElement(kind);
            if (kind.isUnknownReference()) {
                throw GraalError.shouldNotReachHere(String.format("unknown reference alive across safepoint: %s", obj));
            } else {
                Location base = null;
                if (kind.isDerivedReference()) {
                    Variable baseVariable = (Variable) kind.getDerivedReferenceBase();
                    Value baseValue = state.getLiveBasePointers().get(baseVariable.index);
                    assert baseValue.getPlatformKind().getVectorLength() == 1 && ((LIRKind) baseValue.getValueKind()).isReference(0) && !((LIRKind) baseValue.getValueKind()).isDerivedReference();
                    base = toLocation(baseValue, 0);
                }

                for (int i = 0; i < kind.getPlatformKind().getVectorLength(); i++) {
                    if (kind.isReference(i)) {
                        assert kind.isCompressedReference(i) ? (bytes < uncompressedReferenceSize) : (bytes == uncompressedReferenceSize);
                        objects[idx] = toLocation(obj, i * bytes);
                        derivedBase[idx] = base;
                        sizeInBytes[idx] = bytes;
                        idx++;
                    }
                }
            }
        }

        return new HotSpotReferenceMap(objects, derivedBase, sizeInBytes, maxRegisterSize);
    }

    private static int bytesPerElement(LIRKind kind) {
        PlatformKind platformKind = kind.getPlatformKind();
        return platformKind.getSizeInBytes() / platformKind.getVectorLength();
    }

    private Location toLocation(Value v, int offset) {
        if (isRegister(v)) {
            return Location.subregister(asRegister(v), offset);
        } else {
            StackSlot s = asStackSlot(v);
            int totalOffset = s.getOffset(totalFrameSize) + offset;
            if (totalOffset > maxOopMapStackOffset) {
                throw new PermanentBailoutException("stack offset %d for oopmap is greater than encoding limit %d", totalOffset, maxOopMapStackOffset);
            }
            return Location.stack(totalOffset);
        }
    }
}
