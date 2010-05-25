/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This taglet acts as a wrapper to enable
 * {@link com.sun.tools.doclets.Taglet} type taglets to work
 * with the current version of Javadoc.
 * Note: this taglet only works with legacy taglets (those compatible with
 * Javadoc 1.4.x) that writes strings.
 * This taglet is able to wrap most most legacy taglets because
 * the standard doclet is the only known doclet to use legacy taglets.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @since 1.5
 * @author Jamie Ho
 */

public class LegacyTaglet implements Taglet {

    private com.sun.tools.doclets.Taglet legacyTaglet;

    public LegacyTaglet(com.sun.tools.doclets.Taglet t) {
        legacyTaglet = t;
    }

    /**
     * {@inheritDoc}
     */
    public boolean inField() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inField();
    }

    /**
     * {@inheritDoc}
     */
    public boolean inConstructor() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inConstructor();
    }

    /**
     * {@inheritDoc}
     */
    public boolean inMethod() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inMethod();
    }

    /**
     * {@inheritDoc}
     */
    public boolean inOverview() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inOverview();
    }

    /**
     * {@inheritDoc}
     */
    public boolean inPackage() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inPackage();
    }

    /**
     * {@inheritDoc}
     */
    public boolean inType() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inType();
    }

    /**
     * Return true if this <code>Taglet</code>
     * is an inline tag.
     * @return true if this <code>Taglet</code>
     * is an inline tag and false otherwise.
     */
    public boolean isInlineTag() {
        return legacyTaglet.isInlineTag();
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return legacyTaglet.getName();
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getTagletOutput(Tag tag, TagletWriter writer)
            throws IllegalArgumentException {
        TagletOutput output = writer.getOutputInstance();
        output.setOutput(legacyTaglet.toString(tag));
        return output;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getTagletOutput(Doc holder, TagletWriter writer)
            throws IllegalArgumentException {
        TagletOutput output = writer.getOutputInstance();
        output.setOutput(legacyTaglet.toString(holder.tags(getName())));
        return output;
    }
}
