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
package jdk.jpackage.test;

import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.PListWriter.writeArray;
import static jdk.jpackage.internal.util.PListWriter.writeBoolean;
import static jdk.jpackage.internal.util.PListWriter.writeBooleanOptional;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writeKey;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.PListWriter.writeStringArray;
import static jdk.jpackage.internal.util.PListWriter.writeStringOptional;
import static jdk.jpackage.internal.util.XmlUtils.initDocumentBuilder;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.constant.ClassDesc;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import jdk.jpackage.internal.util.Enquoter;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.MacBundle;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSign.CertificateType;
import jdk.jpackage.test.MacSign.ResolvedKeychain;
import jdk.jpackage.test.PackageTest.PackageHandlers;
import jdk.jpackage.test.RunnablePackageTest.Action;
import org.xml.sax.SAXException;

public final class MacHelper {

    public static void withExplodedDmg(JPackageCommand cmd,
            ThrowingConsumer<Path, ? extends Exception> consumer) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);

        // Mount DMG under random temporary folder to avoid collisions when
        // mounting DMG with same name asynchroniusly multiple times.
        // See JDK-8373105. "hdiutil" does not handle such cases very good.
        final var mountRoot = TKit.createTempDirectory("mountRoot");

        // Explode the DMG assuming this can require interaction if the DMG has a license, thus use `yes`.
        final var attachExec = Executor.of("sh", "-c", String.join(" ",
                "yes",
                "|",
                "/usr/bin/hdiutil",
                "attach",
                JPackageCommand.escapeAndJoin(cmd.outputBundle().toString()),
                "-mountroot", PathUtils.normalizedAbsolutePathString(mountRoot),
                "-nobrowse",
                "-plist"
        )).saveOutput().storeOutputInFiles().binaryOutput();

        final var attachResult = attachExec.executeAndRepeatUntilExitCode(0, 10, 6);

        final Path mountPoint;

        boolean mountPointInitialized = false;
        try {
            byte[] stdout = attachResult.byteStdout();

            // If the DMG has a license, it will be printed to the stdout before the plist content.
            // All bytes before the XML declaration of the plist must be skipped.
            // We need to find the location of the {'<', '?', 'x', 'm', 'l'} byte array
            // (the XML declaration) in the captured binary stdout.
            // Instead of crafting an ad-hoc function that operates on byte arrays,
            // we will convert the byte array into a String instance using
            // an 8-bit character set (ISO-8859-1) and use the standard String#indexOf().
            var startPlistIndex = new String(stdout, StandardCharsets.ISO_8859_1).indexOf("<?xml");

            byte[] plistXml;
            if (startPlistIndex > 0) {
                plistXml = Arrays.copyOfRange(stdout, startPlistIndex, stdout.length);
            } else {
                plistXml = stdout;
            }

            // One of "dict" items of "system-entities" array property should contain "mount-point" string property.
            mountPoint = readPList(plistXml).queryArrayValue("system-entities", false)
                    .map(PListReader.class::cast)
                    .map(dict -> {
                        return dict.findValue("mount-point");
                    })
                    .filter(Optional::isPresent).map(Optional::get)
                    .map(Path::of).findFirst().orElseThrow();
            mountPointInitialized = true;
        } finally {
            if (!mountPointInitialized) {
                TKit.trace("Unexpected plist file missing `system-entities` array:");
                attachResult.toCharacterResult(attachExec.charset(), false).stdout().forEach(TKit::trace);
                TKit.trace("Done");
            }
        }

        try {
            // code here used to copy just <runtime name> or <app name>.app
            // We now have option to include arbitrary content, so we copy
            // everything in the mounted image.
            String[] children = mountPoint.toFile().list();
            for (String child : children) {
                Path childPath = mountPoint.resolve(child);
                TKit.trace(String.format("Exploded [%s] in [%s] directory",
                        cmd.outputBundle(), childPath));
                ThrowingConsumer.toConsumer(consumer).accept(childPath);
            }
        } finally {
            // "hdiutil detach" might not work right away due to resource busy error, so
            // repeat detach several times.
            new RetryExecutor<Void, RuntimeException>(RuntimeException.class).setExecutable(context -> {
                var exec = Executor.of("/usr/bin/hdiutil", "detach").storeOutputInFiles();
                if (context.isLastAttempt()) {
                    // The last attempt, force detach.
                    exec.addArgument("-force");
                }
                exec.addArgument(mountPoint);

                // The image can get detached even if we get a resource busy error,
                // so execute the detach command without checking the exit code.
                var result = exec.executeWithoutExitCodeCheck();

                if (result.getExitCode() == 0 || !Files.exists(mountPoint)) {
                    // Detached successfully!
                    return null;
                } else {
                    throw new RuntimeException(String.format("[%s] mount point still attached", mountPoint));
                }
            }).setMaxAttemptsCount(10).setAttemptTimeout(6, TimeUnit.SECONDS).execute();
        }
    }

    public static PListReader readPListFromAppImage(Path appImage) {
        return readPList(appImage.resolve("Contents/Info.plist"));
    }

    public static PListReader readPListFromEmbeddedRuntime(Path appImage) {
        return readPList(appImage.resolve("Contents/runtime/Contents/Info.plist"));
    }

    public static PListReader readPList(Path path) {
        TKit.assertReadableFileExists(path);
        return readPList(toFunction(Files::readAllBytes).apply(path));
    }

    public static PListReader readPList(byte[] xml) {
        return ThrowingSupplier.toSupplier(() -> {
            return new PListReader(xml);
        }).get();
    }

    public static Map<String, String> flatMapPList(PListReader plistReader) {
        return Collections.unmodifiableMap(expandPListDist(new HashMap<>(), "", plistReader.toMap(true)));
    }

    private static Map<String, String> expandPListDist(Map<String, String> accumulator, String root, Map<String, Object> plistDict) {
        Objects.requireNonNull(accumulator);
        Objects.requireNonNull(plistDict);
        Objects.requireNonNull(root);
        for (var e : plistDict.entrySet()) {
            collectPListProperty(accumulator, root + "/" + e.getKey(), e.getValue());
        }
        return accumulator;
    }

    @SuppressWarnings("unchecked")
    private static void collectPListProperty(Map<String, String> accumulator, String key, Object value) {
        Objects.requireNonNull(accumulator);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        switch (value) {
            case PListReader.Raw raw -> {
                accumulator.put(key, raw.value());
            }
            case List<?> array -> {
                if (array.isEmpty()) {
                    accumulator.put(key + "[]", "");
                } else {
                    for (int i = 0; i != array.size(); i++) {
                        collectPListProperty(accumulator, String.format("%s[%d]", key, i), array.get(i));
                    }
                }
            }
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    accumulator.put(key + "{}", "");
                } else {
                    expandPListDist(accumulator, key, (Map<String, Object>)map);
                }
            }
            default -> {
                throw new IllegalArgumentException(String.format(
                        "Unexpected value type [%s] of property [%s]", value.getClass(), key));
            }
        }
    }

    /**
     * Returns {@code true} if the given jpackage command line is configured to sign
     * predefined app image in place.
     * <p>
     * jpackage will not create a new app image or a native bundle.
     *
     * @param cmd the jpackage command to examine
     * @return {@code true} if the given jpackage command line is configured to sign
     *         predefined app image in place and {@code false} otherwise.
     */
    public static boolean signPredefinedAppImage(JPackageCommand cmd) {
        Objects.requireNonNull(cmd);
        if (!TKit.isOSX()) {
            throw new UnsupportedOperationException();
        }
        return cmd.hasArgument("--mac-sign") && cmd.hasArgument("--app-image") && cmd.isImagePackageType();
    }

    /**
     * Returns {@code true} if the given jpackage command line is configured such
     * that the app image it will produce will be signed.
     * <p>
     * If the jpackage command line is bundling a native package, the function
     * returns {@code true} if the bundled app image will be signed.
     *
     * @param cmd the jpackage command to examine
     * @return {@code true} if the given jpackage command line is configured such
     *         that the app image it will produce will be signed and {@code false}
     *         otherwise.
     */
    public static boolean appImageSigned(JPackageCommand cmd) {
        Objects.requireNonNull(cmd);
        if (!TKit.isOSX()) {
            throw new UnsupportedOperationException();
        }

        var runtimeImageBundle = Optional.ofNullable(cmd.getArgumentValue("--runtime-image")).map(Path::of).flatMap(MacBundle::fromPath);
        var appImage = Optional.ofNullable(cmd.getArgumentValue("--app-image")).map(Path::of);

        if (cmd.isRuntime() && runtimeImageBundle.map(MacHelper::isBundleSigned).orElse(false)) {
            // If the predefined runtime is a signed bundle, bundled image should be signed too.
            return true;
        } else if (appImage.map(MacHelper::isBundleSigned).orElse(false)) {
            // The external app image is signed, so the app image is signed too.
            return true;
        }

        if (!cmd.isImagePackageType() && appImage.isPresent()) {
            // Building a ".pkg" or a ".dmg" bundle from the predefined app image.
            // The predefined app image is unsigned, so the app image bundled
            // in the output native package will be unsigned too
            // (even if the ".pkg" file may be signed itself, and we never sign ".dmg" files).
            return false;
        }

        if (!cmd.hasArgument("--mac-sign")) {
            return false;
        }

        return (cmd.hasArgument("--mac-signing-key-user-name") || cmd.hasArgument("--mac-app-image-sign-identity"));
    }

    public static void writeFaPListFragment(JPackageCommand cmd, XMLStreamWriter xml) {
        toRunnable(() -> {
            if (cmd.hasArgument("--app-image")) {
                copyFaPListFragmentFromPredefinedAppImage(cmd, xml);
            } else {
                createFaPListFragmentFromFaProperties(cmd, xml);
            }
        }).run();
    }

    static boolean isBundleSigned(Path bundleRoot) {
        return isBundleSigned(MacBundle.fromPath(bundleRoot).orElseThrow(IllegalArgumentException::new));
    }

    static boolean isBundleSigned(MacBundle bundle) {
        return MacSignVerify.findSpctlSignOrigin(MacSignVerify.SpctlType.EXEC, bundle.root(), true).isPresent();
    }

    private static void createFaPListFragmentFromFaProperties(JPackageCommand cmd, XMLStreamWriter xml)
            throws XMLStreamException, IOException {

        var allProps = Stream.of(cmd.getAllArgumentValues("--file-associations")).map(Path::of).map(propFile -> {
            try (var propFileReader = Files.newBufferedReader(propFile)) {
                var props = new Properties();
                props.load(propFileReader);
                return props;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }).toList();

        if (!allProps.isEmpty()) {
            var bundleId = getPackageId(cmd);

            Function<Properties, String> contentType = fa -> {
                return String.format("%s.%s", bundleId, Objects.requireNonNull(fa.getProperty("extension")));
            };

            Function<Properties, Optional<String>> icon = fa -> {
                return Optional.ofNullable(fa.getProperty("icon")).map(Path::of).map(Path::getFileName).map(Path::toString);
            };

            BiFunction<Properties, String, Optional<Boolean>> asBoolean = (fa, key) -> {
                return Optional.ofNullable(fa.getProperty(key)).map(Boolean::parseBoolean);
            };

            BiFunction<Properties, String, List<String>> asList = (fa, key) -> {
                return Optional.ofNullable(fa.getProperty(key)).map(str -> {
                    return List.of(str.split("[ ,]+"));
                }).orElseGet(List::of);
            };

            writeKey(xml, "CFBundleDocumentTypes");
            writeArray(xml, toXmlConsumer(() -> {
                for (var fa : allProps) {
                    writeDict(xml, toXmlConsumer(() -> {
                        writeStringArray(xml, "LSItemContentTypes", List.of(contentType.apply(fa)));
                        writeStringOptional(xml, "CFBundleTypeName", Optional.ofNullable(fa.getProperty("description")));
                        writeString(xml, "LSHandlerRank", Optional.ofNullable(fa.getProperty("mac.LSHandlerRank")).orElse("Owner"));
                        writeString(xml, "CFBundleTypeRole", Optional.ofNullable(fa.getProperty("mac.CFBundleTypeRole")).orElse("Editor"));
                        writeStringOptional(xml, "NSPersistentStoreTypeKey", Optional.ofNullable(fa.getProperty("mac.NSPersistentStoreTypeKey")));
                        writeStringOptional(xml, "NSDocumentClass", Optional.ofNullable(fa.getProperty("mac.NSDocumentClass")));
                        writeBoolean(xml, "LSIsAppleDefaultForType", true);
                        writeBooleanOptional(xml, "LSTypeIsPackage", asBoolean.apply(fa, "mac.LSTypeIsPackage"));
                        writeBooleanOptional(xml, "LSSupportsOpeningDocumentsInPlace", asBoolean.apply(fa, "mac.LSSupportsOpeningDocumentsInPlace"));
                        writeBooleanOptional(xml, "UISupportsDocumentBrowser", asBoolean.apply(fa, "mac.UISupportsDocumentBrowser"));
                        writeStringOptional(xml, "CFBundleTypeIconFile", icon.apply(fa));
                    }));
                }
            }));

            writeKey(xml, "UTExportedTypeDeclarations");
            writeArray(xml, toXmlConsumer(() -> {
                for (var fa : allProps) {
                    writeDict(xml, toXmlConsumer(() -> {
                        writeString(xml, "UTTypeIdentifier", contentType.apply(fa));
                        writeStringOptional(xml, "UTTypeDescription", Optional.ofNullable(fa.getProperty("description")));
                        if (fa.containsKey("mac.UTTypeConformsTo")) {
                            writeStringArray(xml, "UTTypeConformsTo", asList.apply(fa, "mac.UTTypeConformsTo"));
                        } else {
                            writeStringArray(xml, "UTTypeConformsTo", List.of("public.data"));
                        }
                        writeStringOptional(xml, "UTTypeIconFile", icon.apply(fa));
                        writeKey(xml, "UTTypeTagSpecification");
                        writeDict(xml, toXmlConsumer(() -> {
                            writeStringArray(xml, "public.filename-extension", List.of(fa.getProperty("extension")));
                            writeStringArray(xml, "public.mime-type", List.of(fa.getProperty("mime-type")));
                            writeStringArray(xml, "NSExportableTypes", asList.apply(fa, "mac.NSExportableTypes"));
                        }));
                    }));
                }
            }));
        }
    }

    private static void copyFaPListFragmentFromPredefinedAppImage(JPackageCommand cmd, XMLStreamWriter xml)
            throws IOException, SAXException, XMLStreamException {

        var predefinedAppImage = Path.of(Optional.ofNullable(cmd.getArgumentValue("--app-image")).orElseThrow(IllegalArgumentException::new));

        var plistPath = MacBundle.fromPath(predefinedAppImage).orElseThrow().infoPlistFile();

        try (var plistStream = Files.newInputStream(plistPath)) {
            var plist = new PListReader(initDocumentBuilder().parse(plistStream));

            var entries = Stream.of("CFBundleDocumentTypes", "UTExportedTypeDeclarations").map(key -> {
                return plist.findArrayValue(key, false).map(stream -> {
                    return stream.map(PListReader.class::cast).toList();
                }).map(plistList -> {
                    return Map.entry(key, plistList);
                });
            }).filter(Optional::isPresent).map(Optional::get).toList();

            for (var e : entries) {
                writeKey(xml, e.getKey());
                writeArray(xml, toXmlConsumer(() -> {
                    for (var arrayElement : e.getValue()) {
                        arrayElement.toXmlConsumer().accept(xml);
                    }
                }));
            }
        }
    }

    public static final class RuntimeBundleBuilder {

        public Path create() {
            return createRuntimeBundle(type, Optional.ofNullable(mutator));
        }

        public RuntimeBundleBuilder type(JPackageCommand.RuntimeImageType v) {
            type = Objects.requireNonNull(v);
            return this;
        }

        public RuntimeBundleBuilder mutator(Consumer<JPackageCommand> v) {
            mutator = v;
            return this;
        }

        public RuntimeBundleBuilder mutate(Consumer<RuntimeBundleBuilder> mutator) {
            mutator.accept(this);
            return this;
        }

        private RuntimeBundleBuilder() {
        }

        private JPackageCommand.RuntimeImageType type = JPackageCommand.RuntimeImageType.RUNTIME_TYPE_HELLO_APP;
        private Consumer<JPackageCommand> mutator;
    };

    public static RuntimeBundleBuilder buildRuntimeBundle() {
        return new RuntimeBundleBuilder();
    }

    public static Path createRuntimeBundle(Consumer<JPackageCommand> mutator) {
        return buildRuntimeBundle().mutator(Objects.requireNonNull(mutator)).create();
    }

    public static Path createRuntimeBundle() {
        return buildRuntimeBundle().create();
    }

    private static Path createRuntimeBundle(JPackageCommand.RuntimeImageType type, Optional<Consumer<JPackageCommand>> mutator) {
        Objects.requireNonNull(mutator);

        final var runtimeImage = JPackageCommand.createInputRuntimeImage(type);

        final var runtimeBundleWorkDir = TKit.createTempDirectory("runtime-bundle");

        final var unpackedRuntimeBundleDir = runtimeBundleWorkDir.resolve("unpacked");

        // Preferably create a DMG bundle, fallback to PKG if DMG packaging is disabled.
        new PackageTest().forTypes(Stream.of(
                PackageType.MAC_DMG,
                PackageType.MAC_PKG
        ).filter(PackageType::isEnabled).findFirst().orElseThrow(PackageType::throwSkippedExceptionIfNativePackagingUnavailable))
        .addInitializer(cmd -> {
            cmd.useToolProvider(true)
            .ignoreDefaultRuntime(true)
            .dumpOutput(true)
            .removeArgumentWithValue("--input")
            .setArgumentValue("--name", "foo")
            .setArgumentValue("--runtime-image", runtimeImage)
            .setArgumentValue("--dest", runtimeBundleWorkDir);

            mutator.ifPresent(cmd::mutate);
        }).addInstallVerifier(cmd -> {
            final Path bundleRoot = cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());
            FileUtils.copyRecursive(bundleRoot, unpackedRuntimeBundleDir, LinkOption.NOFOLLOW_LINKS);
        }).run(Action.CREATE, Action.UNPACK, Action.VERIFY_INSTALL, Action.PURGE);

        return unpackedRuntimeBundleDir;
    }

    public static Consumer<JPackageCommand> useKeychain(MacSign.ResolvedKeychain keychain) {
        return useKeychain(keychain.spec().keychain());
    }

    public static Consumer<JPackageCommand> useKeychain(MacSign.Keychain keychain) {
        return cmd -> {
            useKeychain(cmd, keychain);
        };
    }

    public static JPackageCommand useKeychain(JPackageCommand cmd, MacSign.ResolvedKeychain keychain) {
        return useKeychain(cmd, keychain.spec().keychain());
    }

    public static JPackageCommand useKeychain(JPackageCommand cmd, MacSign.Keychain keychain) {
        return sign(cmd).addArguments("--mac-signing-keychain", keychain.name());
    }

    public static JPackageCommand sign(JPackageCommand cmd) {
        if (!cmd.hasArgument("--mac-sign")) {
            cmd.addArgument("--mac-sign");
        }
        return cmd;
    }

    public static final class ResolvableCertificateRequest {

        public ResolvableCertificateRequest(
                CertificateRequest certRequest,
                Function<CertificateRequest, X509Certificate> certResolver,
                String label) {

            Objects.requireNonNull(certRequest);
            Objects.requireNonNull(certResolver);
            Objects.requireNonNull(label);
            if (label.isBlank()) {
                throw new IllegalArgumentException();
            }

            this.certRequest = certRequest;
            this.certResolver = certResolver;
            this.label = label;
        }

        public ResolvableCertificateRequest(
                CertificateRequest certRequest,
                ResolvedKeychain keychain,
                String label) {
            this(certRequest, keychain.asCertificateResolver(), label);
        }

        @Override
        public String toString() {
            return label;
        }

        public CertificateRequest certRequest() {
            return certRequest;
        }

        public X509Certificate cert() {
            return certResolver.apply(certRequest);
        }

        public CertificateType type() {
            return certRequest.type();
        }

        public String name() {
            return certRequest.name();
        }

        public String shortName() {
            return certRequest.shortName();
        }

        public int days() {
            return certRequest.days();
        }

        public boolean expired() {
            return certRequest.expired();
        }

        public boolean trusted() {
            return certRequest.trusted();
        }

        private final CertificateRequest certRequest;
        private final Function<CertificateRequest, X509Certificate> certResolver;
        private final String label;
    }

    public interface NamedCertificateRequestSupplier {

        String name();

        CertificateRequest certRequest();

        default ResolvableCertificateRequest certRequest(ResolvedKeychain keychain) {
            Objects.requireNonNull(keychain);
            var certRequest = Objects.requireNonNull(certRequest());
            if (keychain.spec().certificateRequests().contains(certRequest)) {
                return new ResolvableCertificateRequest(certRequest, keychain.asCertificateResolver(), name());
            } else {
                throw new IllegalArgumentException(String.format(
                        "Certificate request %s not found in [%s] keychain",
                        name(), keychain.spec().keychain().name()));
            }
        }
    }

    public record SignKeyOption(Type type, ResolvableCertificateRequest certRequest, Optional<String> customOptionValue) {

        public SignKeyOption {
            Objects.requireNonNull(type);
            Objects.requireNonNull(certRequest);
            Objects.requireNonNull(customOptionValue);
            if (customOptionValue.isEmpty() == (type == Type.SIGN_KEY_USER_NAME)) {
                throw new IllegalArgumentException();
            }
        }

        public SignKeyOption(Type type, ResolvableCertificateRequest certRequest) {
            this(type, certRequest, Optional.empty());
        }

        public SignKeyOption(
                Type type,
                NamedCertificateRequestSupplier certRequestSupplier,
                ResolvedKeychain keychain) {

            this(type, certRequestSupplier.certRequest(keychain));
        }

        public SignKeyOption(String optionValue, ResolvableCertificateRequest certRequest) {
            this(Type.SIGN_KEY_USER_NAME, certRequest, Optional.of(optionValue));
        }

        public SignKeyOption(
                String optionValue,
                NamedCertificateRequestSupplier certRequestSupplier,
                ResolvedKeychain keychain) {

            this(optionValue, certRequestSupplier.certRequest(keychain));
        }

        public enum Name {
            KEY_USER_NAME("--mac-signing-key-user-name"),
            KEY_IDENTITY_APP_IMAGE("--mac-app-image-sign-identity"),
            KEY_IDENTITY_INSTALLER("--mac-installer-sign-identity"),
            ;

            Name(String optionName) {
                this.optionName = Objects.requireNonNull(optionName);
            }

            public String optionName() {
                return optionName;
            }

            public boolean passThrough() {
                return this != KEY_USER_NAME;
            }

            private final String optionName;
        }

        public enum Type {
            /**
             * "--mac-signing-key-user-name" option with custom value
             */
            SIGN_KEY_USER_NAME(Name.KEY_USER_NAME),

            /**
             * "--mac-signing-key-user-name" option with the short user name, e.g.:
             * {@code --mac-signing-key-user-name foo}
             */
            SIGN_KEY_USER_SHORT_NAME(Name.KEY_USER_NAME),

            /**
             * "--mac-signing-key-user-name" option with the full user name (aka signing
             * identity name), e.g.:
             * {@code --mac-signing-key-user-name 'Developer ID Application: foo'}
             */
            SIGN_KEY_USER_FULL_NAME(Name.KEY_USER_NAME),

            /**
             * "--mac-installer-sign-identity" or "--mac-app-image-sign-identity" option
             * with the signing identity name, e.g.:
             * {@code --mac-app-image-sign-identity 'Developer ID Application: foo'}
             */
            SIGN_KEY_IDENTITY(Map.of(
                    MacSign.CertificateType.CODE_SIGN, Name.KEY_IDENTITY_APP_IMAGE,
                    MacSign.CertificateType.INSTALLER, Name.KEY_IDENTITY_INSTALLER)),

            /**
             * "--mac-app-image-sign-identity" regardless of the type of signing identity
             * (for signing app image or .pkg installer).
             */
            SIGN_KEY_IDENTITY_APP_IMAGE(Name.KEY_IDENTITY_APP_IMAGE),

            /**
             * "--mac-installer-sign-identity" regardless of the type of signing identity
             * (for signing app image or .pkg installer).
             */
            SIGN_KEY_IDENTITY_INSTALLER(Name.KEY_IDENTITY_INSTALLER),

            ;

            Type(Map<MacSign.CertificateType, Name> optionNameMap) {
                Objects.requireNonNull(optionNameMap);
                this.optionNameMapper = certType -> {
                    return Optional.of(optionNameMap.get(certType));
                };
            }

            Type(Name optionName) {
                Objects.requireNonNull(optionName);
                this.optionNameMapper = _ -> Optional.of(optionName);
            }

            Type() {
                this.optionNameMapper = _ -> Optional.empty();
            }

            public Optional<Name> mapOptionName(MacSign.CertificateType certType) {
                return optionNameMapper.apply(Objects.requireNonNull(certType));
            }

            public static Type[] defaultValues() {
                return new Type[] {
                        SIGN_KEY_USER_SHORT_NAME,
                        SIGN_KEY_USER_FULL_NAME,
                        SIGN_KEY_IDENTITY
                };
            }

            private final Function<MacSign.CertificateType, Optional<Name>> optionNameMapper;
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append('{');
            applyTo((optionName, _) -> {
                sb.append(optionName);
                switch (type) {
                    case SIGN_KEY_USER_FULL_NAME -> {
                        sb.append("/full");
                    }
                    case SIGN_KEY_USER_NAME -> {
                        customOptionValue.ifPresent(optionValue -> {
                            sb.append("=").append(ENQUOTER.applyTo(optionValue));
                        });
                    }
                    default -> {
                        // NOP
                    }
                }
                sb.append(": ");
            });
            sb.append(certRequest).append('}');
            return sb.toString();
        }

        public JPackageCommand addTo(JPackageCommand cmd) {
            applyTo(cmd::addArguments);
            return sign(cmd);
        }

        public JPackageCommand setTo(JPackageCommand cmd) {
            applyTo(cmd::setArgumentValue);
            return sign(cmd);
        }

        public Optional<Name> optionName() {
            return type.mapOptionName(certRequest.type());
        }

        public List<String> asCmdlineArgs() {
            String[] args = new String[2];
            applyTo((optionName, optionValue) -> {
                args[0] = optionName;
                args[1] = optionValue;
            });
            return List.of(args);
        }

        public Optional<Boolean> passThrough() {
            return optionName().map(Name::passThrough);
        }

        private void applyTo(BiConsumer<String, String> sink) {
            type.mapOptionName(certRequest.type()).ifPresent(optionName -> {
                sink.accept(optionName.optionName(), optionValue());
            });
        }

        private String optionValue() {
            return customOptionValue.orElseGet(() -> {
                switch (type) {
                    case    SIGN_KEY_IDENTITY,
                            SIGN_KEY_USER_FULL_NAME,
                            SIGN_KEY_IDENTITY_APP_IMAGE,
                            SIGN_KEY_IDENTITY_INSTALLER -> {
                        return certRequest.name();
                    }
                    case SIGN_KEY_USER_SHORT_NAME -> {
                        return certRequest.shortName();
                    }
                    default -> {
                        throw new IllegalStateException();
                    }
                }
            });
        }

        private static final Enquoter ENQUOTER = Enquoter.identity()
                .setEnquotePredicate(Enquoter.QUOTE_IF_WHITESPACES).setQuoteChar('\'');
    }

    public record SignKeyOptionWithKeychain(SignKeyOption signKeyOption, ResolvedKeychain keychain) {

        public SignKeyOptionWithKeychain {
            Objects.requireNonNull(signKeyOption);
            Objects.requireNonNull(keychain);
        }

        public SignKeyOptionWithKeychain(
                SignKeyOption.Type type,
                ResolvableCertificateRequest certRequest,
                ResolvedKeychain keychain) {

            this(new SignKeyOption(type, certRequest), keychain);
        }

        public SignKeyOptionWithKeychain(
                SignKeyOption.Type type,
                NamedCertificateRequestSupplier certRequestSupplier,
                ResolvedKeychain keychain) {

            this(type, certRequestSupplier.certRequest(keychain), keychain);
        }

        public SignKeyOptionWithKeychain(
                String optionValue,
                ResolvableCertificateRequest certRequest,
                ResolvedKeychain keychain) {

            this(new SignKeyOption(optionValue, certRequest), keychain);
        }

        public SignKeyOptionWithKeychain(
                String optionValue,
                NamedCertificateRequestSupplier certRequestSupplier,
                ResolvedKeychain keychain) {

            this(optionValue, certRequestSupplier.certRequest(keychain), keychain);
        }

        public SignKeyOptionWithKeychain(
                SignKeyOption.Type type,
                CertificateRequest certRequest,
                ResolvedKeychain keychain) {

            this(new SignKeyOption(
                    type,
                    new ResolvableCertificateRequest(
                            certRequest,
                            keychain.asCertificateResolver(),
                            certRequest.toString())),
                    keychain);
        }

        @Override
        public String toString() {
            return String.format("%s@%s", signKeyOption, keychain.name());
        }

        public SignKeyOption.Type type() {
            return signKeyOption.type();
        }

        public ResolvableCertificateRequest certRequest() {
            return signKeyOption.certRequest();
        }

        public Optional<SignKeyOption.Name> optionName() {
            return signKeyOption.optionName();
        }

        public Optional<Boolean> passThrough() {
            return signKeyOption.passThrough();
        }

        public JPackageCommand addTo(JPackageCommand cmd) {
            Optional.ofNullable(cmd.getArgumentValue("--mac-signing-keychain")).ifPresentOrElse(configuredKeychain -> {
                if (!configuredKeychain.equals(keychain.name())) {
                    throw new IllegalStateException(String.format(
                            "Command line [%s] already has the '--mac-signing-keychain' option, not adding another one with [%s] value",
                            cmd, keychain.name()));
                }
            }, () -> {
                useKeychain(cmd, keychain);
            });
            return signKeyOption.addTo(cmd);
        }

        public JPackageCommand setTo(JPackageCommand cmd) {
            cmd.removeArgumentWithValue("--mac-signing-keychain");
            useKeychain(cmd, keychain);
            return signKeyOption.setTo(cmd);
        }
    }

    static boolean isVerbatimCopyFromPredefinedAppImage(JPackageCommand cmd, Path path) {
        cmd.verifyIsOfType(PackageType.MAC);

        final var predefinedAppImage = Path.of(cmd.getArgumentValue("--app-image"));

        final var appLayout = ApplicationLayout.macAppImage().resolveAt(predefinedAppImage);

        if (!path.startsWith(predefinedAppImage)) {
            throw new IllegalArgumentException(
                    String.format("Path [%s] is not in directory [%s]", path, predefinedAppImage));
        }

        if (path.startsWith(appLayout.contentDirectory().resolve("_CodeSignature"))) {
            // A file in the "Contents/_CodeSignature" directory.
            return false;
        }

        final var outputAppImageDir = cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());

        final var outputAppImagePath = outputAppImageDir.resolve(predefinedAppImage.relativize(path));

        if (path.startsWith(appLayout.launchersDirectory()) &&
                cmd.launcherNames(true).stream().map(cmd::appLauncherPath).collect(toSet()).contains(outputAppImagePath)) {
            // The `path` references a launcher.
            // It can be signed and its digest may change.
            return false;
        }

        if (path.startsWith(appLayout.runtimeHomeDirectory().resolve("bin"))) {
            // The `path` references an executable native command in JDK's "bin" subdirectory.
            // It can be signed and its digest may change.
            return false;
        }

        return true;
    }

    static void verifyUnsignedBundleSignature(JPackageCommand cmd) {
        if (!cmd.isImagePackageType()) {
            MacSignVerify.assertUnsigned(cmd.outputBundle());
        }

        final Path bundleRoot;
        if (cmd.isImagePackageType()) {
            bundleRoot = cmd.outputBundle();
        } else {
            bundleRoot = cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());
        }

        MacSignVerify.assertAdhocSigned(bundleRoot);
    }

    static PackageHandlers createDmgPackageHandlers() {
        return new PackageHandlers(MacHelper::installDmg, MacHelper::uninstallDmg, MacHelper::unpackDmg);
    }

    private static int installDmg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);
        withExplodedDmg(cmd, dmgImage -> {
            Executor.of("sudo", "cp", "-R")
                    .addArgument(dmgImage)
                    .addArgument(getInstallationDirectory(cmd).getParent())
                    .execute(0);
        });
        return 0;
    }

    private static void uninstallDmg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);
        Executor.of("sudo", "rm", "-rf")
        .addArgument(cmd.appInstallationDirectory())
        .execute();
    }

    private static Path unpackDmg(JPackageCommand cmd, Path destinationDir) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);
        Path unpackDir = destinationDir.resolve(
                TKit.removeRootFromAbsolutePath(
                        getInstallationDirectory(cmd)).getParent());
        try {
            Files.createDirectories(unpackDir);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        withExplodedDmg(cmd, dmgImage -> {
            Executor.of("cp", "-R")
            .addArgument(dmgImage)
            .addArgument(unpackDir)
            .execute();
        });
        return destinationDir;
    }

    static PackageHandlers createPkgPackageHandlers() {
        return new PackageHandlers(MacHelper::installPkg, MacHelper::uninstallPkg, MacHelper::unpackPkg);
    }

    private static int installPkg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        return Executor.of("sudo", "/usr/sbin/installer", "-allowUntrusted", "-pkg")
                .addArgument(cmd.outputBundle())
                .addArguments("-target", "/")
                .execute().getExitCode();
    }

    private static void uninstallPkg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        if (Files.exists(getUninstallCommand(cmd))) {
            Executor.of("sudo", "/bin/sh",
                    getUninstallCommand(cmd).toString()).execute();
        } else {
            Executor.of("sudo", "rm", "-rf")
                    .addArgument(cmd.appInstallationDirectory())
                    .execute();
        }
    }

    private static Path unpackPkg(JPackageCommand cmd, Path destinationDir) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);

        var dataDir = destinationDir.resolve("data");

        Executor.of("pkgutil", "--expand")
                .addArgument(cmd.outputBundle())
                .addArgument(dataDir) // We need non-existing folder
                .execute();

        final Path unpackRoot = destinationDir.resolve("unpacked");

        // Unpack all ".pkg" files from $dataDir folder in $unpackDir folder
        try (var dataListing = Files.list(dataDir)) {
            dataListing.filter(file -> {
                return ".pkg".equals(PathUtils.getSuffix(file.getFileName()));
            }).forEach(ThrowingConsumer.toConsumer(pkgDir -> {
                // Installation root of the package is stored in
                // /pkg-info@install-location attribute in $pkgDir/PackageInfo xml file
                var doc = XmlUtils.initDocumentBuilder().parse(
                        new ByteArrayInputStream(Files.readAllBytes(
                                pkgDir.resolve("PackageInfo"))));
                var xPath = XPathFactory.newInstance().newXPath();

                final String installRoot = (String) xPath.evaluate(
                        "/pkg-info/@install-location", doc,
                        XPathConstants.STRING);

                final Path unpackDir = unpackRoot.resolve(
                        TKit.removeRootFromAbsolutePath(Path.of(installRoot)));

                Files.createDirectories(unpackDir);

                Executor.of("tar", "-C")
                        .addArgument(unpackDir)
                        .addArgument("-xvf")
                        .addArgument(pkgDir.resolve("Payload"))
                        .execute();
            }));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return unpackRoot;
    }

    static void verifyBundleStructure(JPackageCommand cmd) {
        final Path bundleRoot;
        if (cmd.isImagePackageType()) {
            bundleRoot = cmd.outputBundle();
        } else {
            bundleRoot = cmd.pathToUnpackedPackageFile(
                    cmd.appInstallationDirectory());
        }

        TKit.assertDirectoryContent(bundleRoot).match(Path.of("Contents"));

        final var contentsDir = bundleRoot.resolve("Contents");
        final var expectedContentsItems = cmd.isRuntime() ? RUNTIME_BUNDLE_CONTENTS : APP_BUNDLE_CONTENTS;

        var contentsVerifier = TKit.assertDirectoryContent(contentsDir);
        if (!cmd.hasArgument("--app-content")) {
            contentsVerifier.match(expectedContentsItems);
        } else {
            // Additional content added to the bundle.
            // Verify there is no period (.) char in the names of additional directories if any.
            contentsVerifier.contains(expectedContentsItems);
            contentsVerifier = contentsVerifier.removeAll(expectedContentsItems);
            contentsVerifier.match(contentsVerifier.items().stream().filter(path -> {
                if (Files.isDirectory(contentsDir.resolve(path))) {
                    return !path.getFileName().toString().contains(".");
                } else {
                    return true;
                }
            }).collect(toSet()));
        }
    }

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC);
        return String.format("%s-%s%s", getPackageName(cmd), cmd.version(),
                cmd.packageType().getSuffix());
    }

    static Path getInstallationDirectory(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC);

        final var defaultInstallLocation = Path.of(
                cmd.isRuntime() ? "/Library/Java/JavaVirtualMachines" : "/Applications");

        final Path installLocation = Optional.ofNullable(cmd.getArgumentValue("--install-dir"))
                .map(Path::of).orElse(defaultInstallLocation);

        return installLocation.resolve(cmd.name() + (cmd.isRuntime() ? ".jdk" : ".app"));
    }

    static Path getUninstallCommand(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        return cmd.pathToUnpackedPackageFile(Path.of(
                "/Library/Application Support", getPackageName(cmd),
                "uninstall.command"));
    }

    static Path getServicePlistFilePath(JPackageCommand cmd, String launcherName) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        return cmd.pathToUnpackedPackageFile(
                Path.of("/Library/LaunchDaemons").resolve(
                        getServicePListFileName(getPackageId(cmd),
                                Optional.ofNullable(launcherName).orElseGet(
                                        cmd::name))));
    }

    private static String getPackageName(JPackageCommand cmd) {
        return cmd.getArgumentValue("--mac-package-name", cmd::installerName);
    }

    private static String getPackageId(JPackageCommand cmd) {
        UnaryOperator<String> getPackageIdFromClassName = className -> {
            var packageName = ClassDesc.of(className).packageName();
            if (packageName.isEmpty()) {
                return className;
            } else {
                return packageName;
            }
        };

        return PropertyFinder.findAppProperty(cmd,
                PropertyFinder.cmdlineOptionWithValue("--mac-package-identifier").or(
                        PropertyFinder.cmdlineOptionWithValue("--main-class").map(getPackageIdFromClassName)
                ),
                PropertyFinder.appImageFileOptional(AppImageFile::mainLauncherClassName).map(getPackageIdFromClassName)
        ).orElseGet(cmd::name);
    }

    public static boolean isForAppStore(JPackageCommand cmd) {
        return PropertyFinder.findAppProperty(cmd,
                PropertyFinder.cmdlineBooleanOption("--mac-app-store"),
                PropertyFinder.appImageFile(appImageFile -> {
                    return Boolean.toString(appImageFile.macAppStore());
                })
        ).map(Boolean::parseBoolean).orElse(false);
    }

    public static boolean isXcodeDevToolsInstalled() {
        return Inner.XCODE_DEV_TOOLS_INSTALLED;
    }

    private static String getServicePListFileName(String packageName,
            String launcherName) {
        try {
            return getServicePListFileName.invoke(null, packageName,
                    launcherName).toString();
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Method initGetServicePListFileName() {
        try {
            return Class.forName(
                    "jdk.jpackage.internal.MacLaunchersAsServices").getMethod(
                            "getServicePListFileName", String.class, String.class);
        } catch (ClassNotFoundException ex) {
            if (TKit.isOSX()) {
                throw new RuntimeException(ex);
            } else {
                return null;
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class Inner {
        private static final boolean XCODE_DEV_TOOLS_INSTALLED =
                Executor.of("/usr/bin/xcrun", "--help").executeWithoutExitCodeCheck().getExitCode() == 0;
    }

    private static Set<Path> createBundleContents(String... customItems) {
        return Stream.concat(Stream.of(customItems), Stream.of(
                "MacOS",
                "Info.plist",
                "_CodeSignature"
        )).map(Path::of).collect(toSet());
    }

    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "Contents/Home/lib/server/libjvm.dylib"));

    private static final Method getServicePListFileName = initGetServicePListFileName();

    private static final Set<Path> APP_BUNDLE_CONTENTS = createBundleContents(
            "app",
            "runtime",
            "Resources",
            "PkgInfo"
    );

    private static final Set<Path> RUNTIME_BUNDLE_CONTENTS = createBundleContents(
            "Home"
    );
}
