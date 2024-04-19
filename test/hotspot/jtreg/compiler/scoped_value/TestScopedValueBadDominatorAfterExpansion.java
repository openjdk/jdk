/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8320649
 * @requires vm.gc.Parallel
 * @summary SIGSEGV in PhaseIdealLoop::get_early_ctrl()
 * @compile --enable-preview -source ${jdk.version} TestScopedValueBadDominatorAfterExpansion.java
 * @run main/othervm --enable-preview -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:+UseParallelGC TestScopedValueBadDominatorAfterExpansion
 * @run main/othervm --enable-preview -XX:-BackgroundCompilation -XX:-TieredCompilation TestScopedValueBadDominatorAfterExpansion
 */

public class TestScopedValueBadDominatorAfterExpansion {
    static ScopedValue<Object> sv1 = ScopedValue.newInstance();
    static ScopedValue<Object> sv2 = ScopedValue.newInstance();
    private static Object field1;
    private static Object field2;

    public static void main(String[] args) {
        Object o1 = new Object();
        Object o2 = new Object();
        for (int i = 0; i < 20_000; i++) {
            field1 = null;
            field2 = null;
            ScopedValue.where(sv1, o1).where(sv2, o2).run(
                    () -> {
                        test();
                    }
            );
            if (field1 != o1) {
                throw new RuntimeException("field1 assigned wrong value");
            }
            if (field2 != o2) {
                throw new RuntimeException("field2 assigned wrong value");
            }
        }
    }

    private static void test() {
        final ScopedValue<Object> localSv2 = sv2;
        final ScopedValue<Object> localSv1 = sv1;
        if (localSv2 == null) {
        }
        Object v1 = localSv1.get();
        field1 = v1;
        Object v2 = localSv2.get();
        field2 = v2;
    }
}
