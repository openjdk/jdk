/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8080535 8191410 8215194 8221431 8239383 8268081 8283465
 * @summary Check if the NUM_ENTITIES field reflects the correct number
 *      of Character.UnicodeBlock constants.
 * @modules java.base/java.lang:open
 * @run testng NumberEntities
 */

import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Map;

@Test
public class NumberEntities {
    public void test_NumberEntities() throws Throwable {
        // The number of entries in Character.UnicodeBlock.map.
        // See src/java.base/share/classes/java/lang/Character.java
        Field n = Character.UnicodeBlock.class.getDeclaredField("NUM_ENTITIES");
        Field m = Character.UnicodeBlock.class.getDeclaredField("map");
        n.setAccessible(true);
        m.setAccessible(true);
        assertEquals(((Map)m.get(null)).size(), n.getInt(null));
    }
}
