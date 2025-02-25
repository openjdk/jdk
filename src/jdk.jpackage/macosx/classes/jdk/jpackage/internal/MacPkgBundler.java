/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.MacAppImageBuilder.APP_STORE;
import static jdk.jpackage.internal.MacAppImageBuilder.MAC_CF_BUNDLE_IDENTIFIER;
import static jdk.jpackage.internal.MacApplicationBuilder.isValidBundleIdentifier;
import static jdk.jpackage.internal.StandardBundlerParam.SIGN_BUNDLE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.internal.util.Architecture;
import jdk.internal.util.OSVersion;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.XmlUtils;

public class MacPkgBundler extends MacBaseInstallerBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MacResources");

    private static final String DEFAULT_BACKGROUND_IMAGE = "background_pkg.png";
    private static final String DEFAULT_PDF = "product-def.plist";

    public static final
            BundlerParamInfo<String> DEVELOPER_ID_INSTALLER_SIGNING_KEY =
            new BundlerParamInfo<>(
            "mac.signing-key-developer-id-installer",
            String.class,
            params -> {
                    String user = SIGNING_KEY_USER.fetchFrom(params);
                    String keychain = SIGNING_KEYCHAIN.fetchFrom(params);
                    String result = null;
                    if (APP_STORE.fetchFrom(params)) {
                        result = MacCertificate.findCertificateKey(
                            "3rd Party Mac Developer Installer: ",
                            user, keychain);
                    }
                    // if either not signing for app store or couldn't find
                    if (result == null) {
                        result = MacCertificate.findCertificateKey(
                            "Developer ID Installer: ", user, keychain);
                    }

                    if (result != null) {
                        MacCertificate certificate = new MacCertificate(result);

                        if (!certificate.isValid()) {
                            Log.error(MessageFormat.format(
                                    I18N.getString("error.certificate.expired"),
                                    result));
                        }
                    }

                    return result;
                },
            (s, p) -> s);

    public Path bundle(Map<String, ? super Object> params,
            Path outdir) throws PackagerException {

        final var pkg = MacFromParams.PKG_PACKAGE.fetchFrom(params);
        var env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

        Log.verbose(MessageFormat.format(I18N.getString("message.building-pkg"),
                pkg.app().name()));

        IOUtils.writableOutputDir(outdir);

        try {

            prepareConfigFiles(pkg, env);
            Path configScript = getConfig_Script(pkg, env);
            if (IOUtils.exists(configScript)) {
                IOUtils.run("bash", configScript);
            }

            return createPKG(pkg, env, outdir);
        } catch (IOException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    private Path packagesRoot(BuildEnv env) {
        return env.buildRoot().resolve("packages");
    }

    private Path scriptsRoot(BuildEnv env) {
        return env.configDir().resolve("scripts");
    }

    private Path getPackages_AppPackage(MacPkgPackage pkg, BuildEnv env) {
        return packagesRoot(env).resolve(pkg.app().name() + "-app.pkg");
    }

    private Path getPackages_ServicesPackage(MacPkgPackage pkg, BuildEnv env) {
        return packagesRoot(env).resolve(pkg.app().name() + "-services.pkg");
    }

    private Path getPackages_SupportPackage(MacPkgPackage pkg, BuildEnv env) {
        return packagesRoot(env).resolve(pkg.app().name() + "-support.pkg");
    }

    private Path getConfig_DistributionXMLFile(MacPkgPackage pkg, BuildEnv env) {
        return env.configDir().resolve("distribution.dist");
    }

    private Path getConfig_PDF(MacPkgPackage pkg, BuildEnv env) {
        return env.configDir().resolve("product-def.plist");
    }

    private Path getConfig_BackgroundImage(MacPkgPackage pkg, BuildEnv env) {
        return env.configDir().resolve(pkg.app().name() + "-background.png");
    }

    private Path getConfig_BackgroundImageDarkAqua(MacPkgPackage pkg, BuildEnv env) {
        return env.configDir().resolve(pkg.app().name() + "-background-darkAqua.png");
    }

    private String getAppIdentifier(MacPkgPackage pkg) {
        return ((MacApplication)pkg.app()).bundleIdentifier();
    }

    private String getServicesIdentifier(MacPkgPackage pkg) {
        return ((MacApplication)pkg.app()).bundleIdentifier() + ".services";
    }

    private String getSupportIdentifier(MacPkgPackage pkg) {
        return ((MacApplication)pkg.app()).bundleIdentifier() + ".support";
    }

    private void preparePackageScripts(MacPkgPackage pkg, BuildEnv env)
            throws IOException {
        Log.verbose(I18N.getString("message.preparing-scripts"));

        Files.createDirectories(scriptsRoot(env));

        Map<String, String> data = new HashMap<>();

        Path appLocation = pkg.asInstalledPackageApplicationLayout().orElseThrow().appDirectory();

        data.put("INSTALL_LOCATION", Path.of("/").resolve(pkg.relativeInstallDir()).toString());
        data.put("APP_LOCATION", appLocation.toString());

        MacPkgInstallerScripts.createAppScripts()
                .setResourceDir(env.resourceDir().orElse(null))
                .setSubstitutionData(data)
                .saveInFolder(scriptsRoot(env));
    }

    private void addPackageToInstallerGuiScript(XMLStreamWriter xml,
            String pkgId, String pkgName, String pkgVersion) throws IOException,
            XMLStreamException {
        xml.writeStartElement("pkg-ref");
        xml.writeAttribute("id", pkgId);
        xml.writeEndElement(); // </pkg-ref>
        xml.writeStartElement("choice");
        xml.writeAttribute("id", pkgId);
        xml.writeAttribute("visible", "false");
        xml.writeStartElement("pkg-ref");
        xml.writeAttribute("id", pkgId);
        xml.writeEndElement(); // </pkg-ref>
        xml.writeEndElement(); // </choice>
        xml.writeStartElement("pkg-ref");
        xml.writeAttribute("id", pkgId);
        xml.writeAttribute("version", pkgVersion);
        xml.writeAttribute("onConclusion", "none");
        try {
            xml.writeCharacters(new URI(null, null, pkgName, null).toASCIIString());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        xml.writeEndElement(); // </pkg-ref>
    }

    private void prepareDistributionXMLFile(MacPkgPackage pkg, BuildEnv env)
            throws IOException {
        Path f = getConfig_DistributionXMLFile(pkg, env);

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.preparing-distribution-dist"), f.toAbsolutePath().toString()));

        XmlUtils.createXml(f, xml -> {
            xml.writeStartElement("installer-gui-script");
            xml.writeAttribute("minSpecVersion", "1");

            xml.writeStartElement("title");
            xml.writeCharacters(pkg.app().name());
            xml.writeEndElement();

            xml.writeStartElement("background");
            xml.writeAttribute("file",
                    getConfig_BackgroundImage(pkg, env).getFileName().toString());
            xml.writeAttribute("mime-type", "image/png");
            xml.writeAttribute("alignment", "bottomleft");
            xml.writeAttribute("scaling", "none");
            xml.writeEndElement();

            xml.writeStartElement("background-darkAqua");
            xml.writeAttribute("file",
                    getConfig_BackgroundImageDarkAqua(pkg, env).getFileName().toString());
            xml.writeAttribute("mime-type", "image/png");
            xml.writeAttribute("alignment", "bottomleft");
            xml.writeAttribute("scaling", "none");
            xml.writeEndElement();

            final var licFile = pkg.licenseFile();
            if (licFile.isPresent()) {
                xml.writeStartElement("license");
                xml.writeAttribute("file", licFile.orElseThrow().toAbsolutePath().toString());
                xml.writeAttribute("mime-type", "text/rtf");
                xml.writeEndElement();
            }

            /*
             * Note that the content of the distribution file
             * below is generated by productbuild --synthesize
             */

            Map<String, Path> pkgs = new LinkedHashMap<>();

            pkgs.put(getAppIdentifier(pkg), getPackages_AppPackage(pkg, env));
            if (withServicesPkg(pkg, env)) {
                pkgs.put(getServicesIdentifier(pkg),
                        getPackages_ServicesPackage(pkg, env));
                pkgs.put(getSupportIdentifier(pkg),
                        getPackages_SupportPackage(pkg, env));
            }

            for (var p : pkgs.entrySet()) {
                addPackageToInstallerGuiScript(xml, p.getKey(),
                        p.getValue().getFileName().toString(),
                        pkg.app().version());
            }

            xml.writeStartElement("options");
            xml.writeAttribute("customize", "never");
            xml.writeAttribute("require-scripts", "false");
            xml.writeAttribute("hostArchitectures",
                    Architecture.isAARCH64() ? "arm64" : "x86_64");
            xml.writeEndElement(); // </options>
            xml.writeStartElement("choices-outline");
            xml.writeStartElement("line");
            xml.writeAttribute("choice", "default");
            for (var pkgId : pkgs.keySet()) {
                xml.writeStartElement("line");
                xml.writeAttribute("choice", pkgId);
                xml.writeEndElement(); // </line>
            }
            xml.writeEndElement(); // </line>
            xml.writeEndElement(); // </choices-outline>
            xml.writeStartElement("choice");
            xml.writeAttribute("id", "default");
            xml.writeEndElement(); // </choice>

            xml.writeEndElement(); // </installer-gui-script>
        });
    }

    private boolean prepareConfigFiles(MacPkgPackage pkg, BuildEnv env)
            throws IOException {

        env.createResource(DEFAULT_BACKGROUND_IMAGE)
                .setCategory(I18N.getString("resource.pkg-background-image"))
                .saveToFile(getConfig_BackgroundImage(pkg, env));

        env.createResource(DEFAULT_BACKGROUND_IMAGE)
                .setCategory(I18N.getString("resource.pkg-background-image"))
                .saveToFile(getConfig_BackgroundImageDarkAqua(pkg, env));

        env.createResource(DEFAULT_PDF)
                .setCategory(I18N.getString("resource.pkg-pdf"))
                .saveToFile(getConfig_PDF(pkg, env));

        prepareDistributionXMLFile(pkg, env);

        env.createResource(null)
                .setCategory(I18N.getString("resource.post-install-script"))
                .saveToFile(getConfig_Script(pkg, env));

        return true;
    }

    // name of post-image script
    private Path getConfig_Script(MacPkgPackage pkg, BuildEnv env) {
        return env.configDir().resolve(pkg.app().name() + "-post-image.sh");
    }

    private void patchCPLFile(Path cpl) throws IOException {
        String cplData = Files.readString(cpl);
        String[] lines = cplData.split("\n");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(cpl))) {
            int skip = 0;
            // Used to skip Java.runtime bundle, since
            // pkgbuild with --root will find two bundles app and Java runtime.
            // We cannot generate component proprty list when using
            // --component argument.
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().equals("<key>BundleIsRelocatable</key>")) {
                    out.println(lines[i]);
                    out.println("<false/>");
                    i++;
                } else if (lines[i].trim().equals("<key>ChildBundles</key>")) {
                    ++skip;
                } else if ((skip > 0) && lines[i].trim().equals("</array>")) {
                    --skip;
                } else {
                    if (skip == 0) {
                        out.println(lines[i]);
                    }
                }
            }
        }
    }

    // pkgbuild includes all components from "--root" and subfolders,
    // so if we have app image in folder which contains other images, then they
    // will be included as well. It does have "--filter" option which use regex
    // to exclude files/folder, but it will overwrite default one which excludes
    // based on doc "any .svn or CVS directories, and any .DS_Store files".
    // So easy approach will be to copy user provided app-image into temp folder
    // if root path contains other files.
    private Path getRoot(MacPkgPackage pkg, BuildEnv env) throws IOException {
        Path rootDir = env.appImageDir().getParent();

        // Not needed for runtime installer and it might break runtime installer
        // if parent does not have any other files
        if (!pkg.isRuntimeInstaller()) {
            try (var fileList = Files.list(rootDir)) {
                Path[] list = fileList.toArray(Path[]::new);
                // We should only have app image and/or .DS_Store
                if (list.length == 1) {
                    return rootDir;
                } else if (list.length == 2) {
                    // Check case with app image and .DS_Store
                    if (list[0].toString().toLowerCase().endsWith(".ds_store") ||
                        list[1].toString().toLowerCase().endsWith(".ds_store")) {
                        return rootDir; // Only app image and .DS_Store
                    }
                }
            }
        }

        // Copy to new root
        Path newRoot = Files.createTempDirectory(
                env.buildRoot(), "root-");

        Path source, dest;

        if (pkg.isRuntimeInstaller()) {
            // firs, is this already a runtime with
            // <runtime>/Contents/Home - if so we need the Home dir
            Path original = env.appImageDir();
            Path home = original.resolve("Contents/Home");
            source = (Files.exists(home)) ? home : original;

            // Then we need to put back the <NAME>/Content/Home
            dest = newRoot.resolve(((MacApplication)pkg.app()).bundleIdentifier() + "/Contents/Home");
        } else {
            source = env.appImageDir();
            dest = newRoot.resolve(source.getFileName());
        }
        FileUtils.copyRecursive(source, dest);

        return newRoot;
    }

    private boolean withServicesPkg(MacPkgPackage pkg, BuildEnv env) {
        return MacLaunchersAsServices.create(env, pkg).isPresent();
    }

    private void createServicesPkg(MacPkgPackage pkg, BuildEnv env) throws
            IOException {
        Path root = env.buildRoot().resolve("services");

        Path srcRoot = root.resolve("src");

        var services = MacLaunchersAsServices.create(BuildEnv.withAppImageDir(env, srcRoot), pkg).orElseThrow();

        Path scriptsDir = root.resolve("scripts");

        var data = services.create();
        data.put("SERVICES_PACKAGE_ID", getServicesIdentifier(pkg));

        MacPkgInstallerScripts.createServicesScripts()
                .setResourceDir(env.resourceDir().orElse(null))
                .setSubstitutionData(data)
                .saveInFolder(scriptsDir);

        var pb = new ProcessBuilder("/usr/bin/pkgbuild",
                "--root",
                srcRoot.toString(),
                "--install-location",
                "/",
                "--scripts",
                scriptsDir.toString(),
                "--identifier",
                getServicesIdentifier(pkg),
                getPackages_ServicesPackage(pkg, env).toAbsolutePath().toString());
        IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);

        createSupportPkg(pkg, env, data);
    }

    private void createSupportPkg(MacPkgPackage pkg, BuildEnv env,
            Map<String, String> servicesSubstitutionData) throws IOException {
        Path root = env.buildRoot().resolve("support");

        Path srcRoot = root.resolve("src");

        var enqouter = Enquoter.forShellLiterals().setEnquotePredicate(str -> true);

        Map<String, String> data = new HashMap<>(servicesSubstitutionData);
        data.put("APP_INSTALLATION_FOLDER", Path.of("/").resolve(pkg.relativeInstallDir()).toString());
        data.put("SUPPORT_INSTALLATION_FOLDER", enqouter.applyTo(Path.of(
                "/Library/Application Support", pkg.app().name()).toString()));

        new ShellScriptResource("uninstall.command")
                .setResource(env.createResource("uninstall.command.template")
                        .setCategory(I18N.getString("resource.pkg-uninstall-script"))
                        .setPublicName("uninstaller")
                        .setSubstitutionData(data))
                .saveInFolder(srcRoot.resolve(pkg.app().name()));

        var pb = new ProcessBuilder("/usr/bin/pkgbuild",
                "--root",
                srcRoot.toString(),
                "--install-location",
                "/Library/Application Support",
                "--identifier",
                getSupportIdentifier(pkg),
                getPackages_SupportPackage(pkg, env).toAbsolutePath().toString());
        IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);
    }

    private Path createPKG(MacPkgPackage pkg, BuildEnv env, Path outdir) {
        // generic find attempt
        try {
            Path appPKG = getPackages_AppPackage(pkg, env);

            Files.createDirectories(packagesRoot(env));

            Path root = getRoot(pkg, env);

            if (withServicesPkg(pkg, env)) {
                createServicesPkg(pkg, env);
            }

            final var installDir = Path.of("/").resolve(pkg.relativeInstallDir()).toString();

            // Generate default CPL file
            Path cpl = env.configDir().resolve("cpl.plist");
            ProcessBuilder pb = new ProcessBuilder("/usr/bin/pkgbuild",
                    "--root",
                    root.toString(),
                    "--install-location",
                    installDir,
                    "--analyze",
                    cpl.toAbsolutePath().toString());

            IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);

            patchCPLFile(cpl);

            // build application package
            if (((MacApplication)pkg.app()).appStore()) {
                pb = new ProcessBuilder("/usr/bin/pkgbuild",
                        "--root",
                        root.toString(),
                        "--install-location",
                        installDir,
                        "--component-plist",
                        cpl.toAbsolutePath().toString(),
                        "--identifier",
                        ((MacApplication)pkg.app()).bundleIdentifier(),
                        appPKG.toAbsolutePath().toString());
                IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);
            } else {
                preparePackageScripts(pkg, env);
                pb = new ProcessBuilder("/usr/bin/pkgbuild",
                        "--root",
                        root.toString(),
                        "--install-location",
                        installDir,
                        "--component-plist",
                        cpl.toAbsolutePath().toString(),
                        "--scripts",
                        scriptsRoot(env).toAbsolutePath().toString(),
                        "--identifier",
                        ((MacApplication)pkg.app()).bundleIdentifier(),
                        appPKG.toAbsolutePath().toString());
                IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);
            }

            // build final package
            Path finalPKG = outdir.resolve(pkg.packageFileNameWithSuffix());
            Files.createDirectories(outdir);

            List<String> commandLine = new ArrayList<>();
            commandLine.add("/usr/bin/productbuild");

            commandLine.add("--resources");
            commandLine.add(env.configDir().toAbsolutePath().toString());

            // maybe sign
            if (pkg.sign()) {
                if (OSVersion.current().compareTo(new OSVersion(10, 12)) >= 0) {
                    // we need this for OS X 10.12+
                    Log.verbose(I18N.getString("message.signing.pkg"));
                }

                final var pkgSigningConfig = pkg.signingConfig().orElseThrow();

                commandLine.add("--sign");
                commandLine.add(pkgSigningConfig.identifier().orElseThrow().name());

                pkgSigningConfig.keyChain().ifPresent(keyChain -> {
                    commandLine.add("--keychain");
                    commandLine.add(keyChain.toString());
                });
            }

            if (((MacApplication)pkg.app()).appStore()) {
                commandLine.add("--product");
                commandLine.add(getConfig_PDF(pkg, env)
                        .toAbsolutePath().toString());
                commandLine.add("--component");
                Path p = root.resolve(pkg.app().appImageDirName());
                commandLine.add(p.toAbsolutePath().toString());
                commandLine.add(installDir);
            } else {
                commandLine.add("--distribution");
                commandLine.add(getConfig_DistributionXMLFile(pkg, env)
                        .toAbsolutePath().toString());
                commandLine.add("--package-path");
                commandLine.add(packagesRoot(env).toAbsolutePath().toString());
            }
            commandLine.add(finalPKG.toAbsolutePath().toString());

            pb = new ProcessBuilder(commandLine);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 PrintStream ps = new PrintStream(baos)) {
                try {
                    IOUtils.exec(pb, false, ps, true, Executor.INFINITE_TIMEOUT);
                } catch (IOException ioe) {
                    // Log output of "productbuild" in case of
                    // error. It should help user to diagnose
                    // issue when using --mac-installer-sign-identity
                    Log.info(MessageFormat.format(I18N.getString(
                             "error.tool.failed.with.output"), "productbuild"));
                    Log.info(baos.toString().strip());
                    throw ioe;
                }
            }

            return finalPKG;
        } catch (Exception ignored) {
            Log.verbose(ignored);
            return null;
        }
    }

    /*
     * Implement Bundler
     */

    @Override
    public String getName() {
        return I18N.getString("pkg.bundler.name");
    }

    @Override
    public String getID() {
        return "pkg";
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            Objects.requireNonNull(params);

            MacFromParams.PKG_PACKAGE.fetchFrom(params);

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            String identifier = MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params);
            if (identifier == null) {
                throw new ConfigException(
                        I18N.getString("message.app-image-requires-identifier"),
                        I18N.getString(
                            "message.app-image-requires-identifier.advice"));
            }
            if (!isValidBundleIdentifier(identifier)) {
                throw new ConfigException(
                        MessageFormat.format(I18N.getString(
                        "message.invalid-identifier"), identifier),
                        I18N.getString("message.invalid-identifier.advice"));
            }

            // reject explicitly set sign to true and no valid signature key
            if (Optional.ofNullable(
                    SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.FALSE)) {
                if (!SIGNING_KEY_USER.getIsDefaultValue(params)) {
                    String signingIdentity =
                            DEVELOPER_ID_INSTALLER_SIGNING_KEY.fetchFrom(params);
                    if (signingIdentity == null) {
                        throw new ConfigException(
                                I18N.getString("error.explicit-sign-no-cert"),
                                I18N.getString(
                                "error.explicit-sign-no-cert.advice"));
                    }
                }

                // No need to validate --mac-installer-sign-identity, since it is
                // pass through option.
            }

            // hdiutil is always available so there's no need
            // to test for availability.

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    @Override
    public Path execute(Map<String, ? super Object> params,
            Path outputParentDir) throws PackagerException {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return true;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

}
