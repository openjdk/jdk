/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4190323
 * @summary EMPTY_SET, EMPTY_LIST, and the collections returned by
 *          nCopies and singleton were spec'd to be serializable, but weren't.
 * @library /test/lib
 */

import jdk.test.lib.valueclass.AsValueClass;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;

public class Ser {

    @AsValueClass
    static final class SerV implements Serializable {
        int x;
        SerV(int x) { this.x = x; }
        public boolean equals(Object o) { return o instanceof SerV v && x == v.x; }
        public int hashCode() { return x; }
        private Object writeReplace() { return new SerProxy(x); }
        private static class SerProxy implements Serializable {
            int x;
            SerProxy(int x) { this.x = x; }
            private Object readResolve() { return new SerV(x); }
        }
    }

    public static void main(String[] args) throws Exception {
        checkSerialization(Collections.EMPTY_SET, "empty set");
        checkSerialization(Collections.EMPTY_LIST, "empty list");
        checkSerialization(Collections.singleton("gumby"), "singleton");
        checkSerialization(Collections.nCopies(50, "gumby"), "nCopies");
        checkSerialization(Collections.singleton(new SerV(1)), "value singleton");
        checkSerialization(Collections.nCopies(5, new SerV(2)), "value nCopies");
    }

    private static void checkSerialization(Object obj, String label) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();
            ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(bos.toByteArray()));

            if (!obj.equals(in.readObject()))
                throw new RuntimeException(label + " Ser/Deser failure.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize " + label + ":" + e);
        }
    }
}
