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
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.replacements.verifier.InjectedDependencies.WellKnownDependency;

/**
 * Create graph builder plugins for {@link Fold} methods.
 */
public class GeneratedFoldPlugin extends GeneratedPlugin {

    public GeneratedFoldPlugin(ExecutableElement intrinsicMethod) {
        super(intrinsicMethod);
    }

    private static TypeMirror stringType(ProcessingEnvironment env) {
        return env.getElementUtils().getTypeElement("java.lang.String").asType();
    }

    @Override
    public void extraImports(Set<String> imports) {
        imports.add("jdk.vm.ci.meta.JavaConstant");
        imports.add("jdk.vm.ci.meta.JavaKind");
        imports.add("org.graalvm.compiler.nodes.ConstantNode");
    }

    @Override
    protected InjectedDependencies createExecute(ProcessingEnvironment env, PrintWriter out) {
        InjectedDependencies deps = new InjectedDependencies();
        List<? extends VariableElement> params = intrinsicMethod.getParameters();

        int argCount = 0;
        Object receiver;
        if (intrinsicMethod.getModifiers().contains(Modifier.STATIC)) {
            receiver = intrinsicMethod.getEnclosingElement();
        } else {
            receiver = "arg0";
            TypeElement type = (TypeElement) intrinsicMethod.getEnclosingElement();
            constantArgument(env, out, deps, argCount, type.asType(), argCount);
            argCount++;
        }

        int firstArg = argCount;
        for (VariableElement param : params) {
            if (param.getAnnotation(InjectedParameter.class) == null) {
                constantArgument(env, out, deps, argCount, param.asType(), argCount);
            } else {
                out.printf("            assert checkInjectedArgument(b, args[%d], targetMethod);\n", argCount);
                out.printf("            %s arg%d = %s;\n", param.asType(), argCount, deps.use(env, (DeclaredType) param.asType()));
            }
            argCount++;
        }

        Set<String> suppressWarnings = new TreeSet<>();
        if (intrinsicMethod.getAnnotation(Deprecated.class) != null) {
            suppressWarnings.add("deprecation");
        }
        if (hasRawtypeWarning(intrinsicMethod.getReturnType())) {
            suppressWarnings.add("rawtypes");
        }
        for (VariableElement param : params) {
            if (hasUncheckedWarning(param.asType())) {
                suppressWarnings.add("unchecked");
            }
        }
        if (suppressWarnings.size() > 0) {
            out.printf("            @SuppressWarnings({");
            String sep = "";
            for (String suppressWarning : suppressWarnings) {
                out.printf("%s\"%s\"", sep, suppressWarning);
                sep = ", ";
            }
            out.printf("})\n");
        }

        out.printf("            %s result = %s.%s(", getErasedType(intrinsicMethod.getReturnType()), receiver, intrinsicMethod.getSimpleName());
        if (argCount > firstArg) {
            out.printf("arg%d", firstArg);
            for (int i = firstArg + 1; i < argCount; i++) {
                out.printf(", arg%d", i);
            }
        }
        out.printf(");\n");

        TypeMirror returnType = intrinsicMethod.getReturnType();
        switch (returnType.getKind()) {
            case BOOLEAN:
                out.printf("            JavaConstant constant = JavaConstant.forInt(result ? 1 : 0);\n");
                break;
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
                out.printf("            JavaConstant constant = JavaConstant.forInt(result);\n");
                break;
            case LONG:
                out.printf("            JavaConstant constant = JavaConstant.forLong(result);\n");
                break;
            case FLOAT:
                out.printf("            JavaConstant constant = JavaConstant.forFloat(result);\n");
                break;
            case DOUBLE:
                out.printf("            JavaConstant constant = JavaConstant.forDouble(result);\n");
                break;
            case ARRAY:
            case TYPEVAR:
            case DECLARED:
                if (returnType.equals(stringType(env))) {
                    out.printf("            JavaConstant constant = %s.forString(result);\n", deps.use(WellKnownDependency.CONSTANT_REFLECTION));
                } else {
                    out.printf("            JavaConstant constant = %s.forObject(result);\n", deps.use(WellKnownDependency.SNIPPET_REFLECTION));
                }
                break;
            default:
                throw new IllegalArgumentException(returnType.toString());
        }

        out.printf("            ConstantNode node = ConstantNode.forConstant(constant, %s, %s);\n", deps.use(WellKnownDependency.META_ACCESS), deps.use(WellKnownDependency.STRUCTURED_GRAPH));
        out.printf("            b.push(JavaKind.%s, node);\n", getReturnKind(intrinsicMethod));
        out.printf("            return true;\n");

        return deps;
    }
}
