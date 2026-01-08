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
 * @summary test dynamic table entry eviction scenarios
 * @modules java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack:+open
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=EXTRA EntriesEvictionTest
 */

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.Encoder.SectionReference;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.QPACK.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EntriesEvictionTest {

    @ParameterizedTest
    @MethodSource("evictionScenarios")
    public void evictionInsertionTest(TestHeader headerToAdd,
                                      SectionReference sectionReference,
                                      long insertedId,
                                      long largestEvictedId) {
        Logger logger = QPACK.getLogger().subLogger("evictionInsertionTest");
        DynamicTable dynamicTable = new DynamicTable(logger);

        dynamicTable.setMaxTableCapacity(TABLE_CAPACITY);
        dynamicTable.setCapacity(TABLE_CAPACITY);

        for (TestHeader header : TEST_HEADERS) {
            dynamicTable.insert(header.name, header.value);
        }

        // Insert last entry
        long id = dynamicTable.insert(headerToAdd.name, headerToAdd.value, sectionReference);

        Assertions.assertEquals(insertedId, id);

        if (largestEvictedId != -1) {
            // Check that evicted entry with the largest absolute index
            // is not accessible
            Assertions.assertThrows(Throwable.class, () -> dynamicTable.get(largestEvictedId));
            // Check that an entry after that can be acquired with its
            // absolute index
            dynamicTable.get(largestEvictedId + 1);
        }

        if (insertedId != -1) {
            HeaderField insertedField = dynamicTable.get(insertedId);
            Assertions.assertEquals(new HeaderField(headerToAdd.name(), headerToAdd.value()),
                                    insertedField);
        }
    }

    public static Object[][] evictionScenarios() {

        // Header that requires only one entry to be evicted
        String oneSizedValue = HEADER_PREXIX + String.format("%03d", HEADERS_COUNT);
        TestHeader headerToAddWithOneEviction = TestHeader.newHeader(
                oneSizedValue, oneSizedValue);

        // Header that requires two entries to be evicted
        // "e" is repeated 16 times to compensate 32 bytes - 16 in header name,
        // another 16 in header value
        String doubleSizedHeaderValue = (HEADER_PREXIX + "Dbl").repeat(2) +
                "e".repeat(16);
        TestHeader headerToAddWithTwoEvictions = TestHeader.newHeader(
                doubleSizedHeaderValue, doubleSizedHeaderValue);

        // Construct header with size equals to the dynamic table capacity
        //  / 2 since the string used two times - for headers name and value
        String hugeStrPart1 = TEST_HEADERS.stream().map(TestHeader::name)
                .collect(Collectors.joining());
        String hugeStrToCompensate32PerElement = "a".repeat(32 * (HEADERS_COUNT - 1) / 2);
        String hugeStr = hugeStrPart1 + hugeStrToCompensate32PerElement;

        TestHeader hugeEntryWithAllEviction = TestHeader.newHeader(hugeStr, hugeStr);

        // Header with size 2 bytes bigger than the dynamic table capacity
        TestHeader hugeEntryExceedsCapacity = TestHeader.newHeader(
                hugeStr + "H", hugeStr + "H");

        return new Object[][]{
                // Evict one to have space for a new entry
                {headerToAddWithOneEviction, SectionReference.noReferences(),
                        HEADERS_COUNT, 0},

                // Evict all entries to have space for a new entry
                {hugeEntryWithAllEviction, SectionReference.noReferences(),
                        HEADERS_COUNT, HEADERS_COUNT - 1},

                // Not enough capacity for a new entry even if all entries are evicted
                {hugeEntryExceedsCapacity, SectionReference.noReferences(),
                        -1, -1},

                // Entry with size == capacity and there are section references preventing
                // eviction of all entries
                {hugeEntryWithAllEviction, new SectionReference(0, 1),
                        -1, -1},

                // Element with 0 absolute id is not referenced and therefore can be evicted
                {headerToAddWithOneEviction, new SectionReference(1, 2),
                        HEADERS_COUNT, 0},

                // Elements with 0 and 1 ids are not referenced and should be
                // evicted to insert double-sized entry
                {headerToAddWithTwoEvictions, new SectionReference(2, 3),
                        HEADERS_COUNT, 1},

                // Element with 1 id cannot be evicted since it is
                // referenced
                {headerToAddWithTwoEvictions, new SectionReference(1, 3),
                        -1, -1}
        };
    }

    record TestHeader(String name, String value, long size) {
        public static TestHeader newHeader(String name, String value) {
            return new TestHeader(name, value, 32L + name.length() + value.length());
        }

        @Override
        public String toString() {
            return name + ":" + value + "[" + size + "]";
        }
    }

    // Number of headers to insert before running an eviction scenario
    private static final int HEADERS_COUNT = 3;
    // Test header prefix
    private static final String HEADER_PREXIX = "HeaderPrefix";
    // List of headers to insert before running an eviction scenario
    private static final List<TestHeader> TEST_HEADERS;
    // Table capacity required by test scenarios
    private static final long TABLE_CAPACITY;

    static {
        List<TestHeader> testHeaders = new ArrayList<>();
        long capacity = 0;

        // List of headers to prepopulate dynamic table before running
        // test cases
        for (int i = 0; i < HEADERS_COUNT; i++) {
            String headerStr = HEADER_PREXIX + String.format("%03d", i);
            var header = TestHeader.newHeader(headerStr, headerStr);
            capacity += header.size();
            testHeaders.add(header);
        }
        TEST_HEADERS = testHeaders;
        TABLE_CAPACITY = capacity;
    }
}
