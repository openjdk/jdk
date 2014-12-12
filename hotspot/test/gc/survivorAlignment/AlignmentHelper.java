/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.MemoryPoolMXBean;
import java.util.Optional;

import sun.hotspot.WhiteBox;

/**
 * Helper class aimed to provide information about alignment of objects in
 * particular heap space, expected memory usage after objects' allocation so on.
 */
public class AlignmentHelper {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static final long OBJECT_ALIGNMENT_IN_BYTES_FOR_32_VM = 8L;

    /**
     * Max relative allowed actual memory usage deviation from expected memory
     * usage.
     */
    private static final float MAX_RELATIVE_DEVIATION = 0.05f; // 5%

    public static final long OBJECT_ALIGNMENT_IN_BYTES = Optional.ofNullable(
            AlignmentHelper.WHITE_BOX.getIntxVMFlag("ObjectAlignmentInBytes"))
            .orElse(AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES_FOR_32_VM);

    public static final long SURVIVOR_ALIGNMENT_IN_BYTES = Optional.ofNullable(
            AlignmentHelper.WHITE_BOX.getIntxVMFlag("SurvivorAlignmentInBytes"))
            .orElseThrow(() ->new AssertionError(
                    "Unable to get SurvivorAlignmentInBytes value"));
    /**
     * Min amount of memory that will be occupied by an object.
     */
    public static final long MIN_OBJECT_SIZE
            = AlignmentHelper.WHITE_BOX.getObjectSize(new Object());
    /**
     * Min amount of memory that will be occupied by an empty byte array.
     */
    public static final long MIN_ARRAY_SIZE
            = AlignmentHelper.WHITE_BOX.getObjectSize(new byte[0]);

    /**
     * Precision at which actual memory usage in a heap space represented by
     * this sizing helper could be measured.
     */
    private final long memoryUsageMeasurementPrecision;
    /**
     * Min amount of memory that will be occupied by an object allocated in a
     * heap space represented by this sizing helper.
     */
    private final long minObjectSizeInThisSpace;
    /**
     * Object's alignment in a heap space represented by this sizing helper.
     */
    private final long objectAlignmentInThisRegion;
    /**
     * MemoryPoolMXBean associated with a heap space represented by this sizing
     * helper.
     */
    private final MemoryPoolMXBean poolMXBean;

    private static long alignUp(long value, long alignment) {
        return ((value - 1) / alignment + 1) * alignment;
    }

    protected AlignmentHelper(long memoryUsageMeasurementPrecision,
            long objectAlignmentInThisRegion, long minObjectSizeInThisSpace,
            MemoryPoolMXBean poolMXBean) {
        this.memoryUsageMeasurementPrecision = memoryUsageMeasurementPrecision;
        this.minObjectSizeInThisSpace = minObjectSizeInThisSpace;
        this.objectAlignmentInThisRegion = objectAlignmentInThisRegion;
        this.poolMXBean = poolMXBean;
    }

    /**
     * Returns how many objects have to be allocated to fill
     * {@code memoryToFill} bytes in this heap space using objects of size
     * {@code objectSize}.
     */
    public int getObjectsCount(long memoryToFill, long objectSize) {
        return (int) (memoryToFill / getObjectSizeInThisSpace(objectSize));
    }

    /**
     * Returns amount of memory that {@code objectsCount} of objects with size
     * {@code objectSize} will occupy this this space after allocation.
     */
    public long getExpectedMemoryUsage(long objectSize, int objectsCount) {
        long correctedObjectSize = getObjectSizeInThisSpace(objectSize);
        return AlignmentHelper.alignUp(correctedObjectSize * objectsCount,
                memoryUsageMeasurementPrecision);
    }

    /**
     * Returns current memory usage in this heap space.
     */
    public long getActualMemoryUsage() {
        return poolMXBean.getUsage().getUsed();
    }

    /**
     * Returns maximum memory usage deviation from {@code expectedMemoryUsage}
     * given the max allowed relative deviation equal to
     * {@code relativeDeviation}.
     *
     * Note that value returned by this method is aligned according to
     * memory measurement precision for this heap space.
     */
    public long getAllowedMemoryUsageDeviation(long expectedMemoryUsage) {
        long unalignedDeviation = (long) (expectedMemoryUsage *
                AlignmentHelper.MAX_RELATIVE_DEVIATION);
        return AlignmentHelper.alignUp(unalignedDeviation,
                memoryUsageMeasurementPrecision);
    }

    /**
     * Returns amount of memory that will be occupied by an object with size
     * {@code objectSize} in this heap space.
     */
    public long getObjectSizeInThisSpace(long objectSize) {
        objectSize = Math.max(objectSize, minObjectSizeInThisSpace);

        long alignedObjectSize = AlignmentHelper.alignUp(objectSize,
                objectAlignmentInThisRegion);
        long sizeDiff = alignedObjectSize - objectSize;

        // If there is not enough space to fit padding object, then object will
        // be aligned to {@code 2 * objectAlignmentInThisRegion}.
        if (sizeDiff >= AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES
                && sizeDiff < AlignmentHelper.MIN_OBJECT_SIZE) {
            alignedObjectSize += AlignmentHelper.MIN_OBJECT_SIZE;
            alignedObjectSize = AlignmentHelper.alignUp(alignedObjectSize,
                    objectAlignmentInThisRegion);
        }

        return alignedObjectSize;
    }
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format("AlignmentHelper for memory pool '%s':%n",
                poolMXBean.getName()));
        builder.append(String.format("Memory usage measurement precision: %d%n",
                memoryUsageMeasurementPrecision));
        builder.append(String.format("Min object size in this space: %d%n",
                minObjectSizeInThisSpace));
        builder.append(String.format("Object alignment in this space: %d%n",
                objectAlignmentInThisRegion));

        return builder.toString();
    }
}
