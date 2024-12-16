/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;

public final class LauncherIconVerifier {
    public LauncherIconVerifier() {
    }

    public LauncherIconVerifier setLauncherName(String v) {
        launcherName = v;
        return this;
    }

    public LauncherIconVerifier setExpectedIcon(Path v) {
        expectedIcon = v;
        return this;
    }

    public LauncherIconVerifier setExpectedDefaultIcon() {
        expectedDefault = true;
        return this;
    }

    public void applyTo(JPackageCommand cmd) throws IOException {
        final String curLauncherName;
        final String label;
        if (launcherName == null) {
            curLauncherName = cmd.name();
            label = "main";
        } else {
            curLauncherName = launcherName;
            label = String.format("[%s]", launcherName);
        }

        Path iconPath = cmd.appLayout().destktopIntegrationDirectory().resolve(
                curLauncherName + TKit.ICON_SUFFIX);

        if (TKit.isWindows()) {
            TKit.assertPathExists(iconPath, false);
            WinIconVerifier.instance.verifyLauncherIcon(cmd, launcherName,
                    expectedIcon, expectedDefault);
        } else if (expectedDefault) {
            TKit.assertPathExists(iconPath, true);
        } else if (expectedIcon == null) {
            TKit.assertPathExists(iconPath, false);
        } else {
            TKit.assertFileExists(iconPath);
            TKit.assertTrue(-1 == Files.mismatch(expectedIcon, iconPath),
                    String.format(
                    "Check icon file [%s] of %s launcher is a copy of source icon file [%s]",
                    iconPath, label, expectedIcon));
        }
    }

    private static class WinIconVerifier {

        void verifyLauncherIcon(JPackageCommand cmd, String launcherName,
                Path expectedIcon, boolean expectedDefault) {
            TKit.withTempDirectory("icons", tmpDir -> {
                Path launcher = cmd.appLauncherPath(launcherName);
                Path iconWorkDir = tmpDir.resolve(launcher.getFileName());
                Path iconContainer = iconWorkDir.resolve("container.exe");
                Files.createDirectories(iconContainer.getParent());
                Files.copy(getDefaultAppLauncher(expectedIcon == null
                        && !expectedDefault), iconContainer);
                if (expectedIcon != null) {
                    Executor.tryRunMultipleTimes(() -> {
                        setIcon(expectedIcon, iconContainer);
                    }, 3, 5);
                }

                Path extractedExpectedIcon = extractIconFromExecutable(
                        iconWorkDir, iconContainer, "expected");
                Path extractedActualIcon = extractIconFromExecutable(iconWorkDir,
                        launcher, "actual");

                TKit.trace(String.format(
                        "Check icon file [%s] of %s launcher is a copy of source icon file [%s]",
                        extractedActualIcon,
                        Optional.ofNullable(launcherName).orElse("main"),
                        extractedExpectedIcon));

                if (Files.mismatch(extractedExpectedIcon, extractedActualIcon)
                        != -1) {
                    // On Windows11 .NET API extracting icons from executables
                    // produce slightly different output for the same icon.
                    // To workaround it, compare pixels of images and if the
                    // number of off pixels is below a threshold, assume
                    // equality.
                    BufferedImage expectedImg = ImageIO.read(
                            extractedExpectedIcon.toFile());
                    BufferedImage actualImg = ImageIO.read(
                            extractedActualIcon.toFile());

                    int w = expectedImg.getWidth();
                    int h = expectedImg.getHeight();

                    TKit.assertEquals(w, actualImg.getWidth(),
                            "Check expected and actual icons have the same width");
                    TKit.assertEquals(h, actualImg.getHeight(),
                            "Check expected and actual icons have the same height");

                    int diffPixelCount = 0;

                    for (int i = 0; i != w; ++i) {
                        for (int j = 0; j != h; ++j) {
                            int expectedRGB = expectedImg.getRGB(i, j);
                            int actualRGB = actualImg.getRGB(i, j);

                            if (expectedRGB != actualRGB) {
                                TKit.trace(String.format(
                                        "Images mismatch at [%d, %d] pixel", i,
                                        j));
                                diffPixelCount++;
                            }
                        }
                    }

                    double threshold = 0.1;
                    TKit.assertTrue(((double) diffPixelCount) / (w * h)
                            < threshold,
                            String.format(
                                    "Check the number of mismatched pixels [%d] of [%d] is < [%f] threshold",
                                    diffPixelCount, (w * h), threshold));
                }
            });
        }

        private WinIconVerifier() {
            try {
                executableRebranderClass = Class.forName(
                        "jdk.jpackage.internal.ExecutableRebrander");

                lockResource = executableRebranderClass.getDeclaredMethod(
                        "lockResource", String.class);
                // Note: this reflection call requires
                // --add-opens jdk.jpackage/jdk.jpackage.internal=ALL-UNNAMED
                lockResource.setAccessible(true);

                unlockResource = executableRebranderClass.getDeclaredMethod(
                        "unlockResource", long.class);
                unlockResource.setAccessible(true);

                iconSwapWrapper = executableRebranderClass.getDeclaredMethod(
                        "iconSwapWrapper", long.class, String.class);
                iconSwapWrapper.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException
                    | SecurityException ex) {
                throw rethrowUnchecked(ex);
            }
        }

        private Path extractIconFromExecutable(Path outputDir, Path executable,
                String label) {
            // Run .NET code to extract icon from the given executable.
            // ExtractAssociatedIcon() will succeed even if the target file
            // is locked (by an antivirus). It will output a default icon
            // in case of error. To prevent this "fail safe" behavior we try
            // lock the target file with Open() call. If the attempt
            // fails ExtractAssociatedIcon() is not called and the script exits
            // with the exit code that will be trapped
            // inside of Executor.executeAndRepeatUntilExitCode() method that
            // will keep running the script until it succeeds or the number of
            // allowed attempts is exceeded.

            Path extractedIcon = outputDir.resolve(label + ".bmp");
            String script = String.join(";",
                    String.format(
                            "try { [System.io.File]::Open('%s', 'Open', 'Read', 'None') } catch { exit 100 }",
                            executable.toAbsolutePath().normalize()),
                    "[System.Reflection.Assembly]::LoadWithPartialName('System.Drawing')",
                    String.format(
                            "[System.Drawing.Icon]::ExtractAssociatedIcon('%s').ToBitmap().Save('%s', [System.Drawing.Imaging.ImageFormat]::Bmp)",
                            executable.toAbsolutePath().normalize(),
                            extractedIcon.toAbsolutePath().normalize()));

            Executor.of("powershell", "-NoLogo", "-NoProfile", "-Command",
                    script).executeAndRepeatUntilExitCode(0, 5, 10);

            return extractedIcon;
        }

        private Path getDefaultAppLauncher(boolean noIcon) {
            // Create app image with the sole purpose to get the default app launcher
            Path defaultAppOutputDir = TKit.workDir().resolve(String.format(
                    "out-%d", ProcessHandle.current().pid()));
            JPackageCommand cmd = JPackageCommand.helloAppImage().setFakeRuntime().setArgumentValue(
                    "--dest", defaultAppOutputDir);

            String launcherName;
            if (noIcon) {
                launcherName = "no-icon";
                new AdditionalLauncher(launcherName).setNoIcon().applyTo(cmd);
            } else {
                launcherName = null;
            }

            if (!Files.isExecutable(cmd.appLauncherPath(launcherName))) {
                cmd.execute();
            }
            return cmd.appLauncherPath(launcherName);
        }

        private void setIcon(Path iconPath, Path launcherPath) {
            TKit.trace(String.format("Set icon of [%s] launcher to [%s] file",
                    launcherPath, iconPath));
            try {
                launcherPath.toFile().setWritable(true, true);
                try {
                    long lock = 0;
                    try {
                        lock = (Long) lockResource.invoke(null, new Object[]{
                            launcherPath.toAbsolutePath().normalize().toString()});
                        if (lock == 0) {
                            throw new RuntimeException(String.format(
                                    "Failed to lock [%s] executable",
                                    launcherPath));
                        }
                        iconSwapWrapper.invoke(null, new Object[]{lock,
                            iconPath.toAbsolutePath().normalize().toString()});
                    } finally {
                        if (lock != 0) {
                            unlockResource.invoke(null, new Object[]{lock});
                        }
                    }
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw rethrowUnchecked(ex);
                }
            } finally {
                launcherPath.toFile().setWritable(false, true);
            }
        }

        static final WinIconVerifier instance = new WinIconVerifier();

        private final Class executableRebranderClass;
        private final Method lockResource;
        private final Method unlockResource;
        private final Method iconSwapWrapper;
    }

    private String launcherName;
    private Path expectedIcon;
    private boolean expectedDefault;
}
