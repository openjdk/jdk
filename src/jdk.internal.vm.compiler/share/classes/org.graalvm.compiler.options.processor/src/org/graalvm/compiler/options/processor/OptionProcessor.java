/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.options.processor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Processes static fields annotated with {@code Option}. An {@code OptionDescriptors}
 * implementation is generated for each top level class containing at least one such field. The name
 * of the generated class for top level class {@code com.foo.Bar} is
 * {@code com.foo.Bar_OptionDescriptors}.
 */
@SupportedAnnotationTypes({"org.graalvm.compiler.options.Option"})
public class OptionProcessor extends AbstractProcessor {

    private static final String OPTION_CLASS_NAME = "org.graalvm.compiler.options.Option";
    private static final String OPTION_KEY_CLASS_NAME = "org.graalvm.compiler.options.OptionKey";
    private static final String OPTION_TYPE_CLASS_NAME = "org.graalvm.compiler.options.OptionType";
    private static final String OPTION_DESCRIPTOR_CLASS_NAME = "org.graalvm.compiler.options.OptionDescriptor";
    private static final String OPTION_DESCRIPTORS_CLASS_NAME = "org.graalvm.compiler.options.OptionDescriptors";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private final Set<Element> processed = new HashSet<>();

    private TypeMirror optionTypeMirror;
    private TypeMirror optionKeyTypeMirror;

    private void processElement(Element element, OptionsInfo info) {

        if (!element.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be static", element);
            return;
        }
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field cannot be private", element);
            return;
        }

        AnnotationMirror annotation = getAnnotation(element, optionTypeMirror);
        assert annotation != null;
        assert element instanceof VariableElement;
        assert element.getKind() == ElementKind.FIELD;
        VariableElement field = (VariableElement) element;
        String fieldName = field.getSimpleName().toString();

        Types types = processingEnv.getTypeUtils();

        TypeMirror fieldType = field.asType();
        if (fieldType.getKind() != TypeKind.DECLARED) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be of type " + OPTION_KEY_CLASS_NAME, element);
            return;
        }
        DeclaredType declaredFieldType = (DeclaredType) fieldType;

        if (!types.isSubtype(fieldType, types.erasure(optionKeyTypeMirror))) {
            String msg = String.format("Option field type %s is not a subclass of %s", fieldType, optionKeyTypeMirror);
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
            return;
        }

        if (!field.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be static", element);
            return;
        }
        if (field.getModifiers().contains(Modifier.PRIVATE)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field cannot be private", element);
            return;
        }

        String optionName = getAnnotationValue(annotation, "name", String.class);
        if (optionName.equals("")) {
            optionName = fieldName;
        }

        if (!Character.isUpperCase(optionName.charAt(0))) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option name must start with an upper case letter", element);
            return;
        }

        DeclaredType declaredOptionKeyType = declaredFieldType;
        while (!types.isSameType(types.erasure(declaredOptionKeyType), types.erasure(optionKeyTypeMirror))) {
            List<? extends TypeMirror> directSupertypes = types.directSupertypes(declaredFieldType);
            assert !directSupertypes.isEmpty();
            declaredOptionKeyType = (DeclaredType) directSupertypes.get(0);
        }

        assert !declaredOptionKeyType.getTypeArguments().isEmpty();
        String optionType = declaredOptionKeyType.getTypeArguments().get(0).toString();
        if (optionType.startsWith("java.lang.")) {
            optionType = optionType.substring("java.lang.".length());
        }

        Element enclosing = element.getEnclosingElement();
        String declaringClass = "";
        String separator = "";
        Set<Element> originatingElementsList = info.originatingElements;
        originatingElementsList.add(field);
        PackageElement enclosingPackage = null;
        while (enclosing != null) {
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                    String msg = String.format("Option field cannot be declared in a private %s %s", enclosing.getKind().name().toLowerCase(), enclosing);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
                    return;
                }
                originatingElementsList.add(enclosing);
                declaringClass = enclosing.getSimpleName() + separator + declaringClass;
                separator = ".";
            } else if (enclosing.getKind() == ElementKind.PACKAGE) {
                enclosingPackage = (PackageElement) enclosing;
            }
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosingPackage == null) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field cannot be declared in the unnamed package", element);
            return;
        }
        List<String> helpValue = getAnnotationValueList(annotation, "help", String.class);
        String help = "";
        List<String> extraHelp = new ArrayList<>();

        if (helpValue.size() == 1) {
            help = helpValue.get(0);
            if (help.startsWith("file:")) {
                String path = help.substring("file:".length());
                Filer filer = processingEnv.getFiler();
                try {
                    FileObject file;
                    try {
                        file = filer.getResource(StandardLocation.SOURCE_PATH, enclosingPackage.getQualifiedName(), path);
                    } catch (IllegalArgumentException | IOException e) {
                        // Handle the case when a compiler doesn't support the SOURCE_PATH location
                        file = filer.getResource(StandardLocation.CLASS_OUTPUT, enclosingPackage.getQualifiedName(), path);
                    }
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(file.openInputStream()))) {
                        help = br.readLine();
                        if (help == null) {
                            help = "";
                        }
                        String line = br.readLine();
                        while (line != null) {
                            extraHelp.add(line);
                            line = br.readLine();
                        }
                    }
                } catch (IOException e) {
                    String msg = String.format("Error reading %s containing the help text for option field: %s", path, e);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
                    return;
                }
            }
        } else if (helpValue.size() > 1) {
            help = helpValue.get(0);
            extraHelp = helpValue.subList(1, helpValue.size());
        }
        if (help.length() != 0) {
            char firstChar = help.charAt(0);
            if (!Character.isUpperCase(firstChar)) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Option help text must start with an upper case letter", element);
                return;
            }
        }

        String optionTypeName = getAnnotationValue(annotation, "type", VariableElement.class).getSimpleName().toString();
        info.options.add(new OptionInfo(optionName, optionTypeName, help, extraHelp, optionType, declaringClass, field));
    }

    private void createFiles(OptionsInfo info) {
        String pkg = ((PackageElement) info.topDeclaringType.getEnclosingElement()).getQualifiedName().toString();
        Name topDeclaringClass = info.topDeclaringType.getSimpleName();
        Element[] originatingElements = info.originatingElements.toArray(new Element[info.originatingElements.size()]);

        createOptionsDescriptorsFile(info, pkg, topDeclaringClass, originatingElements);
    }

    private void createOptionsDescriptorsFile(OptionsInfo info, String pkg, Name topDeclaringClass, Element[] originatingElements) {
        String optionsClassName = topDeclaringClass + "_" + getSimpleName(OPTION_DESCRIPTORS_CLASS_NAME);

        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(pkg, optionsClassName, filer, originatingElements)) {

            out.println("// CheckStyle: stop header check");
            out.println("// CheckStyle: stop line length check");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Source: " + topDeclaringClass + ".java");
            out.println("package " + pkg + ";");
            out.println("");
            out.println("import java.util.*;");
            out.println("import " + getPackageName(OPTION_DESCRIPTORS_CLASS_NAME) + ".*;");
            out.println("import " + OPTION_TYPE_CLASS_NAME + ";");
            out.println("");
            out.println("public class " + optionsClassName + " implements " + getSimpleName(OPTION_DESCRIPTORS_CLASS_NAME) + " {");

            String desc = getSimpleName(OPTION_DESCRIPTOR_CLASS_NAME);

            Collections.sort(info.options);

            out.println("    @Override");
            out.println("    public OptionDescriptor get(String value) {");
            out.println("        switch (value) {");
            out.println("        // CheckStyle: stop line length check");
            for (OptionInfo option : info.options) {
                String name = option.name;
                String optionField;
                if (option.field.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new InternalError();
                } else {
                    optionField = option.declaringClass + "." + option.field.getSimpleName();
                }
                out.println("        case \"" + name + "\": {");
                String optionType = option.optionType;
                String type = option.type;
                String help = option.help;
                List<String> extraHelp = option.extraHelp;
                String declaringClass = option.declaringClass;
                Name fieldName = option.field.getSimpleName();
                out.printf("            return " + desc + ".create(\n");
                out.printf("                /*name*/ \"%s\",\n", name);
                out.printf("                /*optionType*/ %s.%s,\n", getSimpleName(OPTION_TYPE_CLASS_NAME), optionType);
                out.printf("                /*optionValueType*/ %s.class,\n", type);
                out.printf("                /*help*/ \"%s\",\n", help);
                if (extraHelp.size() != 0) {
                    out.printf("                /*extraHelp*/ new String[] {\n");
                    for (String line : extraHelp) {
                        out.printf("                         \"%s\",\n", line.replace("\\", "\\\\").replace("\"", "\\\""));
                    }
                    out.printf("                              },\n");
                }
                out.printf("                /*declaringClass*/ %s.class,\n", declaringClass);
                out.printf("                /*fieldName*/ \"%s\",\n", fieldName);
                out.printf("                /*option*/ %s);\n", optionField);
                out.println("        }");
            }
            out.println("        // CheckStyle: resume line length check");
            out.println("        }");
            out.println("        return null;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public Iterator<" + desc + "> iterator() {");
            out.println("        return new Iterator<OptionDescriptor>() {");
            out.println("            int i = 0;");
            out.println("            @Override");
            out.println("            public boolean hasNext() {");
            out.println("                return i < " + info.options.size() + ";");
            out.println("            }");
            out.println("            @Override");
            out.println("            public OptionDescriptor next() {");
            out.println("                switch (i++) {");
            for (int i = 0; i < info.options.size(); i++) {
                OptionInfo option = info.options.get(i);
                out.println("                    case " + i + ": return get(\"" + option.name + "\");");
            }
            out.println("                }");
            out.println("                throw new NoSuchElementException();");
            out.println("            }");
            out.println("        };");
            out.println("    }");
            out.println("}");
        }
    }

    protected PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            // Ensure Unix line endings to comply with code style guide checked by Checkstyle
            JavaFileObject sourceFile = filer.createSourceFile(pkg + "." + relativeName, originatingElements);
            return new PrintWriter(sourceFile.openWriter()) {

                @Override
                public void println() {
                    print("\n");
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class OptionInfo implements Comparable<OptionInfo> {

        final String name;
        final String optionType;
        final String help;
        final List<String> extraHelp;
        final String type;
        final String declaringClass;
        final VariableElement field;

        OptionInfo(String name, String optionType, String help, List<String> extraHelp, String type, String declaringClass, VariableElement field) {
            this.name = name;
            this.optionType = optionType;
            this.help = help;
            this.extraHelp = extraHelp;
            this.type = type;
            this.declaringClass = declaringClass;
            this.field = field;
        }

        @Override
        public int compareTo(OptionInfo other) {
            return name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return declaringClass + "." + field;
        }
    }

    static class OptionsInfo {

        final Element topDeclaringType;
        final List<OptionInfo> options = new ArrayList<>();
        final Set<Element> originatingElements = new HashSet<>();

        OptionsInfo(Element topDeclaringType) {
            this.topDeclaringType = topDeclaringType;
        }
    }

    private static Element topDeclaringType(Element element) {
        Element enclosing = element.getEnclosingElement();
        if (enclosing == null || enclosing.getKind() == ElementKind.PACKAGE) {
            assert element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE;
            return element;
        }
        return topDeclaringType(enclosing);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        TypeElement optionTypeElement = getTypeElement(OPTION_CLASS_NAME);

        optionTypeMirror = optionTypeElement.asType();
        optionKeyTypeMirror = getTypeElement(OPTION_KEY_CLASS_NAME).asType();

        Map<Element, OptionsInfo> map = new HashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(optionTypeElement)) {
            if (!processed.contains(element)) {
                processed.add(element);
                Element topDeclaringType = topDeclaringType(element);
                OptionsInfo options = map.get(topDeclaringType);
                if (options == null) {
                    options = new OptionsInfo(topDeclaringType);
                    map.put(topDeclaringType, options);
                }
                if (!element.getEnclosingElement().getSimpleName().toString().endsWith("Options")) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Option declaring classes must have a name that ends with 'Options'", element.getEnclosingElement());
                }
                processElement(element, options);
            }
        }

        boolean ok = true;
        Map<String, OptionInfo> uniqueness = new HashMap<>();
        for (OptionsInfo info : map.values()) {
            for (OptionInfo option : info.options) {
                OptionInfo conflict = uniqueness.put(option.name, option);
                if (conflict != null) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Duplicate option names for " + option + " and " + conflict, option.field);
                    ok = false;
                }
            }
        }

        if (ok) {
            for (OptionsInfo info : map.values()) {
                createFiles(info);
            }
        }

        return true;
    }
}
