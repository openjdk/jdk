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

import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LinuxDebPackage;
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
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.AppImageLayout;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;

public class LinuxDebBundler extends LinuxPackageBundler {

    private static final String TOOL_DPKG_DEB = "dpkg-deb";
    private static final String TOOL_DPKG = "dpkg";
    private static final String TOOL_FAKEROOT = "fakeroot";

    public LinuxDebBundler() {
        super(LinuxFromParams.DEB_PACKAGE);
    }

    @Override
    protected void doValidate(BuildEnv env, LinuxPackage pkg) throws ConfigException {

        // Show warning if license file is missing
        if (pkg.licenseFile().isEmpty()) {
            Log.verbose(I18N.getString("message.debs-like-licenses"));
        }
    }

    @Override
    protected List<ToolValidator> getToolValidators() {
        return Stream.of(TOOL_DPKG_DEB, TOOL_DPKG, TOOL_FAKEROOT).map(
                ToolValidator::new).toList();
    }

    @Override
    protected void createConfigFiles(Map<String, String> replacementData,
            BuildEnv env, LinuxPackage pkg) throws IOException {
        prepareProjectConfig(replacementData, env, pkg);
        adjustPermissionsRecursive(env.appImageDir());
    }

    @Override
    protected Path buildPackageBundle(BuildEnv env, LinuxPackage pkg,
            Path outputParentDir) throws PackagerException, IOException {
        return buildDeb(env, pkg, outputParentDir);
    }

    private static final Pattern PACKAGE_NAME_REGEX = Pattern.compile("^(^\\S+):");

    @Override
    protected void initLibProvidersLookup(LibProvidersLookup libProvidersLookup) {

        libProvidersLookup.setPackageLookup(file -> {
            Path realPath = file.toRealPath();

            try {
                // Try the real path first as it works better on newer Ubuntu versions
                return findProvidingPackages(realPath);
            } catch (IOException ex) {
                // Try the default path if differ
                if (!realPath.toString().equals(file.toString())) {
                    return findProvidingPackages(file);
                } else {
                    throw ex;
                }
            }
        });
    }

    private static Stream<String> findProvidingPackages(Path file) throws IOException {
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

        var debArch = LinuxPackageArch.getValue(LINUX_DEB);

        Executor.of(TOOL_DPKG, "-S", file.toString())
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

    @Override
    protected List<ConfigException> verifyOutputBundle(BuildEnv env, LinuxPackage pkg,
            Path packageBundle) {
        List<ConfigException> errors = new ArrayList<>();

        String controlFileName = "control";

        List<PackageProperty> properties = List.of(
                new PackageProperty("Package", pkg.packageName(),
                        "APPLICATION_PACKAGE", controlFileName),
                new PackageProperty("Version", ((LinuxDebPackage)pkg).versionWithRelease(),
                        "APPLICATION_VERSION_WITH_RELEASE",
                        controlFileName),
                new PackageProperty("Architecture", pkg.arch(), "APPLICATION_ARCH", controlFileName));

        List<String> cmdline = new ArrayList<>(List.of(TOOL_DPKG_DEB, "-f",
                packageBundle.toString()));
        properties.forEach(property -> cmdline.add(property.name));
        try {
            Map<String, String> actualValues = Executor.of(cmdline.toArray(String[]::new))
                    .saveOutput(true)
                    .executeExpectSuccess()
                    .getOutput().stream()
                            .map(line -> line.split(":\\s+", 2))
                            .collect(Collectors.toMap(
                                    components -> components[0],
                                    components -> components[1]));
            properties.forEach(property -> errors.add(property.verifyValue(
                    actualValues.get(property.name))));
        } catch (IOException ex) {
            // Ignore error as it is not critical. Just report it.
            Log.verbose(ex);
        }

        return errors;
    }

    /*
     * set permissions with a string like "rwxr-xr-x"
     *
     * This cannot be directly backport to 22u which is built with 1.6
     */
    private void setPermissions(Path file, String permissions) {
        Set<PosixFilePermission> filePermissions =
                PosixFilePermissions.fromString(permissions);
        try {
            if (Files.exists(file)) {
                Files.setPosixFilePermissions(file, filePermissions);
            }
        } catch (IOException ex) {
            Log.error(ex.getMessage());
            Log.verbose(ex);
        }

    }

    public static boolean isDebian() {
        // we are just going to run "dpkg -s coreutils" and assume Debian
        // or deritive if no error is returned.
        try {
            Executor.of(TOOL_DPKG, "-s", "coreutils").executeExpectSuccess();
            return true;
        } catch (IOException e) {
            // just fall thru
        }
        return false;
    }

    private void adjustPermissionsRecursive(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs)
                    throws IOException {
                if (file.endsWith(".so") || !Files.isExecutable(file)) {
                    setPermissions(file, "rw-r--r--");
                } else if (Files.isExecutable(file)) {
                    setPermissions(file, "rwxr-xr-x");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                if (e == null) {
                    setPermissions(dir, "rwxr-xr-x");
                    return FileVisitResult.CONTINUE;
                } else {
                    // directory iteration failed
                    throw e;
                }
            }
        });
    }

    private class DebianFile {

        DebianFile(Path dstFilePath, String comment) {
            this.dstFilePath = dstFilePath;
            this.comment = comment;
        }

        DebianFile setExecutable() {
            permissions = "rwxr-xr-x";
            return this;
        }

        void create(Map<String, String> data, Function<String, OverridableResource> resourceFactory)
                throws IOException {
            resourceFactory.apply("template." + dstFilePath.getFileName().toString())
                    .setCategory(I18N.getString(comment))
                    .setSubstitutionData(data)
                    .saveToFile(dstFilePath);
            if (permissions != null) {
                setPermissions(dstFilePath, permissions);
            }
        }

        private final Path dstFilePath;
        private final String comment;
        private String permissions;
    }

    private void prepareProjectConfig(Map<String, String> data, BuildEnv env, LinuxPackage pkg) throws IOException {

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

        ((LinuxDebPackage)pkg).relativeCopyrightFilePath().ifPresent(copyrightFile -> {
            debianFiles.add(new DebianFile(env.appImageDir().resolve(copyrightFile),
                    "resource.copyright-file"));
        });

        for (DebianFile debianFile : debianFiles) {
            debianFile.create(data, env::createResource);
        }
    }

    @Override
    protected Map<String, String> createReplacementData(BuildEnv env, LinuxPackage pkg) throws IOException {
        Map<String, String> data = new HashMap<>();

        String licenseText = pkg.licenseFile().map(toFunction(Files::readString)).orElse("Unknown");

        data.put("APPLICATION_MAINTAINER", ((LinuxDebPackage) pkg).maintainer());
        data.put("APPLICATION_SECTION", pkg.category().orElseThrow());
        data.put("APPLICATION_COPYRIGHT", pkg.app().copyright());
        data.put("APPLICATION_LICENSE_TEXT", licenseText);
        data.put("APPLICATION_ARCH", pkg.arch());
        data.put("APPLICATION_INSTALLED_SIZE", Long.toString(
                AppImageLayout.toPathGroup(pkg.packageLayout().resolveAt(
                        env.appImageDir())).sizeInBytes() >> 10));
        data.put("APPLICATION_HOMEPAGE", pkg.aboutURL().map(
                value -> "Homepage: " + value).orElse(""));
        data.put("APPLICATION_VERSION_WITH_RELEASE", ((LinuxDebPackage) pkg).versionWithRelease());

        return data;
    }

    private Path buildDeb(BuildEnv env, LinuxPackage pkg, Path outdir) throws IOException {
        Path outFile = outdir.resolve(pkg.packageFileNameWithSuffix());
        Log.verbose(I18N.format("message.outputting-to-location", outFile.toAbsolutePath()));

        List<String> cmdline = new ArrayList<>();
        cmdline.addAll(List.of(TOOL_FAKEROOT, TOOL_DPKG_DEB));
        if (Log.isVerbose()) {
            cmdline.add("--verbose");
        }
        cmdline.addAll(List.of("-b", env.appImageDir().toString(),
                outFile.toAbsolutePath().toString()));

        // run dpkg
        RetryExecutor.retryOnKnownErrorMessage(
                "semop(1): encountered an error: Invalid argument").execute(
                        cmdline.toArray(String[]::new));

        Log.verbose(I18N.format("message.output-to-location", outFile.toAbsolutePath()));

        return outFile;
    }

    @Override
    public String getName() {
        return I18N.getString("deb.bundler.name");
    }

    @Override
    public String getID() {
        return "deb";
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return OperatingSystem.isLinux() && (new ToolValidator(TOOL_DPKG_DEB).validate() == null);
    }

    @Override
    public boolean isDefault() {
        return isDebian();
    }
}
