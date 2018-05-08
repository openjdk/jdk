/*
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8080535 8191410
 * @summary Expected size of Character.UnicodeBlock.map is not optimal
 * @library /lib/testlibrary
 * @modules java.base/java.lang:open
 *          java.base/java.util:open
 * @build jdk.testlibrary.OptimalCapacity
 * @run main OptimalMapSize
 */

import jdk.testlibrary.OptimalCapacity;

// What will be the number of the Unicode blocks in the future.
//
// According to http://www.unicode.org/versions/Unicode7.0.0/ ,
// in Unicode 7 there will be added 32 new blocks (96 with aliases).
// According to http://www.unicode.org/versions/beta-8.0.0.html ,
// in Unicode 8 there will be added 10 more blocks (30 with aliases).
//
// After implementing support of Unicode 9 and 10 in Java, there will
// be 638 entries in Character.UnicodeBlock.map.
//
// Initialization of the map and this test will have to be adjusted
// accordingly then.

public class OptimalMapSize {
    public static void main(String[] args) throws Throwable {
        // The initial size of Character.UnicodeBlock.map.
        // See src/java.base/share/classes/java/lang/Character.java
        int initialCapacity = (int)(638 / 0.75f + 1.0f);

        OptimalCapacity.ofHashMap(Character.UnicodeBlock.class,
                "map", initialCapacity);
    }
}
