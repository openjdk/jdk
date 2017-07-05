/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.tools.internal.xjc.api.impl.j2s;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.declaration.FieldDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.TypeMirror;
import com.sun.tools.internal.jxc.apt.InlineAnnotationReaderImpl;
import com.sun.tools.internal.jxc.model.nav.APTNavigator;
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
        AnnotationProcessorEnvironment env) {

        ModelBuilder<TypeMirror,TypeDeclaration,FieldDeclaration,MethodDeclaration> builder =
            new ModelBuilder<TypeMirror,TypeDeclaration,FieldDeclaration,MethodDeclaration>(
                InlineAnnotationReaderImpl.theInstance,
                new APTNavigator(env),
                Collections.<TypeDeclaration,TypeDeclaration>emptyMap(),
                defaultNamespaceRemap );

        builder.setErrorHandler(new ErrorHandlerImpl(env.getMessager()));

        for( Reference ref : rootClasses ) {
            TypeMirror t = ref.type;

            XmlJavaTypeAdapter xjta = ref.annotations.getAnnotation(XmlJavaTypeAdapter.class);
            XmlList xl = ref.annotations.getAnnotation(XmlList.class);

            builder.getTypeInfo(new Ref<TypeMirror,TypeDeclaration>(builder,t,xjta,xl));
        }

        TypeInfoSet r = builder.link();
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
        return new JAXBModelImpl(r,builder.reader,rootClasses,new HashMap(additionalElementDecls));
    }

    private static final class ErrorHandlerImpl implements ErrorHandler {
        private final Messager messager;

        public ErrorHandlerImpl(Messager messager) {
            this.messager = messager;
        }

        public void error(IllegalAnnotationException e) {
            messager.printError(e.toString());
        }
    }
}
