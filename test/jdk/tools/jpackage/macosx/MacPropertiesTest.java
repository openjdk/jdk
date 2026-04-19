/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageOutputValidator;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.TKit;


/**
 * Test --mac-package-name, --mac-package-identifier parameters.
 */

/*
 * @test
 * @summary jpackage with --mac-package-name, --mac-package-identifier
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @requires (os.family == "mac")
 * @compile -Xlint:all -Werror MacPropertiesTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=MacPropertiesTest
 */
public class MacPropertiesTest {

    @Test
    @ParameterSupplier
    public void test(TestSpec spec) {
        spec.run();
    }

    public static Collection<Object[]> test() {

        var testCases = new ArrayList<TestSpec.Builder>();

        testCases.addAll(List.of(
                TestSpec.build("CFBundleName").addArgs("--mac-package-name", "MacPackageNameTest").expect("MacPackageNameTest"),
                TestSpec.build("CFBundleIdentifier").addArgs("--mac-package-identifier", "Foo").expect("Foo"),
                // Should derive from the input data.
                TestSpec.build("CFBundleIdentifier").appDesc("com.acme.Hello").expect("com.acme").expect(BundleIdentifierMessage.VALUE.asCannedFormattedString("com.acme"))
        ));

        return testCases.stream().map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    enum BundleIdentifierMessage implements CannedFormattedString.Spec {
        VALUE("message.derived-bundle-identifier", "bundle-id"),
        ;

        BundleIdentifierMessage(String key, Object ... args) {
            this.key = Objects.requireNonNull(key);
            this.args = List.of(args);
        }

        @Override
        public String format() {
            return key;
        }

        @Override
        public List<Object> modelArgs() {
            return args;
        }

        private final String key;
        private final List<Object> args;
    }

    record TestSpec(
            Optional<String> appDesc,
            List<String> addArgs,
            List<String> delArgs,
            List<CannedFormattedString> expectedTraceMessages,
            String expectedInfoPlistKeyValue,
            String infoPlistKey,
            Optional<Class<? extends CannedFormattedString.Spec>> traceMessagesClass) {

        TestSpec {
            Objects.requireNonNull(addArgs);
            Objects.requireNonNull(delArgs);
            Objects.requireNonNull(expectedTraceMessages);
            Objects.requireNonNull(expectedInfoPlistKeyValue);
            Objects.requireNonNull(infoPlistKey);
            Objects.requireNonNull(traceMessagesClass);
        }

        void run() {
            var cmd = appDesc.map(JPackageCommand::helloAppImage).orElseGet(JPackageCommand::helloAppImage)
                    .setFakeRuntime().addArguments(addArgs);

            delArgs.forEach(cmd::removeArgumentWithValue);

            Consumer<JPackageOutputValidator> validatorMutator = validator -> {
                validator.matchTimestamps().stripTimestamps();
            };

            traceMessagesClass.ifPresentOrElse(v -> {
                cmd.validateOutput(v, validatorMutator, expectedTraceMessages);
            }, () -> {
                new JPackageOutputValidator()
                        .mutate(validatorMutator)
                        .expectMatchingStrings(expectedTraceMessages)
                        .applyTo(cmd);
            });

            cmd.executeAndAssertHelloAppImageCreated();

            var plist = MacHelper.readPListFromAppImage(cmd.outputBundle());

            TKit.assertEquals(
                    expectedInfoPlistKeyValue,
                    plist.queryValue(infoPlistKey),
                    String.format("Check value of %s plist key", infoPlistKey));
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();

            tokens.add(String.format("%s=>%s", infoPlistKey, expectedInfoPlistKeyValue));

            appDesc.ifPresent(v -> {
                tokens.add("app-desc=" + v);
            });

            if (!addArgs.isEmpty()) {
                tokens.add("args-add=" + addArgs);
            }

            if (!delArgs.isEmpty()) {
                tokens.add("args-del=" + delArgs);
            }

            if (!expectedTraceMessages.isEmpty()) {
                tokens.add("expect=" + expectedTraceMessages);
            }

            return tokens.stream().collect(Collectors.joining("; "));
        }

        static Builder build() {
            return new Builder();
        }

        static Builder build(String infoPlistKey) {
            return build().infoPlistKey(Objects.requireNonNull(infoPlistKey));
        }

        static final class Builder {

            TestSpec create() {

                Class<? extends CannedFormattedString.Spec> traceMessagesClass = switch (Objects.requireNonNull(infoPlistKey)) {
                    case "CFBundleIdentifier" -> BundleIdentifierMessage.class;
                    case "CFBundleName" -> null;
                    default -> {
                        throw new IllegalStateException();
                    }
                };

                return new TestSpec(
                        Optional.ofNullable(appDesc),
                        addArgs,
                        delArgs,
                        expectedTraceMessages,
                        expectedInfoPlistKeyValue,
                        infoPlistKey,
                        Optional.ofNullable(traceMessagesClass));
            }

            Builder appDesc(String v) {
                appDesc = v;
                return this;
            }

            Builder addArgs(List<String> v) {
                addArgs.addAll(v);
                return this;
            }

            Builder addArgs(String... args) {
                return addArgs(List.of(args));
            }

            Builder delArgs(List<String> v) {
                delArgs.addAll(v);
                return this;
            }

            Builder delArgs(String... args) {
                return delArgs(List.of(args));
            }

            Builder expect(CannedFormattedString traceMessage) {
                expectedTraceMessages.add(traceMessage);
                return this;
            }

            Builder expect(String v) {
                expectedInfoPlistKeyValue = v;
                return this;
            }

            Builder infoPlistKey(String v) {
                infoPlistKey = v;
                return this;
            }

            private String appDesc;
            private final List<String> addArgs = new ArrayList<>();
            private final List<String> delArgs = new ArrayList<>();
            private final List<CannedFormattedString> expectedTraceMessages = new ArrayList<>();
            private String expectedInfoPlistKeyValue;
            private String infoPlistKey;
        }
    }
}
