/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;

public final class WinExecutableIconVerifier {

    static void verifyLauncherIcon(JPackageCommand cmd, String launcherName,
            Path expectedIcon, boolean expectedDefault) {
        Objects.requireNonNull(cmd);
        INSTANCE.verifyExecutablesHaveSameIcon(new Input() {
            @Override
            public Path executableWithExpectedIcon(Path iconWorkDir) throws IOException {
                final var iconContainer = iconWorkDir.resolve("container.exe");
                Files.createDirectories(iconContainer.getParent());
                Files.copy(INSTANCE.getDefaultAppLauncher(expectedIcon == null
                        && !expectedDefault), iconContainer);
                if (expectedIcon != null) {
                    Executor.tryRunMultipleTimes(() -> {
                        INSTANCE.setIcon(expectedIcon, iconContainer);
                    }, 3, 5);
                }
                return iconContainer;
            }

            @Override
            public Path executableWithActualIcon(Path iconWorkDir) {
                return cmd.appLauncherPath(launcherName);
            }

            @Override
            public void trace(Path extractedActualIcon, Path extractedExpectedIcon) {
                TKit.trace(String.format(
                        "Check icon file [%s] of %s launcher is a copy of source icon file [%s]",
                        extractedActualIcon,
                        Optional.ofNullable(launcherName).orElse("main"),
                        extractedExpectedIcon));
            }
        });
    }

    public static void verifyExecutablesHaveSameIcon(Path executableWithExpectedIcon,
            Path executableWithActualIcon) {
        Objects.requireNonNull(executableWithExpectedIcon);
        Objects.requireNonNull(executableWithActualIcon);
        INSTANCE.verifyExecutablesHaveSameIcon(new Input() {
            @Override
            public Path executableWithExpectedIcon(Path iconWorkDir) {
                return executableWithExpectedIcon;
            }

            @Override
            public Path executableWithActualIcon(Path iconWorkDir) {
                return executableWithActualIcon;
            }

            @Override
            public void trace(Path extractedActualIcon, Path extractedExpectedIcon) {
                TKit.trace(String.format(
                        "Check icon file [%s] extracted from [%s] executable is a copy of icon file [%s] extracted from [%s] executable",
                        extractedActualIcon, executableWithActualIcon,
                        extractedExpectedIcon, executableWithExpectedIcon));
            }
        });
    }

    private interface Input {
        Path executableWithExpectedIcon(Path iconWorkDir) throws IOException;
        Path executableWithActualIcon(Path iconWorkDir) throws IOException;
        void trace(Path extractedActualIcon, Path extractedExpectedIcon);
    }

    private void verifyExecutablesHaveSameIcon(Input input) {

        Objects.requireNonNull(input);

        TKit.withTempDirectory("icons", iconWorkDir -> {

            final var executableWithExpectedIcon = input.executableWithExpectedIcon(iconWorkDir);
            final var executableWithActualIcon = input.executableWithActualIcon(iconWorkDir);

            if (Files.isSameFile(executableWithExpectedIcon, executableWithActualIcon)) {
                throw new IllegalArgumentException("Supply different files for icon comparison");
            }

            Path extractedExpectedIcon = extractIconFromExecutable(
                    iconWorkDir, executableWithExpectedIcon, "expected");
            Path extractedActualIcon = extractIconFromExecutable(iconWorkDir,
                    executableWithActualIcon, "actual");

            input.trace(extractedActualIcon, extractedExpectedIcon);

            // If executable doesn't have an icon, icon file will be empty.
            // Both icon files must be empty or not empty.
            // If only one icon file is empty executables have different icons.
            final var expectedIconIsEmpty = isFileEmpty(extractedExpectedIcon);
            final var actualIconIsEmpty = isFileEmpty(extractedActualIcon);

            TKit.assertTrue(expectedIconIsEmpty == actualIconIsEmpty,
                    "Check both icon files are empty or not empty");

            if (!expectedIconIsEmpty && Files.mismatch(extractedExpectedIcon, extractedActualIcon) != -1) {
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

    private WinExecutableIconVerifier() {
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

            iconSwap = executableRebranderClass.getDeclaredMethod(
                    "iconSwap", long.class, String.class);
            iconSwap.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException
                | SecurityException ex) {
            throw rethrowUnchecked(ex);
        }
    }

    private Path extractIconFromExecutable(Path outputDir, Path executable,
            String extractedIconFilename) {

        Objects.requireNonNull(outputDir);
        Objects.requireNonNull(executable);
        Objects.requireNonNull(extractedIconFilename);

        Path extractedIcon = outputDir.resolve(extractedIconFilename + ".bmp");

        Executor.of("powershell", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Unrestricted",
                "-File", EXTRACT_ICON_PS1.toString(),
                "-InputExecutable", executable.toAbsolutePath().normalize().toString(),
                "-OutputIcon", extractedIcon.toAbsolutePath().normalize().toString()
        ).executeAndRepeatUntilExitCode(0, 5, 10);

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

    private void setIcon(Path iconPath, Path executable) {
        Objects.requireNonNull(iconPath);
        Objects.requireNonNull(executable);

        try {
            executable.toFile().setWritable(true, true);
            try {
                long lock = 0;
                try {
                    lock = (Long) lockResource.invoke(null, new Object[]{
                            executable.toAbsolutePath().normalize().toString()});
                    if (lock == 0) {
                        throw new RuntimeException(String.format(
                                "Failed to lock [%s] executable",
                                executable));
                    }
                    var exitCode = (Integer) iconSwap.invoke(null, new Object[]{
                        lock,
                        iconPath.toAbsolutePath().normalize().toString()});
                    if (exitCode != 0) {
                        throw new RuntimeException(String.format(
                                "Failed to swap icon of [%s] executable",
                                executable));
                    }
                } finally {
                    if (lock != 0) {
                        unlockResource.invoke(null, new Object[]{lock});
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw rethrowUnchecked(ex);
            }
        } finally {
            executable.toFile().setWritable(false, true);
        }
    }

    private static boolean isFileEmpty(Path file) {
        return file.toFile().length() == 0;
    }

    private final Class<?> executableRebranderClass;
    private final Method lockResource;
    private final Method unlockResource;
    private final Method iconSwap;

    private static final WinExecutableIconVerifier INSTANCE = new WinExecutableIconVerifier();

    private static final Path EXTRACT_ICON_PS1 = TKit.TEST_SRC_ROOT.resolve(Path.of("resources/read-executable-icon.ps1")).normalize();
}
