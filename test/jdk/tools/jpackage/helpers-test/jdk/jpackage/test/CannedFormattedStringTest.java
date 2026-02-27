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
package jdk.jpackage.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class CannedFormattedStringTest {

    @Test
    void test_getValue() {
        var a = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}", "Duke");
        assertEquals("Hello Duke! Bye Duke", a.getValue());
        assertEquals("Hello {0}! Bye {0}+[Duke]", a.toString());

        var b = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}");
        assertEquals("Hello {0}! Bye {0}", b.getValue());
        assertEquals("Hello {0}! Bye {0}", b.toString());
    }

    @Test
    void test_equals() {
        var a = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}", "Duke");
        var b = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}", "Duke");

        assertEquals(a, b);

        a = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}", "Duke");
        b = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}", "Java");

        assertNotEquals(a, b);
    }

    @Test
    void test_addPrefix() {
        var a = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}", "Duke");
        var b = a.addPrefix("{0} and {0}").addPrefix("They say: {0}");
        var str = "Hello Duke! Bye Duke";
        assertEquals(str, a.getValue());
        assertEquals("They say: " + str + " and " + str, b.getValue());
        assertEquals("They say: {0}+[{0} and {0}, Hello {0}! Bye {0}, Duke]", b.toString());

        var c = a.addPrefix("{0} and {0}").addPrefix("They say: {0}");
        assertEquals(c, b);
        assertNotSame(b,  c);
    }

    @Test
    void test_mapArgs() {
        var a = Formatter.MESSAGE_FORMAT.create("Hello {0}! Bye {0}", "Duke");
        var b = a.mapArgs(arg -> {
            assertEquals("Duke", arg);
            return "Java";
        });

        assertEquals("Hello Duke! Bye Duke", a.getValue());
        assertEquals("Hello Java! Bye Java", b.getValue());
    }

    @Test
    void test_CannedArgument() {
        var a = Formatter.MESSAGE_FORMAT.create("Current directory: {0}", CannedFormattedString.cannedAbsolutePath("foo"));
        assertEquals("Current directory: " + Path.of("foo").toAbsolutePath(), a.getValue());
        assertEquals("Current directory: {0}+[AbsolutePath(foo)]", a.toString());
    }

    enum Formatter implements BiFunction<String, Object[], String> {
        MESSAGE_FORMAT {
            @Override
            public String apply(String format, Object[] formatArgs) {
                return MessageFormat.format(format, formatArgs);
            }
        },
        ;

        CannedFormattedString create(String format, Object ... formatArgs) {
            return new CannedFormattedString(this, format, List.of(formatArgs));
        }
    }
}
