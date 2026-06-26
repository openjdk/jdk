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

import jdk.internal.misc.PreviewFeatures;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

// Test app for flat arrays in the AOT map
public class AOTMapTestApp {

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

    public static value class WrapperWrapper {
        Wrapper w;

        WrapperWrapper(int i) {
            this.w = new Wrapper(i);
        }
    }

    // Check that arrays of both migrated value
    // classes and custom value classes are archived
    public static class ArchivedData {
        Integer[] boxArray;
        Integer[] nullFreeBoxArray;
        Wrapper[] wrapperArray;
        WrapperWrapper[] wrapperWrapperArray;
        Object[] objArray;
        Wrapper wrapper;
        @NullRestricted
        WrapperWrapper wrapperWrapper;
        int a;
        Object b;

        ArchivedData() {
            boxArray = new Integer[3];  // flattened
            boxArray[0] = new Integer(0xaaaa);
            boxArray[1] = new Integer(0xbbbb);
            boxArray[2] = null;

            nullFreeBoxArray = (Integer[])ValueClass.newNullRestrictedAtomicArray(Integer.class, 3, new Integer(0));
            nullFreeBoxArray[0] = new Integer(0xaaaa);
            nullFreeBoxArray[1] = new Integer(0xbbbb);
            nullFreeBoxArray[2] = new Integer(0xcccc);

            wrapperArray = new Wrapper[3]; // flattened
            wrapperArray[0] = new Wrapper(0xaaaa);
            wrapperArray[1] = new Wrapper(0xbbbb);
            wrapperArray[2] = new Wrapper(0xcccc);

            wrapperWrapperArray = new WrapperWrapper[3];
            wrapperWrapperArray[0] = new WrapperWrapper(0xaaaa);
            wrapperWrapperArray[1] = new WrapperWrapper(0xbbbb);
            wrapperWrapperArray[2] = new WrapperWrapper(0xcccc);

            objArray = new Object[3]; // not flattened
            objArray[0] = new Integer(0xaaaa);
            objArray[1] = new Integer(0xbbbb);
            objArray[2] = new Integer(0xcccc);

            wrapper = new Wrapper(0xaaaa5555);
            wrapperWrapper = new WrapperWrapper(0xbbbb6666);
            a = 0x7788;
            b = new Integer(0x8899);
        }
    }

    static ArchivedData archivedObjects;
    static {
        if (archivedObjects == null) {
            archivedObjects = new ArchivedData();
        } else {
            System.out.println("Initialized from CDS");
            System.out.println("boxArray " + archivedObjects.boxArray);
            System.out.println("wrapperArray " + archivedObjects.wrapperArray);
            System.out.println("wrapperWrapperArray " + archivedObjects.wrapperWrapperArray);
            System.out.println("objArray " + archivedObjects.objArray);
            System.out.println("wrapper " + archivedObjects.wrapper);
            System.out.println("wrapperWrapper " + archivedObjects.wrapperWrapper);
            System.out.println("a " + archivedObjects.a);
            System.out.println("b " + archivedObjects.b);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hello FlatAOTMapTestApp");
        Class.forName("Hello");

        if (PreviewFeatures.isEnabled() && !ValueClass.isFlatArray(archivedObjects.boxArray)) {
            throw new RuntimeException("Boxing class array should be flat");
        }

        if (PreviewFeatures.isEnabled() && (!ValueClass.isNullRestrictedArray(archivedObjects.nullFreeBoxArray) ||
            !ValueClass.isFlatArray(archivedObjects.nullFreeBoxArray))) {
            throw new RuntimeException("Boxing class array should be null-free and flat");
        }

        if (PreviewFeatures.isEnabled() && !ValueClass.isFlatArray(archivedObjects.wrapperArray)) {
            throw new RuntimeException("Wrapper array should be flat");
        }

        if (PreviewFeatures.isEnabled() && !ValueClass.isFlatArray(archivedObjects.wrapperWrapperArray)) {
            throw new RuntimeException("WrapperWrapper array should be flat");
        }

        if (PreviewFeatures.isEnabled() && ValueClass.isFlatArray(archivedObjects.objArray)) {
            throw new RuntimeException("Object array should not be flat");
        }
    }
}
