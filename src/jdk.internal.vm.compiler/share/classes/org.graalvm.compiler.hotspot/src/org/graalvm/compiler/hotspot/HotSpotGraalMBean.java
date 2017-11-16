/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;

import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.EconomicSet;
import org.graalvm.util.Equivalence;
import org.graalvm.util.UnmodifiableEconomicMap;

public final class HotSpotGraalMBean implements javax.management.DynamicMBean {
    private static Object mBeanServerField;
    private final HotSpotGraalCompiler compiler;
    private final OptionValues options;
    private final EconomicMap<OptionKey<?>, Object> changes;
    private final EconomicSet<Dump> methodDumps;
    private volatile EconomicSet<Reference<ClassLoader>> loaders;
    private javax.management.ObjectName registered;
    private OptionValues cachedOptions;

    private HotSpotGraalMBean(HotSpotGraalCompiler compiler, OptionValues options) {
        this.compiler = compiler;
        this.options = options;
        this.changes = EconomicMap.create();
        this.methodDumps = EconomicSet.create();
        EconomicSet<Reference<ClassLoader>> systemLoaderSet = EconomicSet.create(RefEquivalence.INSTANCE);
        systemLoaderSet.add(new WeakReference<>(ClassLoader.getSystemClassLoader()));
        this.loaders = systemLoaderSet;
    }

    private static boolean isMXServerOn() {
        if (mBeanServerField == null) {
            try {
                final Field field = java.lang.management.ManagementFactory.class.getDeclaredField("platformMBeanServer");
                field.setAccessible(true);
                mBeanServerField = field;
            } catch (Exception ex) {
                mBeanServerField = java.lang.management.ManagementFactory.class;
            }
        }
        if (mBeanServerField instanceof Field) {
            try {
                return ((Field) mBeanServerField).get(null) != null;
            } catch (Exception ex) {
                return true;
            }
        } else {
            return false;
        }
    }

    public static HotSpotGraalMBean create(HotSpotGraalCompiler compiler) {
        OptionValues options = HotSpotGraalOptionValues.HOTSPOT_OPTIONS;
        HotSpotGraalMBean mbean = new HotSpotGraalMBean(compiler, options);
        return mbean;
    }

    public javax.management.ObjectName ensureRegistered(boolean check) {
        for (int cnt = 0;; cnt++) {
            if (registered != null) {
                return registered;
            }
            if (check && !isMXServerOn()) {
                return null;
            }
            try {
                javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
                javax.management.ObjectName name = new javax.management.ObjectName("org.graalvm.compiler.hotspot:type=Options" + (cnt == 0 ? "" : cnt));
                mbs.registerMBean(this, name);
                registered = name;
                break;
            } catch (javax.management.MalformedObjectNameException | javax.management.MBeanRegistrationException | javax.management.NotCompliantMBeanException ex) {
                throw new IllegalStateException(ex);
            } catch (javax.management.InstanceAlreadyExistsException ex) {
                continue;
            }
        }
        return registered;
    }

    public OptionValues optionsFor(OptionValues initialValues, ResolvedJavaMethod forMethod) {
        ensureRegistered(true);
        if (forMethod instanceof HotSpotResolvedJavaMethod) {
            HotSpotResolvedObjectType type = ((HotSpotResolvedJavaMethod) forMethod).getDeclaringClass();
            if (type instanceof HotSpotResolvedJavaType) {
                Class<?> clazz = ((HotSpotResolvedJavaType) type).mirror();
                Reference<ClassLoader> addNewRef = new WeakReference<>(clazz.getClassLoader());
                if (!loaders.contains(addNewRef)) {
                    EconomicSet<Reference<ClassLoader>> newLoaders = EconomicSet.create(RefEquivalence.INSTANCE, loaders);
                    newLoaders.add(addNewRef);
                    this.loaders = newLoaders;
                }
            }
        }
        return currentMap(initialValues, forMethod);
    }

    private OptionValues currentMap(OptionValues initialValues, ResolvedJavaMethod method) {
        if (changes.isEmpty() && methodDumps.isEmpty()) {
            return initialValues;
        }
        OptionValues current = cachedOptions;
        if (current == null) {
            current = new OptionValues(initialValues, changes);
            cachedOptions = current;
        }
        if (method != null) {
            for (Dump request : methodDumps) {
                final String clazzName = method.getDeclaringClass().getName();
                if (method.getName().equals(request.method) && clazzName.equals(request.clazz)) {
                    current = new OptionValues(current, DebugOptions.Dump, request.filter,
                                    DebugOptions.PrintGraphHost, request.host,
                                    DebugOptions.PrintBinaryGraphPort, request.port);
                    break;
                }
            }
        }
        return current;
    }

    @Override
    public Object getAttribute(String attribute) {
        UnmodifiableEconomicMap<OptionKey<?>, Object> map = currentMap(options, null).getMap();
        for (OptionKey<?> k : map.getKeys()) {
            if (k.getName().equals(attribute)) {
                return map.get(k);
            }
        }
        return null;
    }

    @Override
    public void setAttribute(javax.management.Attribute attribute) throws javax.management.AttributeNotFoundException {
        javax.management.Attribute newAttr = setImpl(attribute);
        if (newAttr == null) {
            throw new javax.management.AttributeNotFoundException();
        }
    }

    private javax.management.Attribute setImpl(javax.management.Attribute attribute) {
        cachedOptions = null;
        for (OptionDescriptor option : allOptionDescriptors()) {
            if (option.getName().equals(attribute.getName())) {
                changes.put(option.getOptionKey(), attribute.getValue());
                return attribute;
            }
        }
        return null;
    }

    @Override
    public javax.management.AttributeList getAttributes(String[] names) {
        javax.management.AttributeList list = new javax.management.AttributeList();
        for (String name : names) {
            Object value = getAttribute(name);
            if (value != null) {
                list.add(new javax.management.Attribute(name, value));
            }
        }
        return list;
    }

    @Override
    public javax.management.AttributeList setAttributes(javax.management.AttributeList attributes) {
        javax.management.AttributeList setOk = new javax.management.AttributeList();
        for (javax.management.Attribute attr : attributes.asList()) {
            javax.management.Attribute newAttr = setImpl(attr);
            if (newAttr != null) {
                setOk.add(newAttr);
            }
        }
        return setOk;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws javax.management.MBeanException, javax.management.ReflectionException {
        if ("dumpMethod".equals(actionName)) {
            try {
                String className = param(params, 0, "className", String.class, null);
                String methodName = param(params, 1, "methodName", String.class, null);
                String filter = param(params, 2, "filter", String.class, ":3");
                String host = param(params, 3, "host", String.class, "localhost");
                Number port = param(params, 4, "port", Number.class, 4445);
                dumpMethod(className, methodName, filter, host, port.intValue());
            } catch (Exception ex) {
                throw new javax.management.ReflectionException(ex);
            }
        }
        return null;
    }

    private static <T> T param(Object[] arr, int index, String name, Class<T> type, T defaultValue) {
        Object value = arr.length > index ? arr[index] : null;
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            if (defaultValue == null) {
                throw new IllegalArgumentException(name + " must be specified");
            }
            value = defaultValue;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new IllegalArgumentException("Expecting " + type.getName() + " for " + name + " but was " + value);
    }

    public void dumpMethod(String className, String methodName, String filter, String host, int port) throws javax.management.MBeanException {
        String jvmName = MetaUtil.toInternalName(className);
        methodDumps.add(new Dump(host, port, jvmName, methodName, filter));

        ClassNotFoundException last = null;
        EconomicSet<Class<?>> found = EconomicSet.create();
        Iterator<Reference<ClassLoader>> it = loaders.iterator();
        while (it.hasNext()) {
            Reference<ClassLoader> ref = it.next();
            ClassLoader loader = ref.get();
            if (loader == null) {
                it.remove();
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className, false, loader);
                if (found.add(clazz)) {
                    ResolvedJavaType type = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(clazz);
                    if (compiler != null) {
                        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
                            if (methodName.equals(method.getName()) && method instanceof HotSpotResolvedJavaMethod) {
                                HotSpotResolvedJavaMethod hotSpotMethod = (HotSpotResolvedJavaMethod) method;
                                compiler.compileMethod(new HotSpotCompilationRequest(hotSpotMethod, -1, 0L), false);
                            }
                        }
                    }
                }
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        if (found.isEmpty()) {
            throw new javax.management.MBeanException(last, "Cannot find class " + className + " to schedule recompilation");
        }
    }

    @Override
    public javax.management.MBeanInfo getMBeanInfo() {
        List<javax.management.MBeanAttributeInfo> attrs = new ArrayList<>();
        for (OptionDescriptor descr : allOptionDescriptors()) {
            attrs.add(new javax.management.MBeanAttributeInfo(descr.getName(), descr.getType().getName(), descr.getHelp(), true, true, false));
        }
        javax.management.MBeanOperationInfo[] ops = {
                        new javax.management.MBeanOperationInfo("dumpMethod", "Enable IGV dumps for provided method", new javax.management.MBeanParameterInfo[]{
                                        new javax.management.MBeanParameterInfo("className", "java.lang.String", "Class to observe"),
                                        new javax.management.MBeanParameterInfo("methodName", "java.lang.String", "Method to observe"),
                        }, "void", javax.management.MBeanOperationInfo.ACTION),
                        new javax.management.MBeanOperationInfo("dumpMethod", "Enable IGV dumps for provided method", new javax.management.MBeanParameterInfo[]{
                                        new javax.management.MBeanParameterInfo("className", "java.lang.String", "Class to observe"),
                                        new javax.management.MBeanParameterInfo("methodName", "java.lang.String", "Method to observe"),
                                        new javax.management.MBeanParameterInfo("filter", "java.lang.String", "The parameter for Dump option"),
                        }, "void", javax.management.MBeanOperationInfo.ACTION),
                        new javax.management.MBeanOperationInfo("dumpMethod", "Enable IGV dumps for provided method", new javax.management.MBeanParameterInfo[]{
                                        new javax.management.MBeanParameterInfo("className", "java.lang.String", "Class to observe"),
                                        new javax.management.MBeanParameterInfo("methodName", "java.lang.String", "Method to observe"),
                                        new javax.management.MBeanParameterInfo("filter", "java.lang.String", "The parameter for Dump option"),
                                        new javax.management.MBeanParameterInfo("host", "java.lang.String", "The host where the IGV tool is running at"),
                                        new javax.management.MBeanParameterInfo("port", "int", "The port where the IGV tool is listening at"),
                        }, "void", javax.management.MBeanOperationInfo.ACTION)
        };

        return new javax.management.MBeanInfo(
                        HotSpotGraalMBean.class.getName(),
                        "Graal",
                        attrs.toArray(new javax.management.MBeanAttributeInfo[attrs.size()]),
                        null, ops, null);
    }

    private static Iterable<OptionDescriptor> allOptionDescriptors() {
        List<OptionDescriptor> arr = new ArrayList<>();
        for (OptionDescriptors set : OptionsParser.getOptionsLoader()) {
            for (OptionDescriptor descr : set) {
                arr.add(descr);
            }
        }
        return arr;
    }

    private static final class Dump {
        final String host;
        final int port;
        final String clazz;
        final String method;
        final String filter;

        Dump(String host, int port, String clazz, String method, String filter) {
            this.host = host;
            this.port = port;
            this.clazz = clazz;
            this.method = method;
            this.filter = filter;
        }
    }

    private static final class RefEquivalence extends Equivalence {
        static final Equivalence INSTANCE = new RefEquivalence();

        private RefEquivalence() {
        }

        @Override
        public boolean equals(Object a, Object b) {
            Reference<?> refA = (Reference<?>) a;
            Reference<?> refB = (Reference<?>) b;
            return Objects.equals(refA.get(), refB.get());
        }

        @Override
        public int hashCode(Object o) {
            Reference<?> ref = (Reference<?>) o;
            Object obj = ref.get();
            return obj == null ? 0 : obj.hashCode();
        }

    }
}
