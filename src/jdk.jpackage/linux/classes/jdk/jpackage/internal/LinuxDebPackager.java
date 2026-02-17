/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LinuxDebPackage;

final class LinuxDebPackager extends LinuxPackager<LinuxDebPackage> {

    LinuxDebPackager(BuildEnv env, LinuxDebPackage pkg, Path outputDir, LinuxDebSystemEnvironment sysEnv) {
        super(env, pkg, outputDir, sysEnv);
        this.sysEnv = Objects.requireNonNull(sysEnv);
    }

    @Override
    protected void createConfigFiles(Map<String, String> replacementData) throws IOException {
        prepareProjectConfig(replacementData);
        adjustPermissionsRecursive();
    }

    @Override
    protected void initLibProvidersLookup(LibProvidersLookup libProvidersLookup) {

        libProvidersLookup.setPackageLookup(file -> {
            Path realPath = file.toRealPath();

            try {
                // Try the real path first as it works better on newer Ubuntu versions
                return findProvidingPackages(realPath, sysEnv);
            } catch (IOException ex) {
                // Try the default path if differ
                if (!realPath.equals(file)) {
                    return findProvidingPackages(file, sysEnv);
                } else {
                    throw ex;
                }
            }
        });
    }

    @Override
    protected List<? extends Exception> findErrorsInOutputPackage() throws IOException {
        List<ConfigException> errors = new ArrayList<>();

        var controlFileName = "control";

        List<PackageProperty> properties = List.of(
                new PackageProperty("Package", pkg.packageName(),
                        "APPLICATION_PACKAGE", controlFileName),
                new PackageProperty("Version", pkg.versionWithRelease(),
                        "APPLICATION_VERSION_WITH_RELEASE",
                        controlFileName),
                new PackageProperty("Architecture", pkg.arch(), "APPLICATION_ARCH", controlFileName));

        List<String> cmdline = new ArrayList<>(List.of(
                sysEnv.dpkgdeb().toString(), "-f", outputPackageFile().toString()));

        properties.forEach(property -> cmdline.add(property.name));

        Map<String, String> actualValues = Executor.of(cmdline)
                .saveOutput(true)
                .executeExpectSuccess()
                .getOutput().stream()
                        .map(line -> line.split(":\\s+", 2))
                        .collect(Collectors.toMap(
                                components -> components[0],
                                components -> components[1]));

        for (var property : properties) {
            Optional.ofNullable(property.verifyValue(actualValues.get(property.name))).ifPresent(errors::add);
        }

        return errors;
    }

    @Override
    protected Map<String, String> createReplacementData() throws IOException {
        Map<String, String> data = new HashMap<>();

        String licenseText = pkg.licenseFile().map(toFunction(Files::readString)).orElse("Unknown");

        data.put("APPLICATION_MAINTAINER", pkg.maintainer());
        data.put("APPLICATION_SECTION", pkg.category().orElseThrow());
        data.put("APPLICATION_COPYRIGHT", pkg.app().copyright());
        data.put("APPLICATION_LICENSE_TEXT", licenseText);
        data.put("APPLICATION_ARCH", pkg.arch());
        data.put("APPLICATION_INSTALLED_SIZE", Long.toString(
                AppImageLayout.toPathGroup(env.appImageLayout()).sizeInBytes() >> 10));
        data.put("APPLICATION_HOMEPAGE", pkg.aboutURL().map(
                value -> "Homepage: " + value).orElse(""));
        data.put("APPLICATION_VERSION_WITH_RELEASE", pkg.versionWithRelease());

        return data;
    }

    @Override
    protected void buildPackage() throws IOException {

        Path debFile = outputPackageFile();

        List<String> cmdline = new ArrayList<>();
        Stream.of(sysEnv.fakeroot(), sysEnv.dpkgdeb()).map(Path::toString).forEach(cmdline::add);
        if (Log.isVerbose()) {
            cmdline.add("--verbose");
        }
        cmdline.addAll(List.of("-b", env.appImageDir().toString(), debFile.toAbsolutePath().toString()));

        // run dpkg
        Executor.of(cmdline).retryOnKnownErrorMessage(
                "semop(1): encountered an error: Invalid argument").execute();
    }

    @Override
    public void accept(PackagingPipeline.Builder pipelineBuilder) {
        super.accept(pipelineBuilder);

        // Build deb config files after app image contents are ready because
        // it calculates the size of the image and saves the value in one of the config files.
        pipelineBuilder.configuredTasks().filter(task -> {
            return PackageTaskID.CREATE_CONFIG_FILES.equals(task.task());
        }).findFirst().orElseThrow()
                .addDependencies(PrimaryTaskID.BUILD_APPLICATION_IMAGE, PrimaryTaskID.COPY_APP_IMAGE)
                .add();
    }

    private void adjustPermissionsRecursive() throws IOException {
        Files.walkFileTree(env.appImageDir(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.endsWith(".so") || !Files.isExecutable(file)) {
                    Files.setPosixFilePermissions(file, SO_PERMISSIONS);
                } else if (Files.isExecutable(file)) {
                    Files.setPosixFilePermissions(file, EXECUTABLE_PERMISSIONS);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e == null) {
                    Files.setPosixFilePermissions(dir, FOLDER_PERMISSIONS);
                    return FileVisitResult.CONTINUE;
                } else {
                    // directory iteration failed
                    throw e;
                }
            }
        });
    }

    private void prepareProjectConfig(Map<String, String> data) throws IOException {

        Path configDir = env.appImageDir().resolve("DEBIAN");
        List<DebianFile> debianFiles = new ArrayList<>();
        debianFiles.add(new DebianFile(
                configDir.resolve("control"),
                "resource.deb-control-file"));
        debianFiles.add(new DebianFile(
                configDir.resolve("preinst"),
                "resource.deb-preinstall-script").setExecutable());
        debianFiles.add(new DebianFile(
                configDir.resolve("prerm"),
                "resource.deb-prerm-script").setExecutable());
        debianFiles.add(new DebianFile(
                configDir.resolve("postinst"),
                "resource.deb-postinstall-script").setExecutable());
        debianFiles.add(new DebianFile(
                configDir.resolve("postrm"),
                "resource.deb-postrm-script").setExecutable());

        pkg.relativeCopyrightFilePath().ifPresent(copyrightFile -> {
            debianFiles.add(new DebianFile(env.appImageDir().resolve(copyrightFile),
                    "resource.copyright-file"));
        });

        for (DebianFile debianFile : debianFiles) {
            debianFile.create(data, env::createResource);
        }
    }

    private static Stream<String> findProvidingPackages(Path file, LinuxDebSystemEnvironment sysEnv) throws IOException {
        //
        // `dpkg -S` command does glob pattern lookup. If not the absolute path
        // to the file is specified it might return mltiple package names.
        // Even for full paths multiple package names can be returned as
        // it is OK for multiple packages to provide the same file. `/opt`
        // directory is such an example. So we have to deal with multiple
        // packages per file situation.
        //
        // E.g.: `dpkg -S libc.so.6` command reports three packages:
        // libc6-x32: /libx32/libc.so.6
        // libc6:amd64: /lib/x86_64-linux-gnu/libc.so.6
        // libc6-i386: /lib32/libc.so.6
        // `:amd64` is architecture suffix and can (should) be dropped.
        // Still need to decide what package to choose from three.
        // libc6-x32 and libc6-i386 both depend on libc6:
        // $ dpkg -s libc6-x32
        // Package: libc6-x32
        // Status: install ok installed
        // Priority: optional
        // Section: libs
        // Installed-Size: 10840
        // Maintainer: Ubuntu Developers <ubuntu-devel-discuss@lists.ubuntu.com>
        // Architecture: amd64
        // Source: glibc
        // Version: 2.23-0ubuntu10
        // Depends: libc6 (= 2.23-0ubuntu10)
        //
        // We can dive into tracking dependencies, but this would be overly
        // complicated.
        //
        // For simplicity lets consider the following rules:
        // 1. If there is one item in `dpkg -S` output, accept it.
        // 2. If there are multiple items in `dpkg -S` output and there is at
        //  least one item with the default arch suffix (DEB_ARCH),
        //  accept only these items.
        // 3. If there are multiple items in `dpkg -S` output and there are
        //  no with the default arch suffix (DEB_ARCH), accept all items.
        // So lets use this heuristics: don't accept packages for whom
        //  `dpkg -p` command fails.
        // 4. Arch suffix should be stripped from accepted package names.
        //

        Set<String> archPackages = new HashSet<>();
        Set<String> otherPackages = new HashSet<>();

        var debArch = sysEnv.packageArch().value();

        Executor.of(sysEnv.dpkg().toString(), "-S", file.toString())
                .saveOutput(true).executeExpectSuccess()
                .getOutput().forEach(line -> {
                    Matcher matcher = PACKAGE_NAME_REGEX.matcher(line);
                    if (matcher.find()) {
                        String name = matcher.group(1);
                        if (name.endsWith(":" + debArch)) {
                            // Strip arch suffix
                            name = name.substring(0,
                                    name.length() - (debArch.length() + 1));
                            archPackages.add(name);
                        } else {
                            otherPackages.add(name);
                        }
                    }
                });

        if (!archPackages.isEmpty()) {
            return archPackages.stream();
        }
        return otherPackages.stream();
    }


    private static final class DebianFile {

        DebianFile(Path dstFilePath, String comment) {
            this.dstFilePath = Objects.requireNonNull(dstFilePath);
            this.comment = Objects.requireNonNull(comment);
        }

        DebianFile setExecutable() {
            permissions = EXECUTABLE_PERMISSIONS;
            return this;
        }

        void create(Map<String, String> data, Function<String, OverridableResource> resourceFactory)
                throws IOException {
            resourceFactory.apply("template." + dstFilePath.getFileName().toString())
                    .setCategory(I18N.getString(comment))
                    .setSubstitutionData(data)
                    .saveToFile(dstFilePath);
            if (permissions != null) {
                Files.setPosixFilePermissions(dstFilePath, permissions);
            }
        }

        private final Path dstFilePath;
        private final String comment;
        private Set<PosixFilePermission> permissions;
    }


    private final LinuxDebSystemEnvironment sysEnv;

    private static final Pattern PACKAGE_NAME_REGEX = Pattern.compile("^(^\\S+):");

    private static final Set<PosixFilePermission> EXECUTABLE_PERMISSIONS = PosixFilePermissions.fromString("rwxr-xr-x");
    private static final Set<PosixFilePermission> FOLDER_PERMISSIONS = PosixFilePermissions.fromString("rwxr-xr-x");
    private static final Set<PosixFilePermission> SO_PERMISSIONS = PosixFilePermissions.fromString("rw-r--r--");
}
