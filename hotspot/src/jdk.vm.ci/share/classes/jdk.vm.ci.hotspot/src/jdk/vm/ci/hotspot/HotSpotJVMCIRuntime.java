/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.inittimer.InitTimer.*;

import java.util.*;

import jdk.vm.ci.code.*;
import jdk.vm.ci.common.*;
import jdk.vm.ci.compiler.*;
import jdk.vm.ci.compiler.Compiler;
import jdk.vm.ci.inittimer.*;
import jdk.vm.ci.meta.*;
import jdk.vm.ci.runtime.*;
import jdk.vm.ci.service.*;

//JaCoCo Exclude

public final class HotSpotJVMCIRuntime implements HotSpotJVMCIRuntimeProvider, HotSpotProxified {

    /**
     * The proper initialization of this class is complex because it's tangled up with the
     * initialization of the JVMCI and really should only ever be triggered through
     * {@link JVMCI#getRuntime}. However since {@link #runtime} can also be called directly it
     * should also trigger proper initialization. To ensure proper ordering, the static initializer
     * of this class initializes {@link JVMCI} and then access to {@link DelayedInit#instance}
     * triggers the final initialization of the {@link HotSpotJVMCIRuntime}.
     */
    static {
        JVMCI.initialize();
    }

    @SuppressWarnings("try")
    static class DelayedInit {
        private static final HotSpotJVMCIRuntime instance;

        static {
            try (InitTimer t0 = timer("HotSpotJVMCIRuntime.<clinit>")) {
                try (InitTimer t = timer("StartupEventListener.beforeJVMCIStartup")) {
                    for (StartupEventListener l : Services.load(StartupEventListener.class)) {
                        l.beforeJVMCIStartup();
                    }
                }

                try (InitTimer t = timer("HotSpotJVMCIRuntime.<init>")) {
                    instance = new HotSpotJVMCIRuntime();
                }

                try (InitTimer t = timer("HotSpotJVMCIRuntime.completeInitialization")) {
                    instance.completeInitialization();
                }
            }
        }
    }

    /**
     * Gets the singleton {@link HotSpotJVMCIRuntime} object.
     */
    public static HotSpotJVMCIRuntime runtime() {
        assert DelayedInit.instance != null;
        return DelayedInit.instance;
    }

    /**
     * Do deferred initialization.
     */
    public void completeInitialization() {
        compiler = HotSpotJVMCICompilerConfig.getCompilerFactory().createCompiler(this);
        for (HotSpotVMEventListener vmEventListener : vmEventListeners) {
            vmEventListener.completeInitialization(this);
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
        return runtime().getHostJVMCIBackend().getCodeCache().getTarget().wordKind;
    }

    protected final CompilerToVM compilerToVm;

    protected final HotSpotVMConfig config;
    private final JVMCIBackend hostBackend;

    private Compiler compiler;
    protected final JVMCIMetaAccessContext metaAccessContext;

    private final Map<Class<? extends Architecture>, JVMCIBackend> backends = new HashMap<>();

    private final Iterable<HotSpotVMEventListener> vmEventListeners;

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

        CompilerFactory compilerFactory = HotSpotJVMCICompilerConfig.getCompilerFactory();

        try (InitTimer t = timer("create JVMCI backend:", hostArchitecture)) {
            hostBackend = registerBackend(factory.createJVMCIBackend(this, compilerFactory, null));
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

    public Compiler getCompiler() {
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

    public Map<Class<? extends Architecture>, JVMCIBackend> getBackends() {
        return Collections.unmodifiableMap(backends);
    }

    /**
     * Called from the VM.
     */
    @SuppressWarnings({"unused"})
    private void compileMethod(HotSpotResolvedJavaMethod method, int entryBCI, long jvmciEnv, int id) {
        compiler.compileMethod(method, entryBCI, jvmciEnv, id);
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
     * @param compResult
     */
    void notifyInstall(HotSpotCodeCacheProvider hotSpotCodeCacheProvider, InstalledCode installedCode, CompilationResult compResult) {
        for (HotSpotVMEventListener vmEventListener : vmEventListeners) {
            vmEventListener.notifyInstall(hotSpotCodeCacheProvider, installedCode, compResult);
        }
    }
}
