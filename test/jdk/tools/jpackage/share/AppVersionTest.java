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


import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.XmlUtils.createXml;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import static jdk.jpackage.test.JPackageCommand.DEFAULT_VERSION;
import static jdk.jpackage.test.JPackageCommand.normalizeDerivedVersion;
import static jdk.jpackage.test.JPackageCommand.RuntimeImageType.RUNTIME_TYPE_FAKE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.util.MacBundle;
import jdk.jpackage.internal.util.RuntimeReleaseFile;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.ConfigurationTarget;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageCommand.StandardAssert;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage application version testing
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror AppVersionTest.java
 * @run main/othervm/timeout=2880 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppVersionTest
 */

public final class AppVersionTest {

    @Test
    @ParameterSupplier
    public static void testApp(AppTestSpec testSpec) {

        var moduleVersionSource = testSpec.findVersionSource(ModuleVersionSource.class);

        ConfigurationTarget cfg;
        if (testSpec.isImagePackageType()) {
            var cmd = moduleVersionSource.map(ModuleVersionSource::appDesc)
                    .map(JPackageCommand::helloAppImage)
                    .orElseGet(JPackageCommand::helloAppImage);
            cfg = new ConfigurationTarget(cmd);
        } else {
            var nativeTest = new PackageTest().forTypes(testSpec.spec().expected().keySet());
            moduleVersionSource
                    .map(ModuleVersionSource::appDesc)
                    .ifPresentOrElse(nativeTest::configureHelloApp, nativeTest::configureHelloApp);

            cfg = new ConfigurationTarget(nativeTest);
        }

        cfg.addInitializer(JPackageCommand::setFakeRuntime);

        cfg.addInitializer(testSpec::applyTo);

        cfg.cmd().ifPresent(JPackageCommand::executeAndAssertHelloAppImageCreated);

        testSpec.validateVersion(cfg);

        cfg.test().ifPresent(pkg -> {
            pkg.run(testSpec.spec().packageTestActions());
        });
    }

    @Test
    @ParameterSupplier
    @ParameterSupplier(value = "testMacPredefinedRuntimeBundle", ifOS = OperatingSystem.MACOS)
    public static void testRuntime(RuntimeTestSpec testSpec) {

        var predefinedRuntimeDir = Slot.<Path>createEmpty();
        new PackageTest()
        .forTypes(testSpec.spec().expected().keySet())
        .addRunOnceInitializer(() -> {
            predefinedRuntimeDir.set(testSpec.createRuntime());
        })
        .addInitializer(cmd -> {
            cmd.removeArgumentWithValue("--input").setArgumentValue("--runtime-image", predefinedRuntimeDir.get());
        })
        .addInitializer(testSpec::applyTo)
        .mutate(test -> {
            testSpec.validateVersion(new ConfigurationTarget(test));
        })
        .run(testSpec.spec().packageTestActions());
    }

    public static Collection<?> testApp() {

        List<AppTestSpec> testCases = new ArrayList<>();

        for (var modular : List.of(true, false)) {
            String appDesc;
            if (modular) {
                appDesc = "com.other/com.other.Hello";
            } else {
                appDesc = "Hello";
            }

            // Default version.
            AppTestSpec.create(appDesc, TestSpec.build().expectDefaultVersion(), testCases::add);

            // Pick version from the command line.
            AppTestSpec.create(appDesc, TestSpec.build().versionFromCmdline("3.1"), testCases::add);
        }

        // Pick version from the modular jar.
        AppTestSpec.create(TestSpec.build()
                .versionFromAppModule("com.other/com.other.Hello@3.10.16"), testCases::add);

        // Pick version from the command line, ignore version of the modular jar.
        AppTestSpec.create(TestSpec.build()
                .versionFromAppModule("com.other/com.other.Hello@3.10.18")
                .versionFromCmdline("7.5.81"), testCases::add);

        // Pick version from the modular jar. Apply package-specific normalization.
        for (var ver : List.of(
                "30.10.17.204.899-foo"
        )) {
            var versionSource = new ModuleVersionSource("com.other/com.other.Hello@" + ver);

            var builder = TestSpec.build().versionSource(versionSource);
            for (var e : TestSpec.Builder.getExpectedVersions(versionSource).entrySet()) {
                builder.withTypes(e.getKey()).expect(e.getValue());
            }

            AppTestSpec.create(builder, testCases::add);
        }

        if (TKit.isOSX()) {
            // Ensure "0.1" is a valid version on macOS.
            AppTestSpec.create("Hello", TestSpec.build()
                    .versionFromCmdline("0.1"), testCases::add);

            // Ensure "1.2.3.4.5" is a valid version on macOS.
            AppTestSpec.create("Hello", TestSpec.build()
                    .versionFromCmdline("1.2.3.4.5"), testCases::add);
        }

        return testCases.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public static Collection<?> testRuntime() {

        List<RuntimeTestSpec> testCases = new ArrayList<>();

        // Default version.
        RuntimeTestSpec.create(TestSpec.build().expectDefaultVersion(), testCases::add);

        // Pick version from the command line.
        RuntimeTestSpec.create(TestSpec.build().versionFromCmdline("3.1"), testCases::add);

        // Invalid versions.
        for (var ver : List.of("foo", "", "17.21.3+foo")) {
            RuntimeTestSpec.create(TestSpec.build().versionFromReleaseFile(ver).expectDefaultVersion(), testCases::add);
        }

        // Valid version values (see java.lang.Runtime.Version javadoc and https://openjdk.org/jeps/223)
        for (var suffix : jep223VersionSuffixes()) {
            for (var vnum : List.of("17", "17.1", "17.1.2", "17.1.2.3", "17.1.2.3.5")) {
                var ver = new RuntimeReleaseFileVersionSource(vnum + suffix);

                var builder = TestSpec.build().versionSource(ver);
                for (var e : TestSpec.Builder.getExpectedVersions(ver).entrySet()) {
                    builder.withTypes(e.getKey()).expect(e.getValue());
                }

                RuntimeTestSpec.create(builder, testCases::add);
            }
        }

        return testCases.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public static Collection<?> testMacPredefinedRuntimeBundle() {

        List<RuntimeTestSpec> testCases = new ArrayList<>();

        var appendTestCases = skipImagePackageType(testSpec -> {
            for (var runtimeType : List.of(
                    RuntimeType.MAC_BUNDLE_PLIST_FILE_MALFORMED,
                    RuntimeType.MAC_BUNDLE_PLIST_WITHOUT_VERSION
            )) {
                testCases.add(new RuntimeTestSpec(runtimeType, testSpec));
            }
        });

        // Invalid version.
        TestSpec.build().versionFromReleaseFile("foo").expectDefaultVersion().create(appendTestCases);

        // Valid versions.
        for (var suffix : List.of("", "-foo")) {
            for (var vnum : List.of("17", "17.1", "17.1.2", "17.1.2.3")) {
                var ver = new RuntimeReleaseFileVersionSource(vnum + suffix);

                var builder = TestSpec.build().versionSource(ver);
                var allBundleTypes = TestSpec.Builder.getExpectedVersions(ver);
                for (var bundleType : PackageType.MAC) {
                    builder.withTypes(bundleType).expect(Objects.requireNonNull(allBundleTypes.get(bundleType)));
                }

                builder.create(appendTestCases);
            }
        }

        return testCases.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    private static List<String> jep223VersionSuffixes() {
        var suffixes = new HashSet<String>();
        for (var pre : List.of("", "-ea")) {
            for (var build : List.of("", "+5678")) {
                for (var opt : List.of("", "-foo", "-12.UZ3")) {
                    if (pre.isEmpty() && build.isEmpty() && !opt.isEmpty()) {
                        suffixes.add("+" + opt);
                    } else {
                        suffixes.add(pre + build + opt);
                    }
                }
            }
        }
        return suffixes.stream().sorted().peek(suffix -> {
            // Validate version suffixes.
            Runtime.Version.parse("11" + suffix);
        }).toList();
    }

    enum Message implements CannedFormattedString.Spec {
        VERSION_FROM_MODULE("message.module-version", "version", "module"),
        VERSION_FROM_RELEASE_FILE("message.release-version", "version"),
        VERSION_NORMALIZED("message.version-normalized", "version", "version"),
        ;

        Message(String key, Object ... args) {
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

    sealed interface VersionSource {
        String version();

        VersionSource copyWithVersion(String v);
    }

    enum DefaultVersionSource implements VersionSource {
        INSTANCE;

        @Override
        public String version() {
            return DEFAULT_VERSION;
        }

        @Override
        public VersionSource copyWithVersion(String v) {
            return this;
        }
    }

    record ModuleVersionSource(String appDesc) implements VersionSource {

        ModuleVersionSource {
            Objects.requireNonNull(appDesc);
            if (JavaAppDesc.parse(appDesc).moduleVersion() == null) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String version() {
            return JavaAppDesc.parse(appDesc).moduleVersion();
        }

        @Override
        public VersionSource copyWithVersion(String v) {
            return new ModuleVersionSource(moduleName() + "@" + Objects.requireNonNull(v));
        }

        String moduleName() {
            return JavaAppDesc.parse(appDesc).moduleName();
        }

        @Override
        public String toString() {
            return appDesc;
        }
    }

    record CmdlineVersionSource(String version) implements VersionSource {

        CmdlineVersionSource {
            Objects.requireNonNull(version);
        }

        @Override
        public VersionSource copyWithVersion(String v) {
            return new CmdlineVersionSource(v);
        }

        @Override
        public String toString() {
            return String.format("--app-version=[%s]", version);
        }
    }

    record RuntimeReleaseFileVersionSource(String version) implements VersionSource {

        RuntimeReleaseFileVersionSource {
            Objects.requireNonNull(version);
        }

        @Override
        public VersionSource copyWithVersion(String v) {
            return new RuntimeReleaseFileVersionSource(v);
        }

        @Override
        public String toString() {
            return String.format("JAVA_VERSION=[%s]", version);
        }
    }

    record Expected(String version, List<CannedFormattedString> messages) {

        Expected {
            Objects.requireNonNull(version);
            Objects.requireNonNull(messages);
        }

        void applyTo(JPackageCommand cmd) {
            cmd.version(version).validateOutput(Message.class, validator -> {
                validator.matchTimestamps().stripTimestamps();
            }, messages);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append(version);
            if (!messages.isEmpty()) {
                sb.append("; ").append(messages);
            }
            return sb.toString();
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            Expected create() {
                return new Expected(version, List.copyOf(messages));
            }

            Builder version(String v) {
                version = v;
                return this;
            }

            Builder messages(List<CannedFormattedString> v) {
                messages.addAll(v);
                return this;
            }

            Builder messages(CannedFormattedString... v) {
                return messages(List.of(v));
            }

            Builder message(Message message, Object ... args) {
                return messages(message.asCannedFormattedString(args));
            }

            private String version;
            private final List<CannedFormattedString> messages = new ArrayList<>();
        }
    }

    record TestSpec(Collection<VersionSource> versions, Map<PackageType, Expected> expected) {

        TestSpec {
            Objects.requireNonNull(versions);
            Objects.requireNonNull(expected);

            if (expected.isEmpty()) {
                throw new IllegalArgumentException();
            }

            if (expected.keySet().contains(PackageType.IMAGE) && !Collections.disjoint(expected.keySet(), PackageType.NATIVE)) {
                throw new IllegalArgumentException("Mixing of native and app image packaging");
            }

            if (expected.keySet().stream().map(PackageType::os).distinct().count() != 1) {
                throw new IllegalArgumentException("All package types must be for the same OS");
            }
        }

        <T extends VersionSource> Optional<T> findVersionSource(Class<T> versionSourceType) {
            Objects.requireNonNull(versionSourceType);
            return versions.stream().filter(versionSourceType::isInstance).map(versionSourceType::cast).findFirst();
        }

        void applyTo(JPackageCommand cmd) {
            Objects.requireNonNull(cmd);
            findVersionSource(CmdlineVersionSource.class).ifPresent(ver -> {
                cmd.setArgumentValue("--app-version", ver.version());
            });
            expected.get(cmd.packageType()).applyTo(cmd);
        }

        void validateVersion(ConfigurationTarget cfg) {
            cfg.cmd().ifPresent(cmd -> {
                var actualVersion = AppImageFile.load(cmd.outputBundle()).version();
                TKit.assertEquals(expected.get(cmd.packageType()).version(), actualVersion, "Check application version");
            });
            cfg.test().ifPresent(test -> {
                expected.entrySet().forEach(e -> {
                    nativeBundleVersionPropertyName(e.getKey()).ifPresent(propertyName -> {
                        test.forTypes(e.getKey(), _ -> {
                            test.addBundlePropertyVerifier(propertyName, e.getValue().version());
                        });
                    });
                });
            });

            if (os() == OperatingSystem.MACOS) {
                cfg.addInstallVerifier(cmd -> {
                    final var bundleRoot = cmd.isImagePackageType() ? cmd.outputBundle()
                            : cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());
                    var plist = MacHelper.readPListFromAppImage(bundleRoot);
                    var expectedVersion = expected.get(cmd.packageType()).version();
                    TKit.assertEquals(expectedVersion, plist.queryValue("CFBundleVersion"),
                            String.format("Check the value of '%s' property in [%s] bundle",
                            "CFBundleVersion", bundleRoot));
                    TKit.assertEquals(DottedVersion.lazy(expectedVersion).trim(3).toComponentsString(),
                            plist.queryValue("CFBundleShortVersionString"),
                            String.format("Check the value of '%s' property in [%s] bundle",
                            "CFBundleShortVersionString", bundleRoot));
                });
            }
        }

        boolean isImagePackageType() {
            return expected.keySet().contains(PackageType.IMAGE);
        }

        Action[] packageTestActions() {
            if (os() == OperatingSystem.MACOS) {
                return Action.CREATE_AND_UNPACK;
            } else {
                return new Action[] {Action.CREATE};
            }
        }

        OperatingSystem os() {
            return expected.keySet().iterator().next().os();
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            switch (versions.size()) {
                case 0 -> {
                }
                case 1 -> {
                    sb.append(versions.iterator().next()).append("; ");
                }
                default -> {
                    sb.append("versions=").append(versions).append("; ");
                }
            }

            sb.append("expect=");
            if (expected.values().stream().distinct().count() == 1) {
                sb.append(expected.keySet().stream().sorted().toList()).append(":");
                sb.append(expected.values().iterator().next());
            } else {
                sb.append('[').append(expected).append(']');
            }

            return sb.toString();
        }

        private static Optional<String> nativeBundleVersionPropertyName(PackageType type) {
            switch (type) {
                case LINUX_DEB, LINUX_RPM -> {
                    return Optional.of("Version");
                }
                case WIN_MSI -> {
                    return Optional.of("ProductVersion");
                }
                default -> {
                    return Optional.empty();
                }
            }
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            Builder() {
            }

            Builder(Builder other) {
                currentTypes = other.currentTypes;
                currentExpectedVersion = other.currentExpectedVersion;
                pendingCommit = other.pendingCommit;
                versions.addAll(other.versions);
                expected.putAll(other.expected);
            }

            Builder copy() {
                return new Builder(this);
            }

            void create(Consumer<TestSpec> sink) {
                Objects.requireNonNull(sink);
                if (pendingCommit) {
                    copy().expect(expectedVersion()).commit().create(sink);
                } else {
                    var types = expected.keySet();
                    var copiedVersions = List.copyOf(versions);
                    if (types.contains(PackageType.IMAGE) && !Collections.disjoint(types, PackageType.NATIVE)) {
                        sink.accept(new TestSpec(copiedVersions, Map.of(PackageType.IMAGE, expected.get(PackageType.IMAGE))));
                        var copy = new HashMap<>(expected);
                        copy.remove(PackageType.IMAGE);
                        sink.accept(new TestSpec(copiedVersions, copy));
                    } else {
                        new TestSpec(copiedVersions, Map.copyOf(expected));
                    }
                }
            }

            Builder versionSource(VersionSource v) {
                if (Objects.requireNonNull(v) == DefaultVersionSource.INSTANCE) {
                    throw new IllegalArgumentException();
                }
                versions.add(v);
                pendingCommit = true;
                return this;
            }

            Builder versionFromCmdline(String v) {
                return versionSource(new CmdlineVersionSource(v));
            }

            Builder versionFromAppModule(String v) {
                return versionSource(new ModuleVersionSource(v));
            }

            Builder versionFromReleaseFile(String v) {
                return versionSource(new RuntimeReleaseFileVersionSource(v));
            }

            Builder expect(VersionSource v) {
                currentExpectedVersion = v;
                pendingCommit = true;
                return this;
            }

            Builder expectDefaultVersion() {
                return expect(DefaultVersionSource.INSTANCE);
            }

            Builder expectVersionFromCmdline(String v) {
                return expect(new CmdlineVersionSource(v));
            }

            Builder expectVersionFromAppModule(String appDesc) {
                return expect(new ModuleVersionSource(appDesc));
            }

            Builder expectVersionFromReleaseFile(String v) {
                return expect(new RuntimeReleaseFileVersionSource(v));
            }

            Builder withTypes(Set<PackageType> types) {
                if (types.isEmpty()) {
                    types = ALL_TYPES;
                }

                if (!currentTypes.equals(types)) {
                    commit();
                    currentTypes = types;
                }

                return this;
            }

            Builder withTypes(PackageType ... types) {
                return withTypes(Set.of(types));
            }

            static Map<PackageType, VersionSource> getExpectedVersions(VersionSource versionSource) {
                var map = new HashMap<PackageType, VersionSource>(normalizeDerivedVersion(versionSource.version()).entrySet()
                        .stream()
                        .collect(toUnmodifiableMap(Map.Entry::getKey, e -> {
                            return versionSource.copyWithVersion(e.getValue());
                         })
                ));

                ALL_TYPES.forEach(type -> {
                    map.putIfAbsent(type, DefaultVersionSource.INSTANCE);
                });

                return map;
            }

            private VersionSource expectedVersion() {
                return Optional.ofNullable(currentExpectedVersion).or(() -> {
                    return versions.stream().filter(CmdlineVersionSource.class::isInstance).findFirst();
                }).or(() -> {
                    if (versions.size() == 1) {
                        return Optional.of(versions.getFirst());
                    } else {
                        return Optional.empty();
                    }
                }).orElseThrow(IllegalStateException::new);
            }

            private Builder commit() {
                pendingCommit = false;

                if (versions.isEmpty() && currentExpectedVersion == null) {
                    // Nothing to commit.
                    return this;
                }

                var filteredTypes = normalize(currentTypes);
                if (filteredTypes.isEmpty()) {
                    // Version configuration is not supported on the current OS.
                    return this;
                }

                VersionSource expectedVersion = expectedVersion();

                VersionSource versionSource;
                if (expectedVersion == DefaultVersionSource.INSTANCE) {
                    versionSource = expectedVersion;
                } else {
                    versionSource = versions.stream().filter(expectedVersion.getClass()::isInstance).findFirst().orElseThrow();
                }

                var expectedBuilder = Expected.build().version(expectedVersion.version());
                switch (versionSource) {
                    case ModuleVersionSource ver -> {
                        expectedBuilder.message(Message.VERSION_FROM_MODULE, ver.version(), ver.moduleName());
                    }
                    case RuntimeReleaseFileVersionSource ver -> {
                        expectedBuilder.message(Message.VERSION_FROM_RELEASE_FILE, ver.version());
                    }
                    default -> {
                        // NOP
                    }
                }

                if (!versionSource.version().equals(expectedVersion.version())) {
                    expectedBuilder.message(Message.VERSION_NORMALIZED, expectedVersion.version(), versionSource.version());
                }

                var expectedValue = expectedBuilder.create();
                filteredTypes.forEach(type -> {
                    expected.put(type, expectedValue);
                });

                return this;
            }

            private static Set<PackageType> normalize(Collection<PackageType> types) {
                return types.stream().filter(type -> {
                    return type.os() == OperatingSystem.current();
                })
                // Filter out "exe" packaging as it is a duplicate of "msi" packaging and
                // the testing lib can't validate properties of embedded msi file.
                .filter(Predicate.isEqual(PackageType.WIN_EXE).negate())
                .map(type -> {
                    if (type.isAppImage()) {
                        return PackageType.IMAGE;
                    } else {
                        return type;
                    }
                }).collect(toUnmodifiableSet());
            }

            private Set<PackageType> currentTypes = ALL_TYPES;
            private VersionSource currentExpectedVersion;
            private boolean pendingCommit;
            private final List<VersionSource> versions = new ArrayList<>();
            private final Map<PackageType, Expected> expected = new HashMap<>();

            private static final Set<PackageType> ALL_TYPES = Set.of(PackageType.values());
        }
    }

    record AppTestSpec(String appDesc, TestSpec spec) {

        AppTestSpec {
            Objects.requireNonNull(appDesc);
            Objects.requireNonNull(spec);
            spec.findVersionSource(ModuleVersionSource.class).map(ModuleVersionSource::appDesc).ifPresent(moduleAppDesc -> {
                if (!moduleAppDesc.equals(appDesc)) {
                    throw new IllegalArgumentException();
                }
            });
        }

        AppTestSpec(TestSpec spec) {
            this(spec.findVersionSource(ModuleVersionSource.class).orElseThrow().appDesc(), spec);
        }

        static void create(String appDesc, TestSpec.Builder specBuilder, Consumer<AppTestSpec> sink) {
            Objects.requireNonNull(appDesc);
            Objects.requireNonNull(sink);
            specBuilder.create(spec -> {
                sink.accept(new AppTestSpec(appDesc, spec));
            });
        }

        static void create(TestSpec.Builder specBuilder, Consumer<AppTestSpec> sink) {
            Objects.requireNonNull(sink);
            specBuilder.create(spec -> {
                sink.accept(new AppTestSpec(spec));
            });
        }

        <T extends VersionSource> Optional<T> findVersionSource(Class<T> versionSourceType) {
            return spec.findVersionSource(versionSourceType);
        }

        void applyTo(JPackageCommand cmd) {
            spec.applyTo(cmd);
        }

        void validateVersion(ConfigurationTarget cfg) {
            spec.validateVersion(cfg);
        }

        boolean isImagePackageType() {
            return spec.isImagePackageType();
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            if (spec.findVersionSource(ModuleVersionSource.class).map(ModuleVersionSource::appDesc).filter(appDesc::equals).isEmpty()) {
                sb.append("app-desc=").append(appDesc()).append("; ");
            }
            sb.append(spec);
            return sb.toString();
        }
    }

    /**
     * Type of the predefined runtime.
     */
    enum RuntimeType {
        /**
         * A directory with the standard Java runtime structure.
         */
        IMAGE,
        /**
         * macOS bundle with valid Info.plist file.
         */
        MAC_BUNDLE,
        /**
         * macOS bundle with malformed Info.plist file.
         */
        MAC_BUNDLE_PLIST_FILE_MALFORMED,
        /**
         * macOS bundle with Info.plist file without version.
         */
        MAC_BUNDLE_PLIST_WITHOUT_VERSION,
        ;
    }

    record RuntimeTestSpec(RuntimeType type, TestSpec spec) {

        RuntimeTestSpec {
            Objects.requireNonNull(type);
            Objects.requireNonNull(spec);
            if (spec.isImagePackageType()) {
                throw new IllegalArgumentException();
            }
            if (type == RuntimeType.MAC_BUNDLE && spec.os() != OperatingSystem.MACOS) {
                throw new IllegalArgumentException("Bundle runtime is supported for macOS native packaging only");
            }
        }

        static void create(TestSpec.Builder specBuilder, Consumer<RuntimeTestSpec> sink) {
            Objects.requireNonNull(sink);
            specBuilder.create(skipImagePackageType(spec -> {
                if (spec.os() != OperatingSystem.MACOS) {
                    sink.accept(new RuntimeTestSpec(RuntimeType.IMAGE, spec));
                } else {
                    sink.accept(new RuntimeTestSpec(RuntimeType.IMAGE, spec));

                    if (spec.findVersionSource(CmdlineVersionSource.class).isPresent()) {
                        // Disable "AppVersionTest.testRuntime(mac_bundle; versions=[--app-version=[3.1], plist=[1.22.333]]; expect=[MAC_DMG, MAC_PKG]:3.1)" test for now
                        // as it will fail because jpackage halfway ignores the "--app-version"
                        // and any option in general that makes its way into the Info.plist file.
                        // When building a runtime from the predefined runtime bundle,
                        // jpackage copies the Info.plist file from the predefined runtime bundle into the output bundle verbatim.
                        return;
                    }

                    var plistVersionSource = new InheritPListVersionSource(MAC_PREDEFINED_RUNTIME_BUNDLE_VERSION);
                    var specBuilderCopy = specBuilder.copy().versionSource(plistVersionSource).withTypes(PackageType.ALL_MAC);
                    if (spec.findVersionSource(CmdlineVersionSource.class).isEmpty()) {
                        specBuilderCopy.expect(plistVersionSource);
                    }
                    specBuilderCopy.create(skipImagePackageType(augmentedSpec -> {
                        sink.accept(new RuntimeTestSpec(RuntimeType.MAC_BUNDLE, augmentedSpec));
                    }));
                }
            }));
        }

        <T extends VersionSource> Optional<T> findVersionSource(Class<T> versionSourceType) {
            return spec.findVersionSource(versionSourceType);
        }

        void applyTo(JPackageCommand cmd) {
            switch (type) {
                case MAC_BUNDLE_PLIST_FILE_MALFORMED, MAC_BUNDLE_PLIST_WITHOUT_VERSION -> {
                    // Don't validate signature of the output bundle. If the Info.plist file is malformed, it will fail with the error:
                    //
                    // [22:59:25.374] TRACE: Command [/usr/bin/codesign --verify --deep --strict --verbose=2 RuntimeAppVersionTest.jdk](6) exited with exit code 3 and the following output:
                    // [22:59:25.375] TRACE: 2026-03-05 22:59:25.353 codesign[11351:61024] There was an error parsing the Info.plist for the bundle at URL <0x7f81e140ae70>: NSCocoaErrorDomain - 3840
                    // [22:59:25.376] TRACE: 2026-03-05 22:59:25.370 codesign[11351:61024] There was an error parsing the Info.plist for the bundle at URL <0x7f81e1413b90>: NSCocoaErrorDomain - 3840
                    // [22:59:25.376] TRACE: --prepared:RuntimeAppVersionTest.jdk/Contents/MacOS/libjli.dylib
                    // [22:59:25.377] TRACE: --validated:RuntimeAppVersionTest.jdk/Contents/MacOS/libjli.dylib
                    //
                    cmd.excludeStandardAsserts(StandardAssert.MAC_BUNDLE_UNSIGNED_SIGNATURE);
                    // This one will also fail.
                    cmd.excludeStandardAsserts(StandardAssert.MAC_RUNTIME_PLIST_JDK_KEY);
                }
                default -> {
                    // NOP
                }
            }
            spec.applyTo(cmd);
        }

        void validateVersion(ConfigurationTarget cfg) {
            switch (type) {
                case MAC_BUNDLE_PLIST_FILE_MALFORMED, MAC_BUNDLE_PLIST_WITHOUT_VERSION -> {
                    cfg.addInstallVerifier(cmd -> {
                        final var bundleRoot = cmd.isImagePackageType() ? cmd.outputBundle()
                                : cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());
                        final var infoPlist = new MacBundle(bundleRoot).infoPlistFile();
                        TKit.assertFileExists(infoPlist);
                        TKit.trace(String.format("Bundle version property in [%s] file is unavailable. Skip validation", infoPlist));
                    });
                }
                default -> {
                    spec.validateVersion(cfg);
                }
            }
        }

        Path createRuntime() throws IOException {
            return createRuntime(type, findVersionSource(RuntimeReleaseFileVersionSource.class).map(VersionSource::version));
        }

        @Override
        public String toString() {
            return new StringBuilder().append(type.name().toLowerCase()).append("; ").append(spec).toString();
        }

        private record InheritPListVersionSource(String version) implements VersionSource {

            InheritPListVersionSource {
                Objects.requireNonNull(version);
            }

            @Override
            public VersionSource copyWithVersion(String v) {
                return new InheritPListVersionSource(v);
            }

            @Override
            public String toString() {
                return String.format("plist=[%s]", version);
            }
        }

        private Path createRuntime(RuntimeType type, Optional<String> releaseFileVersion) throws IOException {
            Objects.requireNonNull(type);
            Objects.requireNonNull(releaseFileVersion);

            Path predefinedRuntimeDir;
            Path runtimeDir = switch (type) {
                case IMAGE -> {
                    predefinedRuntimeDir = JPackageCommand.createInputRuntimeImage(RUNTIME_TYPE_FAKE);
                    yield predefinedRuntimeDir;
                }
                case MAC_BUNDLE -> {
                    releaseFileVersion.ifPresent(ver -> {
                        if (ver.equals(MAC_PREDEFINED_RUNTIME_BUNDLE_VERSION)) {
                            // The value of `JAVA_VERSION` property in the "release" file of the runtime
                            // and the value of the `CFBundleVersion` property in the plist file should be different
                            // to test corner cases of version picking logic.
                            throw new IllegalArgumentException();
                        }
                    });

                    // Create macOS bundle with `MAC_PREDEFINED_RUNTIME_BUNDLE_VERSION` version.
                    // The idea is to have different values of `JAVA_VERSION` property in the "release" file
                    // of the runtime and the value of the `CFBundleVersion` property in the plist file.
                    predefinedRuntimeDir = MacHelper.buildRuntimeBundle().type(RUNTIME_TYPE_FAKE).mutator(cmd -> {
                        cmd.setArgumentValue("--app-version", MAC_PREDEFINED_RUNTIME_BUNDLE_VERSION);
                    }).create();
                    yield MacBundle.fromPath(predefinedRuntimeDir).orElseThrow().homeDir();
                }
                case MAC_BUNDLE_PLIST_FILE_MALFORMED, MAC_BUNDLE_PLIST_WITHOUT_VERSION -> {
                    predefinedRuntimeDir = MacHelper.buildRuntimeBundle().type(RUNTIME_TYPE_FAKE).create();
                    var plistFile = new MacBundle(predefinedRuntimeDir).infoPlistFile();

                    switch (type) {
                        case MAC_BUNDLE_PLIST_FILE_MALFORMED -> {
                            TKit.trace(String.format("Create invalid plist file [%s]", plistFile));
                        }
                        case MAC_BUNDLE_PLIST_WITHOUT_VERSION -> {
                            TKit.trace(String.format("Create empty plist file [%s]", plistFile));
                        }
                        default -> {
                            throw new AssertionError();
                        }
                    }

                    createXml(plistFile, xml -> {
                        writePList(xml, toXmlConsumer(() -> {
                            if (type == RuntimeType.MAC_BUNDLE_PLIST_WITHOUT_VERSION) {
                                writeDict(xml, toXmlConsumer(() -> {
                                }));
                            }
                        }));
                    });
                    yield MacBundle.fromPath(predefinedRuntimeDir).orElseThrow().homeDir();
                }
            };

            releaseFileVersion.ifPresent(ver -> {
                TKit.createPropertiesFile(
                        RuntimeReleaseFile.releaseFilePathInRuntime(runtimeDir),
                        Map.of("JAVA_VERSION", "\"" + ver + "\""));
            });

            return predefinedRuntimeDir;
        }

        static final String MAC_PREDEFINED_RUNTIME_BUNDLE_VERSION = "1.22.333";
    }

    private static Consumer<TestSpec> skipImagePackageType(Consumer<TestSpec> consumer) {
        Objects.requireNonNull(consumer);
        return spec -> {
            if (!spec.isImagePackageType()) {
                consumer.accept(spec);
            }
        };
    }
}
