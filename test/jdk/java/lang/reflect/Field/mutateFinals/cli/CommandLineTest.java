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
 * @bug 8353835
 * @summary Test the command line option --enable-final-field-mutation
 * @library /test/lib
 * @build CommandLineTestHelper
 * @run junit CommandLineTest
 */

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

class CommandLineTest {

    // helper class name
    private static final String HELPER = "CommandLineTestHelper";

    // warning output
    private static final String WARNING_LINE1 =
            "WARNING: Final field value in class " + HELPER;
    private static final String WARNING_LINE3 =
            "WARNING: Use --enable-final-field-mutation=ALL-UNNAMED to avoid a warning";
    private static final String WARNING_LINE4 =
            "WARNING: Mutating final fields will be blocked in a future release unless final field mutation is enabled";

    // warning line 2 depends on the method
    private static final String WARNING_MUTATED =
            " has been mutated reflectively by class " + HELPER + " in unnamed module";
    private static final String WARNING_UNREFLECTED =
            " has been unreflected for mutation by class " + HELPER + " in unnamed module";

    /**
     * Test that a warning is printed by default.
     */
    @Test
    void testDefault() throws Exception {
        test("testFieldSetInt")
            .shouldContain(WARNING_LINE1)
            .shouldContain(WARNING_MUTATED)
            .shouldContain(WARNING_LINE3)
            .shouldContain(WARNING_LINE4)
            .shouldHaveExitValue(0);

        test("testUnreflectSetter")
            .shouldContain(WARNING_LINE1)
            .shouldContain(WARNING_UNREFLECTED)
            .shouldContain(WARNING_LINE3)
            .shouldContain(WARNING_LINE4)
            .shouldHaveExitValue(0);
    }

    /**
     * Test allow mutation of finals.
     */
    @Test
    void testAllow() throws Exception {
        test("testFieldSetInt", "--illegal-final-field-mutation=allow")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_MUTATED)
            .shouldHaveExitValue(0);

        test("testFieldSetInt", "--enable-final-field-mutation=ALL-UNNAMED")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_MUTATED)
            .shouldHaveExitValue(0);

        // allow ALL-UNNAMED, deny by default
        test("testFieldSetInt", "--enable-final-field-mutation=ALL-UNNAMED", "--illegal-final-field-mutation=deny")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_MUTATED)
            .shouldHaveExitValue(0);

        test("testUnreflectSetter", "--illegal-final-field-mutation=allow")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_UNREFLECTED)
            .shouldHaveExitValue(0);

        test("testUnreflectSetter", "--enable-final-field-mutation=ALL-UNNAMED")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_UNREFLECTED)
            .shouldHaveExitValue(0);

        // allow ALL-UNNAMED, deny by default
        test("testUnreflectSetter", "--enable-final-field-mutation=ALL-UNNAMED", "--illegal-final-field-mutation=deny")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_UNREFLECTED)
            .shouldHaveExitValue(0);
    }

    /**
     * Test warn on first mutation of a final.
     */
    @Test
    void testWarn() throws Exception {
        test("testFieldSetInt", "--illegal-final-field-mutation=warn")
            .shouldContain(WARNING_LINE1)
            .shouldContain(WARNING_MUTATED)
            .shouldContain(WARNING_LINE3)
            .shouldContain(WARNING_LINE4)
            .shouldHaveExitValue(0);

        test("testUnreflectSetter", "--illegal-final-field-mutation=warn")
            .shouldContain(WARNING_LINE1)
            .shouldContain(WARNING_UNREFLECTED)
            .shouldContain(WARNING_LINE3)
            .shouldContain(WARNING_LINE4)
            .shouldHaveExitValue(0);

        // should be only one warning
        test("testFieldSetInt+testUnreflectSetter", "--illegal-final-field-mutation=warn")
            .shouldContain(WARNING_LINE1)
            .shouldContain(WARNING_MUTATED)
            .shouldNotContain(WARNING_UNREFLECTED)
            .shouldHaveExitValue(0);

        // should be only one warning
        test("testUnreflectSetter+testFieldSetInt", "--illegal-final-field-mutation=warn")
            .shouldContain(WARNING_LINE1)
            .shouldNotContain(WARNING_MUTATED)
            .shouldContain(WARNING_UNREFLECTED)
            .shouldHaveExitValue(0);
    }

    /**
     * Test debug mode.
     */
    @Test
    void testDebug() throws Exception {
        test("testFieldSetInt+testUnreflectSetter", "--illegal-final-field-mutation=debug")
            .shouldContain("Final field value in class " + HELPER)
            .shouldContain(WARNING_MUTATED)
            .shouldContain("java.lang.reflect.Field.setInt")
            .shouldContain(WARNING_UNREFLECTED)
            .shouldContain("java.lang.invoke.MethodHandles$Lookup.unreflectSetter")
            .shouldHaveExitValue(0);
    }

    /**
     * Test deny mutation of finals.
     */
    @Test
    void testDeny() throws Exception {
        test("testFieldSetInt", "--illegal-final-field-mutation=deny")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_MUTATED)
            .shouldContain("java.lang.IllegalAccessException")
            .shouldNotHaveExitValue(0);

        test("testUnreflectSetter", "--illegal-final-field-mutation=deny")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_UNREFLECTED)
            .shouldContain("java.lang.IllegalAccessException")
            .shouldNotHaveExitValue(0);
    }

    /**
     * Test last usage of --illegal-final-field-mutation "wins".
     */
    @Test
    void testLastOneWins() throws Exception {
        test("testFieldSetInt", "--illegal-final-field-mutation=allow", "--illegal-final-field-mutation=deny")
            .shouldNotContain(WARNING_LINE1)
            .shouldNotContain(WARNING_MUTATED)
            .shouldContain("java.lang.IllegalAccessException")
            .shouldNotHaveExitValue(0);
    }

    /**
     * Test --illegal-final-field-mutation with bad values.
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "bad" })
    void testInvalidValues(String value) throws Exception {
        test("testFieldSetInt", "--illegal-final-field-mutation=" + value)
            .shouldContain("Value specified to --illegal-final-field-mutation not recognized")
            .shouldNotHaveExitValue(0);
    }

    /**
     * Test setting system property to "allow" at runtime. The saved value from startu
     * should be used, not the system property set at run-time.
     */
    @Test
    void testSetPropertyToAllow() throws Exception {
        test("setSystemPropertyToAllow+testFieldSetInt")
            .shouldContain(WARNING_LINE1)
            .shouldContain(WARNING_MUTATED)
            .shouldContain(WARNING_LINE3)
            .shouldContain(WARNING_LINE4)
            .shouldHaveExitValue(0);
    }

    /**
     * Launch helper with the given arguments and VM options.
     */
    private OutputAnalyzer test(String action, String... vmopts) throws Exception {
        Stream<String> s1 = Stream.of(vmopts);
        Stream<String> s2 = Stream.of(HELPER, action);
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        var outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.err)
                .errorTo(System.err);
        return outputAnalyzer;
    }
}
