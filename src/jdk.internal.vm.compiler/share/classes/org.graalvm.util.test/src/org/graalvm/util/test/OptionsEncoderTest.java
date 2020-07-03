/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.util.test;

import java.lang.annotation.ElementType;
import static org.graalvm.util.OptionsEncoder.decode;
import static org.graalvm.util.OptionsEncoder.encode;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class OptionsEncoderTest {

    @Test
    public void testSmallString() {
        Map<String, Object> options = Collections.singletonMap("key", "smallString");
        assertEquals(options, decode(encode(options)));
    }

    @Test
    public void testLargeString() {
        StringBuilder fillBuilder = new StringBuilder();
        for (int i = 0; i < 1 << 8; i++) {
            fillBuilder.append(' ');
        }
        String fill = fillBuilder.toString();
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i <= Character.MAX_VALUE >>> 8; i++) {
            largeString.append(fill);
        }
        Map<String, Object> options = Collections.singletonMap("key", largeString.toString());
        assertEquals(options, decode(encode(options)));
    }

    @Test
    public void testEnum() {
        Map<String, Object> options = Collections.singletonMap("key", ElementType.TYPE);
        assertEquals(Collections.singletonMap("key", ElementType.TYPE.name()), decode(encode(options)));
    }
}
