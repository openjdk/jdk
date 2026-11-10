/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package compiler.controldependency;

/*
 * @test
 * @bug 8395612
 * @summary C2: Split-if rewires constraint cast to a wrong control
 * @key stress randomness
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=5 ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM ${test.main.class}
 * @run main/othervm ${test.main.class}
 */
public class TestSplitIfConstraintCastControl {
    static volatile Object sink;

    public static void main(String[] args) {
        Box<KV> boxSet   = new Box<>(new KV("key", new Box<>("value")));
        Box<KV> boxEmpty = new Box<>(new KV("empty-key", null));
        for (int i = 0; i < 1_000_000; i++) {
            boolean select = ((i % 2) == 0);
            Box<KV> box = select ? boxSet : boxEmpty;
            String expected = select ? "value" : "empty-key";

            // Important to warm up both branches
            sink = box.get().value();

            if (!expected.equals(test(box))) {
                throw new IllegalStateException("Incorrect value");
            }
        }
    }

    // This exact code shape exposes the bug: split-if incorrectly rewires
    // the users of the incompatible null-checks to the class-cast check
    // from unboxing.
    static String test(Box<KV> box) {
        if (box.get().hasValue()) {
            return box.get().value().get();
        } else {
            return box.get().key();
        }
    }

    // Must be static final to reproduce the bug.
    static final class KV {
        static final Box<String> EMPTY = new Box<>("EMPTY");
        final String k;
        final Box<String> v;
        KV(String k, Box<String> v) {
            this.k = k;
            this.v = v;
        }
        String key() {
            return k;
        }
        Box<String> value() {
            return v == null ? EMPTY : v;
        }
        boolean hasValue() {
            return v != null;
        }
    }

    // Must be static final to reproduce the bug.
    static final class Box<T> {
        final T v;
        Box(T v) {
            this.v = v;
        }
        T get() {
            return v;
        }
    }

}
