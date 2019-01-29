/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.survivorAlignment;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import com.sun.management.ThreadMXBean;
import sun.hotspot.WhiteBox;
import jdk.internal.misc.Unsafe;

/**
 * Main class for tests on {@code SurvivorAlignmentInBytes} option.
 *
 * Typical usage is to obtain instance using fromArgs method, allocate objects
 * and verify that actual memory usage in tested heap space is close to
 * expected.
 */
public class SurvivorAlignmentTestMain {
    enum HeapSpace {
        EDEN,
        SURVIVOR,
        TENURED
    }

    public static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static final long MAX_TENURING_THRESHOLD = Optional.ofNullable(
            SurvivorAlignmentTestMain.WHITE_BOX.getIntxVMFlag(
                    "MaxTenuringThreshold")).orElse(15L);

    /**
     * Regexp used to parse memory size params, like 2G, 34m or 15k.
     */
    private static final Pattern SIZE_REGEX
            = Pattern.compile("(?<size>[0-9]+)(?<multiplier>[GMKgmk])?");

    // Names of different heap spaces.
    private static final String DEF_NEW_EDEN = "Eden Space";
    private static final String DEF_NEW_SURVIVOR = "Survivor Space";
    private static final String PAR_NEW_EDEN = "Par Eden Space";
    private static final String PAR_NEW_SURVIVOR = "Par Survivor Space";
    private static final String PS_EDEN = "PS Eden Space";
    private static final String PS_SURVIVOR = "PS Survivor Space";
    private static final String G1_EDEN = "G1 Eden Space";
    private static final String G1_SURVIVOR = "G1 Survivor Space";
    private static final String SERIAL_TENURED = "Tenured Gen";
    private static final String CMS_TENURED = "CMS Old Gen";
    private static final String PS_TENURED = "PS Old Gen";
    private static final String G1_TENURED = "G1 Old Gen";

    private static final long G1_HEAP_REGION_SIZE = Optional.ofNullable(
            SurvivorAlignmentTestMain.WHITE_BOX.getUintxVMFlag(
                    "G1HeapRegionSize")).orElse(-1L);

    /**
     * Min size of free chunk in CMS generation.
     * An object allocated in CMS generation will at least occupy this amount
     * of bytes.
     */
    private static final long CMS_MIN_FREE_CHUNK_SIZE
            = 3L * Unsafe.ADDRESS_SIZE;

    private static final AlignmentHelper EDEN_SPACE_HELPER;
    private static final AlignmentHelper SURVIVOR_SPACE_HELPER;
    private static final AlignmentHelper TENURED_SPACE_HELPER;
    /**
     * Amount of memory that should be filled during a test run.
     */
    private final long memoryToFill;
    /**
     * The size of an objects that will be allocated during a test run.
     */
    private final long objectSize;
    /**
     * Amount of memory that will be actually occupied by an object in eden
     * space.
     */
    private final long actualObjectSize;
    /**
     * Storage for allocated objects.
     */
    private final Object[] garbage;
    /**
     * Heap space whose memory usage is a subject of assertions during the test
     * run.
     */
    private final HeapSpace testedSpace;

    private long[] baselinedThreadMemoryUsage = null;
    private long[] threadIds = null;

    /**
     * Initialize {@code EDEN_SPACE_HELPER}, {@code SURVIVOR_SPACE_HELPER} and
     * {@code TENURED_SPACE_HELPER} to represent heap spaces in use.
     *
     * Note that regardless to GC object's alignment in survivor space is
     * expected to be equal to {@code SurvivorAlignmentInBytes} value and
     * alignment in other spaces is expected to be equal to
     * {@code ObjectAlignmentInBytes} value.
     *
     * In CMS generation we can't allocate less then {@code MinFreeChunk} value,
     * for other CGs we expect that object of size {@code MIN_OBJECT_SIZE}
     * could be allocated as it is (of course, its size could be aligned
     * according to alignment value used in a particular space).
     *
     * For G1 GC MXBeans could report memory usage only with region size
     * precision (if an object allocated in some G1 heap region, then all region
     * will claimed as used), so for G1's spaces precision is equal to
     * {@code G1HeapRegionSize} value.
     */
    static {
        AlignmentHelper edenHelper = null;
        AlignmentHelper survivorHelper = null;
        AlignmentHelper tenuredHelper = null;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            switch (pool.getName()) {
                case SurvivorAlignmentTestMain.DEF_NEW_EDEN:
                case SurvivorAlignmentTestMain.PAR_NEW_EDEN:
                case SurvivorAlignmentTestMain.PS_EDEN:
                    Asserts.assertNull(edenHelper,
                            "Only one bean for eden space is expected.");
                    edenHelper = new AlignmentHelper(
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.MIN_OBJECT_SIZE, pool);
                    break;
                case SurvivorAlignmentTestMain.G1_EDEN:
                    Asserts.assertNull(edenHelper,
                            "Only one bean for eden space is expected.");
                    edenHelper = new AlignmentHelper(
                            SurvivorAlignmentTestMain.G1_HEAP_REGION_SIZE,
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.MIN_OBJECT_SIZE, pool);
                    break;
                case SurvivorAlignmentTestMain.DEF_NEW_SURVIVOR:
                case SurvivorAlignmentTestMain.PAR_NEW_SURVIVOR:
                case SurvivorAlignmentTestMain.PS_SURVIVOR:
                    Asserts.assertNull(survivorHelper,
                            "Only one bean for survivor space is expected.");
                    survivorHelper = new AlignmentHelper(
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.SURVIVOR_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.MIN_OBJECT_SIZE, pool);
                    break;
                case SurvivorAlignmentTestMain.G1_SURVIVOR:
                    Asserts.assertNull(survivorHelper,
                            "Only one bean for survivor space is expected.");
                    survivorHelper = new AlignmentHelper(
                            SurvivorAlignmentTestMain.G1_HEAP_REGION_SIZE,
                            AlignmentHelper.SURVIVOR_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.MIN_OBJECT_SIZE, pool);
                    break;
                case SurvivorAlignmentTestMain.SERIAL_TENURED:
                case SurvivorAlignmentTestMain.PS_TENURED:
                case SurvivorAlignmentTestMain.G1_TENURED:
                    Asserts.assertNull(tenuredHelper,
                            "Only one bean for tenured space is expected.");
                    tenuredHelper = new AlignmentHelper(
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.MIN_OBJECT_SIZE, pool);
                    break;
                case SurvivorAlignmentTestMain.CMS_TENURED:
                    Asserts.assertNull(tenuredHelper,
                            "Only one bean for tenured space is expected.");
                    tenuredHelper = new AlignmentHelper(
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            AlignmentHelper.OBJECT_ALIGNMENT_IN_BYTES,
                            SurvivorAlignmentTestMain.CMS_MIN_FREE_CHUNK_SIZE,
                            pool);
                    break;
            }
        }
        EDEN_SPACE_HELPER = Objects.requireNonNull(edenHelper,
                "AlignmentHelper for eden space should be initialized.");
        SURVIVOR_SPACE_HELPER = Objects.requireNonNull(survivorHelper,
                "AlignmentHelper for survivor space should be initialized.");
        TENURED_SPACE_HELPER = Objects.requireNonNull(tenuredHelper,
                "AlignmentHelper for tenured space should be initialized.");
    }
    /**
     * Returns an SurvivorAlignmentTestMain instance constructed using CLI
     * options.
     *
     * Following options are expected:
     * <ul>
     *     <li>memoryToFill</li>
     *     <li>objectSize</li>
     * </ul>
     *
     * Both argument may contain multiplier suffix k, m or g.
     */
    public static SurvivorAlignmentTestMain fromArgs(String[] args) {
        Asserts.assertEQ(args.length, 3, "Expected three arguments: "
                + "memory size, object size and tested heap space name.");

        long memoryToFill = parseSize(args[0]);
        long objectSize = Math.max(parseSize(args[1]),
                AlignmentHelper.MIN_ARRAY_SIZE);
        HeapSpace testedSpace = HeapSpace.valueOf(args[2]);

        return new SurvivorAlignmentTestMain(memoryToFill, objectSize,
                testedSpace);
    }

    /**
     * Returns a value parsed from a string with format
     * &lt;integer&gt;&lt;multiplier&gt;.
     */
    private static long parseSize(String sizeString) {
        Matcher matcher = SIZE_REGEX.matcher(sizeString);
        Asserts.assertTrue(matcher.matches(),
                "sizeString should have following format \"[0-9]+([MBK])?\"");
        long size = Long.valueOf(matcher.group("size"));

        if (matcher.group("multiplier") != null) {
            long K = 1024L;
            // fall through multipliers
            switch (matcher.group("multiplier").toLowerCase()) {
                case "g":
                    size *= K;
                case "m":
                    size *= K;
                case "k":
                    size *= K;
            }
        }
        return size;
    }

    private SurvivorAlignmentTestMain(long memoryToFill, long objectSize,
            HeapSpace testedSpace) {
        this.objectSize = objectSize;
        this.memoryToFill = memoryToFill;
        this.testedSpace = testedSpace;

        AlignmentHelper helper = SurvivorAlignmentTestMain.EDEN_SPACE_HELPER;

        this.actualObjectSize = helper.getObjectSizeInThisSpace(
                this.objectSize);
        int arrayLength = helper.getObjectsCount(memoryToFill, this.objectSize);
        garbage = new Object[arrayLength];
    }

    /**
     * Allocate byte arrays to fill {@code memoryToFill} memory.
     */
    public void allocate() {
        int byteArrayLength = Math.max((int) (objectSize
                - Unsafe.ARRAY_BYTE_BASE_OFFSET), 0);

        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = new byte[byteArrayLength];
        }
    }

    /**
     * Release memory occupied after {@code allocate} call.
     */
    public void release() {
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = null;
        }
    }

    /**
     * Returns expected amount of memory occupied in a {@code heapSpace} by
     * objects referenced from {@code garbage} array.
     */
    public long getExpectedMemoryUsage() {
        AlignmentHelper alignmentHelper = getAlignmentHelper(testedSpace);
        return alignmentHelper.getExpectedMemoryUsage(objectSize,
                garbage.length);
    }

    /**
     * Verifies that memory usage in a {@code heapSpace} deviates from
     * {@code expectedUsage} for no more than {@code MAX_RELATIVE_DEVIATION}.
     */
    public void verifyMemoryUsage(long expectedUsage) {
        AlignmentHelper alignmentHelper = getAlignmentHelper(testedSpace);

        long actualMemoryUsage = alignmentHelper.getActualMemoryUsage();
        boolean otherThreadsAllocatedMemory = areOtherThreadsAllocatedMemory();

        long memoryUsageDiff = Math.abs(actualMemoryUsage - expectedUsage);
        long maxAllowedUsageDiff
                = alignmentHelper.getAllowedMemoryUsageDeviation(expectedUsage);

        System.out.println("Verifying memory usage in space: " + testedSpace);
        System.out.println("Allocated objects count: " + garbage.length);
        System.out.println("Desired object size: " + objectSize);
        System.out.println("Actual object size: " + actualObjectSize);
        System.out.println("Expected object size in space: "
                + alignmentHelper.getObjectSizeInThisSpace(objectSize));
        System.out.println("Expected memory usage: " + expectedUsage);
        System.out.println("Actual memory usage: " + actualMemoryUsage);
        System.out.println("Memory usage diff: " + memoryUsageDiff);
        System.out.println("Max allowed usage diff: " + maxAllowedUsageDiff);

        if (memoryUsageDiff > maxAllowedUsageDiff
                && otherThreadsAllocatedMemory) {
            System.out.println("Memory usage diff is incorrect, but it seems "
                    + "like someone else allocated objects");
            return;
        }

        Asserts.assertLTE(memoryUsageDiff, maxAllowedUsageDiff,
                "Actual memory usage should not deviate from expected for " +
                        "more then " + maxAllowedUsageDiff);
    }

    /**
     * Baselines amount of memory allocated by each thread.
     */
    public void baselineMemoryAllocation() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        threadIds = bean.getAllThreadIds();
        baselinedThreadMemoryUsage = bean.getThreadAllocatedBytes(threadIds);
    }

    /**
     * Checks if threads other then the current thread were allocating objects
     * after baselinedThreadMemoryUsage call.
     *
     * If baselinedThreadMemoryUsage was not called, then this method will return
     * {@code false}.
     */
    public boolean areOtherThreadsAllocatedMemory() {
        if (baselinedThreadMemoryUsage == null) {
            return false;
        }

        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentMemoryAllocation[]
                = bean.getThreadAllocatedBytes(threadIds);
        boolean otherThreadsAllocatedMemory = false;

        System.out.println("Verifying amount of memory allocated by threads:");
        for (int i = 0; i < threadIds.length; i++) {
            System.out.format("Thread %d%nbaseline allocation: %d"
                            + "%ncurrent allocation:%d%n", threadIds[i],
                    baselinedThreadMemoryUsage[i], currentMemoryAllocation[i]);
            System.out.println(bean.getThreadInfo(threadIds[i]));

            long bytesAllocated = Math.abs(currentMemoryAllocation[i]
                    - baselinedThreadMemoryUsage[i]);
            if (bytesAllocated > 0
                    && threadIds[i] != Thread.currentThread().getId()) {
                otherThreadsAllocatedMemory = true;
            }
        }

        return otherThreadsAllocatedMemory;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format("SurvivorAlignmentTestMain info:%n"));
        builder.append(String.format("Desired object size: %d%n", objectSize));
        builder.append(String.format("Memory to fill: %d%n", memoryToFill));
        builder.append(String.format("Objects to be allocated: %d%n",
                garbage.length));

        builder.append(String.format("Alignment helpers to be used: %n"));
        for (HeapSpace heapSpace: HeapSpace.values()) {
            builder.append(String.format("For space %s:%n%s%n", heapSpace,
                    getAlignmentHelper(heapSpace)));
        }

        return builder.toString();
    }

    /**
     * Returns {@code AlignmentHelper} for a space {@code heapSpace}.
     */
    public static AlignmentHelper getAlignmentHelper(HeapSpace heapSpace) {
        switch (heapSpace) {
            case EDEN:
                return SurvivorAlignmentTestMain.EDEN_SPACE_HELPER;
            case SURVIVOR:
                return SurvivorAlignmentTestMain.SURVIVOR_SPACE_HELPER;
            case TENURED:
                return SurvivorAlignmentTestMain.TENURED_SPACE_HELPER;
            default:
                throw new Error("Unexpected heap space: " + heapSpace);
        }
    }
}
