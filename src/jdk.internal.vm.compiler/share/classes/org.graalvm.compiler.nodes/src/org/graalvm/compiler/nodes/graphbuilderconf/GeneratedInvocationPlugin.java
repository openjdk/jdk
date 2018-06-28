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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Abstract class for a plugin generated for a method annotated by {@link NodeIntrinsic} or
 * {@link Fold}.
 */
public abstract class GeneratedInvocationPlugin implements InvocationPlugin {

    /**
     * Gets the class of the annotation for which this plugin was generated.
     */
    public abstract Class<? extends Annotation> getSource();

    @Override
    public abstract boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args);

    @Override
    public StackTraceElement getApplySourceLocation(MetaAccessProvider metaAccess) {
        Class<?> c = getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("execute")) {
                return metaAccess.lookupJavaMethod(m).asStackTraceElement(0);
            }
        }
        throw new GraalError("could not find method named \"execute\" in " + c.getName());
    }

    protected boolean checkInjectedArgument(GraphBuilderContext b, ValueNode arg, ResolvedJavaMethod foldAnnotatedMethod) {
        if (arg.isNullConstant()) {
            return true;
        }

        MetaAccessProvider metaAccess = b.getMetaAccess();
        ResolvedJavaMethod executeMethod = metaAccess.lookupJavaMethod(getExecuteMethod());
        ResolvedJavaType thisClass = metaAccess.lookupJavaType(getClass());
        ResolvedJavaMethod thisExecuteMethod = thisClass.resolveConcreteMethod(executeMethod, thisClass);
        if (b.getMethod().equals(thisExecuteMethod)) {
            // The "execute" method of this plugin is itself being compiled. In (only) this context,
            // the injected argument of the call to the @Fold annotated method will be non-null.
            return true;
        }
        throw new AssertionError("must pass null to injected argument of " + foldAnnotatedMethod.format("%H.%n(%p)") + ", not " + arg);
    }

    private static Method getExecuteMethod() {
        try {
            return GeneratedInvocationPlugin.class.getMethod("execute", GraphBuilderContext.class, ResolvedJavaMethod.class, InvocationPlugin.Receiver.class, ValueNode[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GraalError(e);
        }
    }
}
