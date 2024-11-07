/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import jdk.jpackage.internal.IOUtils;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Test --app-version parameter properly supports long version numbers, i.e.
 * version numbers with more than three components.
 * Output of the test should be WinLongVersionTest-1.0.exe,
 * WinLongVersionTest-2.0.0.1.exe, and WinLongVersionTest-2.0.0.2.exe installers.
 * The output installers should provide the same functionality as
 * the default installer (see description of the default installer in
 * SimplePackageTest.java) but have the same product code and different
 * versions.
 * Test scenario:
 *  - Run WinLongVersionTest-2.0.0.2.exe;
 *  - Run WinLongVersionTest-1.0.exe; package installed with
 *    WinLongVersionTest-2.0.0.2.exe command must remain installed
 *  - Run WinLongVersionTest-2.0.0.1.exe; packages installed with
 *    WinLongVersionTest-2.0.0.2.exe and WinLongVersionTest-1.0.exe installers
 *    must be automatically uninstalled, only WinLongVersionTest-2.0.0.1 package
 *    must remain installed
 *  - Uninstall WinLongVersionTest-2.0.0.2; all packages installed in the test
 *    scenario must be uninstalled
 */

/*
 * @test
 * @summary jpackage with long version number
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @requires (jpackage.test.SQETest != null)
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @compile WinLongVersionTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinLongVersionTest.test
 */

/*
 * @test
 * @summary jpackage with long version number
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @requires (jpackage.test.SQETest == null)
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @compile WinLongVersionTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinLongVersionTest
 */

public class WinLongVersionTest {

    @Test
    public static void test() throws IOException {
        Supplier<PackageTest> init = () -> {
            final UUID upgradeCode = UUID.fromString(
                    "65099D7A-D5B1-4E5B-85B1-717F0DE4D5D5");
            return new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(cmd -> cmd.addArguments("--win-upgrade-uuid",
                        upgradeCode.toString())) ;

        };

        PackageTest test1 = init.get().addInitializer(cmd -> {
            cmd.setArgumentValue("--app-version", "2.0.0.2");
            cmd.setArgumentValue("--arguments", "bar");
            cmd.setArgumentValue("--install-dir", cmd.name() + "-1");
        });

        // Tweak Upgrade table of the second package in a way the default
        // FindRelatedProducts MSI action will find 1st package, but custom
        // jpackage's FindRelatedProductsEx action will not as it gracefuly
        // handles more than 3 components of version strings.
        // In MSI log of the 2nd package installartion session it will be something like:
        /*
        Action start 12:08:38: FindRelatedProducts.
        FindRelatedProducts: Found application: {D88EEA02-56CC-34AD-8216-C2CC244FA898}
        MSI (c) (0C:14) [12:08:38:040]: PROPERTY CHANGE: Adding JP_DOWNGRADABLE_FOUND property. Its value is '{D88EEA02-56CC-34AD-8216-C2CC244FA898}'.
        MSI (c) (0C:14) [12:08:38:040]: PROPERTY CHANGE: Adding MIGRATE property. Its value is '{D88EEA02-56CC-34AD-8216-C2CC244FA898}'.
        Action ended 12:08:38: FindRelatedProducts. Return value 1.
        ...
        Action start 12:08:38: JpFindRelatedProducts.
        Java [12:08:38.180 libwixhelper.cpp:120 (FindRelatedProductsEx)] TRACE: Entering FindRelatedProductsEx
        Java [12:08:38.185 libwixhelper.cpp:85 (`anonymous-namespace'::findInstalledPackages)] TRACE: Found {D88EEA02-56CC-34AD-8216-C2CC244FA898} product
        Java [12:08:38.187 MsiCA.cpp:61 (msi::CAImpl::removeProperty)] TRACE: Removing MSI property 'JP_UPGRADABLE_FOUND'
        Java [12:08:38.187 MsiCA.cpp:61 (msi::CAImpl::removeProperty)] TRACE: Removing MSI property 'MIGRATE'
        Java [12:08:38.189 MsiCA.cpp:61 (msi::CAImpl::removeProperty)] TRACE: Removing MSI property 'JP_DOWNGRADABLE_FOUND'
        Java [12:08:38.190 libwixhelper.cpp:0 (FindRelatedProductsEx)] TRACE: Exiting FindRelatedProductsEx (entered at libwixhelper.cpp:120)
        Action ended 12:08:38: JpFindRelatedProducts. Return value 1.
        */
        PackageTest test2 = init.get().addInstallVerifier(cmd -> {
            if (!cmd.isPackageUnpacked()) {
                // Installation of this package must NOT uninstall
                // previously installed package. Verify that.
                test1.run(Action.VERIFY_INSTALL);
            }
        }).forTypes(PackageType.WIN_EXE)
        .addInitializer(cmd -> {
            final Path resourceDir = TKit.createTempDirectory("resources");
            cmd.addArguments("--resource-dir", resourceDir);

            Path scriptPath = resourceDir.resolve(String.format(
                    "%s-post-msi.wsf", cmd.name()));
            IOUtils.createXml(scriptPath, xml -> {
                xml.writeStartElement("job");
                xml.writeAttribute("id", "main");
                xml.writeStartElement("script");
                xml.writeAttribute("language", "JScript");
                xml.writeCData(String.join("\n", Files.readAllLines(
                        TKit.TEST_SRC_ROOT.resolve(String.format(
                                "resources/%s-edit-msi.js", cmd.name())))));
                xml.writeEndElement();
                xml.writeEndElement();
            });
        }).forTypes(PackageType.WIN_MSI)
        .addBundleVerifier(cmd -> {
            Executor.of("cscript.exe", "//Nologo")
                    .addArgument(TKit.TEST_SRC_ROOT.resolve(String.format(
                            "resources/%s-edit-msi.js", cmd.name())))
                    .addArgument(cmd.outputBundle())
                    .execute();
        });

        // Replace real uninstall commands for the first packages with nop action.
        // They will be uninstalled automatically when the last package will
        // be installed.
        test1.disablePackageUninstaller();
        test2.disablePackageUninstaller();

        PackageTest test3 = init.get().addInitializer(cmd -> {
            cmd.setArgumentValue("--app-version", "2.0.0.1");
            cmd.setArgumentValue("--arguments", "buzz");
        });

        new PackageTest.Group(test1, test2, test3).run();
    }

    @Test
    public static void testNoUpgradeTable() throws IOException {
        new PackageTest()
        .forTypes(PackageType.WINDOWS)
        .configureHelloApp()
        .addInitializer(cmd -> {
            final Path resourceDir = TKit.createTempDirectory("resources");
            cmd.addArguments("--resource-dir", resourceDir);

            cmd.setFakeRuntime();

            // Create package without Upgrade table
            Document doc = IOUtils.initDocumentBuilder().parse(
                    Files.newInputStream(TKit.SRC_ROOT.resolve(
                            "windows/classes/jdk/jpackage/internal/resources/main.wxs")));
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate("/Wix/Product/Upgrade",
                    doc, XPathConstants.NODESET);
            nodes.item(0).getParentNode().removeChild(nodes.item(0));

            Source source = new DOMSource(doc);
            Result result = new StreamResult(Files.newOutputStream(
                    resourceDir.resolve("main.wxs")));

            Transformer trans = TransformerFactory.newInstance().newTransformer();
            trans.transform(source, result);
        }).run();
    }
}
