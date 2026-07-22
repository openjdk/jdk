/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.internal.platform.Container;
import jdk.internal.platform.Metrics;
import sun.util.calendar.ZoneInfoFile;

/**
 * Support for printing the settings selected by {@code -XshowSettings}.
 */
public final class ShowSettings {
    private static final String INDENT = "    ";
    private static final String VM_SETTINGS = "VM settings:";
    private static final String PROP_SETTINGS = "Property settings:";
    private static final String LOCALE_SETTINGS = "Locale settings:";
    private static final String BUNDLE_NAME = "sun.launcher.resources.launcher";

    private static class ResourceBundleHolder {
        private static final ResourceBundle RB = ResourceBundle.getBundle(BUNDLE_NAME);
    }

    private static PrintStream ostream;

    enum Option {
        DEFAULT, ALL, LOCALE, PROPERTIES, SECURITY,
        SECURITY_ALL, SECURITY_PROPERTIES, SECURITY_PROVIDERS,
        SECURITY_TLS, SYSTEM, VM
    }

    private ShowSettings() {
    }

    /*
     * A method called by the launcher to print out the standard settings.
     * -XshowSettings prints details of all supported components in non-verbose
     * mode. -XshowSettings:all prints all settings in verbose mode.
     * Specific settings information may be obtained by using suboptions.
     *
     * Suboption values include "all", "locale", "properties", "security",
     * "system" (Linux only) and "vm". A error message is printed for an
     * unknown suboption value and the VM launch aborts.
     *
     * printToStderr: choose between stdout and stderr
     *
     * optionFlag: specifies which options to print default is all other
     *    possible values are vm, properties, locale.
     *
     * initialHeapSize: in bytes, as set by the launcher, a zero-value indicates
     *    this code should determine this value, using a suitable method or
     *    the line could be omitted.
     *
     * maxHeapSize: in bytes, as set by the launcher, a zero-value indicates
     *    this code should determine this value, using a suitable method.
     *
     * stackSize: in bytes, as set by the launcher, a zero-value indicates
     *    this code determine this value, using a suitable method or omit the
     *    line entirely.
     */
    static void showSettingsTo(String optionFlag,
                               long initialHeapSize, long maxHeapSize, long stackSize) {

        Option component = validateOption(optionFlag);
        switch (component) {
            case ALL -> printAllSettings(initialHeapSize, maxHeapSize, stackSize, true);
            case LOCALE -> printLocale(true);
            case PROPERTIES -> printProperties();
            case SECURITY,
                 SECURITY_ALL,
                 SECURITY_PROPERTIES,
                 SECURITY_PROVIDERS,
                 SECURITY_TLS -> SecuritySettings.printSecuritySettings(component, ostream, true);
            case SYSTEM -> printSystemMetrics();
            case VM -> printVmSettings(initialHeapSize, maxHeapSize, stackSize);
            case DEFAULT -> printAllSettings(initialHeapSize, maxHeapSize, stackSize, false);
        }
    }

    static void showSettings(boolean printToStderr, String optionFlag,
                             long initialHeapSize, long maxHeapSize, long stackSize) {
        initOutput(printToStderr);
        try {
            showSettingsTo(optionFlag, initialHeapSize, maxHeapSize, stackSize);
        } catch (IllegalArgumentException e) {
            // the invalid option message has already been written to ostream.
            System.exit(1);
        }
    }

    public static byte[] showSettingsBytes(String optionFlag,
            long initialHeapSize, long maxHeapSize, long stackSize) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            PrintStream old = ostream;
            try {
                ostream = ps;
                showSettingsTo(optionFlag, initialHeapSize, maxHeapSize, stackSize);
            } catch (IllegalArgumentException e) {
                // the invalid option message has already been written to ostream.
            } finally {
                ostream = old;
            }
        }
        return baos.toByteArray();
    }

    /*
     * Validate that the -XshowSettings value is allowed
     * If a valid option is parsed, return enum corresponding
     * to that option. Throw IllegalArgumentException if a bad option is parsed.
     */
    private static Option validateOption(String optionFlag) {
        if (optionFlag.equals("-XshowSettings")) {
            return Option.DEFAULT;
        }

        if (optionFlag.equals("-XshowSetings:")) {
            ostream.println(getLocalizedMessage("java.launcher.bad.option", ":"));
            throw new IllegalArgumentException();
        }

        Map<String, Option> validOpts = Arrays.stream(Option.values())
                .filter(o -> !o.equals(Option.DEFAULT)) // non-valid option
                .collect(Collectors.toMap(o -> o.name()
                        .toLowerCase(Locale.ROOT)
                        .replace("_", ":"), Function.identity()));

        String optStr = optionFlag.substring("-XshowSettings:".length());
        Option component = validOpts.get(optStr);
        if (component == null) {
            ostream.println(getLocalizedMessage("java.launcher.bad.option", optStr));
            throw new IllegalArgumentException();
        }
        return component;
    }


    /*
     * Print settings for all supported components.
     * verbose value used to determine if verbose information
     * should be printed for components that support printing
     * in verbose or non-verbose mode.
     */
    private static void printAllSettings(long initialHeapSize, long maxHeapSize,
                                         long stackSize, boolean verbose) {
        printVmSettings(initialHeapSize, maxHeapSize, stackSize);
        printProperties();
        printLocale(verbose);
        SecuritySettings.printSecuritySettings(
                Option.SECURITY_ALL, ostream, verbose);
        if (OperatingSystem.isLinux()) {
            printSystemMetrics();
        }
    }

    private static void printVmSettings(
            long initialHeapSize, long maxHeapSize,
            long stackSize) {

        ostream.println(VM_SETTINGS);
        if (stackSize != 0L) {
            ostream.println(INDENT + "Stack Size: " +
                    SizePrefix.scaleValue(stackSize));
        }
        if (initialHeapSize != 0L) {
            ostream.println(INDENT + "Min. Heap Size: " +
                    SizePrefix.scaleValue(initialHeapSize));
        }
        if (maxHeapSize != 0L) {
            ostream.println(INDENT + "Max. Heap Size: " +
                    SizePrefix.scaleValue(maxHeapSize));
        } else {
            ostream.println(INDENT + "Max. Heap Size (Estimated): "
                    + SizePrefix.scaleValue(Runtime.getRuntime().maxMemory()));
        }
        ostream.println(INDENT + "Using VM: "
                + System.getProperty("java.vm.name"));
        ostream.println();
    }

    /*
     * prints the properties subopt/section
     */
    private static void printProperties() {
        Properties p = System.getProperties();
        ostream.println(PROP_SETTINGS);
        for (String key : p.stringPropertyNames().stream().sorted().toList()) {
            printPropertyValue(key, p.getProperty(key));
        }
        ostream.println();
    }

    private static boolean isPath(String key) {
        return key.endsWith(".dirs") || key.endsWith(".path");
    }

    private static void printPropertyValue(String key, String value) {
        ostream.print(INDENT + key + " = ");
        if (key.equals("line.separator")) {
            for (byte b : value.getBytes()) {
                switch (b) {
                    case 0xd:
                        ostream.print("\\r ");
                        break;
                    case 0xa:
                        ostream.print("\\n ");
                        break;
                    default:
                        // print any bizarre line separators in hex, but really
                        // shouldn't happen.
                        ostream.printf("0x%02X", b & 0xff);
                        break;
                }
            }
            ostream.println();
            return;
        }
        if (!isPath(key)) {
            ostream.println(value);
            return;
        }
        String[] values = value.split(System.getProperty("path.separator"));
        boolean first = true;
        for (String s : values) {
            if (first) { // first line treated specially
                ostream.println(s);
                first = false;
            } else { // following lines prefix with indents
                ostream.println(INDENT + INDENT + s);
            }
        }
    }

    /*
     * prints the locale subopt/section
     */
    private static void printLocale(boolean verbose) {
        Locale locale = Locale.getDefault();
        if (verbose) {
            ostream.println(LOCALE_SETTINGS);
        } else {
            ostream.println("Locale settings summary:");
            ostream.println(INDENT + "Use \"-XshowSettings:locale\" " +
                    "option for verbose locale settings options");
        }
        ostream.println(INDENT + "default locale = " +
                locale.getDisplayName());
        ostream.println(INDENT + "default display locale = " +
                Locale.getDefault(Category.DISPLAY).getDisplayName());
        ostream.println(INDENT + "default format locale = " +
                Locale.getDefault(Category.FORMAT).getDisplayName());
        ostream.println(INDENT + "default timezone = " +
                TimeZone.getDefault().getID());
        ostream.println(INDENT + "tzdata version = " +
                ZoneInfoFile.getVersion());
        if (verbose) {
            printLocales();
        }
        ostream.println();
    }

    private static void printLocales() {
        Locale[] tlocales = Locale.getAvailableLocales();
        final int len = tlocales == null ? 0 : tlocales.length;
        if (len < 1) {
            return;
        }
        // Locale does not implement Comparable so we convert it to String
        // and sort it for pretty printing.
        Set<String> sortedSet = new TreeSet<>();
        for (Locale l : tlocales) {
            sortedSet.add(l.toString());
        }

        ostream.print(INDENT + "available locales = ");
        Iterator<String> iter = sortedSet.iterator();
        final int last = len - 1;
        for (int i = 0; iter.hasNext(); i++) {
            String s = iter.next();
            ostream.print(s);
            if (i != last) {
                ostream.print(", ");
            }
            // print columns of 8
            if ((i + 1) % 8 == 0) {
                ostream.println();
                ostream.print(INDENT + INDENT);
            }
        }
        ostream.println();
    }

    private static void printSystemMetrics() {
        Metrics c = Container.metrics();

        ostream.println("Operating System Metrics:");

        if (c == null) {
            ostream.println(INDENT + "No metrics available for this platform");
            return;
        }

        final long longRetvalNotSupported = -2;

        ostream.println(INDENT + "Provider: " + c.getProvider());
        if (!c.isContainerized()) {
            ostream.println(INDENT + "System not containerized.");
            return;
        }
        ostream.println(INDENT + "Effective CPU Count: " + c.getEffectiveCpuCount());
        ostream.println(formatCpuVal(c.getCpuPeriod(), INDENT + "CPU Period: ", longRetvalNotSupported));
        ostream.println(formatCpuVal(c.getCpuQuota(), INDENT + "CPU Quota: ", longRetvalNotSupported));
        ostream.println(formatCpuVal(c.getCpuShares(), INDENT + "CPU Shares: ", longRetvalNotSupported));

        int cpus[] = c.getCpuSetCpus();
        if (cpus != null) {
            ostream.println(INDENT + "List of Processors, "
                    + cpus.length + " total: ");

            ostream.print(INDENT);
            for (int i = 0; i < cpus.length; i++) {
                ostream.print(cpus[i] + " ");
            }
            if (cpus.length > 0) {
                ostream.println("");
            }
        } else {
            ostream.println(INDENT + "List of Processors: N/A");
        }

        cpus = c.getEffectiveCpuSetCpus();
        if (cpus != null) {
            ostream.println(INDENT + "List of Effective Processors, "
                    + cpus.length + " total: ");

            ostream.print(INDENT);
            for (int i = 0; i < cpus.length; i++) {
                ostream.print(cpus[i] + " ");
            }
            if (cpus.length > 0) {
                ostream.println("");
            }
        } else {
            ostream.println(INDENT + "List of Effective Processors: N/A");
        }

        int mems[] = c.getCpuSetMems();
        if (mems != null) {
            ostream.println(INDENT + "List of Memory Nodes, "
                    + mems.length + " total: ");

            ostream.print(INDENT);
            for (int i = 0; i < mems.length; i++) {
                ostream.print(mems[i] + " ");
            }
            if (mems.length > 0) {
                ostream.println("");
            }
        } else {
            ostream.println(INDENT + "List of Memory Nodes: N/A");
        }

        mems = c.getEffectiveCpuSetMems();
        if (mems != null) {
            ostream.println(INDENT + "List of Available Memory Nodes, "
                    + mems.length + " total: ");

            ostream.print(INDENT);
            for (int i = 0; i < mems.length; i++) {
                ostream.print(mems[i] + " ");
            }
            if (mems.length > 0) {
                ostream.println("");
            }
        } else {
            ostream.println(INDENT + "List of Available Memory Nodes: N/A");
        }

        long limit = c.getMemoryLimit();
        ostream.println(formatLimitString(limit, INDENT + "Memory Limit: ", longRetvalNotSupported));

        limit = c.getMemorySoftLimit();
        ostream.println(formatLimitString(limit, INDENT + "Memory Soft Limit: ", longRetvalNotSupported));

        limit = c.getMemoryAndSwapLimit();
        ostream.println(formatLimitString(limit, INDENT + "Memory & Swap Limit: ", longRetvalNotSupported));

        limit = c.getPidsMax();
        ostream.println(formatLimitString(limit, INDENT + "Maximum Processes Limit: ",
                longRetvalNotSupported, false));
        ostream.println("");
    }

    private static String formatLimitString(long limit, String prefix, long unavailable) {
        return formatLimitString(limit, prefix, unavailable, true);
    }

    private static String formatLimitString(long limit, String prefix, long unavailable, boolean scale) {
        if (limit >= 0) {
            if (scale) {
                return prefix + SizePrefix.scaleValue(limit);
            } else {
                return prefix + limit;
            }
        } else if (limit == unavailable) {
            return prefix + "N/A";
        } else {
            return prefix + "Unlimited";
        }
    }

    private static String formatCpuVal(long cpuVal, String prefix, long unavailable) {
        if (cpuVal >= 0) {
            return prefix + cpuVal + "us";
        } else if (cpuVal == unavailable) {
            return prefix + "N/A";
        } else {
            return prefix + cpuVal;
        }
    }

    private static String getLocalizedMessage(String key, Object... args) {
        String message = ResourceBundleHolder.RB.getString(key);
        return args != null ? MessageFormat.format(message, args) : message;
    }

    static void initOutput(boolean printToStderr) {
        ostream =  (printToStderr) ? System.err : System.out;
    }

    private enum SizePrefix {

        KILO(1024, "K"),
        MEGA(1024 * 1024, "M"),
        GIGA(1024 * 1024 * 1024, "G"),
        TERA(1024L * 1024L * 1024L * 1024L, "T");
        long size;
        String abbrev;

        SizePrefix(long size, String abbrev) {
            this.size = size;
            this.abbrev = abbrev;
        }

        private static String scale(long v, SizePrefix prefix) {
            return BigDecimal.valueOf(v).divide(BigDecimal.valueOf(prefix.size),
                    2, RoundingMode.HALF_EVEN).toPlainString() + prefix.abbrev;
        }

        /*
         * scale the incoming values to a human readable form, represented as
         * K, M, G and T, see java.c parse_size for the scaled values and
         * suffixes. The lowest possible scaled value is Kilo.
         */
        static String scaleValue(long v) {
            if (v < MEGA.size) {
                return scale(v, KILO);
            } else if (v < GIGA.size) {
                return scale(v, MEGA);
            } else if (v < TERA.size) {
                return scale(v, GIGA);
            } else {
                return scale(v, TERA);
            }
        }
    }
}