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
package jdk.jpackage.internal.cli;

import static jdk.jpackage.internal.cli.StandardBundlingOperation.LINUX;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.MACOS;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.WINDOWS;
import static jdk.jpackage.internal.model.AppImageBundleType.LINUX_APP_IMAGE;
import static jdk.jpackage.internal.model.AppImageBundleType.WIN_APP_IMAGE;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.model.AppImageBundleType;
import jdk.jpackage.internal.model.BundleType;
import jdk.jpackage.internal.model.BundleVersion;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class VersionValidatorTest {

    @ParameterizedTest
    @MethodSource
    void test(TestSpec test) {
        test.run();
    }

    record TestSpec(String version, BundleType bundleType, Optional<CannedException> expected) {

        TestSpec {
            Objects.requireNonNull(version);
            Objects.requireNonNull(bundleType);
            Objects.requireNonNull(expected);
        }

        void run() {

            var validator = VersionValidator.create(bundleType);

            var result = validator.validate(BundleVersion.of(version));

            assertEquals(expected, result);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            sb.append(String.format("[%s] %s", version, bundleType));
            expected.ifPresent(v -> {
                sb.append(": ").append(v.toString());
            });

            return sb.toString();
        }

        static final class Builder {

            Builder version(String v) {
                version = v;
                return this;
            }

            Builder bundleType(BundleType v) {
                bundleType = v;
                return this;
            }

            Builder error(CannedFormattedMessage v) {
                error = v;
                return this;
            }

            Builder advice(CannedFormattedMessage v) {
                advice = v;
                return this;
            }

            String version() {
                return version;
            }

            BundleType bundleType() {
                return bundleType;
            }

            Builder adviceForBundleType() {
                return advice(Optional.ofNullable(bundleType).map(type -> {
                    return switch (type) {
                        case AppImageBundleType appImageType -> {
                            yield switch (appImageType) {
                                case LINUX_APP_IMAGE -> null;
                                case MAC_APP_IMAGE -> MAC_ADVICE;
                                case WIN_APP_IMAGE -> WIN_ADVICE;
                            };
                        }
                        case StandardPackageType pkgType -> {
                            yield switch (pkgType) {
                                case WIN_EXE, WIN_MSI -> MSI_ADVICE;
                                case MAC_DMG, MAC_PKG -> MAC_ADVICE;
                                case LINUX_DEB -> DEB_ADVICE;
                                case LINUX_RPM -> RPM_ADVICE;
                            };
                        }
                        case PackageType pkgType -> null;
                    };
                }).orElse(null));
            }

            Builder majorComponentOutOfScope() {
                return adviceForBundleType().error(outOfRangeComponent(InvalidVersionComponent.MAJOR, version));
            }

            Builder minorComponentOutOfScope() {
                return adviceForBundleType().error(outOfRangeComponent(InvalidVersionComponent.MINOR, version));
            }

            Builder buildComponentOutOfScope() {
                return adviceForBundleType().error(outOfRangeComponent(InvalidVersionComponent.BUILD, version));
            }

            Builder revisionComponentOutOfScope() {
                return adviceForBundleType().error(outOfRangeComponent(InvalidVersionComponent.REVISION, version));
            }

            TestSpec create() {
                return new TestSpec(version, bundleType, Optional.ofNullable(error).map(e -> {
                    return new CannedException(e, Optional.ofNullable(advice));
                }));
            }

            private static CannedFormattedMessage outOfRangeComponent(InvalidVersionComponent component, String version) {

                var dottedVer = DottedVersion.lazy(version);
                return CannedFormattedMessage.build(component.key)
                        .optionValue()
                        .optionName()
                        .bundleTypeName()
                        .str(component.getComponent(dottedVer))
                        .create();
            }

            private enum InvalidVersionComponent {
                MAJOR("error.parameter-not-version.major-out-of-range"),
                MINOR("error.parameter-not-version.minor-out-of-range"),
                BUILD("error.parameter-not-version.build-out-of-range"),
                REVISION("error.parameter-not-version.revision-out-of-range"),
                ;

                InvalidVersionComponent(String key) {
                    this.key = Objects.requireNonNull(key);
                }

                String getComponent(DottedVersion ver) {
                    return ver.getComponents()[ordinal()].toString();
                }

                private final String key;
            }

            private String version;
            private BundleType bundleType;
            CannedFormattedMessage error;
            CannedFormattedMessage advice;
        }

        private final static class Configurator {

            Configurator(String version) {
                this.version = Objects.requireNonNull(version);
            }

            Configurator use(Consumer<TestSpec.Builder> mutator, Iterable<BundleType> scope) {
                for (var type : scope) {
                    cfg.put(type, Optional.ofNullable(mutator).orElse(NOP));
                }
                return this;
            }

            Stream<TestSpec.Builder> purge() {
                var reply = cfg.entrySet().stream().map(e -> {
                    var b = new Builder().version(version).bundleType(e.getKey());
                    e.getValue().accept(b);
                    return b;
                }).toList();
                cfg.clear();
                return reply.stream();
            }

            private final String version;
            private final Map<BundleType, Consumer<TestSpec.Builder>> cfg = new HashMap<>();
        }
    }

    static Collection<TestSpec> test() {

        var testCases = new HashSet<TestSpec>();

        Consumer<TestSpec.Builder> addTestCase = b -> {
            var spec = b.create();
            if (testCases.contains(spec)) {
                throw new IllegalArgumentException(String.format("Duplicated test spec: [%s]", spec));
            }
            testCases.add(spec);
        };

        // Empty version is not allowed.
        with("").use(invalid(), TEST_BUNDLE_TYPES).purge().forEach(addTestCase);

        // Versions valid for all bundles.
        for (var ver : List.of(
                "0.0",
                "0.1",
                "255.255",
                "0.0.0",
                "255.255.65535",
                "0.0.0.0",
                "255.255.65535.65535",
                "1.2",
                "1.2.3",
                "1.2.3.4")) {
            with(ver).use(NOP, TEST_BUNDLE_TYPES).purge().forEach(addTestCase);
        }

        // Windows versions.
        for (var spec : List.<Map.Entry<String, Consumer<TestSpec.Builder>>>of(
                Map.entry("0", mutateIfNot(isOneOf(WIN_APP_IMAGE), invalid())),
                Map.entry("1", mutateIfNot(isOneOf(WIN_APP_IMAGE), invalid())),
                Map.entry("256.01", mutateIfNot(isOneOf(WIN_APP_IMAGE), majorComponentOutOfScope())),
                Map.entry("255.256", mutateIfNot(isOneOf(WIN_APP_IMAGE), minorComponentOutOfScope())),
                Map.entry("255.255.65536", buildComponentOutOfScope()),
                Map.entry("256.256.100", mutateIfNot(isOneOf(WIN_APP_IMAGE), majorComponentOutOfScope())),
                Map.entry("1.256.65536", mutateIfElse(isOneOf(WIN_APP_IMAGE), buildComponentOutOfScope(), minorComponentOutOfScope())),
                Map.entry("1.2.3.65536", revisionComponentOutOfScope()),
                Map.entry("1.2.65536.65536", buildComponentOutOfScope()),
                Map.entry("1.2.3.4.5", invalid()),
                Map.entry("1.2.3-foo", invalid())
        )) {
            with(spec.getKey()).use(spec.getValue(), bundleTypes(WINDOWS)).purge().forEach(addTestCase);
        }

        // macOS versions.
        for (var spec : List.<Map.Entry<String, Consumer<TestSpec.Builder>>>of(
                Map.entry("1.2.3-foo", invalid()),
                Map.entry("0", NOP),
                Map.entry("1", NOP),
                Map.entry("001", NOP)
        )) {
            with(spec.getKey()).use(spec.getValue(), bundleTypes(MACOS)).purge().forEach(addTestCase);
        }

        // Linux versions.
        for (var spec : List.<Map.Entry<String, Consumer<TestSpec.Builder>>>of(
                Map.entry("1.2.3~foo", NOP),
                Map.entry("7a+23^67", mutateIf(isOneOf(LINUX_DEB), invalid())),
                Map.entry("7aa+b23~67", NOP),
                Map.entry("7aa+b23~", NOP),
                Map.entry("7aa+b23~~", NOP),
                Map.entry("7a", NOP),
                Map.entry("a7", mutateIf(isOneOf(LINUX_DEB), invalid())),
                Map.entry("2000:1.2.3:7b", mutateIf(isOneOf(LINUX_RPM), invalid())),
                Map.entry("1.2.3:7b", mutateIfNot(isOneOf(LINUX_APP_IMAGE), invalid()))
        )) {
            with(spec.getKey()).use(spec.getValue(), bundleTypes(LINUX)).purge().forEach(addTestCase);
        }

        // Order test cases for better readability.
        return testCases.stream().sorted(
                Comparator.comparing(TestSpec::version).thenComparing(testSpec -> {
                    return testSpec.bundleType().toString();
                }).thenComparing(testSpec -> {
                    // Can't have the same version, bundle type and different validation result.
                    throw new AssertionError();
                })).toList();
    }

    private static TestSpec.Configurator with(String version) {
        return new TestSpec.Configurator(version);
    }

    private static Consumer<TestSpec.Builder> invalid(CannedFormattedMessage error) {
        Objects.requireNonNull(error);
        return b -> {
            b.adviceForBundleType().error(error);
        };
    }

    private static Consumer<TestSpec.Builder> invalid() {
        return invalid(INVALID_VERSION);
    }

    private static Consumer<TestSpec.Builder> majorComponentOutOfScope() {
        return TestSpec.Builder::majorComponentOutOfScope;
    }

    private static Consumer<TestSpec.Builder> minorComponentOutOfScope() {
        return TestSpec.Builder::minorComponentOutOfScope;
    }

    private static Consumer<TestSpec.Builder> buildComponentOutOfScope() {
        return TestSpec.Builder::buildComponentOutOfScope;
    }

    private static Consumer<TestSpec.Builder> revisionComponentOutOfScope() {
        return TestSpec.Builder::revisionComponentOutOfScope;
    }

    private static Collection<BundleType> bundleTypes(Iterable<BundlingOperationOptionScope> scope) {
        return StreamSupport.stream(scope.spliterator(), false)
                .map(BundlingOperationOptionScope::descriptor)
                .map(descriptor -> {
                    return StandardBundlingOperation.valueOf(descriptor).orElseThrow().bundleType();
                }).distinct().toList();
    }

    private static Consumer<TestSpec.Builder> mutateIfElse(
            Predicate<TestSpec.Builder> pred, Consumer<TestSpec.Builder> ifBranch, Consumer<TestSpec.Builder> elseBranch) {

        Objects.requireNonNull(pred);
        Objects.requireNonNull(ifBranch);
        Objects.requireNonNull(elseBranch);

        return b -> {
            if (pred.test(b)) {
                ifBranch.accept(b);
            } else {
                elseBranch.accept(b);
            }
        };
    }

    private static Consumer<TestSpec.Builder> mutateIf(Predicate<TestSpec.Builder> pred, Consumer<TestSpec.Builder> mutator) {
        return mutateIfElse(pred, mutator, NOP);
    }

    private static Consumer<TestSpec.Builder> mutateIfNot(Predicate<TestSpec.Builder> pred, Consumer<TestSpec.Builder> mutator) {
        return mutateIfElse(pred, NOP, mutator);
    }

    private static Predicate<TestSpec.Builder> isOneOf(BundleType type) {
        Objects.requireNonNull(type);
        return b -> {
            return b.bundleType() == type;
        };
    }

    private enum DummyPackageType implements PackageType {
        DUMMY;

        @Override
        public String label() {
            throw new AssertionError();
        }
    };

    private static final Collection<BundleType> TEST_BUNDLE_TYPES = Stream.concat(
            Stream.of(StandardBundlingOperation.values()).map(StandardBundlingOperation::bundleType),
            Stream.of(DummyPackageType.values())
    ).distinct().toList();

    private static final Consumer<TestSpec.Builder> NOP = _ -> {};

    private static final CannedFormattedMessage INVALID_VERSION =
            CannedFormattedMessage.build("error.parameter-not-version").optionValue().optionName().bundleTypeName().create();

    private static final CannedFormattedMessage RPM_ADVICE =
            CannedFormattedMessage.build("error.parameter-not-rpm-version.advice").bundleTypeName().create();

    private static final CannedFormattedMessage DEB_ADVICE =
            CannedFormattedMessage.build("error.parameter-not-deb-version.advice").bundleTypeName().create();

    private static final CannedFormattedMessage MSI_ADVICE =
            CannedFormattedMessage.build("error.parameter-not-msi-version.advice").bundleTypeName().create();

    private static final CannedFormattedMessage WIN_ADVICE =
            CannedFormattedMessage.build("error.parameter-not-win-version.advice").bundleTypeName().create();

    private static final CannedFormattedMessage MAC_ADVICE =
            CannedFormattedMessage.build("error.parameter-not-mac-version.advice").bundleTypeName().create();
}
