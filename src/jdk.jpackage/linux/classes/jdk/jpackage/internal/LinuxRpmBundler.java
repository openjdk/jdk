/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.LinuxRpmPackage;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;


/**
 * There are two command line options to configure license information for RPM
 * packaging: --linux-rpm-license-type and --license-file. Value of
 * --linux-rpm-license-type command line option configures "License:" section
 * of RPM spec. Value of --license-file command line option specifies a license
 * file to be added to the package. License file is a sort of documentation file
 * but it will be installed even if user selects an option to install the
 * package without documentation. --linux-rpm-license-type is the primary option
 * to set license information. --license-file makes little sense in case of RPM
 * packaging.
 */
public class LinuxRpmBundler extends LinuxPackageBundler {

    private static final String DEFAULT_SPEC_TEMPLATE = "template.spec";

    public static final String TOOL_RPM = "rpm";
    public static final String TOOL_RPMBUILD = "rpmbuild";
    public static final DottedVersion TOOL_RPMBUILD_MIN_VERSION = DottedVersion.lazy(
            "4.10");

    public LinuxRpmBundler() {
        super(LinuxFromParams.RPM_PACKAGE);
    }

    @Override
    protected void doValidate(BuildEnv env, LinuxPackage pkg) throws ConfigException {
    }

    private static ToolValidator createRpmbuildToolValidator() {
        Pattern pattern = Pattern.compile(" (\\d+\\.\\d+)");
        return new ToolValidator(TOOL_RPMBUILD).setMinimalVersion(
                TOOL_RPMBUILD_MIN_VERSION).setVersionParser(lines -> {
                    String versionString = lines.limit(1).collect(
                            Collectors.toList()).get(0);
                    Matcher matcher = pattern.matcher(versionString);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                    return null;
                });
    }

    @Override
    protected List<ToolValidator> getToolValidators() {
        return List.of(createRpmbuildToolValidator());
    }

    protected void createConfigFiles(Map<String, String> replacementData,
            BuildEnv env, LinuxPackage pkg) throws IOException {
        Path specFile = specFile(env, pkg);

        // prepare spec file
        env.createResource(DEFAULT_SPEC_TEMPLATE)
                .setCategory(I18N.getString("resource.rpm-spec-file"))
                .setSubstitutionData(replacementData)
                .saveToFile(specFile);
    }

    @Override
    protected Path buildPackageBundle(BuildEnv env, LinuxPackage pkg,
            Path outputParentDir) throws PackagerException, IOException {
        return buildRPM(env, pkg, outputParentDir);
    }

    private static Path installPrefix(LinuxPackage pkg) {
        Path path = pkg.relativeInstallDir();
        if (!pkg.isInstallDirInUsrTree()) {
            path = path.getParent();
        }
        return Path.of("/").resolve(path);
    }

    @Override
    protected Map<String, String> createReplacementData(BuildEnv env, LinuxPackage pkg) throws IOException {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_RELEASE", pkg.release().orElseThrow());
        data.put("APPLICATION_PREFIX", installPrefix(pkg).toString());
        data.put("APPLICATION_DIRECTORY", Path.of("/").resolve(pkg.relativeInstallDir()).toString());
        data.put("APPLICATION_SUMMARY", pkg.app().name());
        data.put("APPLICATION_LICENSE_TYPE", ((LinuxRpmPackage)pkg).licenseType());

        String licenseFile = pkg.licenseFile().map(v -> {
            return v.toAbsolutePath().normalize().toString();
        }).orElse(null);
        data.put("APPLICATION_LICENSE_FILE", licenseFile);
        data.put("APPLICATION_GROUP", pkg.category().orElse(""));

        data.put("APPLICATION_URL", pkg.aboutURL().orElse(""));

        return data;
    }

    @Override
    protected void initLibProvidersLookup(LibProvidersLookup libProvidersLookup) {
        libProvidersLookup.setPackageLookup(file -> {
            return Executor.of(TOOL_RPM,
                "-q", "--queryformat", "%{name}\\n",
                "-q", "--whatprovides", file.toString())
                .saveOutput(true).executeExpectSuccess().getOutput().stream();
        });
    }

    @Override
    protected List<ConfigException> verifyOutputBundle(BuildEnv env, LinuxPackage pkg,
            Path packageBundle) {
        List<ConfigException> errors = new ArrayList<>();

        String specFileName = specFile(env, pkg).getFileName().toString();

        try {
            List<PackageProperty> properties = List.of(
                    new PackageProperty("Name", pkg.packageName(),
                            "APPLICATION_PACKAGE", specFileName),
                    new PackageProperty("Version", pkg.version(),
                            "APPLICATION_VERSION", specFileName),
                    new PackageProperty("Release", pkg.release().orElseThrow(),
                            "APPLICATION_RELEASE", specFileName),
                    new PackageProperty("Arch", pkg.arch(), null, specFileName));

            List<String> actualValues = Executor.of(TOOL_RPM, "-qp", "--queryformat",
                    properties.stream().map(entry -> String.format("%%{%s}",
                    entry.name)).collect(Collectors.joining("\\n")),
                    packageBundle.toString()).saveOutput(true).executeExpectSuccess().getOutput();

            Iterator<String> actualValuesIt = actualValues.iterator();
            properties.forEach(property -> errors.add(property.verifyValue(
                    actualValuesIt.next())));
        } catch (IOException ex) {
            // Ignore error as it is not critical. Just report it.
            Log.verbose(ex);
        }

        return errors;
    }

    private Path specFile(BuildEnv env, Package pkg) {
        return env.buildRoot().resolve(Path.of("SPECS", pkg.packageName() + ".spec"));
    }

    private Path buildRPM(BuildEnv env, Package pkg, Path outdir) throws IOException {

        Path rpmFile = outdir.toAbsolutePath().resolve(pkg.packageFileNameWithSuffix());

        Log.verbose(I18N.format("message.outputting-bundle-location", rpmFile.getParent()));

        //run rpmbuild
        Executor.of(TOOL_RPMBUILD,
                "-bb", specFile(env, pkg).toAbsolutePath().toString(),
                "--define", String.format("%%_sourcedir %s",
                        env.appImageDir().toAbsolutePath()),
                // save result to output dir
                "--define", String.format("%%_rpmdir %s", rpmFile.getParent()),
                // do not use other system directories to build as current user
                "--define", String.format("%%_topdir %s",
                        env.buildRoot().toAbsolutePath()),
                "--define", String.format("%%_rpmfilename %s", rpmFile.getFileName())
        ).executeExpectSuccess();

        Log.verbose(I18N.format("message.output-bundle-location", rpmFile.getParent()));

        return rpmFile;
    }

    @Override
    public String getName() {
        return I18N.getString("rpm.bundler.name");
    }

    @Override
    public String getID() {
        return "rpm";
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return OperatingSystem.isLinux() && (createRpmbuildToolValidator().validate() == null);
    }

    @Override
    public boolean isDefault() {
        return !LinuxDebBundler.isDebian();
    }
}
