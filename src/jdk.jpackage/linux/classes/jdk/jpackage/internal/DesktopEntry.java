/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.util.Objects;
import jdk.jpackage.internal.util.Enquoter;

/**
 * A subset of desktop entries set by jpackage.
 * <p>
 * See <a href=
 * "https://specifications.freedesktop.org/desktop-entry/latest/recognized-keys.html">Recognized
 * desktop entry keys</a> for the full set.
 */
enum DesktopEntry {

    MIME_TYPE("MimeType"),
    NAME("Name"),
    COMMENT("Comment"),
    EXEC("Exec"),
    PATH("Path"),
    ICON("Icon"),
    TERMINAL("Terminal"),
    TYPE("Type"),
    CATEGORIES("Categories"),
    ;

    DesktopEntry(String desktopEntryKey) {
        this.desktopEntryKey = Objects.requireNonNull(desktopEntryKey);
    }

    String formatDesktopFileEntryValue(String v) {
        Objects.requireNonNull(v);
        return switch (this) {
            case MIME_TYPE, CATEGORIES -> ensureEndsWithSemicolon(v);
            case EXEC -> Enquoter.forPropertyValues().applyTo(v);
            default -> v;
        };
    }

    String formatDesktopFileEntry(String v) {
        return desktopEntryKey + "=" + formatDesktopFileEntryValue(v);
    }

    String entryKey() {
        return desktopEntryKey;
    }

    private static String ensureEndsWithSemicolon(String str) {
        if (!str.endsWith(";")) {
            return str + ';';
        } else {
            return str;
        }
    }

    private final String desktopEntryKey;
}
