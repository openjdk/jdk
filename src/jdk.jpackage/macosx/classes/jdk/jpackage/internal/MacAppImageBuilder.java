/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.MacBaseInstallerBundler.SIGNING_KEYCHAIN;
import static jdk.jpackage.internal.StandardBundlerParam.APP_CONTENT;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class MacAppImageBuilder {

    static final BundlerParamInfo<Boolean> APP_STORE =
            new BundlerParamInfo<>(
            Arguments.CLIOptions.MAC_APP_STORE.getId(),
            Boolean.class,
            params -> {
                return false;
            },
            // valueOf(null) is false, we actually do want null in some cases
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                    null : Boolean.valueOf(s)
        );

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.MAC_BUNDLE_IDENTIFIER.getId(),
                    String.class,
                    params -> {
                        // Get identifier from app image if user provided
                        // app image and did not provide the identifier via CLI.
                        String identifier = extractBundleIdentifier(params);
                        if (identifier == null) {
                            identifier =  MacAppBundler.getIdentifier(params);
                        }
                        if (identifier == null) {
                            identifier = APP_NAME.fetchFrom(params);
                        }
                        return identifier;
                    },
                    (s, p) -> s);

    private static List<String> getCodesignArgs(
            boolean force, Path path, String signingIdentity,
            String identifierPrefix, Path entitlements, String keyChain) {
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList("/usr/bin/codesign",
                    "-s", signingIdentity,
                    "-vvvv"));

        if (!signingIdentity.equals("-")) {
            args.addAll(Arrays.asList("--timestamp",
                    "--options", "runtime",
                    "--prefix", identifierPrefix));
            if (keyChain != null && !keyChain.isEmpty()) {
                args.add("--keychain");
                args.add(keyChain);
            }
            if (Files.isExecutable(path)) {
                if (entitlements != null) {
                    args.add("--entitlements");
                    args.add(entitlements.toString());
                }
            }
        }

        if (force) {
            args.add("--force");
        }

        args.add(path.toString());

        return args;
    }

    private static void runCodesign(
            ProcessBuilder pb, boolean quiet, Map<String, ? super Object> params)
            throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintStream ps = new PrintStream(baos)) {
            try {
            IOUtils.exec(pb, false, ps, false,
                         Executor.INFINITE_TIMEOUT, quiet);
            } catch (IOException ioe) {
                // Log output of "codesign" in case of error. It should help
                // user to diagnose issues when using --mac-app-image-sign-identity.
                // In addition add possible reason for failure. For example
                // "--app-content" can fail "codesign".

                // APP_CONTENT is never null.
                if (!APP_CONTENT.fetchFrom(params).isEmpty()) {
                    Log.info(I18N.getString(
                        "message.codesign.failed.reason.app.content"));
                }

                // Signing might not work without Xcode with command line
                // developer tools. Show user if Xcode is missing as possible
                // reason.
                if (!isXcodeDevToolsInstalled()) {
                    Log.info(I18N.getString(
                        "message.codesign.failed.reason.xcode.tools"));
                }

                // Log "codesign" output
                Log.info(MessageFormat.format(I18N.getString(
                         "error.tool.failed.with.output"), "codesign"));
                Log.info(baos.toString().strip());

                throw ioe;
            }
        }
    }

    private static boolean isXcodeDevToolsInstalled() {
        try {
            Executor.of("/usr/bin/xcrun", "--help").executeExpectSuccess();
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    static void signAppBundle(
            Map<String, ? super Object> params, Path appLocation,
            String signingIdentity, String identifierPrefix, Path entitlements)
            throws IOException {
        AtomicReference<IOException> toThrow = new AtomicReference<>();
        String appExecutable = "/Contents/MacOS/" + APP_NAME.fetchFrom(params);
        String keyChain = SIGNING_KEYCHAIN.fetchFrom(params);

        // sign all dylibs and executables
        try (Stream<Path> stream = Files.walk(appLocation)) {
            stream.peek(path -> { // fix permissions
                try {
                    Set<PosixFilePermission> pfp
                            = Files.getPosixFilePermissions(path);
                    if (!pfp.contains(PosixFilePermission.OWNER_WRITE)) {
                        pfp = EnumSet.copyOf(pfp);
                        pfp.add(PosixFilePermission.OWNER_WRITE);
                        Files.setPosixFilePermissions(path, pfp);
                    }
                } catch (IOException e) {
                    Log.verbose(e);
                }
            }).filter(p -> Files.isRegularFile(p)
                    && (Files.isExecutable(p) || p.toString().endsWith(".dylib"))
                    && !(p.toString().contains("dylib.dSYM/Contents"))
                    && !(p.toString().endsWith(appExecutable))
            ).forEach(p -> {
                // noinspection ThrowableResultOfMethodCallIgnored
                if (toThrow.get() != null) {
                    return;
                }

                // If p is a symlink then skip the signing process.
                if (Files.isSymbolicLink(p)) {
                    Log.verbose(MessageFormat.format(I18N.getString(
                            "message.ignoring.symlink"), p.toString()));
                } else {
                    // unsign everything before signing
                    List<String> args = new ArrayList<>();
                    args.addAll(Arrays.asList("/usr/bin/codesign",
                            "--remove-signature", p.toString()));
                    try {
                        Set<PosixFilePermission> oldPermissions =
                                Files.getPosixFilePermissions(p);
                        p.toFile().setWritable(true, true);
                        ProcessBuilder pb = new ProcessBuilder(args);
                        // run quietly
                        IOUtils.exec(pb, false, null, false,
                                Executor.INFINITE_TIMEOUT, true);
                        Files.setPosixFilePermissions(p, oldPermissions);
                    } catch (IOException ioe) {
                        Log.verbose(ioe);
                        toThrow.set(ioe);
                        return;
                    }

                    // Sign only if we have identity
                    if (signingIdentity != null) {
                        args = getCodesignArgs(false, p, signingIdentity,
                                identifierPrefix, entitlements, keyChain);
                        try {
                            Set<PosixFilePermission> oldPermissions
                                    = Files.getPosixFilePermissions(p);
                            p.toFile().setWritable(true, true);
                            ProcessBuilder pb = new ProcessBuilder(args);
                            // run quietly
                            runCodesign(pb, true, params);
                            Files.setPosixFilePermissions(p, oldPermissions);
                        } catch (IOException ioe) {
                            toThrow.set(ioe);
                        }
                    }
                }
            });
        }
        IOException ioe = toThrow.get();
        if (ioe != null) {
            throw ioe;
        }

        // We cannot continue signing without identity
        if (signingIdentity == null) {
            return;
        }

        // sign all runtime and frameworks
        Consumer<? super Path> signIdentifiedByPList = path -> {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (toThrow.get() != null) return;

            try {
                List<String> args = getCodesignArgs(true, path, signingIdentity,
                            identifierPrefix, entitlements, keyChain);
                ProcessBuilder pb = new ProcessBuilder(args);
                runCodesign(pb, false, params);
            } catch (IOException e) {
                toThrow.set(e);
            }
        };

        Path javaPath = appLocation.resolve("Contents/runtime");
        if (Files.isDirectory(javaPath)) {
            signIdentifiedByPList.accept(javaPath);

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }
        Path frameworkPath = appLocation.resolve("Contents/Frameworks");
        if (Files.isDirectory(frameworkPath)) {
            try (var fileList = Files.list(frameworkPath)) {
                fileList.forEach(signIdentifiedByPList);
            }

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }

        // sign the app itself
        List<String> args = getCodesignArgs(true, appLocation, signingIdentity,
                identifierPrefix, entitlements, keyChain);
        ProcessBuilder pb = new ProcessBuilder(args);
        runCodesign(pb, false, params);
    }

    private static String extractBundleIdentifier(Map<String, Object> params) {
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) == null) {
            return null;
        }

        try {
            Path infoPList = PREDEFINED_APP_IMAGE.fetchFrom(params).resolve("Contents").
                    resolve("Info.plist");

            DocumentBuilderFactory dbf
                    = DocumentBuilderFactory.newDefaultInstance();
            dbf.setFeature("http://apache.org/xml/features/" +
                           "nonvalidating/load-external-dtd", false);
            DocumentBuilder b = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = b.parse(Files.newInputStream(infoPList));

            XPath xPath = XPathFactory.newInstance().newXPath();
            // Query for the value of <string> element preceding <key>
            // element with value equal to CFBundleIdentifier
            String v = (String) xPath.evaluate(
                    "//string[preceding-sibling::key = \"CFBundleIdentifier\"][1]",
                    doc, XPathConstants.STRING);

            if (v != null && !v.isEmpty()) {
                return v;
            }
        } catch (Exception ex) {
            Log.verbose(ex);
        }

        return null;
    }
}
