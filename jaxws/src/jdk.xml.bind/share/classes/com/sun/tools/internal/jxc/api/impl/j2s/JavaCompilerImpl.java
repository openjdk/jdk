/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.internal.jxc.api.impl.j2s;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import com.sun.tools.internal.jxc.ap.InlineAnnotationReaderImpl;
import com.sun.tools.internal.jxc.model.nav.ApNavigator;
import com.sun.tools.internal.xjc.api.J2SJAXBModel;
import com.sun.tools.internal.xjc.api.JavaCompiler;
import com.sun.tools.internal.xjc.api.Reference;
import com.sun.xml.internal.bind.v2.model.core.ErrorHandler;
import com.sun.xml.internal.bind.v2.model.core.Ref;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.impl.ModelBuilder;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class JavaCompilerImpl implements JavaCompiler {
    public J2SJAXBModel bind(
        Collection<Reference> rootClasses,
        Map<QName,Reference> additionalElementDecls,
        String defaultNamespaceRemap,
        ProcessingEnvironment env) {

        ModelBuilder<TypeMirror, TypeElement, VariableElement, ExecutableElement> builder =
                new ModelBuilder<TypeMirror, TypeElement, VariableElement, ExecutableElement>(
                InlineAnnotationReaderImpl.theInstance,
                new ApNavigator(env),
                Collections.<TypeElement, TypeElement>emptyMap(),
                defaultNamespaceRemap );

        builder.setErrorHandler(new ErrorHandlerImpl(env.getMessager()));

        for ( Reference ref : rootClasses ) {
            TypeMirror t = ref.type;

            XmlJavaTypeAdapter xjta = ref.annotations.getAnnotation(XmlJavaTypeAdapter.class);
            XmlList xl = ref.annotations.getAnnotation(XmlList.class);

            builder.getTypeInfo(new Ref<TypeMirror, TypeElement>(builder, t, xjta, xl));
        }

        TypeInfoSet<TypeMirror, TypeElement, VariableElement, ExecutableElement> r = builder.link();
        if(r==null)     return null;

        if(additionalElementDecls==null)
            additionalElementDecls = Collections.emptyMap();
        else {
            // fool proof check
            for (Map.Entry<QName, ? extends Reference> e : additionalElementDecls.entrySet()) {
                if(e.getKey()==null)
                    throw new IllegalArgumentException("nulls in additionalElementDecls");
            }
        }
        return new JAXBModelImpl(r, builder.reader, rootClasses, new HashMap<QName, Reference>(additionalElementDecls));
    }

    private static final class ErrorHandlerImpl implements ErrorHandler {
        private final Messager messager;

        public ErrorHandlerImpl(Messager messager) {
            this.messager = messager;
        }

        public void error(IllegalAnnotationException e) {
            String error = e.toString();
            messager.printMessage(Diagnostic.Kind.ERROR, error);
            System.err.println(error); //TODO: temporary fix problem with no ouput from messager
        }
    }
}
