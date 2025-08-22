/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.http3;

import java.util.Objects;

import jdk.internal.net.http.http3.frames.SettingsFrame;

/**
 * Represents the settings that are conveyed in a HTTP3 SETTINGS frame for a HTTP3 connection
 */
public record ConnectionSettings(
        long maxFieldSectionSize,
        long qpackMaxTableCapacity,
        long qpackBlockedStreams) {

    // we use -1 (an internal value) to represent unlimited
    public static final long UNLIMITED_MAX_FIELD_SECTION_SIZE = -1;

    public static ConnectionSettings createFrom(final SettingsFrame frame) {
        Objects.requireNonNull(frame);
        // default is unlimited as per RFC-9114 section 7.2.4.1
        final long maxFieldSectionSize = getOrDefault(frame, SettingsFrame.SETTINGS_MAX_FIELD_SECTION_SIZE,
                UNLIMITED_MAX_FIELD_SECTION_SIZE);
        // default is zero as per RFC-9204 section 5
        final long qpackMaxTableCapacity = getOrDefault(frame, SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0);
        // default is zero as per RFC-9204, section 5
        final long qpackBlockedStreams = getOrDefault(frame, SettingsFrame.SETTINGS_QPACK_BLOCKED_STREAMS, 0);
        return new ConnectionSettings(maxFieldSectionSize, qpackMaxTableCapacity, qpackBlockedStreams);
    }

    private static long getOrDefault(final SettingsFrame frame, final int paramId, final long defaultValue) {
        final long val = frame.getParameter(paramId);
        if (val == -1) {
            return defaultValue;
        }
        return val;
    }

}
