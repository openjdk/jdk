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
package org.graalvm.compiler.nodes.graphbuilderconf;

import static java.lang.String.format;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitutionRegistry;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.util.Equivalence;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.EconomicSet;
import org.graalvm.util.UnmodifiableMapCursor;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Manages a set of {@link InvocationPlugin}s.
 */
public class InvocationPlugins {

    public static class InvocationPluginReceiver implements InvocationPlugin.Receiver {
        private final GraphBuilderContext parser;
        private ValueNode[] args;
        private ValueNode value;

        public InvocationPluginReceiver(GraphBuilderContext parser) {
            this.parser = parser;
        }

        @Override
        public ValueNode get(boolean performNullCheck) {
            assert args != null : "Cannot get the receiver of a static method";
            if (!performNullCheck) {
                return args[0];
            }
            if (value == null) {
                value = parser.nullCheckedValue(args[0]);
                if (value != args[0]) {
                    args[0] = value;
                }
            }
            return value;
        }

        @Override
        public boolean isConstant() {
            return args[0].isConstant();
        }

        public InvocationPluginReceiver init(ResolvedJavaMethod targetMethod, ValueNode[] newArgs) {
            if (!targetMethod.isStatic()) {
                this.args = newArgs;
                this.value = null;
                return this;
            }
            return null;
        }
    }

    /**
     * A symbol that is lazily {@linkplain OptionalLazySymbol#resolve() resolved} to a {@link Type}.
     */
    static class OptionalLazySymbol implements Type {
        private static final Class<?> MASK_NULL = OptionalLazySymbol.class;
        private final String name;
        private Class<?> resolved;

        OptionalLazySymbol(String name) {
            this.name = name;
        }

        @Override
        public String getTypeName() {
            return name;
        }

        /**
         * Gets the resolved {@link Class} corresponding to this symbol or {@code null} if
         * resolution fails.
         */
        public Class<?> resolve() {
            if (resolved == null) {
                Class<?> resolvedOrNull = resolveClass(name, true);
                resolved = resolvedOrNull == null ? MASK_NULL : resolvedOrNull;
            }
            return resolved == MASK_NULL ? null : resolved;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Utility for {@linkplain InvocationPlugins#register(InvocationPlugin, Class, String, Class...)
     * registration} of invocation plugins.
     */
    public static class Registration implements MethodSubstitutionRegistry {

        private final InvocationPlugins plugins;
        private final Type declaringType;
        private final BytecodeProvider methodSubstitutionBytecodeProvider;
        private boolean allowOverwrite;

        @Override
        public Class<?> getReceiverType() {
            return Receiver.class;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringType the class declaring the methods for which plugins will be registered
         *            via this object
         */
        public Registration(InvocationPlugins plugins, Type declaringType) {
            this.plugins = plugins;
            this.declaringType = declaringType;
            this.methodSubstitutionBytecodeProvider = null;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringType the class declaring the methods for which plugins will be registered
         *            via this object
         * @param methodSubstitutionBytecodeProvider provider used to get the bytecodes to parse for
         *            method substitutions
         */
        public Registration(InvocationPlugins plugins, Type declaringType, BytecodeProvider methodSubstitutionBytecodeProvider) {
            this.plugins = plugins;
            this.declaringType = declaringType;
            this.methodSubstitutionBytecodeProvider = methodSubstitutionBytecodeProvider;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringClassName the name of the class class declaring the methods for which
         *            plugins will be registered via this object
         * @param methodSubstitutionBytecodeProvider provider used to get the bytecodes to parse for
         *            method substitutions
         */
        public Registration(InvocationPlugins plugins, String declaringClassName, BytecodeProvider methodSubstitutionBytecodeProvider) {
            this.plugins = plugins;
            this.declaringType = new OptionalLazySymbol(declaringClassName);
            this.methodSubstitutionBytecodeProvider = methodSubstitutionBytecodeProvider;
        }

        /**
         * Configures this registration to allow or disallow overwriting of invocation plugins.
         */
        public Registration setAllowOverwrite(boolean allowOverwrite) {
            this.allowOverwrite = allowOverwrite;
            return this;
        }

        /**
         * Registers a plugin for a method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register0(String name, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name);
        }

        /**
         * Registers a plugin for a method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register1(String name, Type arg, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg);
        }

        /**
         * Registers a plugin for a method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register2(String name, Type arg1, Type arg2, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2);
        }

        /**
         * Registers a plugin for a method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register3(String name, Type arg1, Type arg2, Type arg3, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3);
        }

        /**
         * Registers a plugin for a method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register4(String name, Type arg1, Type arg2, Type arg3, Type arg4, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4);
        }

        /**
         * Registers a plugin for a method with 5 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register5(String name, Type arg1, Type arg2, Type arg3, Type arg4, Type arg5, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4, arg5);
        }

        /**
         * Registers a plugin for a method with 6 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register6(String name, Type arg1, Type arg2, Type arg3, Type arg4, Type arg5, Type arg6, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4, arg5, arg6);
        }

        /**
         * Registers a plugin for a method with 7 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register7(String name, Type arg1, Type arg2, Type arg3, Type arg4, Type arg5, Type arg6, Type arg7, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        }

        /**
         * Registers a plugin for an optional method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional0(String name, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name);
        }

        /**
         * Registers a plugin for an optional method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional1(String name, Type arg, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg);
        }

        /**
         * Registers a plugin for an optional method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional2(String name, Type arg1, Type arg2, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg1, arg2);
        }

        /**
         * Registers a plugin for an optional method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional3(String name, Type arg1, Type arg2, Type arg3, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg1, arg2, arg3);
        }

        /**
         * Registers a plugin for an optional method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional4(String name, Type arg1, Type arg2, Type arg3, Type arg4, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4);
        }

        /**
         * Registers a plugin that implements a method based on the bytecode of a substitute method.
         *
         * @param substituteDeclaringClass the class declaring the substitute method
         * @param name the name of both the original and substitute method
         * @param argumentTypes the argument types of the method. Element 0 of this array must be
         *            the {@link Class} value for {@link InvocationPlugin.Receiver} iff the method
         *            is non-static. Upon returning, element 0 will have been rewritten to
         *            {@code declaringClass}
         */
        @Override
        public void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, Type... argumentTypes) {
            registerMethodSubstitution(substituteDeclaringClass, name, name, argumentTypes);
        }

        /**
         * Registers a plugin that implements a method based on the bytecode of a substitute method.
         *
         * @param substituteDeclaringClass the class declaring the substitute method
         * @param name the name of both the original method
         * @param substituteName the name of the substitute method
         * @param argumentTypes the argument types of the method. Element 0 of this array must be
         *            the {@link Class} value for {@link InvocationPlugin.Receiver} iff the method
         *            is non-static. Upon returning, element 0 will have been rewritten to
         *            {@code declaringClass}
         */
        @Override
        public void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, String substituteName, Type... argumentTypes) {
            MethodSubstitutionPlugin plugin = createMethodSubstitution(substituteDeclaringClass, substituteName, argumentTypes);
            plugins.register(plugin, false, allowOverwrite, declaringType, name, argumentTypes);
        }

        public MethodSubstitutionPlugin createMethodSubstitution(Class<?> substituteDeclaringClass, String substituteName, Type... argumentTypes) {
            assert methodSubstitutionBytecodeProvider != null : "Registration used for method substitutions requires a non-null methodSubstitutionBytecodeProvider";
            MethodSubstitutionPlugin plugin = new MethodSubstitutionPlugin(methodSubstitutionBytecodeProvider, substituteDeclaringClass, substituteName, argumentTypes);
            return plugin;
        }

    }

    /**
     * Key for a {@linkplain ClassPlugins#entries resolved} plugin registration. Due to the
     * possibility of class redefinition, we cannot directly use {@link ResolvedJavaMethod}s as
     * keys. A {@link ResolvedJavaMethod} implementation might implement {@code equals()} and
     * {@code hashCode()} based on internal representation subject to change by class redefinition.
     */
    static final class ResolvedJavaMethodKey {
        private final ResolvedJavaMethod method;

        ResolvedJavaMethodKey(ResolvedJavaMethod method) {
            this.method = method;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ResolvedJavaMethodKey) {
                ResolvedJavaMethodKey that = (ResolvedJavaMethodKey) obj;
                if (this.method.isStatic() == that.method.isStatic()) {
                    if (this.method.getDeclaringClass().equals(that.method.getDeclaringClass())) {
                        if (this.method.getName().equals(that.method.getName())) {
                            if (this.method.getSignature().equals(that.method.getSignature())) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.method.getName().hashCode();
        }

        @Override
        public String toString() {
            return "ResolvedJavaMethodKey<" + method + ">";
        }
    }

    /**
     * Key for {@linkplain ClassPlugins#registrations registering} an {@link InvocationPlugin} for a
     * specific method.
     */
    static class MethodKey {
        final boolean isStatic;

        /**
         * This method is optional. This is used for new API methods not present in previous JDK
         * versions.
         */
        final boolean isOptional;

        final String name;
        final Type[] argumentTypes;
        final InvocationPlugin value;

        /**
         * Used to lazily initialize {@link #resolved}.
         */
        private final MetaAccessProvider metaAccess;

        private volatile ResolvedJavaMethod resolved;

        MethodKey(MetaAccessProvider metaAccess, InvocationPlugin data, boolean isStatic, boolean isOptional, String name, Type... argumentTypes) {
            this.metaAccess = metaAccess;
            this.value = data;
            this.isStatic = isStatic;
            this.isOptional = isOptional;
            this.name = name;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodKey) {
                MethodKey that = (MethodKey) obj;
                boolean res = this.name.equals(that.name) && areEqual(this.argumentTypes, that.argumentTypes);
                assert !res || this.isStatic == that.isStatic;
                return res;
            }
            return false;
        }

        private static boolean areEqual(Type[] args1, Type[] args2) {
            if (args1.length == args2.length) {
                for (int i = 0; i < args1.length; i++) {
                    if (!args1[i].getTypeName().equals(args2[i].getTypeName())) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        public int getDeclaredParameterCount() {
            return isStatic ? argumentTypes.length : argumentTypes.length - 1;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        private ResolvedJavaMethod resolve(Class<?> declaringClass) {
            if (resolved == null) {
                Executable method = resolveJava(declaringClass);
                if (method == null) {
                    return null;
                }
                resolved = metaAccess.lookupJavaMethod(method);
            }
            return resolved;
        }

        private Executable resolveJava(Class<?> declaringClass) {
            try {
                Executable res;
                Class<?>[] parameterTypes = resolveTypes(argumentTypes, isStatic ? 0 : 1, argumentTypes.length);
                if (name.equals("<init>")) {
                    res = declaringClass.getDeclaredConstructor(parameterTypes);
                } else {
                    res = declaringClass.getDeclaredMethod(name, parameterTypes);
                }
                assert Modifier.isStatic(res.getModifiers()) == isStatic : res;
                return res;
            } catch (NoSuchMethodException | SecurityException e) {
                if (isOptional) {
                    return null;
                }
                throw new InternalError(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(name).append('(');
            for (Type p : argumentTypes) {
                if (sb.charAt(sb.length() - 1) != '(') {
                    sb.append(", ");
                }
                sb.append(p.getTypeName());
            }
            return sb.append(')').toString();
        }
    }

    private final MetaAccessProvider metaAccess;

    private final EconomicMap<String, ClassPlugins> registrations = EconomicMap.create(Equivalence.DEFAULT);

    /**
     * Deferred registrations as well as guard for initialization. The guard uses double-checked
     * locking which is why this field is {@code volatile}.
     */
    private volatile List<Runnable> deferredRegistrations = new ArrayList<>();

    /**
     * Adds a {@link Runnable} for doing registration deferred until the first time
     * {@link #get(ResolvedJavaMethod)} or {@link #closeRegistration()} is called on this object.
     */
    public void defer(Runnable deferrable) {
        assert deferredRegistrations != null : "registration is closed";
        deferredRegistrations.add(deferrable);
    }

    /**
     * Per-class invocation plugins.
     */
    protected static class ClassPlugins {
        private final Type declaringType;

        private final List<MethodKey> registrations = new ArrayList<>();

        public ClassPlugins(Type declaringClass) {
            this.declaringType = declaringClass;
        }

        /**
         * Entry map that is initialized upon first call to {@link #get(ResolvedJavaMethod)}.
         *
         * Note: this must be volatile as threads may race to initialize it.
         */
        private volatile EconomicMap<ResolvedJavaMethodKey, InvocationPlugin> entries;

        void initializeMap() {
            if (!isClosed()) {
                if (registrations.isEmpty()) {
                    entries = EconomicMap.create(Equivalence.DEFAULT);
                } else {
                    Class<?> declaringClass = resolveType(declaringType, true);
                    if (declaringClass == null) {
                        // An optional type that could not be resolved
                        entries = EconomicMap.create(Equivalence.DEFAULT);
                    } else {
                        EconomicMap<ResolvedJavaMethodKey, InvocationPlugin> newEntries = EconomicMap.create(Equivalence.DEFAULT);
                        for (MethodKey methodKey : registrations) {
                            ResolvedJavaMethod m = methodKey.resolve(declaringClass);
                            if (m != null) {
                                newEntries.put(new ResolvedJavaMethodKey(m), methodKey.value);
                                if (entries != null) {
                                    // Another thread finished initializing entries first
                                    return;
                                }
                            }
                        }
                        entries = newEntries;
                    }
                }
            }
        }

        public InvocationPlugin get(ResolvedJavaMethod method) {
            if (!isClosed()) {
                initializeMap();
            }
            return entries.get(new ResolvedJavaMethodKey(method));
        }

        public void register(MethodKey methodKey, boolean allowOverwrite) {
            assert !isClosed() : "registration is closed: " + methodKey;
            if (allowOverwrite) {
                int index = registrations.indexOf(methodKey);
                if (index >= 0) {
                    registrations.set(index, methodKey);
                    return;
                }
            } else {
                assert !registrations.contains(methodKey) : "a value is already registered for " + declaringType + "." + methodKey;
            }
            registrations.add(methodKey);
        }

        public boolean isClosed() {
            return entries != null;
        }
    }

    /**
     * Adds an entry to this map for a specified method.
     *
     * @param value value to be associated with the specified method
     * @param isStatic specifies if the method is static
     * @param isOptional specifies if the method is optional
     * @param declaringClass the class declaring the method
     * @param name the name of the method
     * @param argumentTypes the argument types of the method. Element 0 of this array must be
     *            {@code declaringClass} iff the method is non-static.
     * @return an object representing the method
     */
    MethodKey put(InvocationPlugin value, boolean isStatic, boolean isOptional, boolean allowOverwrite, Type declaringClass, String name, Type... argumentTypes) {
        assert isStatic || argumentTypes[0] == declaringClass;

        String internalName = MetaUtil.toInternalName(declaringClass.getTypeName());
        ClassPlugins classPlugins = registrations.get(internalName);
        if (classPlugins == null) {
            classPlugins = new ClassPlugins(declaringClass);
            registrations.put(internalName, classPlugins);
        }
        assert isStatic || argumentTypes[0] == declaringClass;
        MethodKey methodKey = new MethodKey(metaAccess, value, isStatic, isOptional, name, argumentTypes);
        classPlugins.register(methodKey, allowOverwrite);
        return methodKey;
    }

    /**
     * Determines if a method denoted by a given {@link MethodKey} is in this map.
     */
    boolean containsKey(Type declaringType, MethodKey key) {
        String internalName = MetaUtil.toInternalName(declaringType.getTypeName());
        ClassPlugins classPlugins = registrations.get(internalName);
        return classPlugins != null && classPlugins.registrations.contains(key);
    }

    InvocationPlugin get(ResolvedJavaMethod method) {
        flushDeferrables();
        String internalName = method.getDeclaringClass().getName();
        ClassPlugins classPlugins = registrations.get(internalName);
        if (classPlugins != null) {
            return classPlugins.get(method);
        }
        return null;
    }

    private void flushDeferrables() {
        if (deferredRegistrations != null) {
            synchronized (this) {
                if (deferredRegistrations != null) {
                    for (Runnable deferrable : deferredRegistrations) {
                        deferrable.run();
                    }
                    deferredRegistrations = null;
                }
            }
            for (ClassPlugins e : registrations.getValues()) {
                e.initializeMap();
            }
        }
    }

    /**
     * Disallows new registrations of new plugins, and creates the internal tables for method
     * lookup.
     */
    public void closeRegistration() {
        flushDeferrables();
        for (ClassPlugins e : registrations.getValues()) {
            e.initializeMap();
        }
    }

    public int size() {
        return registrations.size();
    }

    /**
     * The plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched} before searching in
     * this object.
     */
    protected final InvocationPlugins parent;

    private InvocationPlugins(InvocationPlugins parent, MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        InvocationPlugins p = parent;
        this.parent = p;
    }

    /**
     * Creates a set of invocation plugins with a non-null {@linkplain #getParent() parent}.
     */
    public InvocationPlugins(InvocationPlugins parent) {
        this(parent, parent.getMetaAccess());
    }

    public InvocationPlugins(Map<ResolvedJavaMethod, InvocationPlugin> plugins, InvocationPlugins parent, MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        this.parent = parent;

        this.deferredRegistrations = null;

        for (Map.Entry<ResolvedJavaMethod, InvocationPlugin> entry : plugins.entrySet()) {
            ResolvedJavaMethod method = entry.getKey();
            InvocationPlugin plugin = entry.getValue();

            String internalName = method.getDeclaringClass().getName();
            ClassPlugins classPlugins = registrations.get(internalName);
            if (classPlugins == null) {
                classPlugins = new ClassPlugins(null);
                registrations.put(internalName, classPlugins);
                classPlugins.entries = EconomicMap.create(Equivalence.DEFAULT);
            }

            classPlugins.entries.put(new ResolvedJavaMethodKey(method), plugin);
        }
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public InvocationPlugins(MetaAccessProvider metaAccess) {
        this(null, metaAccess);
    }

    protected void register(InvocationPlugin plugin, boolean isOptional, boolean allowOverwrite, Type declaringClass, String name, Type... argumentTypes) {
        boolean isStatic = argumentTypes.length == 0 || argumentTypes[0] != InvocationPlugin.Receiver.class;
        if (!isStatic) {
            argumentTypes[0] = declaringClass;
        }
        MethodKey methodKey = put(plugin, isStatic, isOptional, allowOverwrite, declaringClass, name, argumentTypes);
        assert Checker.check(this, declaringClass, methodKey, plugin);
    }

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     *
     * @param argumentTypes the argument types of the method. Element 0 of this array must be the
     *            {@link Class} value for {@link InvocationPlugin.Receiver} iff the method is
     *            non-static. Upon returning, element 0 will have been rewritten to
     *            {@code declaringClass}
     */
    public void register(InvocationPlugin plugin, Type declaringClass, String name, Type... argumentTypes) {
        register(plugin, false, false, declaringClass, name, argumentTypes);
    }

    public void register(InvocationPlugin plugin, String declaringClass, String name, Type... argumentTypes) {
        register(plugin, false, false, new OptionalLazySymbol(declaringClass), name, argumentTypes);
    }

    /**
     * Registers an invocation plugin for a given, optional method. There must be no plugin
     * currently registered for {@code method}.
     *
     * @param argumentTypes the argument types of the method. Element 0 of this array must be the
     *            {@link Class} value for {@link InvocationPlugin.Receiver} iff the method is
     *            non-static. Upon returning, element 0 will have been rewritten to
     *            {@code declaringClass}
     */
    public void registerOptional(InvocationPlugin plugin, Type declaringClass, String name, Type... argumentTypes) {
        register(plugin, true, false, declaringClass, name, argumentTypes);
    }

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        if (parent != null) {
            InvocationPlugin plugin = parent.lookupInvocation(method);
            if (plugin != null) {
                return plugin;
            }
        }
        return get(method);
    }

    /**
     * Gets the set of methods for which invocation plugins have been registered. Once this method
     * is called, no further registrations can be made.
     */
    public EconomicSet<ResolvedJavaMethod> getMethods() {
        EconomicSet<ResolvedJavaMethod> res = EconomicSet.create(Equivalence.DEFAULT);
        if (parent != null) {
            res.addAll(parent.getMethods());
        }
        flushDeferrables();
        for (ClassPlugins cp : registrations.getValues()) {
            for (ResolvedJavaMethodKey key : cp.entries.getKeys()) {
                res.add(key.method);
            }
        }
        return res;
    }

    /**
     * Gets the invocation plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched}
     * before searching in this object.
     */
    public InvocationPlugins getParent() {
        return parent;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        UnmodifiableMapCursor<String, ClassPlugins> entries = registrations.getEntries();
        while (entries.advance()) {
            buf.append(entries.getKey()).append('.').append(entries.getValue()).append(", ");
        }

        String s = buf.toString();
        if (buf.length() != 0) {
            s = s.substring(buf.length() - ", ".length());
        }
        return s + " / parent: " + this.parent;
    }

    private static class Checker {
        private static final int MAX_ARITY = 7;
        /**
         * The set of all {@link InvocationPlugin#apply} method signatures.
         */
        static final Class<?>[][] SIGS;

        static {
            ArrayList<Class<?>[]> sigs = new ArrayList<>(MAX_ARITY);
            for (Method method : InvocationPlugin.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getName().equals("apply")) {
                    Class<?>[] sig = method.getParameterTypes();
                    assert sig[0] == GraphBuilderContext.class;
                    assert sig[1] == ResolvedJavaMethod.class;
                    assert sig[2] == InvocationPlugin.Receiver.class;
                    assert Arrays.asList(sig).subList(3, sig.length).stream().allMatch(c -> c == ValueNode.class);
                    while (sigs.size() < sig.length - 2) {
                        sigs.add(null);
                    }
                    sigs.set(sig.length - 3, sig);
                }
            }
            assert sigs.indexOf(null) == -1 : format("need to add an apply() method to %s that takes %d %s arguments ", InvocationPlugin.class.getName(), sigs.indexOf(null),
                            ValueNode.class.getSimpleName());
            SIGS = sigs.toArray(new Class<?>[sigs.size()][]);
        }

        public static boolean check(InvocationPlugins plugins, Type declaringType, MethodKey method, InvocationPlugin plugin) {
            InvocationPlugins p = plugins.parent;
            while (p != null) {
                assert !p.containsKey(declaringType, method) : "a plugin is already registered for " + method;
                p = p.parent;
            }
            if (plugin instanceof ForeignCallPlugin || plugin instanceof GeneratedInvocationPlugin) {
                return true;
            }
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msplugin = (MethodSubstitutionPlugin) plugin;
                Method substitute = msplugin.getJavaSubstitute();
                assert substitute.getAnnotation(MethodSubstitution.class) != null : format("Substitute method must be annotated with @%s: %s", MethodSubstitution.class.getSimpleName(), substitute);
                return true;
            }
            int arguments = method.getDeclaredParameterCount();
            assert arguments < SIGS.length : format("need to extend %s to support method with %d arguments: %s", InvocationPlugin.class.getSimpleName(), arguments, method);
            for (Method m : plugin.getClass().getDeclaredMethods()) {
                if (m.getName().equals("apply")) {
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    if (Arrays.equals(SIGS[arguments], parameterTypes)) {
                        return true;
                    }
                }
            }
            throw new AssertionError(format("graph builder plugin for %s not found", method));
        }
    }

    /**
     * Checks a set of nodes added to the graph by an {@link InvocationPlugin}.
     *
     * @param b the graph builder that applied the plugin
     * @param plugin a plugin that was just applied
     * @param newNodes the nodes added to the graph by {@code plugin}
     * @throws AssertionError if any check fail
     */
    public void checkNewNodes(GraphBuilderContext b, InvocationPlugin plugin, NodeIterable<Node> newNodes) {
        if (parent != null) {
            parent.checkNewNodes(b, plugin, newNodes);
        }
    }

    /**
     * Resolves a name to a class.
     *
     * @param className the name of the class to resolve
     * @param optional if true, resolution failure returns null
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    public static Class<?> resolveClass(String className, boolean optional) {
        try {
            // Need to use the system class loader to handle classes
            // loaded by the application class loader which is not
            // delegated to by the JVMCI class loader.
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            if (optional) {
                return null;
            }
            throw new GraalError("Could not resolve type " + className);
        }
    }

    /**
     * Resolves a {@link Type} to a {@link Class}.
     *
     * @param type the type to resolve
     * @param optional if true, resolution failure returns null
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    public static Class<?> resolveType(Type type, boolean optional) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (optional && type instanceof OptionalLazySymbol) {
            return ((OptionalLazySymbol) type).resolve();
        }
        return resolveClass(type.getTypeName(), optional);
    }

    private static final Class<?>[] NO_CLASSES = {};

    /**
     * Resolves an array of {@link Type}s to an array of {@link Class}es.
     *
     * @param types the types to resolve
     * @param from the initial index of the range to be resolved, inclusive
     * @param to the final index of the range to be resolved, exclusive
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    public static Class<?>[] resolveTypes(Type[] types, int from, int to) {
        int length = to - from;
        if (length <= 0) {
            return NO_CLASSES;
        }
        Class<?>[] classes = new Class<?>[length];
        for (int i = 0; i < length; i++) {
            classes[i] = resolveType(types[i + from], false);
        }
        return classes;
    }
}
