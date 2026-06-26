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
 *
 */

import java.util.Arrays;
import jdk.internal.value.ValueClass;

public class ArchivedFlatArrayApp {

    // Check that arrays of both migrated value
    // classes and custom value classes are archived
    public static class ArchivedData {
        Integer[] intArray;
        CharPair[] charPairArray;
        Wrapper[] wrapperArray;
    }

    public static value class CharPair implements Comparable<CharPair> {
        char c0, c1;

        public String toString() {
            return "(" + c0 + ", " + c1 + ")";
        }

        public int compareTo(CharPair o) {
            return (c0 - o.c0) - (c1 - o.c1);
        }

        public CharPair(char c0, char c1) {
            this.c0 = c0;
            this.c1 = c1;
        }
    }

    public static value class Wrapper implements Comparable<Wrapper> {
        Integer i;

        public String toString() {
            return i.toString();
        }

        public int compareTo(Wrapper o) {
            return i - o.i;
        }

        Wrapper(int i) {
            this.i = new Integer(i);
        }
    }

    static ArchivedData archivedObjects;
    static boolean restored;
    static {
        if (archivedObjects == null) {
            restored = false;
            System.out.println("Not archived");
            archivedObjects = new ArchivedData();

            archivedObjects.intArray = new Integer[3];
            archivedObjects.intArray[0] = new Integer(0);
            archivedObjects.intArray[1] = new Integer(1);
            archivedObjects.intArray[2] = new Integer(2);

            archivedObjects.charPairArray = new CharPair[3];
            archivedObjects.charPairArray[0] = new CharPair('a', 'b');
            archivedObjects.charPairArray[1] = new CharPair('c', 'd');
            archivedObjects.charPairArray[2] = new CharPair('e', 'f');

            archivedObjects.wrapperArray = new Wrapper[3];
            archivedObjects.wrapperArray[0] = new Wrapper(0);
            archivedObjects.wrapperArray[1] = new Wrapper(1);
            archivedObjects.wrapperArray[2] = new Wrapper(2);
        } else {
            restored = true;
            System.out.println("Initialized from CDS");
            System.out.println("intArray " + archivedObjects.intArray);
            System.out.println("charPairArray " + archivedObjects.charPairArray);
            System.out.println("wrapperArray " + archivedObjects.wrapperArray);
        }

        for (Integer i : archivedObjects.intArray) {
            System.out.println(i);
        }

        for (CharPair c : archivedObjects.charPairArray) {
            System.out.println(c);
        }

        for (Wrapper w : archivedObjects.wrapperArray) {
            System.out.println(w);
        }
    }

    public static void main(String[] args) {
        if (!ValueClass.isFlatArray(archivedObjects.intArray)) {
            throw new RuntimeException("Integer array should be flat");
        }

        if (!ValueClass.isFlatArray(archivedObjects.charPairArray)) {
            throw new RuntimeException("CharPair array should be flat");
        }

        if (!ValueClass.isFlatArray(archivedObjects.wrapperArray)) {
            throw new RuntimeException("Wrapper array should be flat");
        }

        if (restored) {
            // Ensure archived arrays are restored properly
            Integer[] runtimeIntArray = new Integer[3];
            runtimeIntArray[0] = new Integer(0);
            runtimeIntArray[1] = new Integer(1);
            runtimeIntArray[2] = new Integer(2);

            CharPair[] runtimeCharPairArray = new CharPair[3];
            runtimeCharPairArray[0] = new CharPair('a', 'b');
            runtimeCharPairArray[1] = new CharPair('c', 'd');
            runtimeCharPairArray[2] = new CharPair('e', 'f');

            Wrapper[] runtimeWrapperArray = new Wrapper[3];
            runtimeWrapperArray[0] = new Wrapper(0);
            runtimeWrapperArray[1] = new Wrapper(1);
            runtimeWrapperArray[2] = new Wrapper(2);

            if (Arrays.compare(archivedObjects.intArray, runtimeIntArray) != 0) {
                throw new RuntimeException("Integer array not restored correctly");
            }

            if (Arrays.compare(archivedObjects.charPairArray, runtimeCharPairArray) != 0) {
                throw new RuntimeException("CharPair array not restored correctly");
            }

            if (Arrays.compare(archivedObjects.wrapperArray, runtimeWrapperArray) != 0) {
                throw new RuntimeException("Wrapper array not restored correctly");
            }
        }
    }
}
