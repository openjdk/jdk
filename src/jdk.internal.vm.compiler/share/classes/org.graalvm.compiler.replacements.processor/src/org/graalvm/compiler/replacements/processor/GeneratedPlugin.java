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


package org.graalvm.compiler.replacements.processor;

import java.io.PrintWriter;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

import org.graalvm.compiler.processor.AbstractProcessor;
import org.graalvm.compiler.replacements.processor.InjectedDependencies.Dependency;
import org.graalvm.compiler.replacements.processor.InjectedDependencies.WellKnownDependency;

public abstract class GeneratedPlugin {

    protected final ExecutableElement intrinsicMethod;
    private boolean needInjectionProvider;

    private String pluginName;

    public GeneratedPlugin(ExecutableElement intrinsicMethod) {
        this.intrinsicMethod = intrinsicMethod;
        this.needInjectionProvider = false;
        // Lets keep generated class names short to mitigate hitting file name length limits.
        this.pluginName = "Plugin_" + intrinsicMethod.getEnclosingElement().getSimpleName() + "_" + intrinsicMethod.getSimpleName().toString();
    }

    protected abstract TypeElement getAnnotationClass(AbstractProcessor processor);

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public void generate(AbstractProcessor processor, PrintWriter out) {
        out.printf("//        class: %s\n", intrinsicMethod.getEnclosingElement());
        out.printf("//       method: %s\n", intrinsicMethod);
        out.printf("// generated-by: %s\n", getClass().getName());
        out.printf("final class %s extends GeneratedInvocationPlugin {\n", pluginName);
        out.printf("\n");
        out.printf("    @Override\n");
        out.printf("    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args) {\n");
        InjectedDependencies deps = createExecute(processor, out);
        out.printf("    }\n");
        out.printf("    @Override\n");
        out.printf("    public Class<? extends Annotation> getSource() {\n");
        out.printf("        return %s.class;\n", getAnnotationClass(processor).getQualifiedName().toString().replace('$', '.'));
        out.printf("    }\n");

        createPrivateMembers(processor, out, deps);

        out.printf("}\n");
    }

    public void register(PrintWriter out) {
        out.printf("        plugins.register(new %s(", pluginName);
        if (needInjectionProvider) {
            out.printf("injection");
        }
        out.printf("), %s.class, \"%s\"", intrinsicMethod.getEnclosingElement(), intrinsicMethod.getSimpleName());
        if (!intrinsicMethod.getModifiers().contains(Modifier.STATIC)) {
            out.printf(", InvocationPlugin.Receiver.class");
        }
        for (VariableElement arg : intrinsicMethod.getParameters()) {
            out.printf(", %s.class", getErasedType(arg.asType()));
        }
        out.printf(");\n");
    }

    public abstract void extraImports(Set<String> imports);

    protected abstract InjectedDependencies createExecute(AbstractProcessor processor, PrintWriter out);

    static String getErasedType(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                DeclaredType declared = (DeclaredType) type;
                TypeElement element = (TypeElement) declared.asElement();
                return element.getQualifiedName().toString();
            case TYPEVAR:
                return getErasedType(((TypeVariable) type).getUpperBound());
            case WILDCARD:
                return getErasedType(((WildcardType) type).getExtendsBound());
            case ARRAY:
                return getErasedType(((ArrayType) type).getComponentType()) + "[]";
            default:
                return type.toString();
        }
    }

    static boolean hasRawtypeWarning(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                DeclaredType declared = (DeclaredType) type;
                return declared.getTypeArguments().size() > 0;
            case TYPEVAR:
                return false;
            case WILDCARD:
                return false;
            case ARRAY:
                return hasRawtypeWarning(((ArrayType) type).getComponentType());
            default:
                return false;
        }
    }

    static boolean hasUncheckedWarning(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                DeclaredType declared = (DeclaredType) type;
                for (TypeMirror typeParam : declared.getTypeArguments()) {
                    if (hasUncheckedWarning(typeParam)) {
                        return true;
                    }
                }
                return false;
            case TYPEVAR:
                return true;
            case WILDCARD:
                return ((WildcardType) type).getExtendsBound() != null;
            case ARRAY:
                return hasUncheckedWarning(((ArrayType) type).getComponentType());
            default:
                return false;
        }
    }

    private void createPrivateMembers(AbstractProcessor processor, PrintWriter out, InjectedDependencies deps) {
        if (!deps.isEmpty()) {
            out.printf("\n");
            for (Dependency dep : deps) {
                out.printf("    private final %s %s;\n", dep.type, dep.name);
            }

            out.printf("\n");
            out.printf("    %s(NodeIntrinsicPluginFactory.InjectionProvider injection) {\n", pluginName);
            for (Dependency dep : deps) {
                out.printf("        this.%s = %s;\n", dep.name, dep.inject(processor, intrinsicMethod));
            }
            out.printf("    }\n");

            needInjectionProvider = true;
        }
    }

    protected static String getReturnKind(ExecutableElement method) {
        switch (method.getReturnType().getKind()) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
                return "Int";
            case LONG:
                return "Long";
            case FLOAT:
                return "Float";
            case DOUBLE:
                return "Double";
            case VOID:
                return "Void";
            case ARRAY:
            case TYPEVAR:
            case DECLARED:
                return "Object";
            default:
                throw new IllegalArgumentException(method.getReturnType().toString());
        }
    }

    protected static void constantArgument(AbstractProcessor processor, PrintWriter out, InjectedDependencies deps, int argIdx, TypeMirror type, int nodeIdx) {
        if (hasRawtypeWarning(type)) {
            out.printf("        @SuppressWarnings({\"rawtypes\"})\n");
        }
        out.printf("        %s arg%d;\n", getErasedType(type), argIdx);
        out.printf("        if (args[%d].isConstant()) {\n", nodeIdx);
        if (type.equals(processor.getType("jdk.vm.ci.meta.ResolvedJavaType"))) {
            out.printf("            arg%d = %s.asJavaType(args[%d].asConstant());\n", argIdx, deps.use(WellKnownDependency.CONSTANT_REFLECTION), nodeIdx);
        } else {
            switch (type.getKind()) {
                case BOOLEAN:
                    out.printf("            arg%d = args[%d].asJavaConstant().asInt() != 0;\n", argIdx, nodeIdx);
                    break;
                case BYTE:
                    out.printf("            arg%d = (byte) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case CHAR:
                    out.printf("            arg%d = (char) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case SHORT:
                    out.printf("            arg%d = (short) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case INT:
                    out.printf("            arg%d = args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case LONG:
                    out.printf("            arg%d = args[%d].asJavaConstant().asLong();\n", argIdx, nodeIdx);
                    break;
                case FLOAT:
                    out.printf("            arg%d = args[%d].asJavaConstant().asFloat();\n", argIdx, nodeIdx);
                    break;
                case DOUBLE:
                    out.printf("            arg%d = args[%d].asJavaConstant().asDouble();\n", argIdx, nodeIdx);
                    break;
                case ARRAY:
                case DECLARED:
                    out.printf("            arg%d = %s.asObject(%s.class, args[%d].asJavaConstant());\n", argIdx, deps.use(WellKnownDependency.SNIPPET_REFLECTION), getErasedType(type), nodeIdx);
                    break;
                default:
                    throw new IllegalArgumentException(type.toString());
            }
        }
        out.printf("        } else {\n");
        out.printf("            assert b.canDeferPlugin(this) : b.getClass().toString();\n");
        out.printf("            return false;\n");
        out.printf("        }\n");
    }
}
