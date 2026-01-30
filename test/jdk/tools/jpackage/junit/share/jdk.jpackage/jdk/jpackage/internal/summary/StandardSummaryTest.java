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

package jdk.jpackage.internal.summary;

import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.XmlUtils.createXml;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.Globals;
import jdk.jpackage.internal.cli.CliBundlingEnvironment;
import jdk.jpackage.internal.cli.Main;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardBundlingOperation;
import jdk.jpackage.internal.cli.StandardOption;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.util.MacBundle;
import jdk.jpackage.internal.util.SetBuilder;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.CannedArgument;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageOutputValidator;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMock;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;
import jdk.jpackage.test.stdmock.DebToolsMock;
import jdk.jpackage.test.stdmock.EnvironmentMock;
import jdk.jpackage.test.stdmock.JPackageMockUtils;
import jdk.jpackage.test.stdmock.MacToolsMock;
import jdk.jpackage.test.stdmock.RpmToolsMock;
import jdk.jpackage.test.stdmock.WixToolsMock;

public class StandardSummaryTest extends JUnitAdapter {

    @Test
    @ParameterSupplier
    public void test(TestSpec spec) {
        spec.test();
    }

    public static Collection<Object[]> test() {

        Set<OperatingSystem> supportedOperatingSystems = JPackageMockUtils.availableBundlingEnvironments().keySet();

        var supportedBundlingOperations = supportedOperatingSystems.stream()
                .flatMap(StandardBundlingOperation::ofPlatform)
                .toList();

        var testCases = new ArrayList<TestSpec.Builder>();

        for (var op : supportedBundlingOperations) {

            var testCase = testSpec().op(op).addArgs("--app-version", "45.67.1");

            testCase.properties(StandardProperty.VERSION);

            if (op == StandardBundlingOperation.SIGN_MAC_APP_IMAGE) {
                testCase.properties(StandardProperty.MAC_SIGN_APP_IMAGE_OPERATION);
                testCase.addArgs("--mac-app-image-sign-identity", "foo");
            } else {
                testCase.properties(StandardProperty.OPERATION);
                testCase.properties(StandardProperty.OUTPUT_BUNDLE);
            }

            switch (op) {
                case CREATE_LINUX_DEB, CREATE_LINUX_RPM -> {
                    testCase.properties(StandardProperty.LINUX_PACKAGE_NAME);
                    testCase.addArgs("--linux-app-release", "8");
                }
                case CREATE_WIN_MSI, CREATE_WIN_EXE -> {
                    testCase.properties(StandardProperty.WIN_MSI_PRODUCT_CODE);
                    testCase.properties(StandardProperty.WIN_MSI_UPGRADE_CODE);
                    testCase.properties(StandardProperty.WIN_WIX_VERSION);
                }
                default -> {
                    // NOP
                }
            }

            testCases.add(testCase);

        }

        return testCases.stream().map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    enum StandardProperty {

        //
        // Keep the same order as in the jdk.jpackage.internal.summary.StandardProperty enum.
        //

        OPERATION("summary.property.operation", "summary.property.operation.format"),
        MAC_SIGN_APP_IMAGE_OPERATION("summary.property.operation", "summary.property.mac-sign-app-image.format"),
        OUTPUT_BUNDLE("summary.property.output-bundle"),
        LINUX_PACKAGE_NAME("summary.property.package-name"),
        WIN_MSI_PRODUCT_CODE("summary.property.product-code"),
        WIN_MSI_UPGRADE_CODE("summary.property.upgrade-code"),
        VERSION("summary.property.version"),
        WIN_WIX_VERSION("summary.property.wix-version"),
        LINUX_DISABLE_REQUIRED_PACKAGES_SEARCH("summary.property.required-packages-search"),
        LINUX_ENABLE_REQUIRED_PACKAGES_SEARCH("summary.property.required-packages-search"),
        ;

        StandardProperty(String mainKey, Optional<String> valueFormmatter) {
            this.mainKey = Objects.requireNonNull(mainKey);
            this.valueFormmatter = Objects.requireNonNull(valueFormmatter);
        }

        StandardProperty(String mainKey, String valueFormmatter) {
            this(mainKey, Optional.of(valueFormmatter));
        }

        StandardProperty(String mainKey) {
            this(mainKey, Optional.empty());
        }

        String format(JPackageCommand cmd, List<Object> args) {
            Objects.requireNonNull(cmd);
            Objects.requireNonNull(args);
            if (args.isEmpty()) {
                if (valueFormmatter.isEmpty()) {
                    return I18N.getString(mainKey);
                } else {
                    throw new IllegalArgumentException(String.format(
                            "Missing arguments for the formatter of property %", mainKey));
                }
            } else {
                return valueFormmatter.map(f -> {
                    return cmd.getValue(cannedFormattedString(f, args.toArray()));
                }).or(() -> {
                    if (args.size() == 1) {
                        switch (args.getFirst()) {
                            case JPackageCommand.CannedArgument ca -> {
                                return Optional.of(ca.value(cmd));
                            }
                            case CannedArgument ca -> {
                                return Optional.of(ca.getValue());
                            }
                            case Object o -> {
                                return Optional.of(o.toString());
                            }
                        }
                    } else {
                        return Optional.empty();
                    }
                }).map(propertyValue -> {
                    return String.format("%s: %s", I18N.getString(mainKey), propertyValue);
                }).orElseThrow(() -> {
                    return new IllegalArgumentException(String.format("No formatter for property %s", mainKey));
                });
            }
        }

        String format(JPackageCommand cmd, Object... args) {
            return format(cmd, List.of(args));
        }

        String formatKey() {
            return I18N.getString(mainKey);
        }

        boolean isAvailableOn(OperatingSystem os) {
            if (name().startsWith("LINUX_")) {
                return os == OperatingSystem.LINUX;
            } else if (name().startsWith("MAC_")) {
                return os == OperatingSystem.MACOS;
            } else if (name().startsWith("WIN_")) {
                return os == OperatingSystem.WINDOWS;
            } else {
                // Shared property.
                return true;
            }
        }

        private final String mainKey;
        private final Optional<String> valueFormmatter;
    }

    record TestSpec(
            StandardBundlingOperation op,
            Optional<EnvironmentMock> env,
            List<String> addArgs,
            List<String> removeArgs,
            Set<StandardProperty> expectedProperties) {

        TestSpec {
            Objects.requireNonNull(op);
            Objects.requireNonNull(env);
            Objects.requireNonNull(addArgs);
            Objects.requireNonNull(removeArgs);

            Objects.requireNonNull(expectedProperties);
            if (expectedProperties.isEmpty()) {
                throw new IllegalArgumentException("The list of expected properties must be non-empty");
            }
        }

        static final class Builder {

            Builder() {
            }

            Builder op(StandardBundlingOperation v) {
                op = v;
                return this;
            }

            Builder env(EnvironmentMock v) {
                env = v;
                return this;
            }

            Builder setAddArgs(List<String> v) {
                addArgs.clear();
                addArgs.addAll(v);
                return this;
            }

            Builder setAddArgs(String... v) {
                return setAddArgs(List.of(v));
            }

            Builder addArgs(List<String> v) {
                addArgs.addAll(v);
                return this;
            }

            Builder addArgs(String... v) {
                return addArgs(List.of(v));
            }

            Builder setRemoveArgs(List<String> v) {
                removeArgs.clear();
                removeArgs.addAll(v);
                return this;
            }

            Builder setRemoveArgs(String... v) {
                return setRemoveArgs(List.of(v));
            }

            Builder removeArgs(List<String> v) {
                removeArgs.addAll(v);
                return this;
            }

            Builder removeArgs(String... v) {
                return removeArgs(List.of(v));
            }

            Builder properties(List<StandardProperty> v) {
                expectedProperties.addAll(v);
                return this;
            }

            Builder properties(StandardProperty... v) {
                return properties(List.of(v));
            }

            TestSpec create() {
                return new TestSpec(
                        Objects.requireNonNull(op),
                        Optional.ofNullable(env).or(() -> {
                            return defaultEnvironment(op);
                        }),
                        List.copyOf(addArgs),
                        List.copyOf(removeArgs),
                        Set.copyOf(expectedProperties));
            }

            private static Optional<EnvironmentMock> defaultEnvironment(StandardBundlingOperation op) {
                Objects.requireNonNull(op);
                switch (op) {
                    case CREATE_LINUX_RPM -> {
                        return Optional.of(RpmToolsMock.build().version("4.15").create());
                    }
                    case CREATE_LINUX_DEB -> {
                        return Optional.of(DebToolsMock.build().versionDpkg("1.18.4").versionFakeroot("1.20.2").create());
                    }
                    case CREATE_MAC_DMG -> {
                        return Optional.of(MacToolsMock.build().create());
                    }
                    case CREATE_WIN_EXE, CREATE_WIN_MSI -> {
                        return Optional.of(WixToolsMock.build().version("5.43+21").create());
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
            }

            private StandardBundlingOperation op;
            private EnvironmentMock env;
            private List<String> addArgs = new ArrayList<>();
            private List<String> removeArgs = new ArrayList<>();
            private Set<StandardProperty> expectedProperties = new HashSet<>();
        }

        void test() {
            ThrowingRunnable<? extends Exception> withOperatingSystem = () -> {
                Globals.main(() -> {
                    env.ifPresent(e -> {
                        var scriptBuilder = Script.build().commandMockBuilderMutator(CommandMock.Builder::repeatInfinitely);
                        e.applyTo(scriptBuilder);
                        switch (op) {
                            case CREATE_LINUX_RPM -> {
                                // Divert the jpackage from the default DEB packaging.
                                scriptBuilder.map(new CommandMockSpec("dpkg", CommandActionSpecs.build().exit(1).create()));
                            }
                            default -> {
                                // NOP
                            }
                        }
                        JPackageMockUtils.buildJPackage().script(scriptBuilder.createLoop()).applyToGlobals();
                    });
                    testInternal();
                    return 0;
                });
            };

            TKit.withOperatingSystem(withOperatingSystem, op.os());
        }

        private List<StandardProperty> orderedExpectedProperties() {
            return expectedProperties.stream().sorted(Comparator.comparing(Enum::ordinal)).toList();
        }

        private void testInternal() {

            JPackageCommand cmd;
            if (op != StandardBundlingOperation.SIGN_MAC_APP_IMAGE) {
                cmd = JPackageCommand.helloAppImage();
            } else {
                var version = new JPackageCommand().addArguments(addArgs).version();

                cmd = new JPackageCommand();
                cmd.addArguments("--app-image", createMacAppImageMock(version));
                cmd.addArguments("--mac-sign");
            }

            removeArgs.forEach(cmd::removeArgumentWithValue);
            cmd.addArguments(addArgs);
            if (op == StandardBundlingOperation.SIGN_MAC_APP_IMAGE) {
                cmd.removeArgumentWithValue("--app-version");
            }

            cmd.setArgumentValue("--type", op.bundleTypeValue()).removeArgument("--win-console");

            cmd.useToolProvider(configureOnlyJPackage(op.os()));

            cmd.setArgumentValue("--verbose", "summary,warnings,errors");

            // Look up expected properties in the output.
            orderedExpectedProperties().stream().map(p -> {
                return toOutputVerifier(cmd, p);
            }).reduce(new JPackageOutputValidator(),
                    JPackageOutputValidator::validator,
                    JPackageOutputValidator::validator).applyTo(cmd);

            // Look up unexpected properties in the output.
            SetBuilder.build(StandardProperty.values()).remove(expectedProperties).create().stream().filter(prop -> {
                return prop.isAvailableOn(op.os());
            }).sorted().map(p -> {
                return toOutputVerifier(cmd, p).negate();
            }).reduce(new JPackageOutputValidator(),
                    JPackageOutputValidator::validator,
                    JPackageOutputValidator::validator).applyTo(cmd);

            // Don't run output bundle validators because there is no output bundle,
            // but still check that the jpackage succeeds.
            cmd.executeIgnoreExitCode().assertExitCodeIsZero();
        }

        private TKit.TextStreamVerifier toOutputVerifier(JPackageCommand cmd, StandardProperty p) {
            Objects.requireNonNull(p);
            String fixedStr = null;
            Pattern pattern = null;
            switch (p) {
                case OPERATION -> {
                    fixedStr = p.format(cmd, op.bundleType().label());
                }
                case MAC_SIGN_APP_IMAGE_OPERATION -> {
                    if (op == StandardBundlingOperation.SIGN_MAC_APP_IMAGE) {
                        fixedStr = p.format(cmd, op.bundleType().label(), cmd.outputBundle());
                    } else {
                        pattern = createPattern(p.format(cmd, "@@NAME@@", "@@PATH@@"), Map.of(
                                "@@NAME@@", Pattern.compile("[^\"]+"),
                                "@@PATH@@", Pattern.compile("[^\"]+")));
                    }
                }
                case OUTPUT_BUNDLE -> {
                    if (op != StandardBundlingOperation.SIGN_MAC_APP_IMAGE) {
                        fixedStr = p.format(cmd, cmd.outputBundle());
                    }
                }
                case LINUX_PACKAGE_NAME -> {
                    if (PackageType.LINUX.contains(cmd.packageType())) {
                        fixedStr = p.format(cmd, LinuxHelper.getPackageName(cmd));
                    }
                }
                case WIN_MSI_PRODUCT_CODE, WIN_MSI_UPGRADE_CODE -> {
                    if (PackageType.WINDOWS.contains(cmd.packageType())) {
                        pattern = createPattern(p.format(cmd, "@@UUID@@"), Map.of("@@UUID@@",
                                Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
                    }
                }
                case VERSION -> {
                    fixedStr = p.format(cmd, cmd.fullVersion());
                }
                case WIN_WIX_VERSION -> {
                    var wixVersion = env.filter(WixToolsMock.class::isInstance).map(WixToolsMock.class::cast).map(WixToolsMock::version);
                    if (wixVersion.isPresent()) {
                        fixedStr = p.format(cmd, wixVersion.get());
                    }
                }
                case LINUX_DISABLE_REQUIRED_PACKAGES_SEARCH -> {
                    fixedStr = p.format(cmd, I18N.getString("summary.value.disabled"));
                }
                case LINUX_ENABLE_REQUIRED_PACKAGES_SEARCH -> {
                    fixedStr = p.format(cmd, I18N.getString("summary.value.enabled"));
                }
                default -> {
                    throw new AssertionError();
                }
            }

            if (pattern == null && fixedStr == null) {
                return TKit.assertTextStream(p.formatKey()).predicate(String::startsWith);
            } else if (fixedStr == null) {
                return TKit.assertTextStream(pattern).predicate(pattern.asMatchPredicate());
            } else if (pattern == null) {
                return TKit.assertTextStream(fixedStr).predicate(String::equals);
            } else {
                throw new AssertionError();
            }
        }

        private static Pattern createPattern(String str, Map<String, Pattern> tokens) {
            Objects.requireNonNull(str);
            Objects.requireNonNull(tokens);
            if (tokens.isEmpty()) {
                return Pattern.compile(Pattern.quote(str));
            }
            var splitter = tokens.keySet().stream().peek(sep -> {
                if (sep.isEmpty()) {
                    throw new IllegalArgumentException("Separator must be non-empty string");
                }
            }).map(Pattern::quote).map(pattern -> {
                return "(?:" + pattern + ")";
            }).collect(Collectors.joining("|"));

            var patternStr = Stream.of(str.splitWithDelimiters(splitter, -1)).filter(Predicate.not(String::isEmpty)).map(substr -> {
                return Optional.ofNullable(tokens.get(substr)).map(Pattern::pattern).orElseGet(() -> {
                    return Pattern.quote(substr);
                });
            }).collect(Collectors.joining(""));

            return Pattern.compile(patternStr);
        }

        @Override
        public final String toString() {
            final var sb = new StringBuilder();
            sb.append(op);
            env.ifPresent(v -> {
                sb.append("; [").append(v).append("]");
            });
            if (!addArgs.isEmpty()) {
                sb.append("; args-add=").append(addArgs);
            }
            if (!removeArgs.isEmpty()) {
                sb.append("; args-del=").append(removeArgs);
            }
            sb.append("; properties=").append(orderedExpectedProperties());
            return sb.toString();
        }
    }

    private static TestSpec.Builder testSpec() {
        return new TestSpec.Builder();
    }

    private static ToolProvider configureOnlyJPackage(OperatingSystem os) {
        return new Main.Provider(() -> {
            return new CliBundlingEnvironment() {

                @Override
                public Optional<BundlingOperationDescriptor> defaultOperation() {
                    return bundlingEnv.defaultOperation();
                }

                @Override
                public void createBundle(BundlingOperationDescriptor descriptor, Options cmdline) {
                    bundlingEnv.createBundle(descriptor, cmdline.copyWithParent(EXIT_AFTER_CONFIGURATION_PHASE));
                }

                private final CliBundlingEnvironment bundlingEnv = JPackageMockUtils.createBundlingEnvironment(os);

                private final static Options EXIT_AFTER_CONFIGURATION_PHASE = Options.of(Map.of(
                        StandardOption.EXIT_AFTER_CONFIGURATION_PHASE, Boolean.TRUE));
            };
        }, os);
    }

    private static CannedFormattedString cannedFormattedString(String key, Object ... args) {
        return new CannedFormattedString(I18N::format, key, args);
    }

    private static Path createMacAppImageMock(String version) {
        Objects.requireNonNull(version);

        final var appImageRoot = TKit.createTempDirectory("appimage");

        try {
            new AppImageFile(
                    "foo",
                    Optional.of("org.foo.Hello"),
                    version,
                    false,
                    Map.of("foo", Map.of())).save(appImageRoot);

            var macBundle = new MacBundle(appImageRoot);

            Files.createDirectories(macBundle.macOsDir());

            createXml(macBundle.infoPlistFile(), xml -> {
                writePList(xml, toXmlConsumer(() -> {
                    writeDict(xml, toXmlConsumer(() -> {
                        writeString(xml, "CFBundleIdentifier", "bar");
                        writeString(xml, "CFBundleName", "bar");
                        writeString(xml, "NSHumanReadableCopyright", "Copyright");
                        writeString(xml, "CFBundleShortVersionString", version);
                        writeString(xml, "CFBundleVersion", version);
                        writeString(xml, "LSApplicationCategoryType", "utilities");
                    }));
                }));
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return appImageRoot;
    }
}
