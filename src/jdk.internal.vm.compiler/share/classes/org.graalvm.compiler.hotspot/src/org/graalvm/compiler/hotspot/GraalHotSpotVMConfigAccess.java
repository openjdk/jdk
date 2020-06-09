/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck.Version;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck.Version2;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck.Version3;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMField;
import jdk.vm.ci.services.Services;

/**
 * Interposes on {@link HotSpotVMConfigAccess} to {@linkplain #isPresent check} when retrieving VM
 * configuration values that they are only present in the VM iff expected to be.
 */
public class GraalHotSpotVMConfigAccess {

    protected final HotSpotVMConfigAccess access;
    private final Map<String, Long> vmAddresses;
    private final Map<String, Long> vmConstants;
    private final Map<String, VMField> vmFields;

    GraalHotSpotVMConfigAccess(HotSpotVMConfigStore store) {
        this.access = new HotSpotVMConfigAccess(store);
        this.vmAddresses = store.getAddresses();
        this.vmConstants = store.getConstants();
        this.vmFields = store.getFields();

        String value = getProperty("os.name");
        switch (value) {
            case "Linux":
                value = "linux";
                break;
            case "SunOS":
                value = "solaris";
                break;
            case "Mac OS X":
                value = "darwin";
                break;
            default:
                // Of course Windows is different...
                if (value.startsWith("Windows")) {
                    value = "windows";
                } else {
                    throw new JVMCIError("Unexpected OS name: " + value);
                }
        }
        assert KNOWN_OS_NAMES.contains(value);
        this.osName = value;

        String arch = getProperty("os.arch");
        switch (arch) {
            case "x86_64":
                arch = "amd64";
                break;
            case "sparcv9":
                arch = "sparc";
                break;
        }
        osArch = arch;
        assert KNOWN_ARCHITECTURES.contains(arch) : arch;
    }

    public HotSpotVMConfigStore getStore() {
        return access.getStore();
    }

    protected static String getProperty(String name, String def) {
        String value = Services.getSavedProperties().get(name);
        if (value == null) {
            return def;
        }
        return value;
    }

    protected static String getProperty(String name) {
        return getProperty(name, null);
    }

    public static final Set<String> KNOWN_ARCHITECTURES = new HashSet<>(Arrays.asList("amd64", "sparc", "aarch64"));
    public static final Set<String> KNOWN_OS_NAMES = new HashSet<>(Arrays.asList("windows", "linux", "darwin", "solaris"));

    /**
     * Name for current OS. Will be a value in {@value #KNOWN_OS_NAMES}.
     */
    public final String osName;

    /**
     * Name for current CPU architecture. Will be a value in {@value #KNOWN_ARCHITECTURES}.
     */
    public final String osArch;

    protected static final Version JVMCI_0_55 = new Version2(0, 55);
    protected static final Version JVMCI_20_1_b01 = new Version3(20, 1, 1);
    protected static final Version JVMCI_20_0_b03 = new Version3(20, 0, 3);
    protected static final Version JVMCI_19_3_b03 = new Version3(19, 3, 3);
    protected static final Version JVMCI_19_3_b04 = new Version3(19, 3, 4);
    protected static final Version JVMCI_19_3_b07 = new Version3(19, 3, 7);

    public static boolean jvmciGE(Version v) {
        return JVMCI_PRERELEASE || !JVMCI_VERSION.isLessThan(v);
    }

    public static final int JDK = JavaVersionUtil.JAVA_SPEC;
    public static final int JDK_UPDATE = GraalServices.getJavaUpdateVersion();
    public static final boolean IS_OPENJDK = getProperty("java.vm.name", "").startsWith("OpenJDK");
    public static final Version JVMCI_VERSION;
    public static final boolean JVMCI;
    public static final boolean JVMCI_PRERELEASE;
    static {
        String vmVersion = getProperty("java.vm.version");
        JVMCI_VERSION = Version.parse(vmVersion);
        JVMCI_PRERELEASE = vmVersion.contains("SNAPSHOT") || vmVersion.contains("internal") || vmVersion.contains("-dev");
        JVMCI = JVMCI_VERSION != null || JVMCI_PRERELEASE;
    }

    private final List<String> missing = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();

    /**
     * Records an error if {@code map.contains(name) != expectPresent}. That is, it's an error if
     * {@code value} is unexpectedly present in the VM or is unexpectedly absent from the VM.
     *
     * @param expectPresent the expectation that the value is present in the VM
     */
    private boolean isPresent(String name, Map<String, ?> map, boolean expectPresent) {
        if (map.containsKey(name)) {
            if (!expectPresent) {
                recordError(name, unexpected, String.valueOf(map.get(name)));
            }
            return true;
        }
        if (expectPresent) {
            recordError(name, missing, null);
        }

        return false;
    }

    // Only defer errors while in the GraalHotSpotVMConfig
    // constructor until reportErrors() is called.
    private boolean deferErrors = this instanceof GraalHotSpotVMConfig;

    private void recordError(String name, List<String> list, String unexpectedValue) {
        if (JVMCI_PRERELEASE) {
            return;
        }
        String message = name;
        if (deferErrors) {
            StackTraceElement[] trace = new Exception().getStackTrace();
            for (StackTraceElement e : trace) {
                if (e.getClassName().equals(GraalHotSpotVMConfigAccess.class.getName())) {
                    // Skip methods in GraalHotSpotVMConfigAccess
                    continue;
                }
                // Looking for the field assignment in a constructor
                if (e.getMethodName().equals("<init>")) {
                    message += " at " + e;
                    break;
                }
            }
        }
        if (unexpectedValue != null) {
            message += " [value: " + unexpectedValue + "]";
        }
        list.add(message);
        if (!deferErrors) {
            reportErrors();
        }
    }

    protected void reportErrors() {
        deferErrors = false;
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            String jvmci = JVMCI_VERSION == null ? "" : " jvmci-" + JVMCI_VERSION;
            String runtime = String.format("JDK %d%s %s-%s (java.home=%s, java.vm.name=%s, java.vm.version=%s)",
                            JDK, jvmci, osName, osArch,
                            getProperty("java.home"),
                            getProperty("java.vm.name"),
                            getProperty("java.vm.version"));
            List<String> messages = new ArrayList<>();
            if (!missing.isEmpty()) {
                messages.add(String.format("VM config values missing that should be present in %s:%n    %s", runtime,
                                missing.stream().sorted().collect(Collectors.joining(System.lineSeparator() + "    "))));
            }
            if (!unexpected.isEmpty()) {
                messages.add(String.format("VM config values not expected to be present in %s:%n    %s", runtime,
                                unexpected.stream().sorted().collect(Collectors.joining(System.lineSeparator() + "    "))));
            }
            throw new JVMCIError(String.join(System.lineSeparator(), messages));
        }
    }

    /**
     * @see HotSpotVMConfigAccess#getAddress(String, Long)
     */
    public long getAddress(String name, Long notPresent, boolean expectPresent) {
        if (isPresent(name, vmAddresses, expectPresent)) {
            return access.getAddress(name, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getAddress(String)
     */
    public long getAddress(String name) {
        if (isPresent(name, vmAddresses, true)) {
            return access.getAddress(name, 0L);
        }
        return 0L;
    }

    /**
     * @see HotSpotVMConfigAccess#getConstant(String, Class, Object)
     */
    public <T> T getConstant(String name, Class<T> type, T notPresent, boolean expectPresent) {
        if (isPresent(name, vmConstants, expectPresent)) {
            return access.getConstant(name, type, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getConstant(String, Class)
     */
    public <T> T getConstant(String name, Class<T> type) {
        if (isPresent(name, vmConstants, true)) {
            return access.getConstant(name, type);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldOffset(String, Class, String, Object)
     */
    public <T> T getFieldOffset(String name, Class<T> type, String cppType, T notPresent, boolean expectPresent) {
        if (isPresent(name, vmFields, expectPresent)) {
            return access.getFieldOffset(name, type, cppType, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldOffset(String, Class, String)
     */
    public <T> T getFieldOffset(String name, Class<T> type, String cppType) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldOffset(name, type, cppType);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldOffset(String, Class)
     */
    public <T> T getFieldOffset(String name, Class<T> type) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldOffset(name, type);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldAddress(String, String)
     */
    public long getFieldAddress(String name, String cppType) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldAddress(name, cppType);
        }
        return 0L;
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldValue(String, Class, String, Object)
     */
    public <T> T getFieldValue(String name, Class<T> type, String cppType, T notPresent, boolean expectPresent) {
        if (isPresent(name, vmFields, expectPresent)) {
            return access.getFieldValue(name, type, cppType, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldValue(String, Class, String)
     */
    public <T> T getFieldValue(String name, Class<T> type, String cppType) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldValue(name, type, cppType);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldValue(String, Class)
     */
    public <T> T getFieldValue(String name, Class<T> type) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldValue(name, type);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFlag(String, Class)
     */
    public <T> T getFlag(String name, Class<T> type) {
        try {
            return access.getFlag(name, type);
        } catch (JVMCIError e) {
            recordError(name, missing, null);
            return getDefault(type);
        }
    }

    /**
     * @see HotSpotVMConfigAccess#getFlag(String, Class, Object)
     */
    @SuppressWarnings("deprecation")
    public <T> T getFlag(String name, Class<T> type, T notPresent, boolean expectPresent) {
        if (expectPresent) {
            return getFlag(name, type);
        }
        if (Assertions.assertionsEnabled()) {
            // There's more overhead for checking unexpectedly
            // present flag values due to the fact that a VM call
            // not exposed by JVMCI is needed to determine whether
            // a flag value is available. As such, only pay the
            // overhead when running with assertions enabled.
            T sentinel;
            if (type == Boolean.class) {
                sentinel = type.cast(new Boolean(false));
            } else if (type == Byte.class) {
                sentinel = type.cast(new Byte((byte) 123));
            } else if (type == Integer.class) {
                sentinel = type.cast(new Integer(1234567890));
            } else if (type == Long.class) {
                sentinel = type.cast(new Long(1234567890987654321L));
            } else if (type == String.class) {
                sentinel = type.cast(new String("1234567890987654321"));
            } else {
                throw new JVMCIError("Unsupported flag type: " + type.getName());
            }
            T value = access.getFlag(name, type, sentinel);
            if (value != sentinel) {
                recordError(name, unexpected, String.valueOf(value));
            }
        }
        return access.getFlag(name, type, notPresent);
    }

    private static <T> T getDefault(Class<T> type) {
        if (type == Boolean.class) {
            return type.cast(Boolean.FALSE);
        }
        if (type == Byte.class) {
            return type.cast(Byte.valueOf((byte) 0));
        }
        if (type == Integer.class) {
            return type.cast(Integer.valueOf(0));
        }
        if (type == Long.class) {
            return type.cast(Long.valueOf(0));
        }
        if (type == String.class) {
            return type.cast(null);
        }
        throw new JVMCIError("Unsupported VM config value type: " + type.getName());
    }
}
