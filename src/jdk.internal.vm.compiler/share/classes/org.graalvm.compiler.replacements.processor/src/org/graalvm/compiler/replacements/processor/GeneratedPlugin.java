/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

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

    protected String pluginSuperclass() {
        return "GeneratedInvocationPlugin";
    }

    public void generate(AbstractProcessor processor, PrintWriter out) {
        out.printf("//        class: %s\n", intrinsicMethod.getEnclosingElement());
        out.printf("//       method: %s\n", intrinsicMethod);
        out.printf("// generated-by: %s\n", getClass().getName());
        out.printf("final class %s extends %s {\n", pluginName, pluginSuperclass());
        out.printf("\n");
        out.printf("    @Override\n");
        out.printf("    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args) {\n");
        out.printf("        if (!b.isPluginEnabled(this)) {\n");
        out.printf("            return false;\n");
        out.printf("        }\n");
        InjectedDependencies deps = new InjectedDependencies(true, intrinsicMethod);
        createExecute(processor, out, deps);
        out.printf("    }\n");
        out.printf("    @Override\n");
        out.printf("    public Class<? extends Annotation> getSource() {\n");
        out.printf("        return %s.class;\n", getAnnotationClass(processor).getQualifiedName().toString().replace('$', '.'));
        out.printf("    }\n");

        createPrivateMembers(processor, out, deps, pluginName);

        out.printf("}\n");

        createOtherClasses(processor, out);

    }

    protected void createOtherClasses(AbstractProcessor processor, PrintWriter out) {
        String name = getReplacementName();
        out.printf("final class %s implements PluginReplacementNode.ReplacementFunction {\n", name);
        out.printf("    static PluginReplacementNode.ReplacementFunction FUNCTION = new %s();\n", name);
        InjectedDependencies deps = new InjectedDependencies(false, intrinsicMethod);
        createHelpers(processor, out, deps);
        out.printf("}\n");
    }

    protected abstract void createHelpers(AbstractProcessor processor, PrintWriter out, InjectedDependencies deps);

    protected String getReplacementName() {
        return "PluginReplacementNode_" + getBaseName();
    }

    private String getBaseName() {
        assert getPluginName().startsWith("Plugin_");
        return getPluginName().substring("Plugin_".length());
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

    public abstract void extraImports(AbstractProcessor processor, Set<String> imports);

    protected abstract void createExecute(AbstractProcessor processor, PrintWriter out, InjectedDependencies deps);

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

    protected void createPrivateMembers(AbstractProcessor processor, PrintWriter out, InjectedDependencies deps, String constructorName) {
        if (!deps.isEmpty()) {
            out.printf("\n");
            for (InjectedDependencies.Dependency dep : deps) {
                out.printf("    private final %s %s;\n", dep.getType(), dep.getName(processor, intrinsicMethod));
            }

            out.printf("\n");
            out.printf("    %s(GeneratedPluginInjectionProvider injection) {\n", constructorName);
            for (InjectedDependencies.Dependency dep : deps) {
                out.printf("        this.%s = %s;\n", dep.getName(processor, intrinsicMethod), dep.getExpression(processor, intrinsicMethod));
            }
            out.printf("    }\n");

            needInjectionProvider = true;
        }
    }

    /**
     * @param processor
     * @return true if this plugin needs support for {@code PluginReplacementNode}
     */
    protected boolean needsReplacement(AbstractProcessor processor) {
        return true;
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

    protected String constantArgument(AbstractProcessor processor,
                    PrintWriter out,
                    InjectedDependencies deps,
                    int argIdx,
                    TypeMirror type,
                    int nodeIdx,
                    boolean isReplacement) {
        Function<Integer, String> argFormatter;
        if (isReplacement) {
            argFormatter = (i) -> String.format("args.get(%d)", i);
        } else {
            argFormatter = (i) -> String.format("args[%d]", i);
        }
        if (hasRawtypeWarning(type)) {
            out.printf("        @SuppressWarnings({\"rawtypes\"})\n");
        }
        String argName = "arg" + argIdx;
        out.printf("        %s %s;\n", getErasedType(type), argName);
        out.printf("        if (%s.isConstant()) {\n", argFormatter.apply(nodeIdx));
        if (type.equals(processor.getType("jdk.vm.ci.meta.ResolvedJavaType"))) {
            out.printf("            jdk.vm.ci.meta.JavaConstant cst = %s.asJavaConstant();\n", argFormatter.apply(nodeIdx));
            out.printf("            %s = %s.asJavaType(cst);\n", argName, deps.use(processor, WellKnownDependency.CONSTANT_REFLECTION));
            out.printf("            if (%s == null) {\n", argName);
            out.printf("                %s = %s.asObject(jdk.vm.ci.meta.ResolvedJavaType.class, cst);\n", argName, deps.use(processor, WellKnownDependency.SNIPPET_REFLECTION));
            out.printf("            }\n");
        } else {
            switch (type.getKind()) {
                case BOOLEAN:
                    out.printf("            %s = %s.asJavaConstant().asInt() != 0;\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case BYTE:
                    out.printf("            %s = (byte) %s.asJavaConstant().asInt();\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case CHAR:
                    out.printf("            %s = (char) %s.asJavaConstant().asInt();\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case SHORT:
                    out.printf("            %s = (short) %s.asJavaConstant().asInt();\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case INT:
                    out.printf("            %s = %s.asJavaConstant().asInt();\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case LONG:
                    out.printf("            %s = %s.asJavaConstant().asLong();\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case FLOAT:
                    out.printf("            %s = %s.asJavaConstant().asFloat();\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case DOUBLE:
                    out.printf("            %s = %s.asJavaConstant().asDouble();\n", argName, argFormatter.apply(nodeIdx));
                    break;
                case ARRAY:
                case DECLARED:
                    out.printf("            %s = %s.asObject(%s.class, %s.asJavaConstant());\n", argName, deps.use(processor, WellKnownDependency.SNIPPET_REFLECTION), getErasedType(type),
                                    argFormatter.apply(nodeIdx));
                    break;
                default:
                    throw new IllegalArgumentException(type.toString());
            }
        }
        out.printf("        } else {\n");
        if (!isReplacement) {
            out.printf("            if (b.shouldDeferPlugin(this)) {\n");
            out.printf("                b.replacePlugin(this, targetMethod, args, %s.FUNCTION);\n", getReplacementName());
            out.printf("                return true;\n");
            out.printf("            }\n");
            out.printf("            assert b.canDeferPlugin(this) : b.getClass().toString();\n");
        }
        out.printf("            return false;\n");

        out.printf("        }\n");
        return argName;
    }
}
