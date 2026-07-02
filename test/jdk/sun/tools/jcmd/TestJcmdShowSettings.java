/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test jcmd VM.show_settings diagnostic command's various sections
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Xms64m -Xmx128m -XX:+UsePerfData TestJcmdShowSettings
 */

import jdk.test.lib.process.OutputAnalyzer;

public class TestJcmdShowSettings {

    public static void main(String[] args) throws Exception {
        testDefaultSettings();
        testAllSettings();
        testVmSettings();
        testPropertySettings();
        testLocaleSettings();
        testSecuritySettings();
        testSecurityAllSettings();
        testSecurityPropertiesSettings();
        testSecurityProvidersSettings();
        testSecurityTlsSettings();
        testSystemSettings();
        testInvalidSection();
    }

    private static void testDefaultSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings");

        output.shouldHaveExitValue(0);
        output.shouldContain("VM settings:");
        output.shouldContain("Property settings:");
        output.shouldContain("Locale settings:");
        output.shouldContain("Security settings:");
    }

    private static void testAllSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "all");

        output.shouldHaveExitValue(0);
        output.shouldContain("VM settings:");
        output.shouldContain("Property settings:");
        output.shouldContain("Locale settings:");
        output.shouldContain("Security settings:");
    }

    private static void testVmSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "vm");

        output.shouldHaveExitValue(0);
        output.shouldContain("VM settings:");
        output.shouldMatch("Min\\. Heap Size:\\s+64\\.00M");
        output.shouldMatch("Max\\. Heap Size:\\s+128\\.00M");
    }

    private static void testPropertySettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "properties");

        output.shouldHaveExitValue(0);
        output.shouldContain("Property settings:");
        output.shouldContain("java.vm.name");
    }

    private static void testLocaleSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "locale");

        output.shouldHaveExitValue(0);
        output.shouldContain("Locale settings:");
        output.shouldContain("default locale");
    }

    private static void testSecuritySettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "security");

        output.shouldHaveExitValue(0);
        output.shouldContain("Security settings:");
        output.shouldContain("Security properties:");
        output.shouldContain("Security provider static configuration:");
        output.shouldContain("Security TLS configuration");
    }

    private static void testSecurityAllSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "security:all");

        output.shouldHaveExitValue(0);
        output.shouldContain("Security settings:");
        output.shouldContain("Security properties:");
        output.shouldContain("Security provider static configuration:");
        output.shouldContain("Security TLS configuration");
    }

    private static void testSecurityPropertiesSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "security:properties");

        output.shouldHaveExitValue(0);
        output.shouldContain("Security properties:");
    }

    private static void testSecurityProvidersSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "security:providers");

        output.shouldHaveExitValue(0);
        output.shouldContain("Security provider static configuration:");
        output.shouldContain("Provider name:");
    }

    private static void testSecurityTlsSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "security:tls");

        output.shouldHaveExitValue(0);
        output.shouldContain("Security TLS configuration");
        output.shouldContain("Enabled Protocols:");
    }

    private static void testSystemSettings() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "system");

        output.shouldHaveExitValue(0);
        output.shouldContain("Operating System Metrics:");
    }

    private static void testInvalidSection() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd("VM.show_settings", "invalid_section");

        output.shouldHaveExitValue(0);
        output.shouldContain("Unknown section: invalid_section");
        output.shouldContain("Valid sections:");
        output.shouldContain("Valid security sections:");
    }
}
