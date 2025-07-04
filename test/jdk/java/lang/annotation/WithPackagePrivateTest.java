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

/*
 * @test
 * @bug 8349716
 * @summary Test behaviors with public annotations that have elements of package
 *          private accessibility
 * @library /test/lib
 * @run junit WithPackagePrivateTest
 */

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test public annotations that have elements of package-private types.
 */
@WithPackagePrivateTest.InaccessibleElement(WithPackagePrivateTest.MySecretEnum.FIRST)
@WithPackagePrivateTest.InaccessibleElement(WithPackagePrivateTest.MySecretEnum.SECOND)
@WithPackagePrivateTest.InaccessibleElement(WithPackagePrivateTest.MySecretEnum.THIRD)
@WithPackagePrivateTest.DummyPublicAnnotation(WithPackagePrivateTest.MySecretEnum.FIRST)
class WithPackagePrivateTest {

    // element of array of package-private interface
    @Retention(RetentionPolicy.RUNTIME)
    public @interface InaccessibleContainer {
        InaccessibleElement[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(InaccessibleContainer.class)
    @interface InaccessibleElement {
        MySecretEnum value();
    }

    // element of package-private (enum) class
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DummyPublicAnnotation {
        MySecretEnum value();
    }

    /**
     * A package-private enum. Annotation member implementations must be able to
     * access this enum.
     */
    enum MySecretEnum {
        FIRST,
        SECOND,
        THIRD
    }

    /**
     * Ensures the elements work for public/package-private annotation interfaces
     * with elements of package-private types.
     */
    @Test
    void testElements() {
        var container = WithPackagePrivateTest.class.getDeclaredAnnotation(InaccessibleContainer.class);
        assertEquals(List.of(MySecretEnum.FIRST, MySecretEnum.SECOND, MySecretEnum.THIRD),
                     Arrays.stream(container.value()).map(InaccessibleElement::value).toList());
        var dummyPublic = WithPackagePrivateTest.class.getDeclaredAnnotation(DummyPublicAnnotation.class);
        assertSame(MySecretEnum.FIRST, dummyPublic.value());
    }
}
