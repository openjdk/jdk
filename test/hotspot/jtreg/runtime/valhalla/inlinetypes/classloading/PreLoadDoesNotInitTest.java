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
 *
 */

/*
 * @test
 * @summary Ensures that a value class is not initialized when preloaded.
 * @enablePreview
 * @run junit runtime.valhalla.inlinetypes.classloading.PreLoadDoesNotInitTest
 */

package runtime.valhalla.inlinetypes.classloading;

import org.junit.jupiter.api.Test;

class PreLoadDoesNotInitTest {

    @Test
    void test() {
        Outer outer = new Outer();
        outer.doSomething();
        if (Outer.THE_FIELD != 19) {
            throw new IllegalStateException("class was initialized when it should not have been");
        }
        // Sanity: make sure it loads when we actually use it.
        new Inner();
        if (Outer.THE_FIELD != 0) {
            throw new IllegalStateException("class was not initialized when it should have been");
        }
    }

    public static class Outer {
        // Value class as a field should ensure that Outer contains a loadable
        // descriptor for it. We will preload Inner.
        private Inner inner;

        // This is a static field that gets updated by Inner's static
        // initializer. We expect this to remain as 19.
        public static int THE_FIELD = 19;

        private void doSomething() {}
    }

    public static value class Inner {
        private int x = 0;

        static {
            // This static field only gets updated once Inner is initialized.
            // In this test case, this should NOT happen.
            Outer.THE_FIELD = 0;
        }
    }
}
