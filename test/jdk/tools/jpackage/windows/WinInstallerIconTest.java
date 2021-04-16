/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageType;
import static jdk.jpackage.test.RunnablePackageTest.Action.CREATE;
import jdk.jpackage.test.TKit;

/**
 * Test that --icon also changes icon of exe installer.
 */

/*
 * @test
 * @summary jpackage with --icon parameter for exe installer
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build WinInstallerIconTest
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=360 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=WinInstallerIconTest
 */
public class WinInstallerIconTest {

    @Test
    public void test() throws IOException {
        Path customIcon = iconPath("icon");

        BufferedImage[] defaultInstallerIconImg = new BufferedImage[1];

        // Create installer with the default icon
        createInstaller(null, "WithDefaultIcon", installerIconImg -> {
            defaultInstallerIconImg[0] = installerIconImg;
        }, null, null);

        BufferedImage[] customInstallerIconImg = new BufferedImage[1];

        // Create installer with custom icon.
        // This installer icon should differ from the icon
        // of the installer created with the default icon.
        createInstaller(customIcon, "2", installerIconImg -> {
            customInstallerIconImg[0] = installerIconImg;
        }, null, defaultInstallerIconImg[0]);

        // Create installer with custom icon again.
        // This installer icon should differ from the icon
        // of the installer created with the default icon and should have
        // the same icon as the icon of installer created with custom icon.
        createInstaller(customIcon, null, null,
                customInstallerIconImg[0], defaultInstallerIconImg[0]);
    }

    private void createInstaller(Path icon, String nameSuffix,
            Consumer<BufferedImage> installerIconImgConsumer,
            BufferedImage expectedInstallerIconImg,
            BufferedImage unexpectedInstallerIconImg) throws IOException {

        PackageTest test = new PackageTest()
                .forTypes(PackageType.WIN_EXE)
                .addInitializer(JPackageCommand::setFakeRuntime)
                .configureHelloApp();
        if (icon != null) {
            test.addInitializer(cmd -> cmd.addArguments("--icon", icon));
        }

        if (nameSuffix != null) {
            test.addInitializer(cmd -> {
                String name = cmd.name() + nameSuffix;
                cmd.setArgumentValue("--name", name);
            });
        }

        Path installerExePath[] = new Path[1];

        test.addBundleVerifier(cmd -> {
            installerExePath[0] = cmd.outputBundle();

            Icon actualIcon = FileSystemView.getFileSystemView().getSystemIcon(
                    installerExePath[0].toFile());

            BufferedImage actualInstallerIconImg = loadIcon(actualIcon);

            if (installerIconImgConsumer != null) {
                installerIconImgConsumer.accept(actualInstallerIconImg);
            }

            if (expectedInstallerIconImg != null) {
                TKit.assertTrue(imageEquals(expectedInstallerIconImg,
                        actualInstallerIconImg), String.format(
                                "Check icon of %s installer is matching expected value",
                                installerExePath[0]));
            }

            if (unexpectedInstallerIconImg != null) {
                TKit.assertFalse(imageEquals(unexpectedInstallerIconImg,
                        actualInstallerIconImg), String.format(
                                "Check icon of %s installer is NOT matching unexpected value",
                                installerExePath[0]));
            }
        });

        test.run(CREATE);

        if (installerExePath[0] != null && nameSuffix != null) {
            TKit.deleteIfExists(installerExePath[0]);
        }
    }

    private BufferedImage loadIcon(Icon icon) {
        TKit.assertNotEquals(0, icon.getIconWidth(),
                "Check icon has not empty width");
        TKit.assertNotEquals(0, icon.getIconHeight(),
                "Check icon has not empty height");
        BufferedImage img = new BufferedImage(
                icon.getIconWidth(),
                icon.getIconHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    private static boolean imageEquals(BufferedImage imgA, BufferedImage imgB) {
        if (imgA.getWidth() == imgB.getWidth() && imgA.getHeight()
                == imgB.getHeight()) {
            for (int x = 0; x < imgA.getWidth(); x++) {
                for (int y = 0; y < imgA.getHeight(); y++) {
                    if (imgA.getRGB(x, y) != imgB.getRGB(x, y)) {
                        return false;
                    }
                }
            }
        } else {
            return false;
        }
        return true;
    }

    private static Path iconPath(String name) {
        return TKit.TEST_SRC_ROOT.resolve(Path.of("resources", name
                + TKit.ICON_SUFFIX));
    }
}
