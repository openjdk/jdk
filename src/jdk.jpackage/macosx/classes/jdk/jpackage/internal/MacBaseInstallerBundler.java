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

import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE_FILE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.SIGN_BUNDLE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.ConfigException;

public abstract class MacBaseInstallerBundler extends AbstractBundler {

    public MacBaseInstallerBundler() {
        appImageBundler = new MacAppBundler();
    }

    protected void validateAppImageAndBundeler(
            Map<String, ? super Object> params) throws ConfigException {
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) != null) {
            Path applicationImage = PREDEFINED_APP_IMAGE.fetchFrom(params);
            if (new MacAppImageFileExtras(PREDEFINED_APP_IMAGE_FILE.fetchFrom(params)).signed()) {
                var appLayout = ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT.resolveAt(applicationImage);
                if (!Files.exists(
                        PackageFile.getPathInAppImage(appLayout))) {
                    Log.info(MessageFormat.format(I18N.getString(
                            "warning.per.user.app.image.signed"),
                            PackageFile.getPathInAppImage(appLayout)));
                }
            } else {
                if (Optional.ofNullable(
                        SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.FALSE)) {
                    // if signing bundle with app-image, warn user if app-image
                    // is not already signed.
                    Log.info(MessageFormat.format(I18N.getString(
                            "warning.unsigned.app.image"), getID()));
                }
            }
        } else if (StandardBundlerParam.isRuntimeInstaller(params)) {
            // Call appImageBundler.validate(params); to validate signing
            // requirements.
            appImageBundler.validate(params);

            Path runtimeImage = PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);

            // Make sure we have valid runtime image.
            if (!isRuntimeImageJDKBundle(runtimeImage)
                    && !isRuntimeImageJDKImage(runtimeImage)) {
                throw new ConfigException(
                    MessageFormat.format(I18N.getString(
                    "message.runtime-image-invalid"),
                    runtimeImage.toString()),
                    I18N.getString(
                    "message.runtime-image-invalid.advice"));
            }
        } else {
            appImageBundler.validate(params);
        }
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    // JDK bundle: "Contents/Home", "Contents/MacOS/libjli.dylib"
    // and "Contents/Info.plist"
    private static boolean isRuntimeImageJDKBundle(Path runtimeImage) {
        Path path1 = runtimeImage.resolve("Contents/Home");
        Path path2 = runtimeImage.resolve("Contents/MacOS/libjli.dylib");
        Path path3 = runtimeImage.resolve("Contents/Info.plist");
        return IOUtils.exists(path1)
                && path1.toFile().list() != null
                && path1.toFile().list().length > 0
                && IOUtils.exists(path2)
                && IOUtils.exists(path3);
    }

    // JDK image: "lib/*/libjli.dylib"
    static boolean isRuntimeImageJDKImage(Path runtimeImage) {
        final Path jliName = Path.of("libjli.dylib");
        try (Stream<Path> walk = Files.walk(runtimeImage.resolve("lib"))) {
            final Path jli = walk
                    .filter(file -> file.getFileName().equals(jliName))
                    .findFirst()
                    .get();
            return IOUtils.exists(jli);
        } catch (IOException | NoSuchElementException ex) {
            Log.verbose(ex);
            return false;
        }
    }

    private final Bundler appImageBundler;
}
