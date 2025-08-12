/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.model.WinApplication;
import jdk.jpackage.internal.model.WinExePackage;
import jdk.jpackage.internal.model.WinLauncher;

@SuppressWarnings("restricted")
final class ExecutableRebrander {

    ExecutableRebrander(WinExePackage pkg,
            Function<String, OverridableResource> resourceSupplier,
            UpdateResourceAction... extraActions) {
        this(ExecutableProperties.create(pkg), resourceSupplier.apply(
                "WinInstaller.template").setPublicName("WinInstaller.properties"),
                extraActions);
    }

    ExecutableRebrander(WinApplication app, WinLauncher launcher,
            Function<String, OverridableResource> resourceSupplier,
            UpdateResourceAction... extraActions) {
        this(ExecutableProperties.create(app, launcher), resourceSupplier.apply(
                "WinLauncher.template").setPublicName(launcher.executableName() + ".properties"),
                extraActions);
    }

    private ExecutableRebrander(ExecutableProperties props,
            OverridableResource propertiesFileResource,
            UpdateResourceAction... extraActions) {
        this.extraActions = List.of(extraActions);
        this.propertiesFileResource = Objects.requireNonNull(propertiesFileResource);

        this.props = new HashMap<>();

        validateValueAndPut(this.props, Map.entry("COMPANY_NAME", props.vendor), "vendor");
        validateValueAndPut(this.props, Map.entry("FILE_DESCRIPTION",props.description), "description");
        validateValueAndPut(this.props, Map.entry("FILE_VERSION", props.version.toString()), "version");
        validateValueAndPut(this.props, Map.entry("LEGAL_COPYRIGHT", props.copyright), "copyright");
        validateValueAndPut(this.props, Map.entry("PRODUCT_NAME", props.name), "name");

        this.props.put("FIXEDFILEINFO_FILE_VERSION", toFixedFileVersion(props.version));
        this.props.put("INTERNAL_NAME", props.executableName);
        this.props.put("ORIGINAL_FILENAME", props.executableName);
    }

    void execute(BuildEnv env, Path target, Optional<Path> icon) {
        String[] propsArray = toStringArray(propertiesFileResource, props);

        UpdateResourceAction versionSwapper = resourceLock -> {
            if (versionSwap(resourceLock, propsArray) != 0) {
                throw I18N.buildException().message("error.version-swap", target).create(RuntimeException::new);
            }
        };

        Optional<UpdateResourceAction> updateIcon = icon
                .map(Path::toAbsolutePath)
                .map(ShortPathUtils::adjustPath)
                .map(absIcon -> {
                    return resourceLock -> {
                        if (iconSwap(resourceLock, absIcon.toString()) != 0) {
                            throw I18N.buildException().message("error.icon-swap", absIcon).create(RuntimeException::new);
                        }
                    };
                });

        try {
            final List<UpdateResourceAction> actions = new ArrayList<>();
            actions.add(versionSwapper);
            updateIcon.ifPresent(actions::add);
            actions.addAll(extraActions);
            rebrandExecutable(env, target, actions);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void rebrandExecutable(BuildEnv env, final Path target,
            List<UpdateResourceAction> actions) throws IOException {
        Objects.requireNonNull(actions);
        actions.forEach(Objects::requireNonNull);
        try {
            String tempDirectory = env.buildRoot().toAbsolutePath().toString();
            if (WindowsDefender.isThereAPotentialWindowsDefenderIssue(tempDirectory)) {
                Log.verbose(I18N.format("message.potential.windows.defender.issue", tempDirectory));
            }

            target.toFile().setWritable(true, true);

            var shortTargetPath = ShortPathUtils.toShortPath(target);
            long resourceLock = lockResource(shortTargetPath.orElse(target).toString());
            if (resourceLock == 0) {
                throw I18N.buildException().message("error.lock-resource", shortTargetPath.orElse(target)).create(RuntimeException::new);
            }

            final boolean resourceUnlockedSuccess;
            try {
                for (var action : actions) {
                    action.editResource(resourceLock);
                }
            } finally {
                if (resourceLock == 0) {
                    resourceUnlockedSuccess = true;
                } else {
                    resourceUnlockedSuccess = unlockResource(resourceLock);
                    if (shortTargetPath.isPresent()) {
                        // Windows will rename the excuatble in the unlock operation.
                        // Should restore executable's name.
                        var tmpPath = target.getParent().resolve(
                                target.getFileName().toString() + ".restore");
                        Files.move(shortTargetPath.get(), tmpPath);
                        Files.move(tmpPath, target);
                    }
                }
            }

            if (!resourceUnlockedSuccess) {
                throw I18N.buildException().message("error.unlock-resource", target).create(RuntimeException::new);
            }
        } finally {
            target.toFile().setReadOnly();
        }
    }

    private static String toFixedFileVersion(DottedVersion value) {
        int addComponentsCount = 4 - value.getComponents().length;
        if (addComponentsCount > 0) {
            StringBuilder sb = new StringBuilder(value.toComponentsString());
            do {
                sb.append('.');
                sb.append(0);
            } while (--addComponentsCount > 0);
            return sb.toString();
        }
        return value.toComponentsString();
    }

    private static String[] toStringArray(
            OverridableResource propertiesFileResource,
            Map<String, String> props) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            propertiesFileResource
                    .setSubstitutionData(props)
                    .setCategory(I18N.getString(
                            "resource.executable-properties-template"))
                    .saveToStream(buffer);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(
                buffer.toByteArray()), StandardCharsets.UTF_8)) {
            var configProp = new Properties();
            configProp.load(reader);
            return configProp.entrySet().stream().flatMap(e -> Stream.of(
                    e.getKey().toString(), e.getValue().toString())).toArray(
                    String[]::new);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void validateValueAndPut(Map<String, String> target,
            Map.Entry<String, String> e, String label) {
        if (e.getValue().contains("\r") || e.getValue().contains("\n")) {
            Log.error("Configuration parameter " + label
                    + " contains multiple lines of text, ignore it");
            e = Map.entry(e.getKey(), "");
        }
        target.put(e.getKey(), e.getValue());
    }

    @FunctionalInterface
    static interface UpdateResourceAction {
        public void editResource(long resourceLock) throws IOException;
    }

    private static record ExecutableProperties(String vendor, String description,
            DottedVersion version, String copyright, String name, String executableName) {
        static ExecutableProperties create(WinApplication app,
                WinLauncher launcher) {
            return new ExecutableProperties(app.vendor(), launcher.description(),
                    app.winVersion(), app.copyright(), launcher.name(),
                    launcher.executableNameWithSuffix());
        }

        static ExecutableProperties create(WinExePackage pkg) {
            return new ExecutableProperties(pkg.app().vendor(),
                    pkg.description(), DottedVersion.lazy(pkg.version()),
                    pkg.app().copyright(), pkg.packageName(),
                    pkg.packageFileNameWithSuffix());
        }
    }

    private final Map<String, String> props;
    private final List<UpdateResourceAction> extraActions;
    private final OverridableResource propertiesFileResource;

    static {
        System.loadLibrary("jpackage");
    }

    private static native long lockResource(String executable);

    private static native boolean unlockResource(long resourceLock);

    private static native int iconSwap(long resourceLock, String newIcon);

    private static native int versionSwap(long resourceLock, String[] executableProperties);
}
