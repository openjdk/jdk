/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.aarch64.windows.WindowsAArch64Linker;
import jdk.internal.foreign.abi.fallback.FallbackLinker;
import jdk.internal.foreign.abi.ppc64.linux.LinuxPPC64leLinker;
import jdk.internal.foreign.abi.riscv64.linux.LinuxRISCV64Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;
import jdk.internal.foreign.layout.AbstractLayout;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.nio.ByteOrder;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public abstract sealed class AbstractLinker implements Linker permits LinuxAArch64Linker, MacOsAArch64Linker,
                                                                      SysVx64Linker, WindowsAArch64Linker,
                                                                      Windowsx64Linker, LinuxPPC64leLinker,
                                                                      LinuxRISCV64Linker, FallbackLinker {

    public interface UpcallStubFactory {
        MemorySegment makeStub(MethodHandle target, Arena arena);
    }

    private record LinkRequest(FunctionDescriptor descriptor, LinkerOptions options) {}
    private final SoftReferenceCache<LinkRequest, MethodHandle> DOWNCALL_CACHE = new SoftReferenceCache<>();
    private final SoftReferenceCache<LinkRequest, UpcallStubFactory> UPCALL_CACHE = new SoftReferenceCache<>();

    @Override
    @CallerSensitive
    public final MethodHandle downcallHandle(MemorySegment symbol, FunctionDescriptor function, Option... options) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), Linker.class, "downcallHandle");
        SharedUtils.checkSymbol(symbol);
        return downcallHandle0(function, options).bindTo(symbol);
    }

    @Override
    @CallerSensitive
    public final MethodHandle downcallHandle(FunctionDescriptor function, Option... options) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), Linker.class, "downcallHandle");
        return downcallHandle0(function, options);
    }

    private final MethodHandle downcallHandle0(FunctionDescriptor function, Option... options) {
        Objects.requireNonNull(function);
        Objects.requireNonNull(options);
        checkLayouts(function);
        function = stripNames(function);
        FunctionDescriptor unPromotedDesc = function;
        function = promoteVariadicArgs(function, firstVariadicArgIndex(options)); // the erased version can be shared
        LinkerOptions optionSet = LinkerOptions.forDowncall(function, options);

        MethodHandle mh = DOWNCALL_CACHE.get(new LinkRequest(function, optionSet), linkRequest ->  {
            FunctionDescriptor fd = linkRequest.descriptor();
            MethodType type = fd.toMethodType();
            MethodHandle handle = arrangeDowncall(type, fd, linkRequest.options());
            handle = SharedUtils.maybeInsertAllocator(fd, handle);
            return handle;
        });

        return unpromoteVariadicArgs(mh, unPromotedDesc, optionSet);
    }

    protected abstract MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options);

    @Override
    @CallerSensitive
    public final MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, Arena arena, Linker.Option... options) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), Linker.class, "upcallStub");
        Objects.requireNonNull(arena);
        Objects.requireNonNull(target);
        Objects.requireNonNull(function);
        checkLayouts(function);
        SharedUtils.checkExceptions(target);
        function = stripNames(function);
        LinkerOptions optionSet = LinkerOptions.forUpcall(function, options);

        MethodType type = function.toMethodType();
        if (!type.equals(target.type())) {
            throw new IllegalArgumentException("Wrong method handle type: " + target.type());
        }

        UpcallStubFactory factory = UPCALL_CACHE.get(new LinkRequest(function, optionSet), linkRequest ->
            arrangeUpcall(type, linkRequest.descriptor(), linkRequest.options()));
        return factory.makeStub(target, arena);
    }

    protected abstract UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options);

    @Override
    public SystemLookup defaultLookup() {
        return SystemLookup.getInstance();
    }

    /** {@return byte order used by this linker} */
    protected abstract ByteOrder linkerByteOrder();

    private void checkLayouts(FunctionDescriptor descriptor) {
        descriptor.returnLayout().ifPresent(this::checkLayout);
        descriptor.argumentLayouts().forEach(this::checkLayout);
    }

    private void checkLayout(MemoryLayout layout) {
        // Note: we should not worry about padding layouts, as they cannot be present in a function descriptor
        if (layout instanceof SequenceLayout) {
            throw new IllegalArgumentException("Unsupported layout: " + layout);
        } else {
            checkLayoutRecursive(layout);
        }
    }

    private void checkLayoutRecursive(MemoryLayout layout) {
        checkHasNaturalAlignment(layout);
        if (layout instanceof ValueLayout vl) {
            checkByteOrder(vl);
        } else if (layout instanceof StructLayout sl) {
            long offset = 0;
            long lastUnpaddedOffset = 0;
            for (MemoryLayout member : sl.memberLayouts()) {
                // check element offset before recursing so that an error points at the
                // outermost layout first
                checkMemberOffset(sl, member, lastUnpaddedOffset, offset);
                checkLayoutRecursive(member);

                offset += member.byteSize();
                if (!(member instanceof PaddingLayout)) {
                    lastUnpaddedOffset = offset;
                }
            }
            checkGroupSize(sl, lastUnpaddedOffset);
        } else if (layout instanceof UnionLayout ul) {
            long maxUnpaddedLayout = 0;
            for (MemoryLayout member : ul.memberLayouts()) {
                checkLayoutRecursive(member);
                if (!(member instanceof PaddingLayout)) {
                    maxUnpaddedLayout = Long.max(maxUnpaddedLayout, member.byteSize());
                }
            }
            checkGroupSize(ul, maxUnpaddedLayout);
        } else if (layout instanceof SequenceLayout sl) {
            checkLayoutRecursive(sl.elementLayout());
        }
    }

    // check for trailing padding
    private static void checkGroupSize(GroupLayout gl, long maxUnpaddedOffset) {
        long expectedSize = Utils.alignUp(maxUnpaddedOffset, gl.byteAlignment());
        if (gl.byteSize() != expectedSize) {
            throw new IllegalArgumentException("Layout '" + gl + "' has unexpected size: "
                    + gl.byteSize() + " != " + expectedSize);
        }
    }

    // checks both that there is no excess padding between 'memberLayout' and
    // the previous layout
    private static void checkMemberOffset(StructLayout parent, MemoryLayout memberLayout,
                                          long lastUnpaddedOffset, long offset) {
        long expectedOffset = Utils.alignUp(lastUnpaddedOffset, memberLayout.byteAlignment());
        if (expectedOffset != offset) {
            throw new IllegalArgumentException("Member layout '" + memberLayout + "', of '" + parent + "'" +
                    " found at unexpected offset: " + offset + " != " + expectedOffset);
        }
    }

    private static void checkHasNaturalAlignment(MemoryLayout layout) {
        if (!((AbstractLayout<?>) layout).hasNaturalAlignment()) {
            throw new IllegalArgumentException("Layout alignment must be natural alignment: " + layout);
        }
    }

    private static MemoryLayout stripNames(MemoryLayout ml) {
        // we don't care about transferring alignment and byte order here
        // since the linker already restricts those such that they will always be the same
        return switch (ml) {
            case StructLayout sl -> MemoryLayout.structLayout(stripNames(sl.memberLayouts()));
            case UnionLayout ul -> MemoryLayout.unionLayout(stripNames(ul.memberLayouts()));
            case SequenceLayout sl -> MemoryLayout.sequenceLayout(sl.elementCount(), stripNames(sl.elementLayout()));
            case AddressLayout al -> al.targetLayout()
                    .map(tl -> al.withoutName().withTargetLayout(stripNames(tl)))
                    .orElseGet(al::withoutName);
            default -> ml.withoutName(); // ValueLayout and PaddingLayout
        };
    }

    private static MemoryLayout[] stripNames(List<MemoryLayout> layouts) {
        return layouts.stream()
                .map(AbstractLinker::stripNames)
                .toArray(MemoryLayout[]::new);
    }

    private static FunctionDescriptor stripNames(FunctionDescriptor function) {
        return function.returnLayout()
                .map(rl -> FunctionDescriptor.of(stripNames(rl), stripNames(function.argumentLayouts())))
                .orElseGet(() -> FunctionDescriptor.ofVoid(stripNames(function.argumentLayouts())));
    }

    private void checkByteOrder(ValueLayout vl) {
        if (vl.order() != linkerByteOrder()) {
            throw new IllegalArgumentException("Layout does not have the right byte order: " + vl);
        }
    }

    private static int firstVariadicArgIndex(Option[] options) {
        for (Option option : options) {
            if (option instanceof LinkerOptions.FirstVariadicArg fva) {
                return fva.index();
            }
        }
        return -1;
    }

    // C spec mandates that variadic arguments smaller than int are promoted to int,
    // and float is promoted to double
    // See: https://en.cppreference.com/w/c/language/conversion#Default_argument_promotions
    private static FunctionDescriptor promoteVariadicArgs(FunctionDescriptor function, int firstVariadicArgIndex) {
        if (firstVariadicArgIndex != -1) {
            MemoryLayout[] promotedLayouts = function.argumentLayouts().toArray(MemoryLayout[]::new);

            for (int i = firstVariadicArgIndex; i < promotedLayouts.length; i++) {
                MemoryLayout variadicLayout = promotedLayouts[i];

                if (variadicLayout instanceof ValueLayout vl) {
                    if (requiresVariadicIntPromotion(vl)) {
                        promotedLayouts[i] = JAVA_INT;
                    } else if (requiresVariadicDoublePromotion(vl)) {
                        promotedLayouts[i] = JAVA_DOUBLE;
                    }
                }
            }

            function = function.returnLayout()
                    .map(rl -> FunctionDescriptor.of(rl, promotedLayouts))
                    .orElseGet(() -> FunctionDescriptor.ofVoid(promotedLayouts));
        }

        return function;
    }

    private static MethodHandle unpromoteVariadicArgs(MethodHandle mh, FunctionDescriptor unPromotedDesc, LinkerOptions optionSet) {
        if (optionSet.isVariadicFunction()) {
            Class<?>[] unpromotedParams = mh.type().parameterArray();
            List<MemoryLayout> unpromotedLayouts = unPromotedDesc.argumentLayouts();
            int numPrefixArgs = downcallNumPrefixArgs(unPromotedDesc, optionSet);
            assert unpromotedLayouts.size() + numPrefixArgs == unpromotedParams.length;

            for (int i = optionSet.firstVariadicArgIndex(); i < unpromotedLayouts.size(); i++) {
                MemoryLayout variadicLayout = unpromotedLayouts.get(i);
                if (variadicLayout instanceof ValueLayout vl) {
                    if (requiresVariadicPromotion(vl)) {
                        unpromotedParams[numPrefixArgs + i] = vl.carrier();
                    }
                }
            }

            MethodType unpromotedType = MethodType.methodType(mh.type().returnType(), unpromotedParams);
            mh = mh.asType(unpromotedType);
        }

        return mh;
    }

    private static int downcallNumPrefixArgs(FunctionDescriptor descriptor, LinkerOptions options) {
        int result = 1; // target address
        if (SharedUtils.returnsGroupLayout(descriptor)) {
            result++; // SegmentAllocator
        }
        if (options.hasCapturedCallState()) {
            result++;
        }
        return result;
    }

    // For now all of these are the same for every implementation
    // in the future we might need to make one of them abstract
    // and implement on a per-linker basis.
    private static boolean requiresVariadicIntPromotion(ValueLayout vl) {
        return vl.carrier() == boolean.class || vl.carrier() == byte.class
                    || vl.carrier() == char.class || vl.carrier() == short.class;
    }

    private static boolean requiresVariadicDoublePromotion(ValueLayout vl) {
        return vl.carrier() == float.class;
    }

    private static boolean requiresVariadicPromotion(ValueLayout vl) {
        return requiresVariadicIntPromotion(vl) || requiresVariadicDoublePromotion(vl);
    }
}
