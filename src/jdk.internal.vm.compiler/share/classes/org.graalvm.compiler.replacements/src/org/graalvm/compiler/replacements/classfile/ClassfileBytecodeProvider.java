/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.classfile;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A {@link BytecodeProvider} that provides bytecode properties of a {@link ResolvedJavaMethod} as
 * parsed from a class file. This avoids all {@linkplain java.lang.instrument.Instrumentation
 * instrumentation} and any bytecode rewriting performed by the VM.
 *
 * This mechanism retrieves class files based on the name and {@link ClassLoader} of existing
 * {@link Class} instances. It bypasses all VM parsing and verification of the class file and
 * assumes the class files are well formed. As such, it should only be used for classes from a
 * trusted source such as the boot class (or module) path.
 *
 * A combination of {@link Class#forName(String)} and an existing {@link MetaAccessProvider} is used
 * to resolve constant pool references. This opens up the opportunity for linkage errors if the
 * referee is structurally changed through redefinition (e.g., a referred to method is renamed or
 * deleted). This will result in an appropriate {@link LinkageError} being thrown. The only way to
 * avoid this is to have a completely isolated {@code jdk.vm.ci.meta} implementation for parsing
 * snippet/intrinsic bytecodes.
 */
public final class ClassfileBytecodeProvider implements BytecodeProvider {

    private final ClassLoader loader;
    private final EconomicMap<Class<?>, Classfile> classfiles = EconomicMap.create(Equivalence.IDENTITY);
    private final EconomicMap<String, Class<?>> classes = EconomicMap.create();
    private final EconomicMap<ResolvedJavaType, FieldsCache> fields = EconomicMap.create();
    private final EconomicMap<ResolvedJavaType, MethodsCache> methods = EconomicMap.create();
    final MetaAccessProvider metaAccess;
    final SnippetReflectionProvider snippetReflection;

    public ClassfileBytecodeProvider(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection) {
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
        ClassLoader cl = getClass().getClassLoader();
        this.loader = cl == null ? ClassLoader.getSystemClassLoader() : cl;
    }

    public ClassfileBytecodeProvider(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, ClassLoader loader) {
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
        this.loader = loader;
    }

    @Override
    public Bytecode getBytecode(ResolvedJavaMethod method) {
        Classfile classfile = getClassfile(resolveToClass(method.getDeclaringClass().getName()));
        return classfile.getCode(method.getName(), method.getSignature().toMethodDescriptor());
    }

    @Override
    public boolean supportsInvokedynamic() {
        return false;
    }

    @Override
    public boolean shouldRecordMethodDependencies() {
        return false;
    }

    /**
     * Gets a {@link Classfile} created by parsing the class file bytes for {@code c}.
     *
     * @throws NoClassDefFoundError if the class file cannot be found
     */
    private synchronized Classfile getClassfile(Class<?> c) {
        assert !c.isPrimitive() && !c.isArray() : c;
        Classfile classfile = classfiles.get(c);
        if (classfile == null) {
            try {
                ResolvedJavaType type = metaAccess.lookupJavaType(c);
                InputStream in = GraalServices.getClassfileAsStream(c);
                if (in != null) {
                    DataInputStream stream = new DataInputStream(in);
                    classfile = new Classfile(type, stream, this);
                    classfiles.put(c, classfile);
                    return classfile;
                }
                throw new NoClassDefFoundError(c.getName());
            } catch (IOException e) {
                throw (NoClassDefFoundError) new NoClassDefFoundError(c.getName()).initCause(e);
            }
        }
        return classfile;
    }

    synchronized Class<?> resolveToClass(String descriptor) {
        Class<?> c = classes.get(descriptor);
        if (c == null) {
            if (descriptor.length() == 1) {
                c = JavaKind.fromPrimitiveOrVoidTypeChar(descriptor.charAt(0)).toJavaClass();
            } else {
                int dimensions = 0;
                while (descriptor.charAt(dimensions) == '[') {
                    dimensions++;
                }
                String name;
                if (dimensions == 0 && descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                } else {
                    name = descriptor.replace('/', '.');
                }
                try {
                    c = Class.forName(name, true, loader);
                    classes.put(descriptor, c);
                } catch (ClassNotFoundException e) {
                    throw new NoClassDefFoundError(descriptor);
                }
            }
        }
        return c;
    }

    /**
     * Name and type of a field.
     */
    static final class FieldKey {
        final String name;
        final String type;

        FieldKey(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return name + ":" + type;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FieldKey) {
                FieldKey that = (FieldKey) obj;
                return that.name.equals(this.name) && that.type.equals(this.type);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ type.hashCode();
        }
    }

    /**
     * Name and descriptor of a method.
     */
    static final class MethodKey {
        final String name;
        final String descriptor;

        MethodKey(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return name + ":" + descriptor;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodKey) {
                MethodKey that = (MethodKey) obj;
                return that.name.equals(this.name) && that.descriptor.equals(this.descriptor);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ descriptor.hashCode();
        }
    }

    /**
     * Method cache for a {@link ResolvedJavaType}.
     */
    static final class MethodsCache {

        volatile EconomicMap<MethodKey, ResolvedJavaMethod> constructors;
        volatile EconomicMap<MethodKey, ResolvedJavaMethod> methods;

        ResolvedJavaMethod lookup(ResolvedJavaType type, String name, String descriptor) {
            MethodKey key = new MethodKey(name, descriptor);

            if (name.equals("<clinit>")) {
                // No need to cache <clinit> as it will be looked up at most once
                return type.getClassInitializer();
            }
            if (!name.equals("<init>")) {
                if (methods == null) {
                    // Racy initialization is safe since `methods` is volatile
                    methods = createMethodMap(type.getDeclaredMethods());
                }

                return methods.get(key);
            } else {
                if (constructors == null) {
                    // Racy initialization is safe since instanceFields is volatile
                    constructors = createMethodMap(type.getDeclaredConstructors());
                }
                return constructors.get(key);
            }
        }

        private static EconomicMap<MethodKey, ResolvedJavaMethod> createMethodMap(ResolvedJavaMethod[] methodArray) {
            EconomicMap<MethodKey, ResolvedJavaMethod> map = EconomicMap.create();
            for (ResolvedJavaMethod m : methodArray) {
                map.put(new MethodKey(m.getName(), m.getSignature().toMethodDescriptor()), m);
            }
            return map;
        }
    }

    /**
     * Field cache for a {@link ResolvedJavaType}.
     */
    static final class FieldsCache {

        volatile EconomicMap<FieldKey, ResolvedJavaField> instanceFields;
        volatile EconomicMap<FieldKey, ResolvedJavaField> staticFields;

        ResolvedJavaField lookup(ResolvedJavaType type, String name, String fieldType, boolean isStatic) {
            FieldKey key = new FieldKey(name, fieldType);
            if (isStatic) {
                if (staticFields == null) {
                    // Racy initialization is safe since staticFields is volatile
                    staticFields = createFieldMap(type.getStaticFields());
                }
                return staticFields.get(key);
            } else {
                if (instanceFields == null) {
                    // Racy initialization is safe since instanceFields is volatile
                    instanceFields = createFieldMap(type.getInstanceFields(false));
                }
                return instanceFields.get(key);
            }
        }

        private static EconomicMap<FieldKey, ResolvedJavaField> createFieldMap(ResolvedJavaField[] fieldArray) {
            EconomicMap<FieldKey, ResolvedJavaField> map = EconomicMap.create();
            for (ResolvedJavaField f : fieldArray) {
                map.put(new FieldKey(f.getName(), f.getType().getName()), f);
            }
            return map;
        }
    }

    /**
     * Gets the methods cache for {@code type}.
     *
     * Synchronized since the cache is lazily created.
     */
    private synchronized MethodsCache getMethods(ResolvedJavaType type) {
        MethodsCache methodsCache = methods.get(type);
        if (methodsCache == null) {
            methodsCache = new MethodsCache();
            methods.put(type, methodsCache);
        }
        return methodsCache;
    }

    /**
     * Gets the fields cache for {@code type}.
     *
     * Synchronized since the cache is lazily created.
     */
    private synchronized FieldsCache getFields(ResolvedJavaType type) {
        FieldsCache fieldsCache = fields.get(type);
        if (fieldsCache == null) {
            fieldsCache = new FieldsCache();
            fields.put(type, fieldsCache);
        }
        return fieldsCache;
    }

    ResolvedJavaField findField(ResolvedJavaType type, String name, String fieldType, boolean isStatic) {
        return getFields(type).lookup(type, name, fieldType, isStatic);
    }

    ResolvedJavaMethod findMethod(ResolvedJavaType type, String name, String descriptor, boolean isStatic) {
        ResolvedJavaMethod method = getMethods(type).lookup(type, name, descriptor);
        if (method != null && method.isStatic() == isStatic) {
            return method;
        }
        return null;
    }
}
