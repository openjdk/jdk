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

    enum Option { DEFAULT, ALL, LOCALE, PROPERTIES, SECURITY,
        SECURITY_ALL, SECURITY_PROPERTIES, SECURITY_PROVIDERS,
        SECURITY_TLS, SYSTEM, VM }

    private ShowSettings() { }

    public static void showSettings(boolean printToStderr, String optionFlag,
                                    long initialHeapSize, long maxHeapSize, long stackSize) {
        ostream = printToStderr ? System.err : System.out;
        try {
            showSettingsTo(optionFlag, initialHeapSize, maxHeapSize, stackSize);
        } catch (IllegalArgumentException e) {
            System.exit(1);
        }
    }

    public static byte[] showSettingsBytes(String optionFlag,
                                            long initialHeapSize,
                                            long maxHeapSize, long stackSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            PrintStream old = ostream;
            try {
                ostream = ps;
                showSettingsTo(optionFlag, initialHeapSize, maxHeapSize, stackSize);
            } catch (IllegalArgumentException e) {
                // The invalid option message has already been written to ostream.
            } finally {
                ostream = old;
            }
        }
        return baos.toByteArray();
    }

    private static void showSettingsTo(String optionFlag, long initialHeapSize,
                                       long maxHeapSize, long stackSize) {
        Option component = validateOption(optionFlag);
        switch (component) {
            case ALL -> printAllSettings(initialHeapSize, maxHeapSize, stackSize, true);
            case LOCALE -> printLocale(true);
            case PROPERTIES -> printProperties();
            case SECURITY, SECURITY_ALL, SECURITY_PROPERTIES, SECURITY_PROVIDERS,
                 SECURITY_TLS -> SecuritySettings.printSecuritySettings(component, ostream, true);
            case SYSTEM -> printSystemMetrics();
            case VM -> printVmSettings(initialHeapSize, maxHeapSize, stackSize);
            case DEFAULT -> printAllSettings(initialHeapSize, maxHeapSize, stackSize, false);
        }
    }

    private static Option validateOption(String optionFlag) {
        if (optionFlag.equals("-XshowSettings")) return Option.DEFAULT;
        if (optionFlag.equals("-XshowSetings:")) {
            badOption(":");
        }
        Map<String, Option> validOpts = Arrays.stream(Option.values())
                .filter(o -> o != Option.DEFAULT)
                .collect(Collectors.toMap(o -> o.name().toLowerCase(Locale.ROOT)
                        .replace("_", ":"), Function.identity()));
        String optStr = optionFlag.substring("-XshowSettings:".length());
        Option component = validOpts.get(optStr);
        if (component == null) badOption(optStr);
        return component;
    }

    private static void badOption(String option) {
        ostream.println(getLocalizedMessage("java.launcher.bad.option", option));
        throw new IllegalArgumentException();
    }

    private static void printAllSettings(long initialHeapSize, long maxHeapSize,
                                         long stackSize, boolean verbose) {
        printVmSettings(initialHeapSize, maxHeapSize, stackSize);
        printProperties();
        printLocale(verbose);
        SecuritySettings.printSecuritySettings(Option.SECURITY_ALL, ostream, verbose);
        if (OperatingSystem.isLinux()) printSystemMetrics();
    }

    private static void printVmSettings(long initialHeapSize, long maxHeapSize, long stackSize) {
        ostream.println(VM_SETTINGS);
        if (stackSize != 0L) ostream.println(INDENT + "Stack Size: " + SizePrefix.scaleValue(stackSize));
        if (initialHeapSize != 0L) ostream.println(INDENT + "Min. Heap Size: " + SizePrefix.scaleValue(initialHeapSize));
        if (maxHeapSize != 0L) {
            ostream.println(INDENT + "Max. Heap Size: " + SizePrefix.scaleValue(maxHeapSize));
        } else {
            ostream.println(INDENT + "Max. Heap Size (Estimated): " + SizePrefix.scaleValue(Runtime.getRuntime().maxMemory()));
        }
        ostream.println(INDENT + "Using VM: " + System.getProperty("java.vm.name"));
        ostream.println();
    }

    private static void printProperties() {
        Properties p = System.getProperties();
        ostream.println(PROP_SETTINGS);
        for (String key : p.stringPropertyNames().stream().sorted().toList()) {
            printPropertyValue(key, p.getProperty(key));
        }
        ostream.println();
    }

    private static void printPropertyValue(String key, String value) {
        ostream.print(INDENT + key + " = ");
        if (key.equals("line.separator")) {
            for (byte b : value.getBytes()) {
                switch (b) {
                    case 0xd -> ostream.print("\\r ");
                    case 0xa -> ostream.print("\\n ");
                    default -> ostream.printf("0x%02X", b & 0xff);
                }
            }
            ostream.println();
        } else if (!key.endsWith(".dirs") && !key.endsWith(".path")) {
            ostream.println(value);
        } else {
            boolean first = true;
            for (String s : value.split(System.getProperty("path.separator"))) {
                ostream.println(first ? s : INDENT + INDENT + s);
                first = false;
            }
        }
    }

    private static void printLocale(boolean verbose) {
        Locale locale = Locale.getDefault();
        if (verbose) ostream.println(LOCALE_SETTINGS);
        else {
            ostream.println("Locale settings summary:");
            ostream.println(INDENT + "Use \"-XshowSettings:locale\" option for verbose locale settings options");
        }
        ostream.println(INDENT + "default locale = " + locale.getDisplayName());
        ostream.println(INDENT + "default display locale = " + Locale.getDefault(Category.DISPLAY).getDisplayName());
        ostream.println(INDENT + "default format locale = " + Locale.getDefault(Category.FORMAT).getDisplayName());
        ostream.println(INDENT + "default timezone = " + TimeZone.getDefault().getID());
        ostream.println(INDENT + "tzdata version = " + ZoneInfoFile.getVersion());
        if (verbose) printLocales();
        ostream.println();
    }

    private static void printLocales() {
        Locale[] locales = Locale.getAvailableLocales();
        if (locales == null || locales.length < 1) return;
        Set<String> sorted = new TreeSet<>();
        for (Locale locale : locales) sorted.add(locale.toString());
        ostream.print(INDENT + "available locales = ");
        Iterator<String> iter = sorted.iterator();
        for (int i = 0; iter.hasNext(); i++) {
            ostream.print(iter.next());
            if (iter.hasNext()) ostream.print(", ");
            if ((i + 1) % 8 == 0 && iter.hasNext()) {
                ostream.println();
                ostream.print(INDENT + INDENT);
            }
        }
        ostream.println();
    }

    private static void printSystemMetrics() {
        Metrics c = Container.metrics();
        ostream.println("Operating System Metrics:");
        if (c == null) { ostream.println(INDENT + "No metrics available for this platform"); return; }
        final long unavailable = -2;
        ostream.println(INDENT + "Provider: " + c.getProvider());
        if (!c.isContainerized()) { ostream.println(INDENT + "System not containerized."); return; }
        ostream.println(INDENT + "Effective CPU Count: " + c.getEffectiveCpuCount());
        ostream.println(formatCpuVal(c.getCpuPeriod(), INDENT + "CPU Period: ", unavailable));
        ostream.println(formatCpuVal(c.getCpuQuota(), INDENT + "CPU Quota: ", unavailable));
        ostream.println(formatCpuVal(c.getCpuShares(), INDENT + "CPU Shares: ", unavailable));
        printIntArray("List of Processors", c.getCpuSetCpus());
        printIntArray("List of Effective Processors", c.getEffectiveCpuSetCpus());
        printIntArray("List of Memory Nodes", c.getCpuSetMems());
        printIntArray("List of Available Memory Nodes", c.getEffectiveCpuSetMems());
        ostream.println(formatLimitString(c.getMemoryLimit(), INDENT + "Memory Limit: ", unavailable));
        ostream.println(formatLimitString(c.getMemorySoftLimit(), INDENT + "Memory Soft Limit: ", unavailable));
        ostream.println(formatLimitString(c.getMemoryAndSwapLimit(), INDENT + "Memory & Swap Limit: ", unavailable));
        ostream.println(formatLimitString(c.getPidsMax(), INDENT + "Maximum Processes Limit: ", unavailable, false));
        ostream.println();
    }

    private static void printIntArray(String description, int[] values) {
        if (values == null) { ostream.println(INDENT + description + ": N/A"); return; }
        ostream.println(INDENT + description + ", " + values.length + " total: ");
        ostream.print(INDENT);
        for (int value : values) ostream.print(value + " ");
        if (values.length > 0) ostream.println();
    }

    private static String formatLimitString(long limit, String prefix, long unavailable) {
        return formatLimitString(limit, prefix, unavailable, true);
    }
    private static String formatLimitString(long limit, String prefix, long unavailable, boolean scale) {
        if (limit >= 0) return prefix + (scale ? SizePrefix.scaleValue(limit) : limit);
        return prefix + (limit == unavailable ? "N/A" : "Unlimited");
    }
    private static String formatCpuVal(long value, String prefix, long unavailable) {
        if (value >= 0) return prefix + value + "us";
        return prefix + (value == unavailable ? "N/A" : value);
    }

    private enum SizePrefix {
        KILO(1024, "K"), MEGA(1024 * 1024, "M"), GIGA(1024 * 1024 * 1024, "G"),
        TERA(1024L * 1024L * 1024L * 1024L, "T");
        final long size; final String abbrev;
        SizePrefix(long size, String abbrev) { this.size = size; this.abbrev = abbrev; }
        static String scaleValue(long value) {
            SizePrefix prefix = value < MEGA.size ? KILO : value < GIGA.size ? MEGA :
                    value < TERA.size ? GIGA : TERA;
            return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(prefix.size),
                    2, RoundingMode.HALF_EVEN).toPlainString() + prefix.abbrev;
        }
    }

    private static String getLocalizedMessage(String key, Object... args) {
        String message = ResourceBundleHolder.RB.getString(key);
        return args != null ? MessageFormat.format(message, args) : message;
    }
}
