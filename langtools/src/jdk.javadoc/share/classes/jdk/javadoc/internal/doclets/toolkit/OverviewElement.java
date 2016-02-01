/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;

import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import jdk.javadoc.doclet.DocletEnvironment;

/**
 * This is a pseudo element wrapper for the root element, essentially to
 * associate overview documentation's DocCommentTree to this Element.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class OverviewElement implements Element {

    public final DocletEnvironment root;

    OverviewElement(DocletEnvironment root) {
        this.root = root;
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public TypeMirror asType() {
        throw new UnsupportedOperationException("Unsupported method");
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public ElementKind getKind() {
        return ElementKind.OTHER;
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public Set<javax.lang.model.element.Modifier> getModifiers() {
        throw new UnsupportedOperationException("Unsupported method");
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public Name getSimpleName() {
        throw new UnsupportedOperationException("Unsupported method");
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public Element getEnclosingElement() {
        throw new UnsupportedOperationException("Unsupported method");
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public java.util.List<? extends Element> getEnclosedElements() {
        throw new UnsupportedOperationException("Unsupported method");
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public java.util.List<? extends AnnotationMirror> getAnnotationMirrors() {
        throw new UnsupportedOperationException("Unsupported method");
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        throw new UnsupportedOperationException("Unsupported method");
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return v.visitUnknown(this, p);
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        throw new UnsupportedOperationException("Unsupported method");
    }
}

