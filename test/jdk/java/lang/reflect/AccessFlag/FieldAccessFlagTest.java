/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266670
 * @summary Test expected AccessFlag's on fields.
 */

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static java.lang.reflect.AccessFlag.*;

/*
 * Field modifiers include:
 * public, private, protected, static, final, volatile, transient,
 *
 * Additionally, the access flags enum and synthetic cannot be
 * explicitly applied.
 */
public class FieldAccessFlagTest {
    public static void main(String... args) {
        for (var field :
                 FieldAccessFlagTest.class.getDeclaredFields()) {
            checkField(field);
        }

        for (var field :
                 MetaSynVar.class.getDeclaredFields()) {
            checkField(field);
        }
    }

    private static void checkField(Field field) {
        ExpectedFieldFlags expected =
            field.getAnnotation(ExpectedFieldFlags.class);
        if (expected != null) {
            Set<AccessFlag> base = EnumSet.noneOf(AccessFlag.class);
            Collections.addAll(base, expected.value());
            Set<AccessFlag> actual = field.accessFlags();
            if (!base.equals(actual)) {
                throw new RuntimeException("On " + field +
                        " expected " + base +
                        " got " + actual);
            }
        }
    }

    // Fields
    @ExpectedFieldFlags({PUBLIC, STATIC, FINAL})
    public static final String f1 = "foo";

    @ExpectedFieldFlags({PRIVATE, VOLATILE, TRANSIENT})
    private volatile transient String secret = "xxyzzy";

    @ExpectedFieldFlags({PROTECTED})
    protected String meadow = "";

    // Enum constant should have the enum access flag set
    static enum MetaSynVar {
        @ExpectedFieldFlags({PUBLIC, STATIC, FINAL, ENUM})
        FOO,

        @ExpectedFieldFlags({PUBLIC, STATIC, FINAL, ENUM})
        BAR;

        @ExpectedFieldFlags({PRIVATE}) // no "ENUM"
        private int field = 0;
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExpectedFieldFlags {
        AccessFlag[] value();
    }
}
