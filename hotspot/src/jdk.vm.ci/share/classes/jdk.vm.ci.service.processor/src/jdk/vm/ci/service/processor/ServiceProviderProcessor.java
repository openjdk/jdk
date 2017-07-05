/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.service.processor;

import java.io.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic.Kind;

import jdk.vm.ci.service.*;

import javax.tools.*;

@SupportedAnnotationTypes("jdk.vm.ci.service.ServiceProvider")
public class ServiceProviderProcessor extends AbstractProcessor {

    private final Set<TypeElement> processed = new HashSet<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private boolean verifyAnnotation(TypeMirror serviceInterface, TypeElement serviceProvider) {
        if (!processingEnv.getTypeUtils().isSubtype(serviceProvider.asType(), serviceInterface)) {
            String msg = String.format("Service provider class %s must implement service interface %s", serviceProvider.getSimpleName(), serviceInterface);
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, serviceProvider);
            return false;
        }

        return true;
    }

    private void processElement(TypeElement serviceProvider) {
        if (processed.contains(serviceProvider)) {
            return;
        }

        processed.add(serviceProvider);
        ServiceProvider annotation = serviceProvider.getAnnotation(ServiceProvider.class);
        if (annotation != null) {
            try {
                annotation.value();
            } catch (MirroredTypeException ex) {
                TypeMirror serviceInterface = ex.getTypeMirror();
                if (verifyAnnotation(serviceInterface, serviceProvider)) {
                    String interfaceName = ex.getTypeMirror().toString();
                    createProviderFile(serviceProvider, interfaceName);
                }
            }
        }
    }

    private void createProviderFile(TypeElement serviceProvider, String interfaceName) {
        if (serviceProvider.getNestingKind().isNested()) {
            // This is a simplifying constraint that means we don't have to
            // processed the qualified name to insert '$' characters at
            // the relevant positions.
            String msg = String.format("Service provider class %s must be a top level class", serviceProvider.getSimpleName());
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, serviceProvider);
            return;
        }

        String filename = "META-INF/jvmci.providers/" + serviceProvider.getQualifiedName();
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, serviceProvider);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
            writer.println(interfaceName);
            writer.close();
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), serviceProvider);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(ServiceProvider.class)) {
            assert element.getKind().isClass();
            processElement((TypeElement) element);
        }

        return true;
    }
}
