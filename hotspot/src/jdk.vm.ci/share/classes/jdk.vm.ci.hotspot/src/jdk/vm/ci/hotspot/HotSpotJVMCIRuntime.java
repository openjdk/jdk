/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.inittimer.InitTimer.timer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.inittimer.InitTimer;
import jdk.vm.ci.inittimer.SuppressFBWarnings;
import jdk.vm.ci.meta.JVMCIMetaAccessContext;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.services.Services;
import jdk.internal.misc.VM;

//JaCoCo Exclude

/**
 * HotSpot implementation of a JVMCI runtime.
 *
 * The initialization of this class is very fragile since it's initialized both through
 * {@link JVMCI#initialize()} or through calling {@link HotSpotJVMCIRuntime#runtime()} and
 * {@link HotSpotJVMCIRuntime#runtime()} is also called by {@link JVMCI#initialize()}. So this class
 * can't have a static initializer and any required initialization must be done as part of
 * {@link #runtime()}. This allows the initialization to funnel back through
 * {@link JVMCI#initialize()} without deadlocking.
 */
public final class HotSpotJVMCIRuntime implements HotSpotJVMCIRuntimeProvider, HotSpotProxified {

    @SuppressWarnings("try")
    static class DelayedInit {
        private static final HotSpotJVMCIRuntime instance;

        static {
            try (InitTimer t = timer("HotSpotJVMCIRuntime.<init>")) {
                instance = new HotSpotJVMCIRuntime();
            }
        }
    }

    /**
     * Gets the singleton {@link HotSpotJVMCIRuntime} object.
     */
    public static HotSpotJVMCIRuntime runtime() {
        JVMCI.initialize();
        return DelayedInit.instance;
    }

    /**
     * A list of all supported JVMCI options.
     */
    public enum Option {
        Compiler(String.class, null, "Selects the system compiler."),
        ImplicitStableValues(boolean.class, true, "Mark well-known stable fields as such."),
        // Note: The following one is not used (see InitTimer.ENABLED).
        InitTimer(boolean.class, false, "Specifies if initialization timing is enabled."),
        PrintConfig(boolean.class, false, "Prints all HotSpotVMConfig fields."),
        PrintFlags(boolean.class, false, "Prints all JVMCI flags and exits."),
        ShowFlags(boolean.class, false, "Prints all JVMCI flags and continues."),
        TraceMethodDataFilter(String.class, null, ""),
        TrustFinalDefaultFields(boolean.class, true, "Determines whether to treat final fields with default values as constant.");

        /**
         * The prefix for system properties that are JVMCI options.
         */
        private static final String JVMCI_OPTION_PROPERTY_PREFIX = "jvmci.";

        /**
         * Marker for uninitialized flags.
         */
        private static final String UNINITIALIZED = "UNINITIALIZED";

        private final Class<?> type;
        private Object value;
        private final Object defaultValue;
        private boolean isDefault;
        private final String help;

        Option(Class<?> type, Object defaultValue, String help) {
            assert Character.isUpperCase(name().charAt(0)) : "Option name must start with upper-case letter: " + name();
            this.type = type;
            this.value = UNINITIALIZED;
            this.defaultValue = defaultValue;
            this.help = help;
        }

        @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "sentinel must be String since it's a static final in an enum")
        private Object getValue() {
            if (value == UNINITIALIZED) {
                String propertyValue = VM.getSavedProperty(JVMCI_OPTION_PROPERTY_PREFIX + name());
                if (propertyValue == null) {
                    this.value = defaultValue;
                    this.isDefault = true;
                } else {
                    if (type == boolean.class) {
                        this.value = Boolean.parseBoolean(propertyValue);
                    } else if (type == String.class) {
                        this.value = propertyValue;
                    } else {
                        throw new JVMCIError("Unexpected option type " + type);
                    }
                    this.isDefault = false;
                }
                // Saved properties should not be interned - let's be sure
                assert value != UNINITIALIZED;
            }
            return value;
        }

        /**
         * Returns the option's value as boolean.
         *
         * @return option's value
         */
        public boolean getBoolean() {
            return (boolean) getValue();
        }

        /**
         * Returns the option's value as String.
         *
         * @return option's value
         */
        public String getString() {
            return (String) getValue();
        }

        /**
         * Prints all option flags to {@code out}.
         *
         * @param out stream to print to
         */
        public static void printFlags(PrintStream out) {
            out.println("[List of JVMCI options]");
            for (Option option : values()) {
                Object value = option.getValue();
                String assign = option.isDefault ? ":=" : " =";
                out.printf("%9s %-40s %s %-14s %s%n", option.type.getSimpleName(), option, assign, value, option.help);
            }
        }
    }

    public static HotSpotJVMCIBackendFactory findFactory(String architecture) {
        for (HotSpotJVMCIBackendFactory factory : Services.load(HotSpotJVMCIBackendFactory.class)) {
            if (factory.getArchitecture().equalsIgnoreCase(architecture)) {
                return factory;
            }
        }

        throw new JVMCIError("No JVMCI runtime available for the %s architecture", architecture);
    }

    /**
     * Gets the kind of a word value on the {@linkplain #getHostJVMCIBackend() host} backend.
     */
    public static JavaKind getHostWordKind() {
        return runtime().getHostJVMCIBackend().getCodeCache().getTarget().wordJavaKind;
    }

    protected final CompilerToVM compilerToVm;

    protected final HotSpotVMConfig config;
    private final JVMCIBackend hostBackend;

    private volatile JVMCICompiler compiler;
    protected final JVMCIMetaAccessContext metaAccessContext;

    private final Map<Class<? extends Architecture>, JVMCIBackend> backends = new HashMap<>();

    private final Iterable<HotSpotVMEventListener> vmEventListeners;

    @SuppressWarnings("unused") private final String[] trivialPrefixes;

    @SuppressWarnings("try")
    private HotSpotJVMCIRuntime() {
        compilerToVm = new CompilerToVM();

        try (InitTimer t = timer("HotSpotVMConfig<init>")) {
            config = new HotSpotVMConfig(compilerToVm);
        }

        String hostArchitecture = config.getHostArchitectureName();

        HotSpotJVMCIBackendFactory factory;
        try (InitTimer t = timer("find factory:", hostArchitecture)) {
            factory = findFactory(hostArchitecture);
        }

        try (InitTimer t = timer("create JVMCI backend:", hostArchitecture)) {
            hostBackend = registerBackend(factory.createJVMCIBackend(this, null));
        }

        vmEventListeners = Services.load(HotSpotVMEventListener.class);

        JVMCIMetaAccessContext context = null;
        for (HotSpotVMEventListener vmEventListener : vmEventListeners) {
            context = vmEventListener.createMetaAccessContext(this);
            if (context != null) {
                break;
            }
        }
        if (context == null) {
            context = new HotSpotJVMCIMetaAccessContext();
        }
        metaAccessContext = context;

        boolean printFlags = Option.PrintFlags.getBoolean();
        boolean showFlags = Option.ShowFlags.getBoolean();
        if (printFlags || showFlags) {
            Option.printFlags(System.out);
            if (printFlags) {
                System.exit(0);
            }
        }

        if (Option.PrintConfig.getBoolean()) {
            printConfig(config, compilerToVm);
        }

        trivialPrefixes = HotSpotJVMCICompilerConfig.getCompilerFactory().getTrivialPrefixes();
    }

    private JVMCIBackend registerBackend(JVMCIBackend backend) {
        Class<? extends Architecture> arch = backend.getCodeCache().getTarget().arch.getClass();
        JVMCIBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    public ResolvedJavaType fromClass(Class<?> javaClass) {
        return metaAccessContext.fromClass(javaClass);
    }

    public HotSpotVMConfig getConfig() {
        return config;
    }

    public CompilerToVM getCompilerToVM() {
        return compilerToVm;
    }

    public JVMCIMetaAccessContext getMetaAccessContext() {
        return metaAccessContext;
    }

    public JVMCICompiler getCompiler() {
        if (compiler == null) {
            synchronized (this) {
                if (compiler == null) {
                    compiler = HotSpotJVMCICompilerConfig.getCompilerFactory().createCompiler(this);
                }
            }
        }
        return compiler;
    }

    public JavaType lookupType(String name, HotSpotResolvedObjectType accessingType, boolean resolve) {
        Objects.requireNonNull(accessingType, "cannot resolve type without an accessing class");
        // If the name represents a primitive type we can short-circuit the lookup.
        if (name.length() == 1) {
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            return fromClass(kind.toJavaClass());
        }

        // Resolve non-primitive types in the VM.
        HotSpotResolvedObjectTypeImpl hsAccessingType = (HotSpotResolvedObjectTypeImpl) accessingType;
        final HotSpotResolvedObjectTypeImpl klass = compilerToVm.lookupType(name, hsAccessingType.mirror(), resolve);

        if (klass == null) {
            assert resolve == false;
            return HotSpotUnresolvedJavaType.create(this, name);
        }
        return klass;
    }

    public JVMCIBackend getHostJVMCIBackend() {
        return hostBackend;
    }

    public <T extends Architecture> JVMCIBackend getJVMCIBackend(Class<T> arch) {
        assert arch != Architecture.class;
        return backends.get(arch);
    }

    public Map<Class<? extends Architecture>, JVMCIBackend> getJVMCIBackends() {
        return Collections.unmodifiableMap(backends);
    }

    /**
     * Called from the VM.
     */
    @SuppressWarnings({"unused"})
    private CompilationRequestResult compileMethod(HotSpotResolvedJavaMethod method, int entryBCI, long jvmciEnv, int id) {
        CompilationRequestResult result = getCompiler().compileMethod(new HotSpotCompilationRequest(method, entryBCI, jvmciEnv, id));
        assert result != null : "compileMethod must always return something";
        return result;
    }

    /**
     * Shuts down the runtime.
     *
     * Called from the VM.
     */
    @SuppressWarnings({"unused"})
    private void shutdown() throws Exception {
        for (HotSpotVMEventListener vmEventListener : vmEventListeners) {
            vmEventListener.notifyShutdown();
        }
    }

    /**
     * Notify on successful install into the CodeCache.
     *
     * @param hotSpotCodeCacheProvider
     * @param installedCode
     * @param compiledCode
     */
    void notifyInstall(HotSpotCodeCacheProvider hotSpotCodeCacheProvider, InstalledCode installedCode, CompiledCode compiledCode) {
        for (HotSpotVMEventListener vmEventListener : vmEventListeners) {
            vmEventListener.notifyInstall(hotSpotCodeCacheProvider, installedCode, compiledCode);
        }
    }

    private static void printConfig(HotSpotVMConfig config, CompilerToVM vm) {
        Field[] fields = config.getClass().getDeclaredFields();
        Map<String, Field> sortedFields = new TreeMap<>();
        for (Field f : fields) {
            if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                sortedFields.put(f.getName(), f);
            }
        }
        for (Field f : sortedFields.values()) {
            try {
                String line = String.format("%9s %-40s = %s%n", f.getType().getSimpleName(), f.getName(), pretty(f.get(config)));
                byte[] lineBytes = line.getBytes();
                vm.writeDebugOutput(lineBytes, 0, lineBytes.length);
                vm.flushDebugOutput();
            } catch (Exception e) {
            }
        }
    }

    private static String pretty(Object value) {
        if (value == null) {
            return "null";
        }

        Class<?> klass = value.getClass();
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Method) {
            return "method \"" + ((Method) value).getName() + "\"";
        } else if (value instanceof Class<?>) {
            return "class \"" + ((Class<?>) value).getSimpleName() + "\"";
        } else if (value instanceof Integer) {
            if ((Integer) value < 10) {
                return value.toString();
            }
            return value + " (0x" + Integer.toHexString((Integer) value) + ")";
        } else if (value instanceof Long) {
            if ((Long) value < 10 && (Long) value > -10) {
                return value + "l";
            }
            return value + "l (0x" + Long.toHexString((Long) value) + "l)";
        } else if (klass.isArray()) {
            StringBuilder str = new StringBuilder();
            int dimensions = 0;
            while (klass.isArray()) {
                dimensions++;
                klass = klass.getComponentType();
            }
            int length = Array.getLength(value);
            str.append(klass.getSimpleName()).append('[').append(length).append(']');
            for (int i = 1; i < dimensions; i++) {
                str.append("[]");
            }
            str.append(" {");
            for (int i = 0; i < length; i++) {
                str.append(pretty(Array.get(value, i)));
                if (i < length - 1) {
                    str.append(", ");
                }
            }
            str.append('}');
            return str.toString();
        }
        return value.toString();
    }

    public OutputStream getLogStream() {
        return new OutputStream() {

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (b == null) {
                    throw new NullPointerException();
                } else if (off < 0 || off > b.length || len < 0 || (off + len) > b.length || (off + len) < 0) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return;
                }
                compilerToVm.writeDebugOutput(b, off, len);
            }

            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void flush() throws IOException {
                compilerToVm.flushDebugOutput();
            }
        };
    }

    /**
     * Collects the current values of all JVMCI benchmark counters, summed up over all threads.
     */
    public long[] collectCounters() {
        return compilerToVm.collectCounters();
    }
}
