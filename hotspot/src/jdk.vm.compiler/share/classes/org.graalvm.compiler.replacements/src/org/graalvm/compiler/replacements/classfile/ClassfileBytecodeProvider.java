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

import static org.graalvm.compiler.core.common.util.ModuleAPI.getModule;
import static org.graalvm.compiler.core.common.util.ModuleAPI.getResourceAsStream;
import static org.graalvm.compiler.core.common.util.Util.JAVA_SPECIFICATION_VERSION;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A {@link BytecodeProvider} that provides bytecode properties of a {@link ResolvedJavaMethod} as
 * parsed from a class file. This avoids all {@linkplain Instrumentation instrumentation} and any
 * bytecode rewriting performed by the VM.
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
    private final Map<Class<?>, Classfile> classfiles = new HashMap<>();
    private final Map<String, Class<?>> classes = new HashMap<>();
    final MetaAccessProvider metaAccess;
    final SnippetReflectionProvider snippetReflection;

    public ClassfileBytecodeProvider(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection) {
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
        ClassLoader cl = getClass().getClassLoader();
        this.loader = cl == null ? ClassLoader.getSystemClassLoader() : cl;
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

    private static InputStream getClassfileAsStream(Class<?> c) {
        String classfilePath = c.getName().replace('.', '/') + ".class";
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Object module = getModule.invoke(c);
            return getResourceAsStream.invoke(module, classfilePath);
        } else {
            ClassLoader cl = c.getClassLoader();
            if (cl == null) {
                return ClassLoader.getSystemResourceAsStream(classfilePath);
            }
            return cl.getResourceAsStream(classfilePath);
        }
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
                InputStream in = getClassfileAsStream(c);
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

    static ResolvedJavaMethod findMethod(ResolvedJavaType type, String name, String descriptor, boolean isStatic) {
        if (isStatic && name.equals("<clinit>")) {
            ResolvedJavaMethod method = type.getClassInitializer();
            if (method != null) {
                return method;
            }
        }
        ResolvedJavaMethod[] methodsToSearch = name.equals("<init>") ? type.getDeclaredConstructors() : type.getDeclaredMethods();
        for (ResolvedJavaMethod method : methodsToSearch) {
            if (method.isStatic() == isStatic && method.getName().equals(name) && method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    static ResolvedJavaField findField(ResolvedJavaType type, String name, String fieldType, boolean isStatic) {
        ResolvedJavaField[] fields = isStatic ? type.getStaticFields() : type.getInstanceFields(false);
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals(name) && field.getType().getName().equals(fieldType)) {
                return field;
            }
        }
        return null;
    }
}
