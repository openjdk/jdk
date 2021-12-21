/*
 * Copyright (c) 2021, Google LLC. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

/**
 * @test
 * @bug 8271623
 *
 * @clean *
 * @compile OptimizeOuterThis.java InnerClasses.java
 * @run main OptimizeOuterThis
 *
 * @clean *
 * @compile -XDoptimizeOuterThis=true --release 17 OptimizeOuterThis.java InnerClasses.java
 * @run main OptimizeOuterThis
 */
public class OptimizeOuterThis extends InnerClasses {

    public static void main(String[] args) {
        new OptimizeOuterThis().test();
    }

    public void test() {
        checkInner(localCapturesParameter(0), false);
        checkInner(localCapturesLocal(), false);
        checkInner(localCapturesEnclosing(), true);

        checkInner(anonCapturesParameter(0), false);
        checkInner(anonCapturesLocal(), false);
        checkInner(anonCapturesEnclosing(), true);

        checkInner(StaticMemberClass.class, false);
        checkInner(NonStaticMemberClass.class, false);
        checkInner(NonStaticMemberClassCapturesEnclosing.class, true);

        checkInner(N0.class, false);
        checkInner(N0.N1.class, true);
        checkInner(N0.N1.N2.class, true);
        checkInner(N0.N1.N2.N3.class, true);
        checkInner(N0.N1.N2.N3.N4.class, false);
        checkInner(N0.N1.N2.N3.N4.N5.class, false);

        checkInner(SerializableCapture.class, true);
        checkInner(SerializableWithSerialVersionUID.class, true);
        checkInner(SerializableWithInvalidSerialVersionUIDType.class, true);
        checkInner(SerializableWithInvalidSerialVersionUIDNonFinal.class, true);
        checkInner(SerializableWithInvalidSerialVersionUIDNonStatic.class, true);
    }

    private static void checkInner(Class<?> clazz, boolean expectOuterThis) {
        Optional<Field> outerThis = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.getName().startsWith("this$")).findFirst();
        if (expectOuterThis) {
            if (outerThis.isEmpty()) {
                throw new AssertionError(
                        String.format(
                                "expected %s to have an enclosing instance", clazz.getName()));
            }
        } else {
            if (outerThis.isPresent()) {
                throw new AssertionError(
                        String.format("%s had an unexpected enclosing instance %s", clazz.getName(), outerThis.get()));
            }
        }
    }
}
