/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Content;

/**
 * An abstract class for that implements the {@link Taglet} interface.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.4
 */
public abstract class BaseTaglet implements Taglet {

    protected String name = "Default";

    /**
     * Return true if this <code>Taglet</code>
     * is used in constructor documentation.
     * @return true if this <code>Taglet</code>
     * is used in constructor documentation and false
     * otherwise.
     */
    public boolean inConstructor() {
        return true;
    }

    /**
     * Return true if this <code>Taglet</code>
     * is used in field documentation.
     * @return true if this <code>Taglet</code>
     * is used in field documentation and false
     * otherwise.
     */
    public boolean inField() {
        return true;
    }

    /**
     * Return true if this <code>Taglet</code>
     * is used in method documentation.
     * @return true if this <code>Taglet</code>
     * is used in method documentation and false
     * otherwise.
     */
    public boolean inMethod() {
        return true;
    }

    /**
     * Return true if this <code>Taglet</code>
     * is used in overview documentation.
     * @return true if this <code>Taglet</code>
     * is used in method documentation and false
     * otherwise.
     */
    public boolean inOverview() {
        return true;
    }

    /**
     * Return true if this <code>Taglet</code>
     * is used in package documentation.
     * @return true if this <code>Taglet</code>
     * is used in package documentation and false
     * otherwise.
     */
    public boolean inPackage() {
        return true;
    }

    /**
     * Return true if this <code>Taglet</code>
     * is used in type documentation (classes or interfaces).
     * @return true if this <code>Taglet</code>
     * is used in type documentation and false
     * otherwise.
     */
    public boolean inType() {
        return true;
    }

    /**
     * Return true if this <code>Taglet</code>
     * is an inline tag.
     * @return true if this <code>Taglet</code>
     * is an inline tag and false otherwise.
     */
    public boolean isInlineTag() {
        return false;
    }

    /**
     * Return the name of this custom tag.
     * @return the name of this custom tag.
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException thrown when the method is not supported by the taglet.
     */
    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        throw new IllegalArgumentException("Method not supported in taglet " + getName() + ".");
    }

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException thrown when the method is not supported by the taglet.
     */
    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        throw new IllegalArgumentException("Method not supported in taglet " + getName() + ".");
    }
}
