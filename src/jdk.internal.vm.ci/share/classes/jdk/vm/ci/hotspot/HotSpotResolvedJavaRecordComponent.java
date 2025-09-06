/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaType;
import java.lang.reflect.RecordComponent;

import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

import static jdk.vm.ci.hotspot.CompilerToVM.compilerToVM;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

/**
 * Represents a {@linkplain RecordComponent component} in a HotSpot record.
 */
final class HotSpotResolvedJavaRecordComponent implements ResolvedJavaRecordComponent {

    private final HotSpotResolvedObjectTypeImpl declaringRecord;
    private final String name;
    private final JavaType type;

    /**
     * Index in {@code InstanceKlass::_record_components}.
     */
    private final int index;

    /**
     * Called from the VM.
     */
    @VMEntryPoint
    private HotSpotResolvedJavaRecordComponent(HotSpotResolvedObjectTypeImpl declaringRecord, int index, int nameIndex, int typeIndex) {
        this.declaringRecord = declaringRecord;
        this.index = index;
        HotSpotConstantPool cp = declaringRecord.getConstantPool();
        this.name = cp.lookupUtf8(nameIndex);
        this.type = runtime().lookupType(cp.lookupUtf8(typeIndex), declaringRecord, false);
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getDeclaringRecord() {
        return declaringRecord;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getAccessor().format("HotSpotResolvedJavaRecordComponent<%H.%n %r>");
    }

    @Override
    public JavaType getType() {
        return type;
    }
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HotSpotResolvedJavaRecordComponent that) {
            return that.index == this.index && that.declaringRecord.equals(this.declaringRecord);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return declaringRecord.hashCode() ^ index;
    }

    @Override
    public AnnotationsInfo getDeclaredAnnotationInfo() {
        byte[] bytes = compilerToVM().getRawAnnotationBytes('r', declaringRecord, declaringRecord.getKlassPointer(), index, CompilerToVM.DECLARED_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getDeclaringRecord().getConstantPool(), getDeclaringRecord());
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        byte[] bytes = compilerToVM().getRawAnnotationBytes('r', declaringRecord, declaringRecord.getKlassPointer(), index, CompilerToVM.TYPE_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getDeclaringRecord().getConstantPool(), getDeclaringRecord());
    }
}
