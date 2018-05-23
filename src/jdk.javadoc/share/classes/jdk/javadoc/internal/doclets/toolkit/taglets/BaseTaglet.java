/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.util.Set;
import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.Content;

/**
 * A base class that implements the {@link Taglet} interface.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public class BaseTaglet implements Taglet {
    /**
     * The different kinds of place where any given tag may be used.
     */
    enum Site {
        OVERVIEW, MODULE, PACKAGE, TYPE, CONSTRUCTOR, METHOD, FIELD
    }

    protected final String name;
    private final boolean inline;
    private final Set<Site> sites;

    BaseTaglet(String name, boolean inline, Set<Site> sites) {
        this.name = name;
        this.inline = inline;
        this.sites = sites;
    }

    /**
     * Returns true if this {@code Taglet} can be used in constructor documentation.
     * @return true if this {@code Taglet} can be used in constructor documentation and false
     * otherwise.
     */
    public final boolean inConstructor() {
        return sites.contains(Site.CONSTRUCTOR);
    }

    /**
     * Returns true if this {@code Taglet} can be used in field documentation.
     * @return true if this {@code Taglet} can be used in field documentation and false
     * otherwise.
     */
    public final boolean inField() {
        return  sites.contains(Site.FIELD);
    }

    /**
     * Returns true if this {@code Taglet} can be used in method documentation.
     * @return true if this {@code Taglet} can be used in method documentation and false
     * otherwise.
     */
    public final boolean inMethod() {
        return  sites.contains(Site.METHOD);
    }

    /**
     * Returns true if this {@code Taglet} can be used in overview documentation.
     * @return true if this {@code Taglet} can be used in method documentation and false
     * otherwise.
     */
    public final boolean inOverview() {
        return  sites.contains(Site.OVERVIEW);
    }

    /**
     * Returns true if this {@code Taglet} can be used in module documentation.
     * @return true if this {@code Taglet} can be used in module documentation and false
     * otherwise.
     */
    public final boolean inModule() {
        return  sites.contains(Site.MODULE);
    }

    /**
     * Returns true if this {@code Taglet} can be used in package documentation.
     * @return true if this {@code Taglet} can be used in package documentation and false
     * otherwise.
     */
    public final boolean inPackage() {
        return  sites.contains(Site.PACKAGE);
    }

    /**
     * Returns true if this {@code Taglet} can be used in type documentation (classes or interfaces).
     * @return true if this {@code Taglet} can be used in type documentation and false
     * otherwise.
     */
    public final boolean inType() {
        return  sites.contains(Site.TYPE);
    }

    /**
     * Returns true if this {@code Taglet} is an inline tag.
     * @return true if this {@code Taglet} represents an inline tag and false otherwise.
     */
    public final boolean isInlineTag() {
        return inline;
    }

    /**
     * Returns the name of this tag.
     * @return the name of this tag.
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedTagletOperationException thrown when the method is
     *         not supported by the taglet.
     */
    public Content getTagletOutput(Element element, DocTree tag, TagletWriter writer) {
        throw new UnsupportedTagletOperationException("Method not supported in taglet " + getName() + ".");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedTagletOperationException thrown when the method is not
     *         supported by the taglet.
     */
    public Content getTagletOutput(Element holder, TagletWriter writer) {
        throw new UnsupportedTagletOperationException("Method not supported in taglet " + getName() + ".");
    }
}
