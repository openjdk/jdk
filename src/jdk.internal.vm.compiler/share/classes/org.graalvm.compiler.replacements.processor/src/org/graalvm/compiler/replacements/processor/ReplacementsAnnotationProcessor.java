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


package org.graalvm.compiler.replacements.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Processor for annotation types in the {@code org.graalvm.compiler.replacements} name space.
 */
public class ReplacementsAnnotationProcessor extends AbstractProcessor {

    private List<AnnotationHandler> handlers;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            PluginGenerator generator = new PluginGenerator();
            for (AnnotationHandler handler : getHandlers()) {
                TypeElement annotationClass = getTypeElementOrNull(handler.annotationTypeName);
                if (annotationClass != null) {
                    for (Element e : roundEnv.getElementsAnnotatedWith(annotationClass)) {
                        AnnotationMirror annotationMirror = getAnnotation(e, annotationClass.asType());
                        handler.process(e, annotationMirror, generator);
                    }
                } else {
                    Set<? extends Element> roots = roundEnv.getRootElements();
                    String message = String.format("Processor %s disabled as %s is not resolvable on the compilation class path", handler.getClass().getName(), handler.annotationTypeName);
                    if (roots.isEmpty()) {
                        env().getMessager().printMessage(Kind.WARNING, message);
                    } else {
                        env().getMessager().printMessage(Kind.WARNING, message, roots.iterator().next());
                    }
                }
            }
            generator.generateAll(this);
        }
        return false;
    }

    public List<AnnotationHandler> getHandlers() {
        if (handlers == null) {
            handlers = new ArrayList<>();
            handlers.add(new ClassSubstitutionHandler(this));
            handlers.add(new MethodSubstitutionHandler(this));
            handlers.add(new NodeIntrinsicHandler(this));
            handlers.add(new FoldHandler(this));
        }
        return handlers;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new HashSet<>();
        for (AnnotationHandler handler : getHandlers()) {
            annotationTypes.add(handler.annotationTypeName);
        }
        return annotationTypes;
    }
}
