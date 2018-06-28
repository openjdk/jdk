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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.graalvm.compiler.processor.AbstractProcessor;

public class PluginGenerator {

    private final Map<Element, List<GeneratedPlugin>> plugins;

    public PluginGenerator() {
        this.plugins = new HashMap<>();
    }

    public void addPlugin(GeneratedPlugin plugin) {
        Element topLevel = getTopLevelClass(plugin.intrinsicMethod);
        List<GeneratedPlugin> list = plugins.get(topLevel);
        if (list == null) {
            list = new ArrayList<>();
            plugins.put(topLevel, list);
        }
        list.add(plugin);
    }

    public void generateAll(AbstractProcessor processor) {
        for (Entry<Element, List<GeneratedPlugin>> entry : plugins.entrySet()) {
            disambiguateNames(entry.getValue());
            createPluginFactory(processor, entry.getKey(), entry.getValue());
        }
    }

    private static Element getTopLevelClass(Element element) {
        Element prev = element;
        Element enclosing = element.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            prev = enclosing;
            enclosing = enclosing.getEnclosingElement();
        }
        return prev;
    }

    private static void disambiguateWith(List<GeneratedPlugin> plugins, Function<GeneratedPlugin, String> genName) {
        plugins.sort(Comparator.comparing(GeneratedPlugin::getPluginName));

        GeneratedPlugin current = plugins.get(0);
        String currentName = current.getPluginName();

        for (int i = 1; i < plugins.size(); i++) {
            GeneratedPlugin next = plugins.get(i);
            if (currentName.equals(next.getPluginName())) {
                if (current != null) {
                    current.setPluginName(genName.apply(current));
                    current = null;
                }
                next.setPluginName(genName.apply(next));
            } else {
                current = next;
                currentName = current.getPluginName();
            }
        }
    }

    private static void appendSimpleTypeName(StringBuilder ret, TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                DeclaredType declared = (DeclaredType) type;
                TypeElement element = (TypeElement) declared.asElement();
                ret.append(element.getSimpleName());
                break;
            case TYPEVAR:
                appendSimpleTypeName(ret, ((TypeVariable) type).getUpperBound());
                break;
            case WILDCARD:
                appendSimpleTypeName(ret, ((WildcardType) type).getExtendsBound());
                break;
            case ARRAY:
                appendSimpleTypeName(ret, ((ArrayType) type).getComponentType());
                ret.append("Array");
                break;
            default:
                ret.append(type);
        }
    }

    private static void disambiguateNames(List<GeneratedPlugin> plugins) {
        // if we have more than one method with the same name, disambiguate with the argument types
        disambiguateWith(plugins, plugin -> {
            StringBuilder ret = new StringBuilder(plugin.getPluginName());
            for (VariableElement param : plugin.intrinsicMethod.getParameters()) {
                ret.append('_');
                appendSimpleTypeName(ret, param.asType());
            }
            return ret.toString();
        });

        // since we're using simple names for argument types, we could still have a collision
        disambiguateWith(plugins, new Function<GeneratedPlugin, String>() {

            private int idx = 0;

            @Override
            public String apply(GeneratedPlugin plugin) {
                return plugin.getPluginName() + "_" + (idx++);
            }
        });
    }

    private static void createPluginFactory(AbstractProcessor processor, Element topLevelClass, List<GeneratedPlugin> plugins) {
        PackageElement pkg = (PackageElement) topLevelClass.getEnclosingElement();

        String genClassName = "PluginFactory_" + topLevelClass.getSimpleName();

        String qualifiedGenClassName = pkg.getQualifiedName() + "." + genClassName;
        try {
            JavaFileObject factory = processor.env().getFiler().createSourceFile(qualifiedGenClassName, topLevelClass);
            try (PrintWriter out = new PrintWriter(factory.openWriter())) {
                out.printf("// CheckStyle: stop header check\n");
                out.printf("// CheckStyle: stop line length check\n");
                out.printf("// GENERATED CONTENT - DO NOT EDIT\n");
                out.printf("// GENERATORS: %s, %s\n", ReplacementsAnnotationProcessor.class.getName(), PluginGenerator.class.getName());
                out.printf("package %s;\n", pkg.getQualifiedName());
                out.printf("\n");
                createImports(out, plugins);
                out.printf("\n");
                out.printf("public class %s implements NodeIntrinsicPluginFactory {\n", genClassName);
                for (GeneratedPlugin plugin : plugins) {
                    out.printf("\n");
                    plugin.generate(processor, out);
                }
                out.printf("\n");
                createPluginFactoryMethod(out, plugins);
                out.printf("}\n");
            }
        } catch (IOException e) {
            processor.env().getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
        processor.createProviderFile(qualifiedGenClassName, "org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory", topLevelClass);
    }

    protected static void createImports(PrintWriter out, List<GeneratedPlugin> plugins) {
        out.printf("import jdk.vm.ci.meta.ResolvedJavaMethod;\n");
        out.printf("\n");
        out.printf("import java.lang.annotation.Annotation;\n");
        out.printf("import org.graalvm.compiler.nodes.ValueNode;\n");
        out.printf("import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;\n");
        out.printf("import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;\n");
        out.printf("import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;\n");
        out.printf("import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;\n");
        out.printf("import org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;\n");

        HashSet<String> extra = new HashSet<>();
        for (GeneratedPlugin plugin : plugins) {
            plugin.extraImports(extra);
        }
        if (!extra.isEmpty()) {
            out.printf("\n");
            for (String i : extra) {
                out.printf("import %s;\n", i);
            }
        }
    }

    private static void createPluginFactoryMethod(PrintWriter out, List<GeneratedPlugin> plugins) {
        out.printf("    @Override\n");
        out.printf("    public void registerPlugins(InvocationPlugins plugins, InjectionProvider injection) {\n");
        for (GeneratedPlugin plugin : plugins) {
            plugin.register(out);
        }
        out.printf("    }\n");
    }
}
