/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package jdk.internal.org.objectweb.asm.tree.analysis;

import java.util.List;

import jdk.internal.org.objectweb.asm.Type;

/**
 * An extended {@link BasicVerifier} that performs more precise verifications.
 * This verifier computes exact class types, instead of using a single "object
 * reference" type (as done in the {@link BasicVerifier}).
 *
 * @author Eric Bruneton
 * @author Bing Ran
 */
public class SimpleVerifier extends BasicVerifier {

    /**
     * The class that is verified.
     */
    private final Type currentClass;

    /**
     * The super class of the class that is verified.
     */
    private final Type currentSuperClass;

    /**
     * The interfaces implemented by the class that is verified.
     */
    private final List<Type> currentClassInterfaces;

    /**
     * If the class that is verified is an interface.
     */
    private final boolean isInterface;

    /**
     * The loader to use for referenced classes.
     */
    private ClassLoader loader = getClass().getClassLoader();

    /**
     * Constructs a new {@link SimpleVerifier}.
     */
    public SimpleVerifier() {
        this(null, null, false);
    }

    /**
     * Constructs a new {@link SimpleVerifier} to verify a specific class. This
     * class will not be loaded into the JVM since it may be incorrect.
     *
     * @param currentClass
     *            the class that is verified.
     * @param currentSuperClass
     *            the super class of the class that is verified.
     * @param isInterface
     *            if the class that is verified is an interface.
     */
    public SimpleVerifier(final Type currentClass,
            final Type currentSuperClass, final boolean isInterface) {
        this(currentClass, currentSuperClass, null, isInterface);
    }

    /**
     * Constructs a new {@link SimpleVerifier} to verify a specific class. This
     * class will not be loaded into the JVM since it may be incorrect.
     *
     * @param currentClass
     *            the class that is verified.
     * @param currentSuperClass
     *            the super class of the class that is verified.
     * @param currentClassInterfaces
     *            the interfaces implemented by the class that is verified.
     * @param isInterface
     *            if the class that is verified is an interface.
     */
    public SimpleVerifier(final Type currentClass,
            final Type currentSuperClass,
            final List<Type> currentClassInterfaces, final boolean isInterface) {
        this(ASM6, currentClass, currentSuperClass, currentClassInterfaces,
                isInterface);
    }

    protected SimpleVerifier(final int api, final Type currentClass,
            final Type currentSuperClass,
            final List<Type> currentClassInterfaces, final boolean isInterface) {
        super(api);
        this.currentClass = currentClass;
        this.currentSuperClass = currentSuperClass;
        this.currentClassInterfaces = currentClassInterfaces;
        this.isInterface = isInterface;
    }

    /**
     * Set the <code>ClassLoader</code> which will be used to load referenced
     * classes. This is useful if you are verifying multiple interdependent
     * classes.
     *
     * @param loader
     *            a <code>ClassLoader</code> to use
     */
    public void setClassLoader(final ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public BasicValue newValue(final Type type) {
        if (type == null) {
            return BasicValue.UNINITIALIZED_VALUE;
        }

        boolean isArray = type.getSort() == Type.ARRAY;
        if (isArray) {
            switch (type.getElementType().getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
                return new BasicValue(type);
            }
        }

        BasicValue v = super.newValue(type);
        if (BasicValue.REFERENCE_VALUE.equals(v)) {
            if (isArray) {
                v = newValue(type.getElementType());
                String desc = v.getType().getDescriptor();
                for (int i = 0; i < type.getDimensions(); ++i) {
                    desc = '[' + desc;
                }
                v = new BasicValue(Type.getType(desc));
            } else {
                v = new BasicValue(type);
            }
        }
        return v;
    }

    @Override
    protected boolean isArrayValue(final BasicValue value) {
        Type t = value.getType();
        return t != null
                && ("Lnull;".equals(t.getDescriptor()) || t.getSort() == Type.ARRAY);
    }

    @Override
    protected BasicValue getElementValue(final BasicValue objectArrayValue)
            throws AnalyzerException {
        Type arrayType = objectArrayValue.getType();
        if (arrayType != null) {
            if (arrayType.getSort() == Type.ARRAY) {
                return newValue(Type.getType(arrayType.getDescriptor()
                        .substring(1)));
            } else if ("Lnull;".equals(arrayType.getDescriptor())) {
                return objectArrayValue;
            }
        }
        throw new Error("Internal error");
    }

    @Override
    protected boolean isSubTypeOf(final BasicValue value,
            final BasicValue expected) {
        Type expectedType = expected.getType();
        Type type = value.getType();
        switch (expectedType.getSort()) {
        case Type.INT:
        case Type.FLOAT:
        case Type.LONG:
        case Type.DOUBLE:
            return type.equals(expectedType);
        case Type.ARRAY:
        case Type.OBJECT:
            if ("Lnull;".equals(type.getDescriptor())) {
                return true;
            } else if (type.getSort() == Type.OBJECT
                    || type.getSort() == Type.ARRAY) {
                return isAssignableFrom(expectedType, type);
            } else {
                return false;
            }
        default:
            throw new Error("Internal error");
        }
    }

    @Override
    public BasicValue merge(final BasicValue v, final BasicValue w) {
        if (!v.equals(w)) {
            Type t = v.getType();
            Type u = w.getType();
            if (t != null
                    && (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY)) {
                if (u != null
                        && (u.getSort() == Type.OBJECT || u.getSort() == Type.ARRAY)) {
                    if ("Lnull;".equals(t.getDescriptor())) {
                        return w;
                    }
                    if ("Lnull;".equals(u.getDescriptor())) {
                        return v;
                    }
                    if (isAssignableFrom(t, u)) {
                        return v;
                    }
                    if (isAssignableFrom(u, t)) {
                        return w;
                    }
                    // TODO case of array classes of the same dimension
                    // TODO should we look also for a common super interface?
                    // problem: there may be several possible common super
                    // interfaces
                    do {
                        if (t == null || isInterface(t)) {
                            return BasicValue.REFERENCE_VALUE;
                        }
                        t = getSuperClass(t);
                        if (isAssignableFrom(t, u)) {
                            return newValue(t);
                        }
                    } while (true);
                }
            }
            return BasicValue.UNINITIALIZED_VALUE;
        }
        return v;
    }

    protected boolean isInterface(final Type t) {
        if (currentClass != null && t.equals(currentClass)) {
            return isInterface;
        }
        return getClass(t).isInterface();
    }

    protected Type getSuperClass(final Type t) {
        if (currentClass != null && t.equals(currentClass)) {
            return currentSuperClass;
        }
        Class<?> c = getClass(t).getSuperclass();
        return c == null ? null : Type.getType(c);
    }

    protected boolean isAssignableFrom(final Type t, final Type u) {
        if (t.equals(u)) {
            return true;
        }
        if (currentClass != null && t.equals(currentClass)) {
            if (getSuperClass(u) == null) {
                return false;
            } else {
                if (isInterface) {
                    return u.getSort() == Type.OBJECT
                            || u.getSort() == Type.ARRAY;
                }
                return isAssignableFrom(t, getSuperClass(u));
            }
        }
        if (currentClass != null && u.equals(currentClass)) {
            if (isAssignableFrom(t, currentSuperClass)) {
                return true;
            }
            if (currentClassInterfaces != null) {
                for (int i = 0; i < currentClassInterfaces.size(); ++i) {
                    Type v = currentClassInterfaces.get(i);
                    if (isAssignableFrom(t, v)) {
                        return true;
                    }
                }
            }
            return false;
        }
        Class<?> tc = getClass(t);
        if (tc.isInterface()) {
            tc = Object.class;
        }
        return tc.isAssignableFrom(getClass(u));
    }

    protected Class<?> getClass(final Type t) {
        try {
            if (t.getSort() == Type.ARRAY) {
                return Class.forName(t.getDescriptor().replace('/', '.'),
                        false, loader);
            }
            return Class.forName(t.getClassName(), false, loader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e.toString());
        }
    }
}
