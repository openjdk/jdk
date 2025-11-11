/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test StringBuilder internal append pair optimization
 * @library /test/lib
 * @modules java.base/jdk.internal.util
 * @run main/othervm --add-opens java.base/java.lang=ALL-UNNAMED SBAppendPairCount
 */

import java.lang.reflect.Field;
import java.time.LocalTime;

import jdk.internal.util.DecimalDigits;

public class SBAppendPairCount {

    public static void main(String[] args) throws Exception {
        LocalTime now = LocalTime.now();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 1_000_0000; i++) {
            sb.setLength(0);
            DecimalDigits.appendPair(sb, now.getHour());
            sb.append(':').append('.');
            DecimalDigits.appendPair(sb, now.getMinute());
            sb.append(':');
            DecimalDigits.appendPair(sb, now.getSecond());
        }

        Class<?> clazz = Class.forName("java.lang.AbstractStringBuilder");
        Field field = clazz.getDeclaredField("appendPairCount");
        field.setAccessible(true);
        int x = field.getInt(null);
        System.out.println("appendPairCount " + x);

        // The test passes if no exception occurs and the count is reasonable
        if (x <= 0) {
            throw new RuntimeException("Unexpected appendPairCount: " + x);
        }
    }
}