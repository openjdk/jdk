/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @key randomness
 * @library /test/lib
 * @modules java.net.http/jdk.internal.net.http.qpack:+open
 *          java.net.http/jdk.internal.net.http.qpack.readers
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=NORMAL DynamicTableTest
 */

import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.readers.IntegerReader;
import jdk.test.lib.RandomFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DynamicTableTest {

    // Test for addition to the table and that indices are growing monotonically,
    // and they can be used to retrieve previously added entries
    @Test
    public void monotonicIndexes() {
        int tableMaxCapacityBytes = 2048;
        int numberOfElementsToAdd = 1024;
        int charsPerNumber = (int) Math.ceil(Math.log10(numberOfElementsToAdd));
        int oneElementSize = 32 + HEADER_NAME_PREFIX.length() + HEADER_VALUE_PREFIX.length() + charsPerNumber * 2;

        // Expected table capacity in elements
        int maxElementsInTable = tableMaxCapacityBytes / oneElementSize;

        // Test element id counter
        long lastAddedId;
        var dynamicTable = new DynamicTable(QPACK.getLogger().subLogger("monotonicIndexes"));
        dynamicTable.setMaxTableCapacity(2048);
        dynamicTable.setCapacity(tableMaxCapacityBytes);

        for (lastAddedId = 0; lastAddedId < numberOfElementsToAdd; lastAddedId++) {
            var name = generateHeaderString(lastAddedId, true, charsPerNumber);
            var value = generateHeaderString(lastAddedId, false, charsPerNumber);
            long addedId = dynamicTable.insert(name, value);

            // Check that dynamic table put gives back monotonically increasing indexes
            Assertions.assertEquals(lastAddedId, addedId);

            if (lastAddedId > maxElementsInTable) {
                // Check that oldest element is available and not reclaimed
                long oldestAliveId = lastAddedId - maxElementsInTable + 1;
                dynamicTable.get(oldestAliveId);

                // Check that relative indexing can be used to get oldest and newest entry
                dynamicTable.getRelative(maxElementsInTable - 1);
                dynamicTable.getRelative(0);

                // Check that reverse lookup is working for a random index from not reclaimed region
                long rid = RANDOM.nextLong(oldestAliveId, lastAddedId);
                String rName = generateHeaderString(rid, true, charsPerNumber);
                String rValue = generateHeaderString(rid, false, charsPerNumber);

                // The reverse lookup search result range is shifted by 1 to implement search result indexing:
                //    full match found in a table: searchResult = idx + 1
                //    partial match (name) in a table: searchResult = -idx - 1
                //    no match: 0
                long fullMatchSearchResult = dynamicTable.search(rName, rValue);
                long onlyNameSearchResult = dynamicTable.search(rName, "notFoundInTable");
                long noMatchResult = dynamicTable.search(HEADER_NAME_PREFIX, HEADER_VALUE_PREFIX);

                Assertions.assertEquals(rid, fullMatchSearchResult - 1);
                Assertions.assertEquals(rid, -onlyNameSearchResult - 1);
                Assertions.assertEquals(0, noMatchResult);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("randomTableResizeData")
    public void randomTableResize(int initialSize, long tail, long head, int resizeTo)
            throws Throwable {
        HeaderField[] initial = generateHeadersArray(initialSize, tail, head);
        resizeTestRunner(initial, tail, head, resizeTo);
    }

    public Object[][] randomTableResizeData() {
        return IntStream.range(0, 1000)
                .boxed()
                .map(i -> newRandomTableConfiguration())
                .toArray(Object[][]::new);
    }

    @Test
    public void holderArrayLengthTest() {
        // Test that holder array size for storing elements is increased according to demand on array
        // elements, and that by default its length is set to 64 elements.
        var dynamicTable = new DynamicTable(QPACK.getLogger().subLogger("tableResizeTests"));

        // Check that the initial array length is DynamicTable.INITIAL_HOLDER_ARRAY_SIZE
        Assertions.assertEquals(                INITIAL_HOLDER_ARRAY_SIZE, getElementsArrayLength(dynamicTable));

        // Update dynamic table capacity to maximum allowed value and check
        // that holder array is not changed
        dynamicTable.setMaxTableCapacity(IntegerReader.QPACK_MAX_INTEGER_VALUE);
        dynamicTable.setCapacity(IntegerReader.QPACK_MAX_INTEGER_VALUE);
        Assertions.assertEquals(                INITIAL_HOLDER_ARRAY_SIZE, getElementsArrayLength(dynamicTable));

        // Add DynamicTable.INITIAL_HOLDER_ARRAY_SIZE + 1 element to the dynamic table
        // and check that its length is increased 2 times
        for (int i = 0; i <= INITIAL_HOLDER_ARRAY_SIZE; i++) {
            dynamicTable.insert("name" + i, "value" + i);
        }
        Assertions.assertEquals(INITIAL_HOLDER_ARRAY_SIZE << 1, getElementsArrayLength(dynamicTable));
    }

    // Test for a simple resize that checks that unique indexes still reference the correct entry
    @ParameterizedTest
    @MethodSource("simpleTableResizeData")
    public void simpleTableResize(HeaderField[] array, long tail, long head, int resizeTo) throws Throwable {
        resizeTestRunner(array, tail, head, resizeTo);
    }

    public Object[][] simpleTableResizeData() {
        return new Object[][]{
                tableResizeScenario1(), tableResizeScenario2(),
                tableResizeScenario3(), tableResizeScenario4(),
                tableResizeScenario5()};
    }

    private Object[] tableResizeScenario1() {
        HeaderField[] elements = new HeaderField[8];
        elements[5] = new HeaderField("5", "5"); // Tail
        elements[6] = new HeaderField("6", "6");
        elements[7] = new HeaderField("7", "7"); // Head
        return new Object[]{elements, 21L, 24L, 4};
    }

    private Object[] tableResizeScenario2() {
        HeaderField[] elements = new HeaderField[8];
        elements[2] = new HeaderField("2", "2"); // Tail
        elements[3] = new HeaderField("3", "3");
        elements[4] = new HeaderField("4", "4");
        elements[5] = new HeaderField("5", "5"); // Head
        return new Object[]{elements, 26L, 30L, 4};
    }

    private Object[] tableResizeScenario3() {
        HeaderField[] elements = new HeaderField[8];
        elements[0] = new HeaderField("4", "4");
        elements[1] = new HeaderField("5", "5"); // Head
        elements[6] = new HeaderField("2", "2"); // Tail
        elements[7] = new HeaderField("3", "3");
        return new Object[]{elements, 30L, 34L, 64};
    }

    private Object[] tableResizeScenario4() {
        HeaderField[] elements = new HeaderField[8];
        elements[0] = new HeaderField("4", "4");
        elements[1] = new HeaderField("5", "5"); // Head
        elements[5] = new HeaderField("1", "1"); // Tail
        elements[6] = new HeaderField("2", "2");
        elements[7] = new HeaderField("3", "3");
        return new Object[]{elements, 29L, 34L, 16};
    }

    private Object[] tableResizeScenario5() {
        HeaderField[] elements = new HeaderField[64];
        elements[10] = new HeaderField("1", "1");
        return new Object[]{elements, 3977L, 3978L, 16};
    }

    private static void resizeTestRunner(HeaderField[] array, long tail, long head, int resizeTo) throws Throwable {
        assert tail < head;
        var dynamicTable = new DynamicTable(QPACK.getLogger().subLogger("tableResizeTests"));
        dynamicTable.setMaxTableCapacity(2048);
        dynamicTable.setCapacity(2048);
        // Prepare dynamic table state for the resize operation
        DT_ELEMENTS_VH.set(dynamicTable, array);
        DT_HEAD_VH.set(dynamicTable, head);
        DT_TAIL_VH.set(dynamicTable, tail);

        // Call resize
        ReentrantReadWriteLock lock = (ReentrantReadWriteLock) DT_LOCK_VH.get(dynamicTable);
        lock.writeLock().lock();
        HeaderField[] resizeResult;
        try {
            // Call DynamicTable.resize
            DT_RESIZE_MH.invoke(dynamicTable, resizeTo);
            // Acquire resize result
            resizeResult = (HeaderField[]) DT_ELEMENTS_VH.get(dynamicTable);
        } finally {
            lock.writeLock().unlock();
        }

        // Check the resulting array by calculating the expected array
        HeaderField[] expectedResult = calcResizeResult(array, tail, head, resizeTo);

        // Check the resulting of the resize operation
        checkResizeResult(array, resizeResult, expectedResult);
    }

    private static HeaderField[] generateHeadersArray(int size, long tail, long head) {
        assert head > tail;
        HeaderField[] res = new HeaderField[size];
        assert head > 0L;
        int charsPerNumber = (int) (Math.log10(head) + 1);
        for (long eid = tail; eid < head; eid++) {
            int idx = (int) (eid % size);
            res[idx] = new HeaderField(generateHeaderString(eid, true, charsPerNumber),
                                       generateHeaderString(eid, false, charsPerNumber));
        }
        return res;
    }

    private static int getElementsArrayLength(DynamicTable dynamicTable) {
        HeaderField[] array = (HeaderField[]) DT_ELEMENTS_VH.get(dynamicTable);
        return array.length;
    }

    private static Object[] newRandomTableConfiguration() {
        boolean shrink = RANDOM.nextBoolean();
        int initialSize = pow2size(RANDOM.nextInt(2, 2048));
        int resizeTo = shrink ? pow2size(RANDOM.nextInt(1, initialSize)) : pow2size(RANDOM.nextInt(initialSize, 4096));
        int elementsCount = RANDOM.nextInt(0, Math.min(initialSize, resizeTo));
        long tail = RANDOM.nextLong(100000);
        long head = tail + elementsCount + 1;
        return new Object[]{initialSize, tail, head, resizeTo};
    }

    private static HeaderField[] calcResizeResult(HeaderField[] array, long tail, long head, int resizeTo) {
        HeaderField[] result = new HeaderField[resizeTo];
        for (long p = tail; p < head; p++) {
            int newIdx = (int) (p % resizeTo);
            int oldIdx = (int) (p % array.length);
            result[newIdx] = array[oldIdx];
        }
        return result;
    }

    private static void checkResizeResult(HeaderField[] initial, HeaderField[] resized, HeaderField[] expected) {
        Assertions.assertEquals(expected.length, resized.length);
        for (int index = 0; index < expected.length; index++) {
            if (!sameHeaderField(expected[index], resized[index])) {
                System.err.println("Initial Array:" + Arrays.deepToString(initial));
                System.err.println("Resized Array:" + Arrays.deepToString(resized));
                System.err.println("Expected Array:" + Arrays.deepToString(expected));
                Assertions.fail("DynamicTable.resize failed");
            }
        }
    }

    private static boolean sameHeaderField(HeaderField a, HeaderField b) {
        // Check if one HeaderField is null and another is not null
        if (a == null ^ b == null) {
            return false;
        }
        // Given previous check, check if both HeaderField are null
        if (a == null) {
            return true;
        }
        // Both HFs are not null - will check name() and value() values
        return a.name().equals(b.name()) && a.value().equals(b.value());
    }

    private static MethodHandle findDynamicTableResizeMH() {
        try {
            MethodType mt = MethodType.methodType(void.class, int.class);
            return DT_LOOKUP.findVirtual(DynamicTable.class, "resize", mt);
        } catch (Exception e) {
            Assertions.fail("Failed to initialize DynamicTable.resize MH", e);
            return null;
        }
    }

    private static VarHandle findDynamicTableFieldVH(String fieldName, Class<?> fieldType) {
        try {
            return DT_LOOKUP.findVarHandle(DynamicTable.class, fieldName, fieldType);
        } catch (Exception e) {
            Assertions.fail("Failed to initialize DynamicTable private Lookup instance", e);
            return null;
        }
    }

    private static <T> T readDynamicTableStaticFieldValue(String fieldName, Class<T> fieldType) {
        try {
            var vh = DT_LOOKUP.findStaticVarHandle(DynamicTable.class, fieldName, fieldType);
            return (T) vh.get();
        } catch (Exception e) {
            Assertions.fail("Failed to read DynamicTable static field value", e);
            return null;
        }
    }

    private static MethodHandles.Lookup initializeDtLookup() {
        try {
            return MethodHandles.privateLookupIn(DynamicTable.class, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            Assertions.fail("Failed to initialize DynamicTable private Lookup instance", e);
            return null;
        }
    }


    private static final MethodHandles.Lookup DT_LOOKUP;
    private static final MethodHandle DT_RESIZE_MH;
    private static final VarHandle DT_HEAD_VH;
    private static final VarHandle DT_TAIL_VH;
    private static final VarHandle DT_ELEMENTS_VH;
    private static final VarHandle DT_LOCK_VH;
    private static final int INITIAL_HOLDER_ARRAY_SIZE;

    static {
        DT_LOOKUP = initializeDtLookup();
        DT_RESIZE_MH = findDynamicTableResizeMH();
        DT_HEAD_VH = findDynamicTableFieldVH("head", long.class);
        DT_TAIL_VH = findDynamicTableFieldVH("tail", long.class);
        DT_ELEMENTS_VH = findDynamicTableFieldVH("elements", HeaderField[].class);
        DT_LOCK_VH = findDynamicTableFieldVH("lock", ReentrantReadWriteLock.class);
        INITIAL_HOLDER_ARRAY_SIZE = readDynamicTableStaticFieldValue(
                               "INITIAL_HOLDER_ARRAY_LENGTH", int.class);
    }

    private static String generateHeaderString(long id, boolean generateName, int charsPerNumber) {
        return (generateName ? HEADER_NAME_PREFIX : HEADER_VALUE_PREFIX) + ("%0" + charsPerNumber + "d").formatted(id);
    }

    private static int pow2size(int size) {
        return 1 << (32 - Integer.numberOfLeadingZeros(size - 1));
    }

    private static final String HEADER_NAME_PREFIX = "HeaderName";
    private static final String HEADER_VALUE_PREFIX = "HeaderValue";
    private static final Random RANDOM = RandomFactory.getRandom();
}
