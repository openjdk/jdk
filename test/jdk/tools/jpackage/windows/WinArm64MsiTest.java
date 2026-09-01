/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageCommand.MessageCategory;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.WindowsHelper;

/**
 * Test will build a native Windows Arm64 MSI package with WiX Toolset v4+
 * and validate that it was built for the Arm64 platform rather than the x64
 * platform used for every other 64-bit Windows architecture. See JDK-8361207.
 * <p>
 * This test only runs on a Windows/AArch64 JDK, as it relies on the
 * host/target architecture reported by the running JDK matching the
 * architecture of the produced MSI. It is not expected to run in emulation.
 */

/*
 * @test
 * @summary jpackage test to verify native Windows Arm64 MSI packages built
 *          with WiX 4+ use "-arch arm64" and select the Arm64 ("A64") custom
 *          actions from the WiX Util extension instead of the X64 ones.
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows" & os.arch == "aarch64")
 * @compile -Xlint:all -Werror WinArm64MsiTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinArm64MsiTest
 */
public class WinArm64MsiTest {

    @Test
    public static void test() {
        // Probe which WiX Toolset version jpackage will actually use before
        // running the full test. This test only makes sense with WiX 4+, as
        // WiX 3 doesn't support the Arm64 platform in this path and jpackage
        // will fail the build outright (see JDK-8361207). Detect that ahead
        // of time and skip with a clear message instead of failing the test
        // for an environmental reason.
        final JPackageCommand probeCmd = JPackageCommand.helloAppImage()
                .setPackageType(PackageType.WIN_MSI)
                .setFakeRuntime()
                .saveConsoleOutput(true)
                // The WiX version is only reported in jpackage's SUMMARY
                // output. Request it explicitly so this probe doesn't
                // depend on any external "verbose" test configuration
                // that may otherwise reduce the default verbosity level.
                .enableMessageCategories(MessageCategory.SUMMARY);
        final Executor.Result probeResult = probeCmd.executeIgnoreExitCode();

        final WindowsHelper.WixType wixType;
        try {
            wixType = WindowsHelper.getWixTypeFromVerboseJPackageOutput(probeResult);
        } catch (IllegalArgumentException ex) {
            throw TKit.throwSkippedException(
                    "Could not detect the WiX Toolset version from jpackage output: "
                    + ex.getMessage());
        }

        if (wixType != WindowsHelper.WixType.WIX4) {
            throw TKit.throwSkippedException(String.format(
                    "This test requires WiX Toolset v4+ to validate native "
                    + "Arm64 MSI support, but detected %s", wixType));
        }

        new PackageTest()
                .forTypes(PackageType.WIN_MSI)
                .configureHelloApp()
                .addBundleVerifier(WinArm64MsiTest::verifyArm64Msi)
                .run(PackageTest.Action.CREATE);
    }

    private static void verifyArm64Msi(JPackageCommand cmd) throws Exception {
        final Path msi = cmd.outputBundle();

        // Verify the MSI Summary Information "Template" property reports the
        // Arm64 platform (e.g. "Arm64;1033"), proving WiX was invoked with
        // "-arch arm64" rather than the x64 default.
        final String template = queryMsiSummaryTemplate(msi);
        TKit.assertTrue(template.contains("Arm64"),
                "Check MSI Summary Information \"Template\" property reports "
                + "the Arm64 platform, got: [" + template + "]");

        // Verify the WiX Util extension selected the Arm64 ("A64") flavor of
        // its CloseApplications custom action, not the X64 one.
        final List<String> customActions = queryMsiCustomActionNames(msi);
        TKit.assertTrue(customActions.contains("Wix4CloseApplications_A64"),
                "Check MSI selects the Wix4CloseApplications_A64 custom action");
        TKit.assertTrue(!customActions.contains("Wix4CloseApplications_X64"),
                "Check MSI does not select the Wix4CloseApplications_X64 custom action");
    }

    // Reads the MSI Summary Information "Template" property (id 7) using the
    // Windows Installer COM API through PowerShell. Avoids requiring the MSI
    // to be installed to validate its metadata.
    private static String queryMsiSummaryTemplate(Path msi) {
        // Database.SummaryInformation is documented as a (parameterized)
        // Property of the Windows Installer Automation interface, not a
        // method - see
        // https://learn.microsoft.com/windows/win32/msi/database-summaryinformation-property
        // Its single argument is the "updateCount" (0 for read-only access),
        // analogous to the "StringData" parameterized property used below
        // to read record columns. Both are correctly invoked with
        // 'GetProperty', not 'InvokeMethod'.
        return runInstallerScript(msi, List.of(
                "$si = $db.GetType().InvokeMember('SummaryInformation', 'GetProperty', $null, $db, @(0))",
                "$tpl = $si.GetType().InvokeMember('Property', 'GetProperty', $null, $si, @(7))",
                "Write-Output $tpl"
        )).stream().findFirst().orElse("");
    }

    // Reads the names of all rows of the MSI "CustomAction" table using the
    // Windows Installer COM API through PowerShell.
    private static List<String> queryMsiCustomActionNames(Path msi) {
        return runInstallerScript(msi, List.of(
                "$view = $db.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $db,"
                + " @('SELECT `Action` FROM `CustomAction`'))",
                "$view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null)",
                "while ($true) {",
                "  $rec = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null, $view, $null)",
                "  if ($rec -eq $null) { break }",
                "  Write-Output $rec.GetType().InvokeMember('StringData', 'GetProperty', $null, $rec, @(1))",
                "}"
        ));
    }

    private static List<String> runInstallerScript(Path msi, List<String> body) {
        // Escape embedded single quotes for PowerShell's single-quoted string
        // literal syntax (doubling the quote), so paths containing "'" (e.g.
        // "O'Connor") don't break the script or enable injection.
        final var escapedMsiPath = msi.toAbsolutePath().toString().replace("'", "''");
        final var script = new java.util.ArrayList<String>();
        script.add("$installer = New-Object -ComObject WindowsInstaller.Installer");
        script.add("$db = $installer.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $installer,"
                + " @('" + escapedMsiPath + "', 0))");
        script.addAll(body);
        return Executor.of(WindowsHelper.PowerShellPath(), "-NoProfile", "-NonInteractive", "-Command",
                String.join("; ", script)).saveOutput().executeAndGetOutput();
    }
}
