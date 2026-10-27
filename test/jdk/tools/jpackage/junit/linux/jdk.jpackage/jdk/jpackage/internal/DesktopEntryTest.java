/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DesktopEntryTest {

    @ParameterizedTest
    @CsvSource({
        "MIME_TYPE,MimeType",
        "NAME,Name",
        "COMMENT,Comment",
        "EXEC,Exec",
        "PATH,Path",
        "ICON,Icon",
        "TERMINAL,Terminal",
        "TYPE,Type",
        "CATEGORIES,Categories",
    })
    void test_entryKey(DesktopEntry entry, String expectedKey) {
        assertEquals(expectedKey, entry.entryKey());
    }

    @ParameterizedTest
    @CsvSource({
        "MIME_TYPE,,",
        "MIME_TYPE,foo,foo;",
        "MIME_TYPE,foo;,foo;",
        "MIME_TYPE,'',;",

        "NAME,,",
        "NAME,Hello Duke!,Hello Duke!",
        "NAME,'',''",

        "COMMENT,,",
        "COMMENT,Hello Duke!,Hello Duke!",
        "COMMENT,'',''",

        "EXEC,,",
        "EXEC,foo/bar,foo/bar",
        "EXEC,Hello Duke!,\"Hello Duke!\"",
        "EXEC,'',''",

        "PATH,,",
        "PATH,foo/bar,foo/bar",
        "PATH,Hello Duke!,Hello Duke!",
        "PATH,'',''",

        "ICON,,",
        "ICON,Hello Duke!,Hello Duke!",
        "ICON,'',''",

        "TERMINAL,,",
        "TERMINAL,Hello Duke!,Hello Duke!",
        "TERMINAL,'',''",

        "TYPE,,",
        "TYPE,Hello Duke!,Hello Duke!",
        "TYPE,'',''",

        "CATEGORIES,,",
        "CATEGORIES,foo,foo;",
        "CATEGORIES,foo;,foo;",
        "CATEGORIES,'',;",
    })
    void test_formatDesktopFileEntryValue(DesktopEntry entry, String entryValue, String expectedFormattedValue) {

        if (entryValue != null) {
            assertEquals(expectedFormattedValue, entry.formatDesktopFileEntryValue(entryValue));

            assertEquals(entry.entryKey() + "=" + expectedFormattedValue, entry.formatDesktopFileEntry(entryValue));
        } else {
            assertThrowsExactly(NullPointerException.class, () -> {
                entry.formatDesktopFileEntryValue(entryValue);
            });

            assertThrowsExactly(NullPointerException.class, () -> {
                entry.formatDesktopFileEntry(entryValue);
            });
        }
    }
}
