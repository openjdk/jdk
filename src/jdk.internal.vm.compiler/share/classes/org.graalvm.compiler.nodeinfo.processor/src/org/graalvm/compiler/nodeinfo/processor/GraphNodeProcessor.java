/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodeinfo.processor;

import static java.util.Collections.reverse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Processor for {@value #NODE_INFO_CLASS_NAME} annotation.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({"org.graalvm.compiler.nodeinfo.NodeInfo"})
public class GraphNodeProcessor extends AbstractProcessor {
    private static final String NODE_INFO_CLASS_NAME = "org.graalvm.compiler.nodeinfo.NodeInfo";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    /**
     * Node class currently being processed.
     */
    private Element scope;

    public static boolean isEnclosedIn(Element e, Element scopeElement) {
        List<Element> elementHierarchy = getElementHierarchy(e);
        return elementHierarchy.contains(scopeElement);
    }

    void errorMessage(Element element, String format, Object... args) {
        message(Kind.ERROR, element, format, args);
    }

    void message(Kind kind, Element element, String format, Object... args) {
        if (scope != null && !isEnclosedIn(element, scope)) {
            // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=428357#c1
            List<Element> elementHierarchy = getElementHierarchy(element);
            reverse(elementHierarchy);
            String loc = elementHierarchy.stream().filter(e -> e.getKind() != ElementKind.PACKAGE).map(Object::toString).collect(Collectors.joining("."));
            processingEnv.getMessager().printMessage(kind, String.format(loc + ": " + format, args), scope);
        } else {
            processingEnv.getMessager().printMessage(kind, String.format(format, args), element);
        }
    }

    private static List<Element> getElementHierarchy(Element e) {
        List<Element> elements = new ArrayList<>();
        elements.add(e);

        Element enclosing = e.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            elements.add(enclosing);
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosing != null) {
            elements.add(enclosing);
        }
        return elements;
    }

    /**
     * Bugs in an annotation processor can cause silent failure so try to report any exception
     * throws as errors.
     */
    private void reportException(Kind kind, Element element, Throwable t) {
        StringWriter buf = new StringWriter();
        t.printStackTrace(new PrintWriter(buf));
        message(kind, element, "Exception thrown during processing: %s", buf.toString());
    }

    boolean isNodeType(Element element) {
        if (element.getKind() != ElementKind.CLASS) {
            return false;
        }
        TypeElement type = (TypeElement) element;
        Types types = processingEnv.getTypeUtils();

        while (type != null) {
            if (type.toString().equals("org.graalvm.compiler.graph.Node")) {
                return true;
            }
            type = (TypeElement) types.asElement(type.getSuperclass());
        }
        return false;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        GraphNodeVerifier verifier = new GraphNodeVerifier(this);

        for (Element element : roundEnv.getElementsAnnotatedWith(getTypeElement(NODE_INFO_CLASS_NAME))) {
            scope = element;
            try {
                if (!isNodeType(element)) {
                    errorMessage(element, "%s can only be applied to Node subclasses", getSimpleName(NODE_INFO_CLASS_NAME));
                    continue;
                }

                AnnotationMirror nodeInfo = getAnnotation(element, getType(NODE_INFO_CLASS_NAME));
                if (nodeInfo == null) {
                    errorMessage(element, "Cannot get %s annotation from annotated element", getSimpleName(NODE_INFO_CLASS_NAME));
                    continue;
                }

                TypeElement typeElement = (TypeElement) element;

                Set<Modifier> modifiers = typeElement.getModifiers();
                if (!modifiers.contains(Modifier.FINAL) && !modifiers.contains(Modifier.ABSTRACT)) {
                    // TODO(thomaswue): Reenable this check.
                    // errorMessage(element, "%s annotated class must be either final or abstract",
                    // getSimpleName(NODE_INFO_CLASS_NAME));
                    // continue;
                }
                boolean found = false;
                for (Element e : typeElement.getEnclosedElements()) {
                    if (e.getKind() == ElementKind.FIELD) {
                        if (e.getSimpleName().toString().equals("TYPE")) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    errorMessage(element, "%s annotated class must have a field named TYPE", getSimpleName(NODE_INFO_CLASS_NAME));
                }

                if (!typeElement.equals(verifier.Node) && !modifiers.contains(Modifier.ABSTRACT)) {
                    verifier.verify(typeElement);
                }
            } catch (ElementException ee) {
                errorMessage(ee.element, ee.getMessage());
            } catch (Throwable t) {
                reportException(isBug367599(t) ? Kind.NOTE : Kind.ERROR, element, t);
            } finally {
                scope = null;
            }
        }
        return false;
    }

    /**
     * Determines if a given exception is (most likely) caused by
     * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599">Bug 367599</a>.
     */
    public static boolean isBug367599(Throwable t) {
        if (t instanceof FilerException) {
            for (StackTraceElement ste : t.getStackTrace()) {
                if (ste.toString().contains("org.eclipse.jdt.internal.apt.pluggable.core.filer.IdeFilerImpl.create")) {
                    // See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599
                    return true;
                }
            }
        }
        if (t.getCause() != null) {
            return isBug367599(t.getCause());
        }
        return false;
    }
}
