/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test.tutorial;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.core.test.tutorial.StaticAnalysis.MethodState;
import org.graalvm.compiler.core.test.tutorial.StaticAnalysis.TypeFlow;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;

public class StaticAnalysisTests {

    static class A {
        Object foo(Object arg) {
            return arg;
        }
    }

    static class B extends A {
        @Override
        Object foo(Object arg) {
            if (arg instanceof Data) {
                return ((Data) arg).f;
            } else {
                return super.foo(arg);
            }
        }
    }

    static class Data {
        Object f;
    }

    private final MetaAccessProvider metaAccess;
    private final StampProvider stampProvider;

    public StaticAnalysisTests() {
        Backend backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        Providers providers = backend.getProviders();
        this.metaAccess = providers.getMetaAccess();
        this.stampProvider = providers.getStampProvider();
    }

    static void test01Entry() {
        A a = new A();
        a.foo(null);
    }

    @Test
    public void test01() {
        StaticAnalysis sa = new StaticAnalysis(metaAccess, stampProvider);
        sa.addMethod(findMethod(StaticAnalysisTests.class, "test01Entry"));
        sa.finish();

        assertEquals(sa.getResults().getAllInstantiatedTypes(), t(A.class));
        assertEquals(f(sa, Data.class, "f"));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[0], t(A.class));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[1]);
        assertEquals(m(sa, A.class, "foo").getFormalReturn());
    }

    static void test02Entry() {
        A a = new A();
        a.foo(new Data());

        B b = new B();
        b.foo(null);
    }

    @Test
    public void test02() {
        StaticAnalysis sa = new StaticAnalysis(metaAccess, stampProvider);
        sa.addMethod(findMethod(StaticAnalysisTests.class, "test02Entry"));
        sa.finish();

        assertEquals(sa.getResults().getAllInstantiatedTypes(), t(A.class), t(B.class), t(Data.class));
        assertEquals(f(sa, Data.class, "f"));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[0], t(A.class), t(B.class));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[1], t(Data.class));
        assertEquals(m(sa, A.class, "foo").getFormalReturn(), t(Data.class));
        assertEquals(m(sa, B.class, "foo").getFormalParameters()[0], t(B.class));
        assertEquals(m(sa, B.class, "foo").getFormalParameters()[1]);
        assertEquals(m(sa, B.class, "foo").getFormalReturn(), t(Data.class));
    }

    @SuppressWarnings({"deprecation", "unused"})
    static void test03Entry() {
        Data data = new Data();
        data.f = new Integer(42);

        A a = new A();
        a.foo(new Data());

        B b = new B();
        b.foo(null);
    }

    @Test
    public void test03() {
        StaticAnalysis sa = new StaticAnalysis(metaAccess, stampProvider);
        sa.addMethod(findMethod(StaticAnalysisTests.class, "test03Entry"));
        sa.finish();

        assertEquals(sa.getResults().getAllInstantiatedTypes(), t(A.class), t(B.class), t(Data.class), t(Integer.class));
        assertEquals(f(sa, Data.class, "f"), t(Integer.class));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[0], t(A.class), t(B.class));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[1], t(Data.class));
        assertEquals(m(sa, A.class, "foo").getFormalReturn(), t(Data.class));
        assertEquals(m(sa, B.class, "foo").getFormalParameters()[0], t(B.class));
        assertEquals(m(sa, B.class, "foo").getFormalParameters()[1]);
        assertEquals(m(sa, B.class, "foo").getFormalReturn(), t(Data.class), t(Integer.class));
    }

    @SuppressWarnings({"deprecation", "unused"})
    static void test04Entry() {
        Data data = null;
        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                data = new Data();
            } else if (i == 1) {
                data.f = new Integer(42);
            }
        }

        A a = new A();
        a.foo(data);
    }

    @Test
    public void test04() {
        StaticAnalysis sa = new StaticAnalysis(metaAccess, stampProvider);
        sa.addMethod(findMethod(StaticAnalysisTests.class, "test04Entry"));
        sa.finish();

        assertEquals(sa.getResults().getAllInstantiatedTypes(), t(A.class), t(Data.class), t(Integer.class));
        assertEquals(f(sa, Data.class, "f"), t(Integer.class));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[0], t(A.class));
        assertEquals(m(sa, A.class, "foo").getFormalParameters()[1], t(Data.class));
        assertEquals(m(sa, A.class, "foo").getFormalReturn(), t(Data.class));
    }

    private MethodState m(StaticAnalysis sa, Class<?> declaringClass, String name) {
        return sa.getResults().lookupMethod(findMethod(declaringClass, name));
    }

    private TypeFlow f(StaticAnalysis sa, Class<?> declaringClass, String name) {
        return sa.getResults().lookupField(findField(declaringClass, name));
    }

    private static void assertEquals(TypeFlow actual, Object... expected) {
        Collection<?> actualTypes = actual.getTypes();
        if (actualTypes.size() != expected.length || !actualTypes.containsAll(Arrays.asList(expected))) {
            Assert.fail(actualTypes + " != " + Arrays.asList(expected));
        }
    }

    private ResolvedJavaType t(Class<?> clazz) {
        return metaAccess.lookupJavaType(clazz);
    }

    private ResolvedJavaMethod findMethod(Class<?> declaringClass, String name) {
        Method reflectionMethod = null;
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                assert reflectionMethod == null : "More than one method with name " + name + " in class " + declaringClass.getName();
                reflectionMethod = m;
            }
        }
        assert reflectionMethod != null : "No method with name " + name + " in class " + declaringClass.getName();
        return metaAccess.lookupJavaMethod(reflectionMethod);
    }

    private ResolvedJavaField findField(Class<?> declaringClass, String name) {
        Field reflectionField;
        try {
            reflectionField = declaringClass.getDeclaredField(name);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw new AssertionError(ex);
        }
        return metaAccess.lookupJavaField(reflectionField);
    }
}
