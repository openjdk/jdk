/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 *  A holder for an indexed {@link Element} or {@link SearchIndexItem}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class IndexItem {

    private final Element element;
    private final String label;
    private final SearchIndexItem searchTag;

    public static IndexItem of(ModuleElement moduleElement, Utils utils) {
        return new IndexItem(moduleElement, utils.getFullyQualifiedName(moduleElement));
    }

    public static IndexItem of(PackageElement packageElement, Utils utils) {
        return new IndexItem(packageElement, utils.getPackageName(packageElement));
    }

    public static IndexItem of(TypeElement typeElement, Utils utils) {
        return new IndexItem(typeElement, utils.getSimpleName(typeElement));
    }

    public static IndexItem of(TypeElement typeElement, Element member, Utils utils) {
        String name = utils.getSimpleName(member);
        if (utils.isExecutableElement(member)) {
            ExecutableElement ee = (ExecutableElement)member;
            name += utils.flatSignature(ee, typeElement);
        }
        return new IndexItem(member, name) {
            @Override
            public TypeElement getTypeElement() {
                return typeElement;
            }
        };
    }

    private IndexItem(Element element, String label) {
        this.element = element;
        this.label = label;
        this.searchTag = null;
    }

    public IndexItem(SearchIndexItem searchTag) {
        this.element = null;
        this.searchTag = searchTag;
        this.label = searchTag.getLabel();
    }

    public String getLabel() {
        return label;
    }

    public String getFullyQualifiedLabel(Utils utils) {
        TypeElement typeElement = getTypeElement();
        if (typeElement != null) {
            return utils.getFullyQualifiedName(typeElement) + "." + label;
        } else if (element != null) {
            return utils.getFullyQualifiedName(element);
        } else {
            return label;
        }
    }

    public Element getElement() {
        return element;
    }

    public SearchIndexItem getSearchTag() {
        return searchTag;
    }

    public TypeElement getTypeElement() {
        return null;
    }
}
