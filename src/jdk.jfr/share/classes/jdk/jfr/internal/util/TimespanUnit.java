/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.util;

public enum TimespanUnit {
    NANOSECONDS ("ns",                           1L, 1000),
    MICROSECONDS("us",                        1000L, 1000),
    MILLISECONDS("ms",                   1_000_000L, 1000),
    SECONDS     ("s",                1_000_000_000L,   60),
    MINUTES     ("m",           60 * 1_000_000_000L,   60),
    HOURS       ("h",      60 * 60 * 1_000_000_000L,   24),
    DAYS        ("d", 24 * 60 * 60 * 1_000_000_000L,    7);
    public final String text;
    public final long nanos;
    public final int size;
    TimespanUnit(String text, long nanos, int size) {
        this.text = text;
        this.nanos = nanos;
        this.size = size;
    }

    public static TimespanUnit fromText(String text) {
        for (TimespanUnit tu : values()) {
            // Case-sensitive by design
            if (tu.text.equals(text)) {
                return tu;
            }
        }
        return null;
    }
}
