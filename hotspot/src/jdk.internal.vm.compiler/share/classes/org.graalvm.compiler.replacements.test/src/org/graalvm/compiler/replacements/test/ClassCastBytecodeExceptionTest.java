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
package org.graalvm.compiler.replacements.test;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@RunWith(Parameterized.class)
public class ClassCastBytecodeExceptionTest extends BytecodeExceptionTest {

    private static class Exceptions {

        public static void throwClassCast(Object obj, Class<?> cls) {
            /*
             * We don't use cls.cast(obj) here because that gives a different exception message than
             * the checkcast bytecode.
             */
            if (cls == Double.class) {
                Double cast = (Double) obj;
                GraalDirectives.blackhole(cast);
            } else if (cls == byte[].class) {
                byte[] cast = (byte[]) obj;
                GraalDirectives.blackhole(cast);
            } else if (cls == String[].class) {
                String[] cast = (String[]) obj;
                GraalDirectives.blackhole(cast);
            } else if (cls == Object[][].class) {
                Object[][] cast = (Object[][]) obj;
                GraalDirectives.blackhole(cast);
            } else {
                Assert.fail("unexpected class argument");
            }
        }
    }

    @Override
    protected void registerPlugin(InvocationPlugins plugins) {
        plugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj, ValueNode classNode) {
                ResolvedJavaType type = b.getConstantReflection().asJavaType(classNode.asConstant());
                Constant hub = b.getConstantReflection().asObjectHub(type);
                Stamp hubStamp = b.getStampProvider().createHubStamp(StampFactory.object(TypeReference.createExactTrusted(type)));
                ConstantNode hubConst = b.add(ConstantNode.forConstant(hubStamp, hub, b.getMetaAccess()));
                return throwBytecodeException(b, ClassCastException.class, obj, hubConst);
            }
        }, Exceptions.class, "throwClassCast", Object.class, Class.class);
    }

    @Parameter(0) public Object object;
    @Parameter(1) public Class<?> cls;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Object[] objects = {"string", 42, new int[0], new Object[0], new double[0][]};

        ArrayList<Object[]> ret = new ArrayList<>(objects.length);
        for (Object o : objects) {
            ret.add(new Object[]{o, o.getClass()});
        }
        return ret;
    }

    public static void castToDouble(Object obj) {
        Exceptions.throwClassCast(obj, Double.class);
    }

    @Test
    public void testCastToDouble() {
        test("castToDouble", object);
    }

    public static void castToByteArray(Object obj) {
        Exceptions.throwClassCast(obj, byte[].class);
    }

    @Test
    public void testCastToByteArray() {
        test("castToByteArray", object);
    }

    public static void castToStringArray(Object obj) {
        Exceptions.throwClassCast(obj, String[].class);
    }

    @Test
    public void testCastToStringArray() {
        test("castToStringArray", object);
    }

    public static void castToArrayArray(Object obj) {
        Exceptions.throwClassCast(obj, Object[][].class);
    }

    @Test
    public void testCastToArrayArray() {
        test("castToArrayArray", object);
    }
}
