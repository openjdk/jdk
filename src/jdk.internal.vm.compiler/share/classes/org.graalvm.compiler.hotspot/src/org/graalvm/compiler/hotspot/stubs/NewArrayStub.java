/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayAllocationSize;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayPrototypeMarkWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.getAndClearObjectResult;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperElementTypeMask;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperElementTypeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeMask;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeMask;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readLayoutHelper;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.useCMSIncrementalMode;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.formatArray;
import static org.graalvm.compiler.hotspot.stubs.NewInstanceStub.refillAllocate;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.handlePendingException;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.newDescriptor;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.printf;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.verifyObject;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.replacements.NewObjectSnippets;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;

/**
 * Stub implementing the fast path for TLAB refill during instance class allocation. This stub is
 * called from the {@linkplain NewObjectSnippets inline} allocation code when TLAB allocation fails.
 * If this stub fails to refill the TLAB or allocate the object, it calls out to the HotSpot C++
 * runtime to complete the allocation.
 */
public class NewArrayStub extends SnippetStub {

    public NewArrayStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("newArray", options, providers, linkage);
    }

    @Override
    protected Object[] makeConstArgs() {
        HotSpotResolvedObjectType intArrayType = (HotSpotResolvedObjectType) providers.getMetaAccess().lookupJavaType(int[].class);
        int count = method.getSignature().getParameterCount(false);
        Object[] args = new Object[count];
        assert checkConstArg(3, "intArrayHub");
        assert checkConstArg(4, "threadRegister");
        assert checkConstArg(5, "options");
        args[3] = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), intArrayType.klass(), null);
        args[4] = providers.getRegisters().getThreadRegister();
        args[5] = options;
        return args;
    }

    @Fold
    static boolean logging(OptionValues options) {
        return StubOptions.TraceNewArrayStub.getValue(options);
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to
     * -XX:-UseTLAB).
     *
     * @param hub the hub of the object to be allocated
     * @param length the length of the array
     * @param fillContents Should the array be filled with zeroes?
     * @param intArrayHub the hub for {@code int[].class}
     */
    @Snippet
    private static Object newArray(KlassPointer hub, int length, boolean fillContents, @ConstantParameter KlassPointer intArrayHub, @ConstantParameter Register threadRegister,
                    @ConstantParameter OptionValues options) {
        int layoutHelper = readLayoutHelper(hub);
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift(INJECTED_VMCONFIG)) & layoutHelperLog2ElementSizeMask(INJECTED_VMCONFIG);
        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift(INJECTED_VMCONFIG)) & layoutHelperHeaderSizeMask(INJECTED_VMCONFIG);
        int elementKind = (layoutHelper >> layoutHelperElementTypeShift(INJECTED_VMCONFIG)) & layoutHelperElementTypeMask(INJECTED_VMCONFIG);
        int sizeInBytes = arrayAllocationSize(length, headerSize, log2ElementSize);
        if (logging(options)) {
            printf("newArray: element kind %d\n", elementKind);
            printf("newArray: array length %d\n", length);
            printf("newArray: array size %d\n", sizeInBytes);
            printf("newArray: hub=%p\n", hub.asWord().rawValue());
        }

        // check that array length is small enough for fast path.
        Word thread = registerAsWord(threadRegister);
        boolean inlineContiguousAllocationSupported = GraalHotSpotVMConfigNode.inlineContiguousAllocationSupported();
        if (inlineContiguousAllocationSupported && !useCMSIncrementalMode(INJECTED_VMCONFIG) && length >= 0 && length <= MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH) {
            Word memory = refillAllocate(thread, intArrayHub, sizeInBytes, logging(options));
            if (memory.notEqual(0)) {
                if (logging(options)) {
                    printf("newArray: allocated new array at %p\n", memory.rawValue());
                }
                return verifyObject(
                                formatArray(hub, sizeInBytes, length, headerSize, memory, WordFactory.unsigned(arrayPrototypeMarkWord(INJECTED_VMCONFIG)), fillContents, false, null));
            }
        }
        if (logging(options)) {
            printf("newArray: calling new_array_c\n");
        }

        newArrayC(NEW_ARRAY_C, thread, hub, length);
        handlePendingException(thread, true);
        return verifyObject(getAndClearObjectResult(thread));
    }

    public static final ForeignCallDescriptor NEW_ARRAY_C = newDescriptor(NewArrayStub.class, "newArrayC", void.class, Word.class, KlassPointer.class, int.class);

    @NodeIntrinsic(StubForeignCallNode.class)
    public static native void newArrayC(@ConstantNodeParameter ForeignCallDescriptor newArrayC, Word thread, KlassPointer hub, int length);
}
