/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;

import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * This is a pseudo-element wrapper for doc-files html contents, essentially to
 * associate the doc-files' html documentation's {@code DocCommentTree} to an element.
 */
public class DocFileElement implements DocletElement {

    private final Element element;
    private final PackageElement packageElement;
    private final FileObject fo;

    /**
     * Creates a pseudo-element that wraps a {@code doc-files} documentation file.
     *
     * @param utils the standard utilities class
     * @param element the module element or package element that "owns" the {@code doc-files} subdirectory
     * @param fo the file object
     *
     * @throws IllegalArgumentException if the given element is not a module element or package element
     */
    public DocFileElement(Utils utils, Element element, FileObject fo) {
        this.element = element;
        this.fo = fo;

        switch (element.getKind()) {
            case MODULE -> {
                ModuleElement moduleElement = (ModuleElement) element;
                packageElement = utils.elementUtils.getPackageElement(moduleElement, "");
            }

            case PACKAGE ->
                packageElement = (PackageElement) element;

            default -> throw new IllegalArgumentException(element.getKind() + ":" + element);
        }
    }

    /**
     * {@return the element that "owns" the {@code doc-files} directory}
     */
    public Element getElement() {
        return element;
    }

    @Override
    public PackageElement getPackageElement() {
        return packageElement;
    }

    @Override
    public FileObject getFileObject() {
        return fo;
    }

    @Override
    public Kind getSubKind() {
        return Kind.DOCFILE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocFileElement that = (DocFileElement) o;
        return element.equals(that.element) && fo.equals(that.fo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element, fo);
    }
}

