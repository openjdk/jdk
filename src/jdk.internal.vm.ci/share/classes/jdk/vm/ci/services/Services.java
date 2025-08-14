/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.VM;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.Architecture;
import jdk.internal.util.OperatingSystem;

/**
 * Provides utilities needed by JVMCI clients.
 */
public final class Services {

    /**
     * Guards code that should only be run in a JVMCI shared library. Such code must be directly
     * guarded by an {@code if} statement on this field - the guard cannot be behind a method call.
     *
     * The value of this field in a JVMCI shared library runtime must be {@code true}.
     */
    public static final boolean IS_IN_NATIVE_IMAGE;
    static {
        /*
         * Prevents javac from constant folding use of this field. It is set to true by the process
         * that builds the shared library.
         */
        IS_IN_NATIVE_IMAGE = false;
    }

    private Services() {
    }

    /**
     * Lazily initialized in {@link #getSavedProperties}.
     */
    private static volatile Map<String, String> savedProperties;

    /**
     * Gets an unmodifiable copy of the system properties as of VM startup.
     *
     * If running on Hotspot, this will be the system properties parsed by {@code arguments.cpp}
     * plus {@code java.specification.version}, {@code os.name} and {@code os.arch}. The latter two
     * are forced to be the real OS and architecture. That is, values for these two properties set
     * on the command line are ignored.
     */
    public static Map<String, String> getSavedProperties() {
        if (savedProperties == null) {
            synchronized (Services.class) {
                if (savedProperties == null) {
                    savedProperties = initProperties();
                }
            }
        }
        return savedProperties;
    }

    /**
     * Helper method equivalent to {@link #getSavedProperties()}{@code .getOrDefault(name, def)}.
     */
    public static String getSavedProperty(String name, String def) {
        return Services.getSavedProperties().getOrDefault(name, def);
    }

    /**
     * Helper method equivalent to {@link #getSavedProperties()}{@code .get(name)}.
     */
    public static String getSavedProperty(String name) {
        return Services.getSavedProperties().get(name);
    }

    /**
     * Causes the JVMCI subsystem to be initialized if it isn't already initialized.
     */
    public static void initializeJVMCI() {
        try {
            Class.forName("jdk.vm.ci.runtime.JVMCI");
        } catch (ClassNotFoundException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Opens all JVMCI packages to {@code otherModule}.
     */
    static void openJVMCITo(Module otherModule) {
        Module jvmci = Services.class.getModule();
        if (jvmci != otherModule) {
            Set<String> packages = jvmci.getPackages();
            for (String pkg : packages) {
                boolean opened = jvmci.isOpen(pkg, otherModule);
                if (!opened) {
                    jvmci.addOpens(pkg, otherModule);
                }
            }
        }
    }

    /**
     * Creates a thread-local variable that notifies {@code onThreadTermination} when a thread
     * terminates and it has been initialized in the terminating thread (even if it was initialized
     * with a null value). A typical use is to release resources associated with a thread.
     *
     * @param initialValue a supplier to be used to determine the initial value
     * @param onThreadTermination a consumer invoked by a thread when terminating and the
     *            thread-local has an associated value for the terminating thread. The current
     *            thread's value of the thread-local variable is passed as a parameter to the
     *            consumer.
     */
    public static <T> ThreadLocal<T> createTerminatingThreadLocal(Supplier<T> initialValue, Consumer<T> onThreadTermination) {
        Objects.requireNonNull(initialValue, "initialValue must be non null.");
        Objects.requireNonNull(onThreadTermination, "onThreadTermination must be non null.");
        return new TerminatingThreadLocal<>() {

            @Override
            protected T initialValue() {
                return initialValue.get();
            }

            @Override
            protected void threadTerminated(T value) {
                onThreadTermination.accept(value);
            }
        };
    }

    static String toJavaString(Unsafe unsafe, long cstring) {
        if (cstring == 0) {
            return null;
        }
        int len = 0;
        for (long p = cstring; unsafe.getByte(p) != 0; p++) {
            len++;
        }
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            buf[i] = unsafe.getByte(cstring + i);
        }
        return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Gets the value of {@code Arguments::systemProperties()} and puts the offsets
     * of {@code SystemProperty} fields into {@code offsets}. The values returned in
     * {@code offsets} are:
     *
     * <pre>
     *     [ next,  // SystemProperty::next_offset_in_bytes()
     *       key,   // SystemProperty::key_offset_in_bytes()
     *       value  // PathString::value_offset_in_bytes()
     *     ]
     * </pre>
     *
     * Ideally this would be done with vmstructs but that code is in {@code jdk.vm.ci.hotspot}.
     */
    private static native long readSystemPropertiesInfo(int[] offsets);

    /**
     * Parses the native {@code Arguments::systemProperties()} data structure using Unsafe to
     * create a properties map. This parsing is safe as argument parsing in completed in
     * early VM start before this code can be executed, making {@code Arguments::systemProperties()}
     * effectively read-only by now.
     */
    private static Map<String, String> initProperties() {
        int[] offsets = new int[3];
        long systemProperties = readSystemPropertiesInfo(offsets);
        int nextOffset = offsets[0];
        int keyOffset = offsets[1];
        int valueOffset = offsets[2];

        int count = 0;
        Unsafe unsafe = Unsafe.getUnsafe();
        for (long prop = systemProperties; prop != 0; prop = unsafe.getLong(prop + nextOffset)) {
            if (unsafe.getLong(prop + valueOffset) != 0) {
                count++;
            } else {
                // Some internal properties (e.g. jdk.boot.class.path.append) can have a null
                // value and should just be ignored. Note that null is different than the empty string.
            }
        }
        Map<String, SystemProperties.Value> props = new HashMap<>(count + 1);
        int i = 0;
        for (long prop = systemProperties; prop != 0; prop = unsafe.getLong(prop + nextOffset)) {
            String key = toJavaString(unsafe, unsafe.getLong(prop + keyOffset));
            long valueAddress = unsafe.getLong(prop + valueOffset);
            if (valueAddress != 0) {
                props.put(key, new SystemProperties.Value(unsafe, valueAddress));
                i++;
            }
        }
        if (i != count) {
            throw new InternalError(i + " != " + count);
        }
        if (!props.containsKey("java.specification.version")) {
            SystemProperties.Value v = Objects.requireNonNull(props.get("java.vm.specification.version"));
            props.put("java.specification.version", v);
        }

        SystemProperties res = new SystemProperties(unsafe, sanitizeOSArch(props));
        if ("true".equals(res.get("debug.jvmci.PrintSavedProperties"))) {
            System.out.println("[Saved system properties]");
            for (Map.Entry<String, String> e : res.entrySet()) {
                System.out.printf("%s=%s%n", e.getKey(), e.getValue());
            }
        }
        return res;
    }

    // Force os.name and os.arch to reflect the actual OS and architecture.
    // JVMCI configures itself based on these values and needs to be isolated
    // from apps that set them on the command line.
    private static Map<String, SystemProperties.Value> sanitizeOSArch(Map<String, SystemProperties.Value> props) {
        props.put("os.arch", new SystemProperties.Value(realArch()));
        props.put("os.name", new SystemProperties.Value(realOS()));
        return props;
    }

    private static String realOS() {
        OperatingSystem os = OperatingSystem.current();
        switch (os) {
            case LINUX: return "Linux";
            case MACOS: return "Mac OS X";
            case AIX: return "AIX";
            case WINDOWS: {
                String osName = System.getProperty("os.name");
                if (osName.startsWith("Windows")) {
                    // Use original value which is often more "complete"
                    // E.g. "Windows Server 2012"
                    return osName;
                }
                return "Windows";
            }
            default: throw new InternalError("missing case for " + os);
        }
    }

    private static String realArch() {
        Architecture arch = Architecture.current();
        switch (arch) {
            case X64: return "x86_64";
            case X86: return "x86";
            case AARCH64: return "aarch64";
            case RISCV64: return "riscv64";
            case ARM: return "arm";
            case S390: return "s390";
            case PPC64: return "ppc64";
            case OTHER: return "other";
            default: throw new InternalError("missing case for " + arch);
        }
    }
}
