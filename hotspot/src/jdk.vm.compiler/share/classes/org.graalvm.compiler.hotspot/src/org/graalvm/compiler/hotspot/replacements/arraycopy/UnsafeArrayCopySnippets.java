/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements.arraycopy;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayBaseOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayIndexScale;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeMask;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeMask;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.runtime;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.any;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.asm.NumUtil;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.phases.WriteBarrierAdditionPhase;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.extended.UnsafeCopyNode;
import org.graalvm.compiler.nodes.extended.UnsafeLoadNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.DirectObjectStoreNode;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Unsigned;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

/**
 * As opposed to {@link ArrayCopySnippets}, these Snippets do <b>not</b> perform store checks.
 */
public class UnsafeArrayCopySnippets implements Snippets {

    private static final boolean supportsUnalignedMemoryAccess = runtime().getHostJVMCIBackend().getTarget().arch.supportsUnalignedMemoryAccess();

    private static final JavaKind VECTOR_KIND = JavaKind.Long;
    private static final long VECTOR_SIZE = getArrayIndexScale(VECTOR_KIND);

    private static void vectorizedCopy(Object src, int srcPos, Object dest, int destPos, int length, JavaKind baseKind, LocationIdentity locationIdentity) {
        int arrayBaseOffset = arrayBaseOffset(baseKind);
        int elementSize = arrayIndexScale(baseKind);
        long byteLength = (long) length * elementSize;
        long srcOffset = (long) srcPos * elementSize;
        long destOffset = (long) destPos * elementSize;

        long preLoopBytes;
        long mainLoopBytes;
        long postLoopBytes;

        // We can easily vectorize the loop if both offsets have the same alignment.
        if (byteLength >= VECTOR_SIZE && (srcOffset % VECTOR_SIZE) == (destOffset % VECTOR_SIZE)) {
            preLoopBytes = NumUtil.roundUp(arrayBaseOffset + srcOffset, VECTOR_SIZE) - (arrayBaseOffset + srcOffset);
            postLoopBytes = (byteLength - preLoopBytes) % VECTOR_SIZE;
            mainLoopBytes = byteLength - preLoopBytes - postLoopBytes;
        } else {
            // Does the architecture support unaligned memory accesses?
            if (supportsUnalignedMemoryAccess) {
                preLoopBytes = byteLength % VECTOR_SIZE;
                mainLoopBytes = byteLength - preLoopBytes;
                postLoopBytes = 0;
            } else {
                // No. Let's do element-wise copying.
                preLoopBytes = byteLength;
                mainLoopBytes = 0;
                postLoopBytes = 0;
            }
        }

        if (probability(NOT_FREQUENT_PROBABILITY, src == dest) && probability(NOT_FREQUENT_PROBABILITY, srcPos < destPos)) {
            // bad aliased case
            srcOffset += byteLength;
            destOffset += byteLength;

            // Post-loop
            for (long i = 0; i < postLoopBytes; i += elementSize) {
                srcOffset -= elementSize;
                destOffset -= elementSize;
                UnsafeCopyNode.copy(src, arrayBaseOffset + srcOffset, dest, arrayBaseOffset + destOffset, baseKind, locationIdentity);
            }
            // Main-loop
            for (long i = 0; i < mainLoopBytes; i += VECTOR_SIZE) {
                srcOffset -= VECTOR_SIZE;
                destOffset -= VECTOR_SIZE;
                UnsafeCopyNode.copy(src, arrayBaseOffset + srcOffset, dest, arrayBaseOffset + destOffset, VECTOR_KIND, locationIdentity);
            }
            // Pre-loop
            for (long i = 0; i < preLoopBytes; i += elementSize) {
                srcOffset -= elementSize;
                destOffset -= elementSize;
                UnsafeCopyNode.copy(src, arrayBaseOffset + srcOffset, dest, arrayBaseOffset + destOffset, baseKind, locationIdentity);
            }
        } else {
            // Pre-loop
            for (long i = 0; i < preLoopBytes; i += elementSize) {
                UnsafeCopyNode.copy(src, arrayBaseOffset + srcOffset, dest, arrayBaseOffset + destOffset, baseKind, locationIdentity);
                srcOffset += elementSize;
                destOffset += elementSize;
            }
            // Main-loop
            for (long i = 0; i < mainLoopBytes; i += VECTOR_SIZE) {
                UnsafeCopyNode.copy(src, arrayBaseOffset + srcOffset, dest, arrayBaseOffset + destOffset, VECTOR_KIND, locationIdentity);
                srcOffset += VECTOR_SIZE;
                destOffset += VECTOR_SIZE;
            }
            // Post-loop
            for (long i = 0; i < postLoopBytes; i += elementSize) {
                UnsafeCopyNode.copy(src, arrayBaseOffset + srcOffset, dest, arrayBaseOffset + destOffset, baseKind, locationIdentity);
                srcOffset += elementSize;
                destOffset += elementSize;
            }
        }
    }

    @Fold
    static LocationIdentity getArrayLocation(JavaKind kind) {
        return NamedLocationIdentity.getArrayLocation(kind);
    }

    @Snippet
    public static void arraycopyByte(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Byte;
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    @Snippet
    public static void arraycopyBoolean(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Boolean;
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    @Snippet
    public static void arraycopyChar(char[] src, int srcPos, char[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Char;
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    @Snippet
    public static void arraycopyShort(short[] src, int srcPos, short[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Short;
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    @Snippet
    public static void arraycopyInt(int[] src, int srcPos, int[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Int;
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    @Snippet
    public static void arraycopyFloat(float[] src, int srcPos, float[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Float;
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    @Snippet
    public static void arraycopyLong(long[] src, int srcPos, long[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Long;
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    @Snippet
    public static void arraycopyDouble(double[] src, int srcPos, double[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Double;
        /*
         * TODO atomicity problem on 32-bit architectures: The JVM spec requires double values to be
         * copied atomically, but not long values. For example, on Intel 32-bit this code is not
         * atomic as long as the vector kind remains Kind.Long.
         */
        vectorizedCopy(src, srcPos, dest, destPos, length, kind, getArrayLocation(kind));
    }

    /**
     * For this kind, Object, we want to avoid write barriers between writes, but instead have them
     * at the end of the snippet. This is done by using {@link DirectObjectStoreNode}, and rely on
     * {@link WriteBarrierAdditionPhase} to put write barriers after the {@link UnsafeArrayCopyNode}
     * with kind Object.
     */
    @Snippet
    public static void arraycopyObject(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        JavaKind kind = JavaKind.Object;
        final int scale = arrayIndexScale(kind);
        int arrayBaseOffset = arrayBaseOffset(kind);
        LocationIdentity arrayLocation = getArrayLocation(kind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            long start = (long) (length - 1) * scale;
            for (long i = start; i >= 0; i -= scale) {
                Object a = UnsafeLoadNode.load(src, arrayBaseOffset + i + (long) srcPos * scale, kind, arrayLocation);
                DirectObjectStoreNode.storeObject(dest, arrayBaseOffset, i + (long) destPos * scale, a, getArrayLocation(kind), kind);
            }
        } else {
            long end = (long) length * scale;
            for (long i = 0; i < end; i += scale) {
                Object a = UnsafeLoadNode.load(src, arrayBaseOffset + i + (long) srcPos * scale, kind, arrayLocation);
                DirectObjectStoreNode.storeObject(dest, arrayBaseOffset, i + (long) destPos * scale, a, getArrayLocation(kind), kind);
            }
        }
    }

    @Snippet
    public static void arraycopyPrimitive(Object src, int srcPos, Object dest, int destPos, int length, int layoutHelper) {
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift(INJECTED_VMCONFIG)) & layoutHelperLog2ElementSizeMask(INJECTED_VMCONFIG);
        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift(INJECTED_VMCONFIG)) & layoutHelperHeaderSizeMask(INJECTED_VMCONFIG);

        Unsigned vectorSize = Word.unsigned(VECTOR_SIZE);
        Unsigned srcOffset = Word.unsigned(srcPos).shiftLeft(log2ElementSize).add(headerSize);
        Unsigned destOffset = Word.unsigned(destPos).shiftLeft(log2ElementSize).add(headerSize);
        Unsigned destStart = destOffset;
        Unsigned destEnd = destOffset.add(Word.unsigned(length).shiftLeft(log2ElementSize));

        Unsigned destVectorEnd = null;
        Unsigned nonVectorBytes = null;
        Unsigned sizeInBytes = Word.unsigned(length).shiftLeft(log2ElementSize);
        if (supportsUnalignedMemoryAccess) {
            nonVectorBytes = sizeInBytes.unsignedRemainder(vectorSize);
            destVectorEnd = destEnd;
        } else {
            boolean inPhase = srcOffset.and((int) VECTOR_SIZE - 1).equal(destOffset.and((int) VECTOR_SIZE - 1));
            boolean hasAtLeastOneVector = sizeInBytes.aboveOrEqual(vectorSize);
            // We must have at least one full vector, otherwise we must copy each byte separately
            if (hasAtLeastOneVector && inPhase) { // If in phase, we can vectorize
                nonVectorBytes = vectorSize.subtract(destStart.unsignedRemainder(vectorSize));
            } else { // fallback is byte-wise
                nonVectorBytes = sizeInBytes;
            }
            destVectorEnd = destEnd.subtract(destEnd.unsignedRemainder(vectorSize));
        }

        Unsigned destNonVectorEnd = destStart.add(nonVectorBytes);
        while (destOffset.belowThan(destNonVectorEnd)) {
            ObjectAccess.writeByte(dest, destOffset, ObjectAccess.readByte(src, srcOffset, any()), any());
            destOffset = destOffset.add(1);
            srcOffset = srcOffset.add(1);
        }
        // Unsigned destVectorEnd = destEnd.subtract(destEnd.unsignedRemainder(8));
        while (destOffset.belowThan(destVectorEnd)) {
            ObjectAccess.writeWord(dest, destOffset, ObjectAccess.readWord(src, srcOffset, any()), any());
            destOffset = destOffset.add(wordSize());
            srcOffset = srcOffset.add(wordSize());
        }
        // Do the last bytes each when it is required to have absolute alignment.
        while (!supportsUnalignedMemoryAccess && destOffset.belowThan(destEnd)) {
            ObjectAccess.writeByte(dest, destOffset, ObjectAccess.readByte(src, srcOffset, any()), any());
            destOffset = destOffset.add(1);
            srcOffset = srcOffset.add(1);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo[] arraycopySnippets;
        private final SnippetInfo genericPrimitiveSnippet;

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);

            arraycopySnippets = new SnippetInfo[JavaKind.values().length];
            arraycopySnippets[JavaKind.Boolean.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyBoolean");
            arraycopySnippets[JavaKind.Byte.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyByte");
            arraycopySnippets[JavaKind.Short.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyShort");
            arraycopySnippets[JavaKind.Char.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyChar");
            arraycopySnippets[JavaKind.Int.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyInt");
            arraycopySnippets[JavaKind.Long.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyLong");
            arraycopySnippets[JavaKind.Float.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyFloat");
            arraycopySnippets[JavaKind.Double.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyDouble");
            arraycopySnippets[JavaKind.Object.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyObject");

            genericPrimitiveSnippet = snippet(UnsafeArrayCopySnippets.class, "arraycopyPrimitive");
        }

        public void lower(UnsafeArrayCopyNode node, LoweringTool tool) {
            JavaKind elementKind = node.getElementKind();
            SnippetInfo snippet;
            if (elementKind == null) {
                // primitive array of unknown kind
                snippet = genericPrimitiveSnippet;
            } else {
                snippet = arraycopySnippets[elementKind.ordinal()];
                assert snippet != null : "arraycopy snippet for " + elementKind.name() + " not found";
            }

            Arguments args = new Arguments(snippet, node.graph().getGuardsStage(), tool.getLoweringStage());
            node.addSnippetArguments(args);

            SnippetTemplate template = template(args);
            template.instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }
    }
}
