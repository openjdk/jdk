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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class ArrayStoreBytecodeExceptionTest extends BytecodeExceptionTest {

    private static class Exceptions {

        private static Object[] array = new Exceptions[1];

        public static void throwArrayStore(Object obj) {
            array[0] = obj;
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj) {
                return throwBytecodeException(b, ArrayStoreException.class, obj);
            }
        }, Exceptions.class, "throwArrayStore", Object.class);
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static void arrayStoreSnippet(Object obj) {
        Exceptions.throwArrayStore(obj);
    }

    @Parameter(0) public Object object;
    @Parameter(1) public Class<?> cls;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Object[] objects = {"string", 42, new int[0], new String[0], new double[0][]};

        ArrayList<Object[]> ret = new ArrayList<>(objects.length);
        for (Object o : objects) {
            ret.add(new Object[]{o, o.getClass()});
        }
        return ret;
    }

    @Test
    public void testArrayStoreException() {
        test("arrayStoreSnippet", object);
    }
}
