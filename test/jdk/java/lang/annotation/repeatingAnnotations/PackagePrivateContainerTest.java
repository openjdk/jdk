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
 */

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8349716
 * @summary Test container annotations for repeatable package private annotations
 * @run junit PackagePrivateContainerTest
 */

final class PackagePrivateContainerTest {
    // Note: Anno class must be package private
    // But the container can be public (it is a member of interface)
    @Anno
    @Anno(1)
    @Repeatable(Anno.Container.class)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Anno {
        int value() default 0;

        @Retention(RetentionPolicy.RUNTIME)
        @interface Container {
            Anno[] value();
        }
    }

    @Test
    void testGetRepeatable() {
        var annos = Anno.class.getDeclaredAnnotationsByType(Anno.class);
        confirmAnnos(annos);
    }

    @Test
    void testGetContainer() {
        var container = Anno.class.getDeclaredAnnotation(Anno.Container.class);
        confirmAnnos(container.value()); // should not crash
    }

    static void confirmAnnos(Anno[] annos) {
        assertEquals(2, annos.length);
        assertEquals(0, annos[0].value());
        assertEquals(1, annos[1].value());
    }
}
