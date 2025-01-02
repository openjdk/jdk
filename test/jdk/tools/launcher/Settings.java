/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @bug 6994753 7123582 8305950 8281658 8310201 8311653 8343804
 * @summary tests -XshowSettings options
 * @modules jdk.compiler
 *          jdk.zipfs
 * @compile -XDignore.symbol.file Settings.java
 * @run main Settings
 * @author ksrini
 */
public class Settings extends TestHelper {
    private static File testJar = null;

    static void init() throws IOException {
        if  (testJar != null) {
            return;
        }
        testJar = new File("test.jar");
        StringBuilder tsrc = new StringBuilder();
        tsrc.append("public static void main(String... args) {\n");
        tsrc.append("   for (String x : args) {\n");
        tsrc.append("        System.out.println(x);\n");
        tsrc.append("   }\n");
        tsrc.append("}\n");
        createJar(testJar, tsrc.toString());
    }

    static void checkContains(TestResult tr, String str) {
        if (!tr.contains(str)) {
            System.out.println(tr);
            throw new RuntimeException(str + " not found");
        }
    }

    static void checkNotContains(TestResult tr, String str) {
        if (!tr.notContains(str)) {
            System.out.println(tr);
            throw new RuntimeException(str + " found");
        }
    }

    private static final String VM_SETTINGS = "VM settings:";
    private static final String PROP_SETTINGS = "Property settings:";
    private static final String LOCALE_SETTINGS = "Locale settings:";
    private static final String LOCALE_SUMMARY_SETTINGS =
                "Locale settings summary:";
    private static final String AVAILABLE_LOCALES = "available locales";
    private static final String SEC_PROPS_SETTINGS = "Security properties:";
    private static final String SEC_SUMMARY_PROPS_SETTINGS =
                "Security settings summary:";
    private static final String SEC_PROVIDER_SETTINGS =
                "Security provider static configuration:";
    private static final String SEC_TLS_SETTINGS = "Security TLS configuration";
    private static final String BAD_SEC_OPTION_MSG = "Valid \"security\" suboption values are";
    private static final String SYSTEM_SETTINGS = "Operating System Metrics:";
    private static final String METRICS_NOT_AVAILABLE_MSG = "No metrics available for this platform";
    private static final String STACKSIZE_SETTINGS = "Stack Size:";
    private static final String TIMEZONE_SETTINGS = "default timezone";
    private static final String TZDATA_SETTINGS = "tzdata version";
    private static final String ERR_MSG = "Unrecognized showSettings option:";

    /*
     * "all" should print verbose settings
     */
    static void containsAllOptions(TestResult tr) {
        checkContains(tr, VM_SETTINGS);
        checkContains(tr, PROP_SETTINGS);
        checkContains(tr, LOCALE_SETTINGS);
        checkContains(tr, AVAILABLE_LOCALES);
        checkNotContains(tr, LOCALE_SUMMARY_SETTINGS);
        checkContains(tr, SEC_PROPS_SETTINGS);
        checkNotContains(tr, SEC_SUMMARY_PROPS_SETTINGS);
        checkContains(tr, SEC_PROVIDER_SETTINGS);
        checkContains(tr, SEC_TLS_SETTINGS);
        checkContains(tr, TIMEZONE_SETTINGS);
        checkContains(tr, TZDATA_SETTINGS);
        if (System.getProperty("os.name").contains("Linux")) {
            checkContains(tr, SYSTEM_SETTINGS);
        } else {
            // only invoke system option by default on Linux
            checkNotContains(tr, METRICS_NOT_AVAILABLE_MSG);
        }
    }
    /*
     * default (no options) should print non verbose
     * details on each component
     */
    static void containsDefaultOptions(TestResult tr) {
        checkContains(tr, VM_SETTINGS);
        checkContains(tr, PROP_SETTINGS);
        checkNotContains(tr, LOCALE_SETTINGS);
        checkNotContains(tr, AVAILABLE_LOCALES);
        checkContains(tr, LOCALE_SUMMARY_SETTINGS);
        // no verbose security settings unless "security" or "all" used
        checkNotContains(tr, SEC_PROPS_SETTINGS);
        checkContains(tr, SEC_SUMMARY_PROPS_SETTINGS);
        checkContains(tr, SEC_PROVIDER_SETTINGS);
        checkContains(tr, SEC_TLS_SETTINGS);
        checkContains(tr, TIMEZONE_SETTINGS);
        checkContains(tr, TZDATA_SETTINGS);
        if (System.getProperty("os.name").contains("Linux")) {
            checkContains(tr, SYSTEM_SETTINGS);
        } else {
            // only invoke system option by default on Linux
            checkNotContains(tr, METRICS_NOT_AVAILABLE_MSG);
        }
    }

    static void runTestOptionDefault() throws IOException {
        int stackSize = 256; // in kb
        if (getArch().equals("ppc64") || getArch().equals("ppc64le")) {
            stackSize = 800;
        } else if (getArch().equals("aarch64")) {
            /*
             * The max value of minimum stack size allowed for aarch64 can be estimated as
             * such: suppose the vm page size is 64KB and the test runs with a debug build,
             * the initial _java_thread_min_stack_allowed defined in os_linux_aarch64.cpp is
             * 72K, stack guard zones could take 192KB, and the shadow zone needs 128KB,
             * after aligning up all parts to the page size, the final size would be 448KB.
             * See details in JDK-8163363
             */
            stackSize = 448;
        }
        TestResult tr;
        tr = doExec(javaCmd, "-Xms64m", "-Xmx512m",
                "-Xss" + stackSize + "k", "-XshowSettings", "-jar", testJar.getAbsolutePath());
        // Check the stack size logs printed by -XshowSettings to verify -Xss meaningfully.
        checkContains(tr, STACKSIZE_SETTINGS);
        containsDefaultOptions(tr);
        if (!tr.isOK()) {
            System.out.println(tr);
            throw new RuntimeException("test fails");
        }
        tr = doExec(javaCmd, "-Xms65536k", "-Xmx712m",
                "-Xss" + (stackSize * 1024), "-XshowSettings", "-jar", testJar.getAbsolutePath());
        checkContains(tr, STACKSIZE_SETTINGS);
        containsDefaultOptions(tr);
        if (!tr.isOK()) {
            System.out.println(tr);
            throw new RuntimeException("test fails");
        }
    }

    static void runTestOptionAll() throws IOException {
        init();
        TestResult tr = doExec(javaCmd, "-XshowSettings:all");
        containsAllOptions(tr);
    }

    static void runTestOptionVM() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:vm");
        checkContains(tr, VM_SETTINGS);
        checkNotContains(tr, PROP_SETTINGS);
        checkNotContains(tr, LOCALE_SETTINGS);
    }

    static void runTestOptionProperty() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:properties");
        checkNotContains(tr, VM_SETTINGS);
        checkContains(tr, PROP_SETTINGS);
        checkNotContains(tr, LOCALE_SETTINGS);
    }

    static void runTestOptionLocale() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:locale");
        checkNotContains(tr, VM_SETTINGS);
        checkNotContains(tr, PROP_SETTINGS);
        checkContains(tr, LOCALE_SETTINGS);
        checkContains(tr, AVAILABLE_LOCALES);
        checkNotContains(tr, LOCALE_SUMMARY_SETTINGS);
        checkContains(tr, TIMEZONE_SETTINGS);
        checkContains(tr, TZDATA_SETTINGS);
    }

    static void runTestOptionSecurity() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:security");
        checkNotContains(tr, VM_SETTINGS);
        checkNotContains(tr, PROP_SETTINGS);
        checkContains(tr, SEC_PROPS_SETTINGS);
        checkContains(tr, SEC_PROVIDER_SETTINGS);
        checkContains(tr, SEC_TLS_SETTINGS);
    }

    static void runTestOptionSecurityProps() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:security:properties");
        checkContains(tr, SEC_PROPS_SETTINGS);
        checkNotContains(tr, SEC_PROVIDER_SETTINGS);
        checkNotContains(tr, SEC_TLS_SETTINGS);
        // test a well known property for sanity
        checkContains(tr, "keystore.type=pkcs12");
    }

    static void runTestOptionSecurityProv() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:security:providers");
        checkNotContains(tr, SEC_PROPS_SETTINGS);
        checkContains(tr, SEC_PROVIDER_SETTINGS);
        checkNotContains(tr, SEC_TLS_SETTINGS);
        // test a well known Provider for sanity
        checkContains(tr, "Provider name: SUN");
        // test for a well known alias (SunJCE: AlgorithmParameterGenerator.DiffieHellman)
        checkContains(tr, "aliases: [1.2.840.113549.1.3.1, " +
                "DH, OID.1.2.840.113549.1.3.1]");
    }

    static void runTestOptionSecurityTLS() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:security:tls");
        checkNotContains(tr, SEC_PROPS_SETTINGS);
        checkNotContains(tr, SEC_PROVIDER_SETTINGS);
        checkContains(tr, SEC_TLS_SETTINGS);
        // test a well known TLS config for sanity
        checkContains(tr, "TLSv1.2");
    }

    // ensure error message is printed when unrecognized option used
    static void runTestOptionBadSecurityOption() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:security:bad");
        tr.checkNegative();
        checkContains(tr, BAD_SEC_OPTION_MSG);
        // we print all security settings in such scenario
        checkNotContains(tr, SEC_PROPS_SETTINGS);
        checkNotContains(tr, SEC_PROVIDER_SETTINGS);
        checkNotContains(tr, SEC_TLS_SETTINGS);
    }
    static void runTestOptionSystem() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings:system");
        if (System.getProperty("os.name").contains("Linux")) {
            checkNotContains(tr, VM_SETTINGS);
            checkNotContains(tr, PROP_SETTINGS);
            checkNotContains(tr, LOCALE_SETTINGS);
            checkContains(tr, SYSTEM_SETTINGS);
        } else {
            // "system" should print a "No metrics available"
            // message on other OSes
            checkNotContains(tr, VM_SETTINGS);
            checkContains(tr, METRICS_NOT_AVAILABLE_MSG);
        }
    }

    static void runTestBadOptions() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettingsBadOption");
        tr.checkNegative();
        checkNotContains(tr, VM_SETTINGS);
        checkNotContains(tr, PROP_SETTINGS);
        checkNotContains(tr, LOCALE_SETTINGS);
        checkContains(tr, "Unrecognized option: -XshowSettingsBadOption");

        // no such component option
        tr = doExec(javaCmd, "-XshowSettings:BadOption");
        tr.checkNegative();
        checkNotContains(tr, VM_SETTINGS);
        checkNotContains(tr, PROP_SETTINGS);
        checkNotContains(tr, LOCALE_SETTINGS);
        checkContains(tr, ERR_MSG);

        // don't allow invalid sub options
        tr = doExec(javaCmd, "-XshowSettings:locale:bad");
        tr.checkNegative();
        checkContains(tr, ERR_MSG);

        // don't allow ":" as an option
        tr = doExec(javaCmd, "-XshowSettings:");
        tr.checkNegative();
        checkContains(tr, ERR_MSG);

        // case-sensitive test
        tr = doExec(javaCmd, "-XshowSettings:VM");
        tr.checkNegative();
        checkContains(tr, ERR_MSG);

        // exclude this enum value
        tr = doExec(javaCmd, "-XshowSettings:empty");
        tr.checkNegative();
        checkContains(tr, ERR_MSG);


    }

    static void runTest7123582() throws IOException {
        TestResult tr = doExec(javaCmd, "-XshowSettings", "-version");
        if (!tr.isOK()) {
            System.out.println(tr);
            throw new RuntimeException("test fails");
        }
        containsDefaultOptions(tr);
    }

    public static void main(String... args) throws IOException {
        runTestOptionAll();
        runTestOptionDefault();
        runTestOptionVM();
        runTestOptionProperty();
        runTestOptionLocale();
        runTestOptionSecurity();
        runTestOptionSecurityProps();
        runTestOptionSecurityProv();
        runTestOptionSecurityTLS();
        runTestOptionBadSecurityOption();
        runTestOptionSystem();
        runTestBadOptions();
        runTest7123582();
        if (testExitValue != 0) {
            throw new Error(testExitValue + " tests failed");
        }
    }
}
