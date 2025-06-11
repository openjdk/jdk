/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.PathUtils.normalizedAbsolutePathString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jdk.internal.util.Architecture;
import jdk.internal.util.OSVersion;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.StartupParameters;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.resources.ResourceLocator;
import jdk.jpackage.internal.util.XmlUtils;
import org.xml.sax.SAXException;

record MacPkgPackager(MacPkgPackage pkg, BuildEnv env, Optional<Services> services, Path outputDir) {

    enum PkgPackageTaskID implements TaskID {
        PREPARE_MAIN_SCRIPTS,
        CREATE_DISTRIBUTION_XML_FILE,
        CREATE_COMPONENT_PLIST_FILE,
        PREPARE_SERVICES
    }

    static Builder build() {
        return new Builder();
    }

    static final class Builder extends PackagerBuilder<MacPkgPackage, Builder> {

        Path execute() throws PackagerException {
            Log.verbose(MessageFormat.format(I18N.getString("message.building-pkg"),
                    pkg.app().name()));

            IOUtils.writableOutputDir(outputDir);

            return execute(MacPackagingPipeline.build(Optional.of(pkg)));
        }

        @Override
        protected void configurePackagingPipeline(PackagingPipeline.Builder pipelineBuilder,
                StartupParameters startupParameters) {
            final var packager = new MacPkgPackager(pkg, startupParameters.packagingEnv(), createServices(), outputDir);
            packager.applyToPipeline(pipelineBuilder);
        }

        private Optional<Services> createServices() {
            if (pkg.app().isService()) {
                return Optional.of(Services.create(pkg, env));
            } else {
                return Optional.empty();
            }
        }
    }

    record InternalPackage(Path srcRoot, String identifier, Path path, List<String> otherPkgbuildArgs) {

        InternalPackage {
            Objects.requireNonNull(srcRoot);
            Objects.requireNonNull(identifier);
            Objects.requireNonNull(path);
            Objects.requireNonNull(otherPkgbuildArgs);
        }

        private List<String> allPkgbuildArgs() {
            final List<String> args = new ArrayList<>();
            args.add("--root");
            args.add(normalizedAbsolutePathString(srcRoot));
            args.addAll(otherPkgbuildArgs);
            args.add("--identifier");
            args.add(identifier);
            args.add(normalizedAbsolutePathString(path));
            return args;
        }

        void build() {
            final List<String> cmdline = new ArrayList<>();
            cmdline.add("/usr/bin/pkgbuild");
            cmdline.addAll(allPkgbuildArgs());
            try {
                Files.createDirectories(path.getParent());
                IOUtils.exec(new ProcessBuilder(cmdline), false, null, true, Executor.INFINITE_TIMEOUT);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    record Services(InternalPackage servicesPkg, Path servicesScriptsDir, InternalPackage supportPkg) {

        Services {
            Objects.requireNonNull(servicesPkg);
            Objects.requireNonNull(servicesScriptsDir);
            Objects.requireNonNull(supportPkg);
        }

        static Services create(MacPkgPackage pkg, BuildEnv env) {
            final var servicesRoot = env.buildRoot().resolve("services");
            final var supportRoot = env.buildRoot().resolve("support");

            final var servicesScriptsDir = servicesRoot.resolve("scripts");

            final var servicesPkg = InternalPackageType.SERVICES.createInternalPackage(
                    servicesRoot.resolve("src"), pkg, env, List.of(
                            "--install-location", "/",
                            "--scripts", normalizedAbsolutePathString(servicesScriptsDir)));

            final var supportPkg = InternalPackageType.SUPPORT.createInternalPackage(
                    supportRoot.resolve("src"), pkg, env, List.of(
                            "--install-location", "/Library/Application Support"));

            return new Services(servicesPkg, servicesScriptsDir, supportPkg);
        }

        Stream<InternalPackage> asStream() {
            return Stream.of(servicesPkg, supportPkg);
        }

        void prepareForPkgbuild(MacPkgPackage pkg, BuildEnv env) throws IOException {
            prepareSupportForPkgbuild(pkg, env, prepareServicesForPkgbuild(pkg, env));
        }

        private Map<String, String> prepareServicesForPkgbuild(MacPkgPackage pkg, BuildEnv env) throws IOException {
            final var services = new MacLaunchersAsServices(BuildEnv.withAppImageDir(env, servicesPkg.srcRoot()), pkg);

            final var data = services.create();
            data.put("SERVICES_PACKAGE_ID", servicesPkg.identifier());

            MacPkgInstallerScripts.createServicesScripts()
                    .setResourceDir(env.resourceDir().orElse(null))
                    .setSubstitutionData(data)
                    .saveInFolder(servicesScriptsDir);

            return data;
        }

        private void prepareSupportForPkgbuild(MacPkgPackage pkg, BuildEnv env,
                Map<String, String> servicesSubstitutionData) throws IOException {
            final var enqouter = Enquoter.forShellLiterals().setEnquotePredicate(str -> true);

            final var mainInstallDir = Path.of("/").resolve(pkg.relativeInstallDir());
            final var supportInstallDir = Path.of("/Library/Application Support").resolve(pkg.packageName());

            Map<String, String> data = new HashMap<>(servicesSubstitutionData);
            data.put("APP_INSTALLATION_FOLDER", enqouter.applyTo(mainInstallDir.toString()));
            data.put("SUPPORT_INSTALLATION_FOLDER", enqouter.applyTo(supportInstallDir.toString()));

            new ShellScriptResource("uninstall.command")
                    .setResource(env.createResource("uninstall.command.template")
                            .setCategory(I18N.getString("resource.pkg-uninstall-script"))
                            .setPublicName("uninstaller")
                            .setSubstitutionData(data))
                    .saveInFolder(supportPkg.srcRoot().resolve(pkg.app().name()));
        }
    }

    private enum InternalPackageType implements TaskID {
        MAIN,
        SERVICES("services"),
        SUPPORT("support");

        InternalPackage createInternalPackage(Path srcRoot, MacPkgPackage pkg, BuildEnv env, List<String> otherPkgbuildArgs) {
            return new InternalPackage(srcRoot, identifier(pkg), env.buildRoot().resolve("packages").resolve(filename(pkg)), otherPkgbuildArgs);
        }

        private InternalPackageType(String nameSuffix) {
            this.nameSuffix = Optional.of(nameSuffix);
        }

        private InternalPackageType() {
            this.nameSuffix = Optional.empty();
        }

        private String identifier(MacPkgPackage pkg) {
            final var baseIdentifier = pkg.app().bundleIdentifier();
            return nameSuffix.map(v -> baseIdentifier + "." + v).orElse(baseIdentifier);
        }

        private String filename(MacPkgPackage pkg) {
            return String.format("%s-%s.pkg", pkg.app().name(), nameSuffix.orElse("app"));
        }

        private final Optional<String> nameSuffix;
    }

    private void applyToPipeline(PackagingPipeline.Builder pipelineBuilder) {
        pipelineBuilder
                .excludeDirFromCopying(outputDir)
                .task(PkgPackageTaskID.PREPARE_MAIN_SCRIPTS)
                        .action(this::prepareMainScripts)
                        .addDependent(PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .add()
                .task(PkgPackageTaskID.CREATE_DISTRIBUTION_XML_FILE)
                        .action(this::prepareDistributionXMLFile)
                        .addDependent(PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .add()
                .task(PkgPackageTaskID.CREATE_COMPONENT_PLIST_FILE)
                        .action(this::createComponentPlistFile)
                        .addDependent(PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .add()
                .task(PackageTaskID.CREATE_CONFIG_FILES)
                        .action(this::prepareConfigFiles)
                        .add()
                .task(PackageTaskID.CREATE_PACKAGE_FILE)
                        .action(this::productbuild)
                        .addDependencies(InternalPackageType.values())
                        .addDependencies(PkgPackageTaskID.CREATE_DISTRIBUTION_XML_FILE, PkgPackageTaskID.CREATE_COMPONENT_PLIST_FILE)
                        .add()
                .task(PkgPackageTaskID.PREPARE_SERVICES)
                        .action(this::prepareServicesForBkgbuild)
                        .addDependent(PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .add()
                .task(InternalPackageType.SERVICES)
                        .action(this::buildServicesPKG)
                        .addDependencies(PkgPackageTaskID.PREPARE_SERVICES, PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .add()
                .task(InternalPackageType.SUPPORT)
                        .action(this::buildSupportPKG)
                        .addDependencies(PkgPackageTaskID.PREPARE_SERVICES, PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .add()
                .task(InternalPackageType.MAIN)
                        .action(this::buildMainPKG)
                        .addDependencies(PkgPackageTaskID.PREPARE_MAIN_SCRIPTS, PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                        .add();

        final List<TaskID> disabledTasks = new ArrayList<>();

        if (services.isEmpty()) {
            disabledTasks.addAll(List.of(PkgPackageTaskID.PREPARE_SERVICES, InternalPackageType.SERVICES, InternalPackageType.SUPPORT));
        }

        if (scriptsRoot().isEmpty()) {
            disabledTasks.add(PkgPackageTaskID.PREPARE_MAIN_SCRIPTS);
        }

        for (final var taskID : disabledTasks) {
            pipelineBuilder.task(taskID).noaction().add();
        }
    }

    List<InternalPackage> internalPackages() {
        return Stream.concat(Stream.of(mainPkg()),
                services.map(Services::asStream).orElseGet(Stream::of)).toList();
    }

    InternalPackage mainPkg() {
        final List<String> args = new ArrayList<>();
        args.add("--install-location");
        args.add(normalizedAbsolutePathString(installLocation()));
        args.add("--component-plist");
        args.add(normalizedAbsolutePathString(componentPlistFile()));

        scriptsRoot().ifPresent(scriptsRoot -> {
            args.add("--scripts");
            args.add(normalizedAbsolutePathString(scriptsRoot));
        });

        return InternalPackageType.MAIN.createInternalPackage(env.appImageDir(), pkg, env, args);
    }

    Optional<Path> scriptsRoot() {
        if (pkg.app().appStore() || pkg.isRuntimeInstaller()) {
            return Optional.empty();
        } else {
            return Optional.of(env.configDir().resolve("scripts"));
        }
    }

    Path componentPlistFile() {
        return env.configDir().resolve("cpl.plist");
    }

    Path installLocation() {
        return Path.of("/").resolve(pkg.relativeInstallDir()).getParent();
    }

    Path distributionXmlFile() {
        return env.configDir().resolve("distribution.dist");
    }

    Path appStoreProductFile() {
        return env.configDir().resolve("product-def.plist");
    }

    Path backgroundImage() {
        return env.configDir().resolve(pkg.app().name() + "-background.png");
    }

    Path backgroundImageDarkAqua() {
        return env.configDir().resolve(pkg.app().name() + "-background-darkAqua.png");
    }

    private void addInternalPackageToInstallerGuiScript(InternalPackage internalPkg,
            XMLStreamWriter xml) throws IOException, XMLStreamException {
        xml.writeStartElement("pkg-ref");
        xml.writeAttribute("id", internalPkg.identifier());
        xml.writeEndElement(); // </pkg-ref>
        xml.writeStartElement("choice");
        xml.writeAttribute("id", internalPkg.identifier());
        xml.writeAttribute("visible", "false");
        xml.writeStartElement("pkg-ref");
        xml.writeAttribute("id", internalPkg.identifier());
        xml.writeEndElement(); // </pkg-ref>
        xml.writeEndElement(); // </choice>
        xml.writeStartElement("pkg-ref");
        xml.writeAttribute("id", internalPkg.identifier());
        xml.writeAttribute("version", pkg.version());
        xml.writeAttribute("onConclusion", "none");
        try {
            xml.writeCharacters(new URI(null, null, internalPkg.path().getFileName().toString(), null).toASCIIString());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        xml.writeEndElement(); // </pkg-ref>
    }

    private void prepareMainScripts() throws IOException {
        Log.verbose(I18N.getString("message.preparing-scripts"));

        final var scriptsRoot = scriptsRoot().orElseThrow();

        Files.createDirectories(scriptsRoot);

        final Map<String, String> data = new HashMap<>();

        final var appLocation = pkg.asInstalledPackageApplicationLayout().orElseThrow().appDirectory();

        data.put("INSTALL_LOCATION", Path.of("/").resolve(pkg.relativeInstallDir()).toString());
        data.put("APP_LOCATION", appLocation.toString());

        MacPkgInstallerScripts.createAppScripts()
                .setResourceDir(env.resourceDir().orElse(null))
                .setSubstitutionData(data)
                .saveInFolder(scriptsRoot);
    }

    private void prepareDistributionXMLFile() throws IOException {
        final var f = distributionXmlFile();

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.preparing-distribution-dist"), f.toAbsolutePath().toString()));

        XmlUtils.createXml(f, xml -> {
            xml.writeStartElement("installer-gui-script");
            xml.writeAttribute("minSpecVersion", "1");

            xml.writeStartElement("title");
            xml.writeCharacters(pkg.app().name());
            xml.writeEndElement();

            xml.writeStartElement("background");
            xml.writeAttribute("file", backgroundImage().getFileName().toString());
            xml.writeAttribute("mime-type", "image/png");
            xml.writeAttribute("alignment", "bottomleft");
            xml.writeAttribute("scaling", "none");
            xml.writeEndElement();

            xml.writeStartElement("background-darkAqua");
            xml.writeAttribute("file", backgroundImageDarkAqua().getFileName().toString());
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

            for (final var p : internalPackages()) {
                addInternalPackageToInstallerGuiScript(p, xml);
            }

            xml.writeStartElement("options");
            xml.writeAttribute("customize", "never");
            xml.writeAttribute("require-scripts", "false");
            xml.writeAttribute("hostArchitectures", Architecture.isAARCH64() ? "arm64" : "x86_64");
            xml.writeEndElement(); // </options>
            xml.writeStartElement("choices-outline");
            xml.writeStartElement("line");
            xml.writeAttribute("choice", "default");
            for (final var p : internalPackages()) {
                xml.writeStartElement("line");
                xml.writeAttribute("choice", p.identifier());
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

    private void prepareConfigFiles() throws IOException {
        env.createResource(DEFAULT_BACKGROUND_IMAGE)
                .setCategory(I18N.getString("resource.pkg-background-image"))
                .saveToFile(backgroundImage());

        env.createResource(DEFAULT_BACKGROUND_IMAGE)
                .setCategory(I18N.getString("resource.pkg-background-image"))
                .saveToFile(backgroundImageDarkAqua());

        if (pkg.app().appStore()) {
            env.createResource(DEFAULT_PDF)
                    .setCategory(I18N.getString("resource.pkg-pdf"))
                    .saveToFile(appStoreProductFile());
        }
    }

    private void patchCPLFile(Path cpl) throws IOException {
        try (final var xsltResource = ResourceLocator.class.getResourceAsStream("adjust-component-plist.xsl")) {
            final var srcXml =  new DOMSource(XmlUtils.initDocumentBuilder().parse(
                    new ByteArrayInputStream(Files.readAllBytes(cpl))));
            final var dstXml = new StreamResult(cpl.toFile());
            final var xslt = TransformerFactory.newInstance().newTransformer(new StreamSource(xsltResource));
            xslt.transform(srcXml, dstXml);
        } catch (TransformerException|SAXException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void prepareServicesForBkgbuild() throws IOException {
        services.orElseThrow().prepareForPkgbuild(pkg, env);
    }

    private void buildServicesPKG() {
        services.orElseThrow().servicesPkg().build();
    }

    private void buildSupportPKG() {
        services.orElseThrow().supportPkg().build();
    }

    private void buildMainPKG() throws IOException {
        mainPkg().build();
    }

    private void createComponentPlistFile() throws IOException {
        final var cpl = componentPlistFile();

        Files.createDirectories(cpl.getParent());

        final var pb = new ProcessBuilder("/usr/bin/pkgbuild",
                "--root",
                normalizedAbsolutePathString(env.appImageDir()),
                "--install-location",
                normalizedAbsolutePathString(installLocation()),
                "--analyze",
                normalizedAbsolutePathString(cpl));

        IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);

        patchCPLFile(cpl);
    }

    private void productbuild() throws IOException {
        final var finalPkg = outputDir.resolve(pkg.packageFileNameWithSuffix());
        Files.createDirectories(finalPkg.getParent());

        List<String> commandLine = new ArrayList<>();
        commandLine.add("/usr/bin/productbuild");

        commandLine.add("--resources");
        commandLine.add(normalizedAbsolutePathString(env.configDir()));

        // maybe sign
        if (pkg.sign()) {
            if (OSVersion.current().compareTo(new OSVersion(10, 12)) >= 0) {
                // we need this for OS X 10.12+
                Log.verbose(I18N.getString("message.signing.pkg"));
            }

            final var pkgSigningConfig = pkg.signingConfig().orElseThrow();

            commandLine.add("--sign");
            commandLine.add(pkgSigningConfig.identity().id());

            pkgSigningConfig.keychain().map(Keychain::new).ifPresent(keychain -> {
                commandLine.add("--keychain");
                commandLine.add(keychain.asCliArg());
            });
        }

        if (pkg.app().appStore()) {
            commandLine.add("--product");
            commandLine.add(normalizedAbsolutePathString(appStoreProductFile()));
            commandLine.add("--component");
            Path p = env.appImageDir().resolve(pkg.app().appImageDirName());
            commandLine.add(p.toAbsolutePath().toString());
            commandLine.add(normalizedAbsolutePathString(installLocation()));
        } else {
            commandLine.add("--distribution");
            commandLine.add(normalizedAbsolutePathString(distributionXmlFile()));
            commandLine.add("--package-path");
            // Assume all internal .pkg files reside in the same directory.
            commandLine.add(normalizedAbsolutePathString(mainPkg().path().getParent()));
        }
        commandLine.add(normalizedAbsolutePathString(finalPkg));

        final var pb = new ProcessBuilder(commandLine);
        IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);
    }

    private static final String DEFAULT_BACKGROUND_IMAGE = "background_pkg.png";
    private static final String DEFAULT_PDF = "product-def.plist";
}
