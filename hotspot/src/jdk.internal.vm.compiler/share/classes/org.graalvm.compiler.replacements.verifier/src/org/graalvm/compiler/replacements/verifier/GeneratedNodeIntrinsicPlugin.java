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
package org.graalvm.compiler.replacements.verifier;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.InjectedNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;

/**
 * Create graph builder plugins for {@link NodeIntrinsic} methods.
 */
public abstract class GeneratedNodeIntrinsicPlugin extends GeneratedPlugin {

    private final TypeMirror[] signature;

    public GeneratedNodeIntrinsicPlugin(ExecutableElement intrinsicMethod, TypeMirror[] signature) {
        super(intrinsicMethod);
        this.signature = signature;
    }

    private static TypeMirror valueNodeType(ProcessingEnvironment env) {
        return env.getElementUtils().getTypeElement("org.graalvm.compiler.nodes.ValueNode").asType();
    }

    protected abstract List<? extends VariableElement> getParameters();

    protected abstract void factoryCall(ProcessingEnvironment env, PrintWriter out, InjectedDependencies deps, int argCount);

    @Override
    protected InjectedDependencies createExecute(ProcessingEnvironment env, PrintWriter out) {
        InjectedDependencies deps = new InjectedDependencies();

        List<? extends VariableElement> params = getParameters();

        int idx = 0;
        for (; idx < params.size(); idx++) {
            VariableElement param = params.get(idx);
            if (param.getAnnotation(InjectedNodeParameter.class) == null) {
                break;
            }

            out.printf("            %s arg%d = %s;\n", param.asType(), idx, deps.use(env, (DeclaredType) param.asType()));
        }

        for (int i = 0; i < signature.length; i++, idx++) {
            if (intrinsicMethod.getParameters().get(i).getAnnotation(ConstantNodeParameter.class) != null) {
                constantArgument(env, out, deps, idx, signature[i], i);
            } else {
                if (signature[i].equals(valueNodeType(env))) {
                    out.printf("            ValueNode arg%d = args[%d];\n", idx, i);
                } else {
                    out.printf("            %s arg%d = (%s) args[%d];\n", signature[i], idx, signature[i], i);
                }
            }
        }

        factoryCall(env, out, deps, idx);

        return deps;
    }

    public static class ConstructorPlugin extends GeneratedNodeIntrinsicPlugin {

        private final ExecutableElement constructor;

        public ConstructorPlugin(ExecutableElement intrinsicMethod, ExecutableElement constructor, TypeMirror[] signature) {
            super(intrinsicMethod, signature);
            this.constructor = constructor;
        }

        @Override
        public void extraImports(Set<String> imports) {
            if (intrinsicMethod.getReturnType().getKind() != TypeKind.VOID) {
                imports.add("jdk.vm.ci.meta.JavaKind");
            }
        }

        @Override
        protected List<? extends VariableElement> getParameters() {
            return constructor.getParameters();
        }

        @Override
        protected void factoryCall(ProcessingEnvironment env, PrintWriter out, InjectedDependencies deps, int argCount) {
            out.printf("            %s node = new %s(", constructor.getEnclosingElement(), constructor.getEnclosingElement());
            if (argCount > 0) {
                out.printf("arg0");
                for (int i = 1; i < argCount; i++) {
                    out.printf(", arg%d", i);
                }
            }
            out.printf(");\n");

            if (intrinsicMethod.getReturnType().getKind() == TypeKind.VOID) {
                out.printf("            b.add(node);\n");
            } else {
                out.printf("            b.addPush(JavaKind.%s, node);\n", getReturnKind(intrinsicMethod));
            }
            out.printf("            return true;\n");
        }
    }

    public static class CustomFactoryPlugin extends GeneratedNodeIntrinsicPlugin {

        private final ExecutableElement customFactory;

        public CustomFactoryPlugin(ExecutableElement intrinsicMethod, ExecutableElement customFactory, TypeMirror[] signature) {
            super(intrinsicMethod, signature);
            this.customFactory = customFactory;
        }

        @Override
        public void extraImports(Set<String> imports) {
        }

        @Override
        protected List<? extends VariableElement> getParameters() {
            List<? extends VariableElement> ret = customFactory.getParameters();
            // remove initial GraphBuilderContext and ResolvedJavaMethod parameters
            return ret.subList(2, ret.size());
        }

        @Override
        protected void factoryCall(ProcessingEnvironment env, PrintWriter out, InjectedDependencies deps, int argCount) {
            out.printf("            return %s.%s(b, targetMethod", customFactory.getEnclosingElement(), customFactory.getSimpleName());
            for (int i = 0; i < argCount; i++) {
                out.printf(", arg%d", i);
            }
            out.printf(");\n");
        }
    }
}
