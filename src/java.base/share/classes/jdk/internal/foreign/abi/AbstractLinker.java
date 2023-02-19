/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.foreign.abi;

import jdk.internal.foreign.SystemLookup;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.aarch64.windows.WindowsAArch64Linker;
import jdk.internal.foreign.abi.riscv64.linux.LinuxRISCV64Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;
import jdk.internal.foreign.layout.AbstractLayout;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

public abstract sealed class AbstractLinker implements Linker permits LinuxAArch64Linker, MacOsAArch64Linker,
                                                                      SysVx64Linker, WindowsAArch64Linker,
                                                                      Windowsx64Linker, LinuxRISCV64Linker {

    public interface UpcallStubFactory {
        MemorySegment makeStub(MethodHandle target, SegmentScope arena);
    }

    private record LinkRequest(FunctionDescriptor descriptor, LinkerOptions options) {}
    private final SoftReferenceCache<LinkRequest, MethodHandle> DOWNCALL_CACHE = new SoftReferenceCache<>();
    private final SoftReferenceCache<FunctionDescriptor, UpcallStubFactory> UPCALL_CACHE = new SoftReferenceCache<>();

    @Override
    public MethodHandle downcallHandle(FunctionDescriptor function, Option... options) {
        Objects.requireNonNull(function);
        Objects.requireNonNull(options);
        checkHasNaturalAlignment(function);
        LinkerOptions optionSet = LinkerOptions.forDowncall(function, options);

        return DOWNCALL_CACHE.get(new LinkRequest(function, optionSet), linkRequest ->  {
            FunctionDescriptor fd = linkRequest.descriptor();
            MethodType type = fd.toMethodType();
            MethodHandle handle = arrangeDowncall(type, fd, linkRequest.options());
            handle = SharedUtils.maybeInsertAllocator(fd, handle);
            return handle;
        });
    }
    protected abstract MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options);

    @Override
    public MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, SegmentScope scope) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(target);
        Objects.requireNonNull(function);
        checkHasNaturalAlignment(function);
        SharedUtils.checkExceptions(target);

        MethodType type = function.toMethodType();
        if (!type.equals(target.type())) {
            throw new IllegalArgumentException("Wrong method handle type: " + target.type());
        }

        UpcallStubFactory factory = UPCALL_CACHE.get(function, f -> arrangeUpcall(type, f));
        return factory.makeStub(target, scope);
    }

    protected abstract UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function);

    @Override
    public SystemLookup defaultLookup() {
        return SystemLookup.getInstance();
    }

    // Current limitation of the implementation:
    // We don't support packed structs on some platforms,
    // so reject them here explicitly
    private static void checkHasNaturalAlignment(FunctionDescriptor descriptor) {
        descriptor.returnLayout().ifPresent(AbstractLinker::checkHasNaturalAlignmentRecursive);
        descriptor.argumentLayouts().forEach(AbstractLinker::checkHasNaturalAlignmentRecursive);
    }

    private static void checkHasNaturalAlignmentRecursive(MemoryLayout layout) {
        checkHasNaturalAlignment(layout);
        if (layout instanceof GroupLayout gl) {
            for (MemoryLayout member : gl.memberLayouts()) {
                checkHasNaturalAlignmentRecursive(member);
            }
        } else if (layout instanceof SequenceLayout sl) {
            checkHasNaturalAlignmentRecursive(sl.elementLayout());
        }
    }

    private static void checkHasNaturalAlignment(MemoryLayout layout) {
        if (!((AbstractLayout<?>) layout).hasNaturalAlignment()) {
            throw new IllegalArgumentException("Layout bit alignment must be natural alignment: " + layout);
        }
    }
}
