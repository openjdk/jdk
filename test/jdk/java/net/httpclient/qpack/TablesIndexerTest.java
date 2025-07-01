/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.net.http/jdk.internal.net.http.qpack
 * @run testng/othervm -Djdk.internal.httpclient.qpack.log.level=INFO TablesIndexerTest
 */

import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.StaticTable;
import jdk.internal.net.http.qpack.TableEntry;
import jdk.internal.net.http.qpack.TablesIndexer;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static jdk.internal.net.http.qpack.TableEntry.EntryType;

public class TablesIndexerTest {

    @DataProvider(name = "indicesLookupData")
    public Object[][] indicesData() {
        List<Object[]> tcs = new ArrayList<>();

        long index = 0;
        for (HeaderField f : StaticTable.HTTP3_HEADER_FIELDS) {
            // Full name:value match
            tcs.add(new Object[]{
                    f.name(), f.value(), f.value(),
                    Set.of(index), EntryType.NAME_VALUE
            });
            // Static and Dynamic tables contain only name match - we expect static
            // NAME only entry to be returned
            tcs.add(new Object[]{
                    f.name(), "NotInStatic", "InDynamic",
                    acceptableIndicesForName(f.name()), EntryType.NAME});
            index++;
        }
        return tcs.toArray(Object[][]::new);
    }

    @Test(dataProvider = "indicesLookupData")
    public void checkIndicesLookup(String name, String value,
                                   String dynamicTableValue,
                                   Set<Long> indices, EntryType type) {
        // We construct dynamic table with the same name value to check that
        // static entry is returned first
        DynamicTable dt = new DynamicTable(QPACK.getLogger().subLogger(
                "checkStaticIndicesLookup"));
        dt.setMaxTableCapacity(256);
        dt.setCapacity(256);
        dt.insert(name, dynamicTableValue);

        // Construct TablesIndexer
        TablesIndexer tablesIndexer = new TablesIndexer(STATIC_TABLE, dt);

        // Use TablesIndexer to locate TableEntry
        TableEntry tableEntry = tablesIndexer.entryOf(name, value,
                IGNORE_RECEIVED_COUNT_CHECK);

        // TableEntry should be for static table only
        Assert.assertTrue(tableEntry.isStaticTable());

        // If value is not equal to dynamicTableValue, the full name:dynamicTableValue
        // should be found in the dynamic table with index 0
        if (!value.equals(dynamicTableValue)) {
            TableEntry dtEntry = tablesIndexer.entryOf(name, dynamicTableValue,
                    IGNORE_RECEIVED_COUNT_CHECK);
            Assert.assertFalse(dtEntry.isStaticTable());
            Assert.assertEquals(dtEntry.type(), EntryType.NAME_VALUE);
            Assert.assertEquals(dtEntry.index(), 0L);
        }

        // Check that found index is contained in a set and returned indices match
        Assert.assertTrue(indices.contains(tableEntry.index()));

        // Check that entry type matches
        Assert.assertEquals(tableEntry.type(), type);

        var headerField = STATIC_TABLE.get(tableEntry.index());
        // Check that name and/or value matches the one that can be acquired by
        // using looked-up index
        if (tableEntry.type() == EntryType.NAME) {
            Assert.assertEquals(headerField.name(), name);
            // If only name entry is found huffmanName should be set to false
            Assert.assertFalse(tableEntry.huffmanName());
        } else if (tableEntry.type() == EntryType.NAME_VALUE) {
            Assert.assertEquals(headerField.name(), name);
            Assert.assertEquals(headerField.value(), value);
            // If "name:value" match is found huffmanName and huffmanValue should
            // be set to false
            Assert.assertFalse(tableEntry.huffmanName());
            Assert.assertFalse(tableEntry.huffmanValue());

        } else {
            Assert.fail("Unexpected TableEntry type returned:" + tableEntry);
        }
    }

    @Test(dataProvider = "unacknowledgedEntriesLookupData")
    public void unacknowledgedEntryLookup(String headerName, String headerValue,
                                          boolean staticEntryExpected,
                                          EntryType expectedType) {
        // Construct dynamic table with pre-populated entries
        DynamicTable dynamicTable = dynamicTableForUnackedEntriesTest();
        // Construct TablesIndexer
        TablesIndexer tablesIndexer = new TablesIndexer(STATIC_TABLE, dynamicTable);
        // Search for an entry in the dynamic and the static tables
        var entry = tablesIndexer.entryOf(headerName, headerValue, TEST_KNOWN_RECEIVED_COUNT);
        // Check that entry references expected table
        Assert.assertEquals(entry.isStaticTable(), staticEntryExpected);
        // And the type of found entry matches expectations
        Assert.assertEquals(entry.type(), expectedType);
    }

    @DataProvider
    public Object[][] unacknowledgedEntriesLookupData() {
        List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{USER_AGENT_ST_NAME, "not-in-dynamic", true, EntryType.NAME});
        data.add(new Object[]{USER_AGENT_ST_NAME, USER_AGENT_DT_VALUE, false, EntryType.NAME_VALUE});
        data.add(new Object[]{TEST_ACKED_ENTRY, "not-in-dynamic", false, EntryType.NAME});
        data.add(new Object[]{TEST_ACKED_ENTRY, TEST_ACKED_ENTRY, false, EntryType.NAME_VALUE});
        data.add(new Object[]{CONTENT_TYPE_ST_NAME, "what/ever", true, EntryType.NAME});
        data.add(new Object[]{TEST_UNACKED_ENTRY, TEST_UNACKED_ENTRY, false, EntryType.NEITHER});
        return data.toArray(Object[][]::new);
    }

    private static DynamicTable dynamicTableForUnackedEntriesTest() {
        DynamicTable dt = new DynamicTable(QPACK.getLogger()
                .subLogger("unacknowledgedEntryLookup"));
        dt.setMaxTableCapacity(1024);
        dt.setCapacity(1024);
        // Acknowledged entry with name available in static and dynamic table
        dt.insert(USER_AGENT_ST_NAME, USER_AGENT_DT_VALUE); // 0
        // Acknowledged entry with name available in dynamic table only
        dt.insert(TEST_ACKED_ENTRY, TEST_ACKED_ENTRY); // 1
        // Unacknowledged entry with name available in static table
        dt.insert(CONTENT_TYPE_ST_NAME, "what/ever"); // 2
        // Unacknowledged entry with name available in dynamic table only
        dt.insert(TEST_UNACKED_ENTRY, TEST_UNACKED_ENTRY); // 3
        return dt;
    }

    private static final long IGNORE_RECEIVED_COUNT_CHECK = -1L;
    private static final long TEST_KNOWN_RECEIVED_COUNT = 2L;

    private static final String USER_AGENT_ST_NAME = "user-agent";
    private static final String USER_AGENT_DT_VALUE = "qpack-test-client";
    private static final String CONTENT_TYPE_ST_NAME = "content-type";
    private static final String TEST_ACKED_ENTRY = "test-acked-entry";
    private static final String TEST_UNACKED_ENTRY = "test-unacked-entry";

    private final static StaticTable STATIC_TABLE = StaticTable.HTTP3;

    private Set<Long> acceptableIndicesForName(String name) {
        AtomicLong enumerator = new AtomicLong();
        return StaticTable.HTTP3_HEADER_FIELDS.stream()
                .map(f -> new NameEnumeration(enumerator.getAndIncrement(), f.name()))
                .filter(ne -> name.equals(ne.name()))
                .mapToLong(NameEnumeration::id)
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }

    record NameEnumeration(long id, String name) {
    }
}
