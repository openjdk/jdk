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

import static jdk.internal.util.OperatingSystem.WINDOWS;
import static jdk.jpackage.test.HelloApp.configureAndExecute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.TokenReplace;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageCommand.Macro;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Tests environment variables substitution by jpackage launcher
 * @bug 8341641
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @build AppLauncherSubstTest
 * @run main/othervm -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppLauncherSubstTest
 */
public class AppLauncherSubstTest {

    record TestSpec(String str, String expectedStr, Map<String, String> env) {

        static class Builder {

            Builder str(String v) {
                str = v;
                return this;
            }

            Builder expect(String v) {
                expectedStr = v;
                return this;
            }

            Builder var(String name, String value) {
                env.put(name, value);
                return this;
            }

            TestSpec create() {
                return new TestSpec(str, Optional.ofNullable(expectedStr).orElse(str), env);
            }

            private String str;
            private String expectedStr;
            private Map<String, String> env = new LinkedHashMap<>();
        }

        public TestSpec {
            Objects.requireNonNull(str);
            Objects.requireNonNull(expectedStr);
            Objects.requireNonNull(env);
            env.entrySet().forEach(Objects::requireNonNull);
        }

        public String resolveExpectedStr(JPackageCommand cmd) {
            return MACROS.applyTo(expectedStr, token -> {
                // @@APPDIR@@ -> APPDIR
                final var macro = token.substring(2, token.length() - 2);
                return Path.of(cmd.macroValue(Macro.valueOf(macro))).toAbsolutePath();
            });
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append("str=").append(str);
            sb.append(", expect=").append(expectedStr);
            if (!env.isEmpty()) {
                sb.append(", env=").append(env);
            }
            return sb.toString();
        }

        private final static TokenReplace MACROS = new TokenReplace(Stream.of(Macro.values()).map(macro -> {
            return String.format("@@%s@@", macro);
        }).toArray(String[]::new));
    }

    @Test
    @ParameterSupplier
    @ParameterSupplier(value = "testCaseSensitive", ifNotOS = WINDOWS)
    @ParameterSupplier(value = "testCaseInsensitive", ifOS = WINDOWS)
    public static void test(TestSpec spec) throws IOException {
        final var cmd = JPackageCommand.helloAppImage(TEST_APP_JAVA + "*Hello")
                .ignoreFakeRuntime()
                .setArgumentValue("--java-options", "-D" + TEST_PROP + "=");

        cmd.execute();

        // Manually edit main launcher config file. Don't do it directly because
        // jpackage doesn't pass the value of `--java-options` option as-is into the config file.
        final var cfgFile = cmd.appLauncherCfgPath(null);
        TKit.createTextFile(cfgFile, Files.readAllLines(cfgFile).stream().map(line -> {
            return TEST_PROP_REGEXP.matcher(line).replaceFirst(Matcher.quoteReplacement(spec.str()));
        }));

        final var launcherExec = new Executor()
                .saveOutput()
                .dumpOutput()
                .setExecutable(cmd.appLauncherPath().toAbsolutePath())
                .addArguments("--print-sys-prop=" + TEST_PROP);

        spec.env().forEach(launcherExec::setEnvVar);

        final var resolvedExpectedStr = spec.resolveExpectedStr(cmd);
        final var actualStr = configureAndExecute(0, launcherExec).getFirstLineOfOutput().substring((TEST_PROP + "=").length());

        if (TKit.isWindows() && !resolvedExpectedStr.equals(spec.expectedStr())) {
            TKit.assertEquals(resolvedExpectedStr.toLowerCase(), actualStr.toLowerCase(), "Check the property value is as expected [lowercase]");
        } else {
            TKit.assertEquals(resolvedExpectedStr, actualStr, "Check the property value is as expected");
        }
    }

    public static Collection<Object[]> test() {
        return Stream.of(
                testSpec(""),
                testSpec("$ONE ${TWO} ${ONE} $TWO ONE TWO").expect("one two one two ONE TWO").var("ONE", "one").var("TWO", "two"),
                testSpec("\\$FOO\\\\$FOO\\${FOO}\\\\${FOO}").expect("$FOO\\BAR${FOO}\\BAR").var("FOO", "BAR"),
                testSpec("$FOO-$BAR").expect("$FOO-").var("BAR", ""),
                testSpec("${BINDIR}${APPDIR}${ROOTDIR}").expect("@@BINDIR@@@@APPDIR@@@@ROOTDIR@@").var("BINDIR", "a").var("APPDIR", "b").var("ROOTDIR", "c"),
                testSpec("$BINDIR$APPDIR$ROOTDIR").expect("@@BINDIR@@@@APPDIR@@@@ROOTDIR@@"),
                testSpec("$BINDIR2$APPDIR2$ROOTDIR2").expect("$BINDIR2$APPDIR2$ROOTDIR2")
        ).map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public static Collection<Object[]> testCaseSensitive() {
        final List<TestSpec> testCases = new ArrayList<>();
        for (final var macro : Macro.values()) {
            testCases.addAll(createTestCases(macro, true));
        }

        testCases.addAll(Stream.of(
                testSpec("$ALPHA $alpha").expect("A a").var("ALPHA", "A").var("alpha", "a"),
                testSpec("$ALPHA $alpha").expect("$ALPHA a").var("alpha", "a"),
                testSpec("$ALPHA $alpha").expect("A $alpha").var("ALPHA", "A")
        ).map(TestSpec.Builder::create).toList());

        return testCases.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public static Collection<Object[]> testCaseInsensitive() {
        final List<TestSpec> testCases = new ArrayList<>();
        for (final var macro : Macro.values()) {
            testCases.addAll(createTestCases(macro, false));
        }

        testCases.addAll(Stream.of(
                testSpec("$ALPHA $alpha").expect("A A").var("AlphA", "A"),
                testSpec("$ALPHA $alpha").expect("a a").var("alpha", "a"),
                testSpec("$ALPHA $alpha").expect("A A").var("ALPHA", "A")
        ).map(TestSpec.Builder::create).toList());

        return testCases.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    private static List<TestSpec> createTestCases(Macro macro, boolean caseSensitive) {
        final var name = macro.name();
        final var name2 = name.transform(str -> {
            final var chars = name.toCharArray();
            for (int i = 0; i < chars.length; i += 2) {
                chars[i] = Character.toLowerCase(chars[i]);
            }
            return new String(chars);
        });

        if (name.equals(name2)) {
            throw new UnsupportedOperationException();
        }

        final var testSpec = testSpec(String.format("${%s}${%s}", name, name2)).var(name, "A").var(name2, "[foo]");
        if (caseSensitive) {
            testSpec.expect(String.format("@@%s@@[foo]", name, name2));
        } else {
            testSpec.expect(String.format("@@%s@@@@%s@@", name, name));
        }

        final var testSpec2 = testSpec(String.format("${%s}${%s}", name, name2)).var(name, "A");
        if (caseSensitive) {
            testSpec2.expect(String.format("@@%s@@${%s}", name, name2));
        } else {
            testSpec2.expect(String.format("@@%s@@@@%s@@", name, name));
        }

        final var testSpec3 = testSpec(String.format("${%s}${%s}", name, name2));
        if (caseSensitive) {
            testSpec3.expect(String.format("@@%s@@${%s}", name, name2));
        } else {
            testSpec3.expect(String.format("@@%s@@@@%s@@", name, name));
        }

        return Stream.of(testSpec, testSpec2).map(TestSpec.Builder::create).toList();
    }

    private static TestSpec.Builder testSpec(String str) {
        return new TestSpec.Builder().str(str);
    }

    private static final Path TEST_APP_JAVA = TKit.TEST_SRC_ROOT.resolve("apps/PrintEnv.java");

    private static final String TEST_PROP = "jdk.jpackage.test.Property";
    private static final Pattern TEST_PROP_REGEXP = Pattern.compile("(?<=" + Pattern.quote(TEST_PROP) + "=).*");
}
