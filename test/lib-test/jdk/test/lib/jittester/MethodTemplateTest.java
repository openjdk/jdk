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

package jdk.test.lib.jittester;

import java.lang.reflect.Executable;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @summary Unit tests for JITTester string method templates
 *
 * @library /test/lib
 *          /test/hotspot/jtreg/testlibrary/jittester/src
 *
 * @run testng jdk.test.lib.jittester.MethodTemplateTest
 */
public class MethodTemplateTest {

    @Test
    public void testMatchingPatterns() throws NoSuchMethodException {
        Tester.forMethod(System.class, "getenv", String.class)
            .assertMatches("java/lang/System::getenv(Ljava/lang/String;)")
            .assertMatches("*::getenv(Ljava/lang/String;)")
            .assertMatches("java/lang/*::getenv(Ljava/lang/String;)")
            .assertMatches("java/lang/System::*env*(Ljava/lang/String;)")
            .assertMatches("java/lang/System::getenv")
            .assertMatches("java/lang/System::getenv(*)");

        Tester.forCtor(RuntimeException.class, Throwable.class)
            .assertMatches("java/lang/RuntimeException::RuntimeException(Ljava/lang/Throwable;)");

        Tester.forMethod(String.class, "regionMatches", int.class, String.class, int.class, int.class)
            .assertMatches("java/lang/String::regionMatches(ILjava/lang/String;II)");
    }

    @Test
    public void testNonMatchingPatterns() throws NoSuchMethodException {
        Tester.forMethod(String.class, "regionMatches", int.class, String.class, int.class, int.class)
            .assertDoesNotMatch("java/lang/String::regionMatches(IIILjava/lang/String;)");

        Tester.forMethod(String.class, "endsWith", String.class)
              .assertDoesNotMatch("java/lang/String::startsWith(Ljava/lang/String;)");
    }

    @Test
    public void testWildcardStrings() {
        assertTrue(new MethodTemplate.WildcardString("Torment")
                .matches("Torment"));

        assertTrue(new MethodTemplate.WildcardString("Torm*")
                .matches("Torment"));

        assertTrue(new MethodTemplate.WildcardString("*ent")
                .matches("Torment"));

        assertTrue(new MethodTemplate.WildcardString("*")
                .matches("Something"));

        assertTrue(new MethodTemplate.WildcardString("**")
                .matches("Something"));

        assertTrue(new MethodTemplate.WildcardString("*Middle*")
                .matches("OnlyMiddleMatches"));

        assertFalse(new MethodTemplate.WildcardString("Wrong")
                .matches("Correct"));
        assertFalse(new MethodTemplate.WildcardString("Joy")
                .matches("Joyfull"));
        assertFalse(new MethodTemplate.WildcardString("*Torm*")
                .matches("Sorrow"));
    }

    static final class Tester {
        private final Executable executable;

        private Tester(Executable executable) {
            this.executable = executable;
        }

        public Tester assertMatches(String stringTemplate) {
            MethodTemplate template = MethodTemplate.parse(stringTemplate);
            assertTrue(template.matches(executable),
                    "Method '" + executable + "' does not match template '" + stringTemplate + "'");
            return this;
        }

        public Tester assertDoesNotMatch(String stringTemplate) {
            MethodTemplate template = MethodTemplate.parse(stringTemplate);
            assertFalse(template.matches(executable),
                    "Method '" + executable + "' erroneously matches template '" + stringTemplate + "'");
            return this;
        }

        public static Tester forMethod(Class klass, String name, Class<?>... arguments)
                throws  NoSuchMethodException {
            return new Tester(klass.getDeclaredMethod(name, arguments));
        }

        public static Tester forCtor(Class klass, Class<?>... arguments)
                throws  NoSuchMethodException {
                return new Tester(klass.getConstructor(arguments));
        }
    }

}
