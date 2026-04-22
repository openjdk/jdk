/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.qpack;

import static jdk.internal.net.http.qpack.TableEntry.EntryType.NAME;
import static jdk.internal.net.http.qpack.TableEntry.EntryType.NAME_VALUE;

/*
 * Adds reverse lookup to dynamic and static tables.
 * Decoder does not need this functionality. On the other hand,
 *  Encoder does.
 */
public final class TablesIndexer {

    private final DynamicTable dynamicTable;
    private final StaticTable staticTable;

    public TablesIndexer(StaticTable staticTable, DynamicTable dynamicTable) {
        this.dynamicTable = dynamicTable;
        this.staticTable = staticTable;
    }

    /**
     * Searches in dynamic and static tables for an entry that has matching name
     * or name:value.
     * Found dynamic table entry ids are matched against provided
     * known receive count value if it is non-negative.
     * If known receive count value is negative the entry id check is
     * not performed.
     *
     * @param name               entry name to search
     * @param value              entry value to search
     * @param knownReceivedCount known received count to match dynamic table
     *                           entries, if negative - id check is not performed.
     * @return a table entry that matches provided parameters
     */
    public TableEntry entryOf(CharSequence name, CharSequence value,
                              long knownReceivedCount) {
        // Invoking toString() will possibly allocate Strings for the sake of
        // the searchDynamic, which doesn't feel right.
        String n = name.toString();
        String v = value.toString();

        // Tests can use -1 known receive count value to filter dynamic table
        // entry ids.
        boolean limitDynamicTableEntryIds = knownReceivedCount >= 0;

        // 1. Try exact match in the static table
        var staticSearchResult = staticTable.search(n, v);
        if (staticSearchResult > 0) {
            // name:value pair is found in static table
            return new TableEntry(true, staticSearchResult - 1,
                    name, value, NAME_VALUE);
        }
        // 2. Try exact match in the dynamic table
        var dynamicSearchResult = dynamicTable.search(n, v);
        if (dynamicSearchResult == 0 && staticSearchResult == 0) {
            // dynamic and static tables do not contain name or name:value entries
            // - use literal table entry
            return new TableEntry(name, value);
        }
        long dtEntryId;
        // name:value hit in dynamic table
        if (dynamicSearchResult > 0) {
            dtEntryId = dynamicSearchResult - 1;
            if (!limitDynamicTableEntryIds || dtEntryId < knownReceivedCount) {
                return new TableEntry(false, dtEntryId, name, value,
                        NAME_VALUE);
            }
        }
        // Name only hit in the static table
        if (staticSearchResult < 0) {
            return new TableEntry(true, -staticSearchResult - 1, name,
                    value, NAME);
        }

        // Name only hit in the dynamic table
        if (dynamicSearchResult < 0) {
            dtEntryId = -dynamicSearchResult - 1;
            if (!limitDynamicTableEntryIds || dtEntryId < knownReceivedCount) {
                return new TableEntry(false, dtEntryId, name, value, NAME);
            }
        }

        // No match found in the tables, or there is a dynamic table entry that has
        // name or 'name:value' match but its index is greater than max allowed dynamic
        // table index, ie the entry is not acknowledged by the decoder.
        return new TableEntry(name, value);
    }
}
