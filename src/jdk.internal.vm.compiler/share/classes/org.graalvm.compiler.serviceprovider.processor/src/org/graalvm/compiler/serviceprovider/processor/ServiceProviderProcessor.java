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


package org.graalvm.compiler.serviceprovider.processor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Processes classes annotated with {@code ServiceProvider}. For a service defined by {@code S} and
 * a class {@code P} implementing the service, this processor generates the file
 * {@code META-INF/providers/P} whose contents are a single line containing the fully qualified name
 * of {@code S}.
 */
@SupportedAnnotationTypes("org.graalvm.compiler.serviceprovider.ServiceProvider")
public class ServiceProviderProcessor extends AbstractProcessor {

    private static final String SERVICE_PROVIDER_CLASS_NAME = "org.graalvm.compiler.serviceprovider.ServiceProvider";
    private final Set<TypeElement> processed = new HashSet<>();
    private final Map<TypeElement, String> serviceProviders = new HashMap<>();

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
        AnnotationMirror annotation = getAnnotation(serviceProvider, getType(SERVICE_PROVIDER_CLASS_NAME));
        if (annotation != null) {
            TypeMirror service = getAnnotationValue(annotation, "value", TypeMirror.class);
            if (verifyAnnotation(service, serviceProvider)) {
                if (serviceProvider.getNestingKind().isNested()) {
                    /*
                     * This is a simplifying constraint that means we don't have to process the
                     * qualified name to insert '$' characters at the relevant positions.
                     */
                    String msg = String.format("Service provider class %s must be a top level class", serviceProvider.getSimpleName());
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, serviceProvider);
                } else {
                    /*
                     * Since the definition of the service class is not necessarily modifiable, we
                     * need to support a non-top-level service class and ensure its name is properly
                     * expressed with '$' separating nesting levels instead of '.'.
                     */
                    TypeElement serviceElement = (TypeElement) processingEnv.getTypeUtils().asElement(service);
                    String serviceName = serviceElement.getSimpleName().toString();
                    Element enclosing = serviceElement.getEnclosingElement();
                    while (enclosing != null) {
                        final ElementKind kind = enclosing.getKind();
                        if (kind == ElementKind.PACKAGE) {
                            serviceName = ((PackageElement) enclosing).getQualifiedName().toString() + "." + serviceName;
                            break;
                        } else if (kind == ElementKind.CLASS || kind == ElementKind.INTERFACE) {
                            serviceName = ((TypeElement) enclosing).getSimpleName().toString() + "$" + serviceName;
                            enclosing = enclosing.getEnclosingElement();
                        } else {
                            String msg = String.format("Cannot generate provider descriptor for service class %s as it is not nested in a package, class or interface",
                                            serviceElement.getQualifiedName());
                            processingEnv.getMessager().printMessage(Kind.ERROR, msg, serviceProvider);
                            return;
                        }
                    }
                    serviceProviders.put(serviceProvider, serviceName);
                }
            }
        }
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            for (Entry<TypeElement, String> e : serviceProviders.entrySet()) {
                createProviderFile(e.getKey().getQualifiedName().toString(), e.getValue(), e.getKey());
            }
            serviceProviders.clear();
            return true;
        }

        TypeElement serviceProviderTypeElement = getTypeElement(SERVICE_PROVIDER_CLASS_NAME);
        for (Element element : roundEnv.getElementsAnnotatedWith(serviceProviderTypeElement)) {
            assert element.getKind().isClass();
            processElement((TypeElement) element);
        }

        return true;
    }
}
