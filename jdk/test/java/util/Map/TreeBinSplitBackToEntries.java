/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.lang.reflect.Field;

/*
 * @test
 * @bug 8005698
 * @summary Test the case where TreeBin.splitTreeBin() converts a bin back to an Entry list
 * @run main TreeBinSplitBackToEntries unused
 * @author Brent Christian
 */

public class TreeBinSplitBackToEntries {
    private static int EXPECTED_TREE_THRESHOLD = 16;

    // Easiest if this covers one bit higher then 'bit' in splitTreeBin() on the
    // call where the TreeBin is converted back to an Entry list
    private static int HASHMASK = 0x7F;
    private static boolean verbose = false;
    private static boolean fastFail = false;
    private static boolean failed = false;

    static void printlnIfVerbose(String msg) {
        if (verbose) {System.out.println(msg); }
    }

    public static void main(String[] args) {
        for (String arg : args) {
            switch(arg) {
                case "-verbose":
                    verbose = true;
                    break;
                case "-fastfail":
                    fastFail = true;
                    break;
            }
        }
        checkTreeThreshold();
        testMapHiTree();
        testMapLoTree();
        if (failed) {
            System.out.println("Test Failed");
            System.exit(1);
        } else {
            System.out.println("Test Passed");
        }
    }

    public static void checkTreeThreshold() {
        int threshold = -1;
        try {
            Class treeBinClass = Class.forName("java.util.HashMap$TreeBin");
            Field treeThreshold = treeBinClass.getDeclaredField("TREE_THRESHOLD");
            treeThreshold.setAccessible(true);
            threshold = treeThreshold.getInt(treeBinClass);
        } catch (ClassNotFoundException|NoSuchFieldException|IllegalAccessException e) {
            e.printStackTrace();
            throw new Error("Problem accessing TreeBin.TREE_THRESHOLD", e);
        }
        check("Expected TREE_THRESHOLD: " + EXPECTED_TREE_THRESHOLD +", found: " + threshold,
              threshold == EXPECTED_TREE_THRESHOLD);
        printlnIfVerbose("TREE_THRESHOLD: " + threshold);
    }

    public static void testMapHiTree() {
        Object[][] mapKeys = makeHiTreeTestData();
        testMapsForKeys(mapKeys, "hiTree");
    }

    public static void testMapLoTree() {
        Object[][] mapKeys = makeLoTreeTestData();

        testMapsForKeys(mapKeys, "loTree");
    }

    public static void testMapsForKeys(Object[][] mapKeys, String desc) {
        // loop through data sets
        for (Object[] keys_desc : mapKeys) {
            Map<Object, Object>[] maps = (Map<Object, Object>[]) new Map[]{
              new HashMap<>(4, 0.8f),
              new LinkedHashMap<>(4, 0.8f),
            };
            // for each map type.
            for (Map<Object, Object> map : maps) {
                Object[] keys = (Object[]) keys_desc[1];
                System.out.println(desc + ": testPutThenGet() for " + map.getClass());
                testPutThenGet(map, keys);
            }
        }
    }

    private static <T> void testPutThenGet(Map<T, T> map, T[] keys) {
        for (T key : keys) {
            printlnIfVerbose("put()ing 0x" + Integer.toHexString(Integer.parseInt(key.toString())) + ", hashCode=" + Integer.toHexString(key.hashCode()));
            map.put(key, key);
        }
        for (T key : keys) {
            check("key: 0x" + Integer.toHexString(Integer.parseInt(key.toString())) + " not found in resulting " + map.getClass().getSimpleName(), map.get(key) != null);
        }
    }

    /* Data to force a non-empty loTree in TreeBin.splitTreeBin() to be converted back
     * into an Entry list
     */
    private static Object[][] makeLoTreeTestData() {
        HashableInteger COLLIDING_OBJECTS[] = new HashableInteger[] {
            new HashableInteger( 0x23, HASHMASK),
            new HashableInteger( 0x123, HASHMASK),
            new HashableInteger( 0x323, HASHMASK),
            new HashableInteger( 0x523, HASHMASK),

            new HashableInteger( 0x723, HASHMASK),
            new HashableInteger( 0x923, HASHMASK),
            new HashableInteger( 0xB23, HASHMASK),
            new HashableInteger( 0xD23, HASHMASK),

            new HashableInteger( 0xF23, HASHMASK),
            new HashableInteger( 0xF123, HASHMASK),
            new HashableInteger( 0x1023, HASHMASK),
            new HashableInteger( 0x1123, HASHMASK),

            new HashableInteger( 0x1323, HASHMASK),
            new HashableInteger( 0x1523, HASHMASK),
            new HashableInteger( 0x1723, HASHMASK),
            new HashableInteger( 0x1923, HASHMASK),

            new HashableInteger( 0x1B23, HASHMASK),
            new HashableInteger( 0x1D23, HASHMASK),
            new HashableInteger( 0x3123, HASHMASK),
            new HashableInteger( 0x3323, HASHMASK),
            new HashableInteger( 0x3523, HASHMASK),

            new HashableInteger( 0x3723, HASHMASK),
            new HashableInteger( 0x1001, HASHMASK),
            new HashableInteger( 0x4001, HASHMASK),
            new HashableInteger( 0x1, HASHMASK),
        };
        return new Object[][] {
            new Object[]{"Colliding Objects", COLLIDING_OBJECTS},
        };
    }

    /* Data to force the hiTree in TreeBin.splitTreeBin() to be converted back
     * into an Entry list
     */
    private static Object[][] makeHiTreeTestData() {
        HashableInteger COLLIDING_OBJECTS[] = new HashableInteger[] {
            new HashableInteger( 0x1, HASHMASK),
            new HashableInteger( 0x101, HASHMASK),
            new HashableInteger( 0x301, HASHMASK),
            new HashableInteger( 0x501, HASHMASK),
            new HashableInteger( 0x701, HASHMASK),

            new HashableInteger( 0x1001, HASHMASK),
            new HashableInteger( 0x1101, HASHMASK),
            new HashableInteger( 0x1301, HASHMASK),

            new HashableInteger( 0x1501, HASHMASK),
            new HashableInteger( 0x1701, HASHMASK),
            new HashableInteger( 0x4001, HASHMASK),
            new HashableInteger( 0x4101, HASHMASK),
            new HashableInteger( 0x4301, HASHMASK),

            new HashableInteger( 0x4501, HASHMASK),
            new HashableInteger( 0x4701, HASHMASK),
            new HashableInteger( 0x8001, HASHMASK),
            new HashableInteger( 0x8101, HASHMASK),


            new HashableInteger( 0x8301, HASHMASK),
            new HashableInteger( 0x8501, HASHMASK),
            new HashableInteger( 0x8701, HASHMASK),
            new HashableInteger( 0x9001, HASHMASK),

            new HashableInteger( 0x23, HASHMASK),
            new HashableInteger( 0x123, HASHMASK),
            new HashableInteger( 0x323, HASHMASK),
            new HashableInteger( 0x523, HASHMASK),
        };
        return new Object[][] {
            new Object[]{"Colliding Objects", COLLIDING_OBJECTS},
        };
    }

    static void check(String desc, boolean cond) {
        if (!cond) {
            fail(desc);
        }
    }

    static void fail(String msg) {
        failed = true;
        (new Error("Failure: " + msg)).printStackTrace(System.err);
        if (fastFail) {
            System.exit(1);
        }
    }

    final static class HashableInteger implements Comparable<HashableInteger> {
        final int value;
        final int hashmask; //yes duplication

        HashableInteger(int value, int hashmask) {
            this.value = value;
            this.hashmask = hashmask;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof HashableInteger) {
                HashableInteger other = (HashableInteger) obj;
                return other.value == value;
            }
            return false;
        }

        @Override
        public int hashCode() {
            // This version ANDs the mask
            return value & hashmask;
        }

        @Override
        public int compareTo(HashableInteger o) {
            return value - o.value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }
}
