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

/* @test
 * @bug 8351362
 * @summary Unit Test for StrictProcessor
 * @enablePreview
 * @library /test/lib
 * @build StrictProcessorTest
 * @run driver jdk.test.lib.helpers.StrictProcessor StrictProcessorTest$StrictTarget
 * @run junit StrictProcessorTest
 */

import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StrictProcessorTest {
    @Test
    void testReflectMyself() throws Throwable {
        for (var field : StrictTarget.class.getDeclaredFields()) {
            assertEquals(ACC_STRICT_INIT | ACC_FINAL, field.getModifiers(), () -> field.getName());
        }
    }

    static final class StrictTarget {
        @StrictInit
        final int a;
        @StrictInit
        final Object b;

        StrictTarget() {
            this.a = 1;
            this.b = 2392352234L;
            super();
        }
    }
}
