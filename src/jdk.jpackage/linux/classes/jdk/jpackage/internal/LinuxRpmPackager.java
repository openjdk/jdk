/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LinuxRpmPackage;


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
final class LinuxRpmPackager extends LinuxPackager<LinuxRpmPackage> {

    LinuxRpmPackager(BuildEnv env, LinuxRpmPackage pkg, Path outputDir, LinuxRpmSystemEnvironment sysEnv) {
        super(env, pkg, outputDir, sysEnv);
        this.sysEnv = Objects.requireNonNull(sysEnv);
    }

    @Override
    protected void createConfigFiles(Map<String, String> replacementData) throws IOException {
        Path specFile = specFile();

        // prepare spec file
        env.createResource("template.spec")
                .setCategory(I18N.getString("resource.rpm-spec-file"))
                .setSubstitutionData(replacementData)
                .saveToFile(specFile);
    }

    @Override
    protected Map<String, String> createReplacementData() {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_RELEASE", pkg.release().orElseThrow());
        data.put("APPLICATION_PREFIX", installPrefix().toString());
        data.put("APPLICATION_DIRECTORY", Path.of("/").resolve(pkg.relativeInstallDir()).toString());
        data.put("APPLICATION_SUMMARY", pkg.app().name());
        data.put("APPLICATION_LICENSE_TYPE", pkg.licenseType());

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
            return Executor.of(sysEnv.rpm().toString(),
                "-q", "--queryformat", "%{name}\\n",
                "-q", "--whatprovides", file.toString()
            ).saveOutput(true).executeExpectSuccess().getOutput().stream();
        });
    }

    @Override
    protected List<? extends Exception> findErrorsInOutputPackage() throws IOException {
        List<ConfigException> errors = new ArrayList<>();

        var specFileName = specFile().getFileName().toString();

        var properties = List.of(
                new PackageProperty("Name", pkg.packageName(),
                        "APPLICATION_PACKAGE", specFileName),
                new PackageProperty("Version", pkg.version(),
                        "APPLICATION_VERSION", specFileName),
                new PackageProperty("Release", pkg.release().orElseThrow(),
                        "APPLICATION_RELEASE", specFileName),
                new PackageProperty("Arch", pkg.arch(), null, specFileName));

        var actualValues = Executor.of(
                sysEnv.rpm().toString(),
                "-qp",
                "--queryformat", properties.stream().map(e -> String.format("%%{%s}", e.name)).collect(joining("\\n")),
                outputPackageFile().toString()
        ).saveOutput(true).executeExpectSuccess().getOutput();

        for (int i = 0; i != properties.size(); i++) {
            Optional.ofNullable(properties.get(i).verifyValue(actualValues.get(i))).ifPresent(errors::add);
        }

        return errors;
    }

    @Override
    protected void buildPackage() throws IOException {

        Path rpmFile = outputPackageFile();

        //run rpmbuild
        Executor.of(sysEnv.rpmbuild().toString(),
                "-bb", specFile().toAbsolutePath().toString(),
                "--define", String.format("%%_sourcedir %s",
                        env.appImageDir().toAbsolutePath()),
                // save result to output dir
                "--define", String.format("%%_rpmdir %s", rpmFile.getParent()),
                // do not use other system directories to build as current user
                "--define", String.format("%%_topdir %s",
                        env.buildRoot().toAbsolutePath()),
                "--define", String.format("%%_rpmfilename %s", rpmFile.getFileName())
        ).executeExpectSuccess();
    }

    private Path installPrefix() {
        Path path = pkg.relativeInstallDir();
        if (!pkg.isInstallDirInUsrTree()) {
            path = path.getParent();
        }
        return Path.of("/").resolve(path);
    }

    private Path specFile() {
        return env.buildRoot().resolve(Path.of("SPECS", pkg.packageName() + ".spec"));
    }

    private final LinuxRpmSystemEnvironment sysEnv;
}
