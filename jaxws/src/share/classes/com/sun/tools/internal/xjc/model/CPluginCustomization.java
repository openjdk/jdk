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
package com.sun.tools.internal.xjc.model;

import com.sun.tools.internal.xjc.Plugin;

import org.w3c.dom.Element;
import org.xml.sax.Locator;

/**
 * Vendor extension customization contributed from {@link Plugin}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class CPluginCustomization {
    /**
     * The annotation found in a schema (or in an external binding file.)
     *
     * Always non-null.
     */
    public final Element element;

    /**
     * The source location where this customization is placed.
     *
     * <p>
     * When an error is found in this customization, this information
     * should be used to point the user to the source of the problem.
     *
     * Always non-nul.
     */
    public final Locator locator;

    private boolean acknowledged;

    /**
     * When a {@link Plugin} "uses" this annotation, call this method
     * to mark it.
     *
     * <p>
     * {@link CPluginCustomization}s that are not marked will be
     * reporeted as an error to users. This allows us to catch
     * customizations that are not used by anybody.
     */
    public void markAsAcknowledged() {
        acknowledged = true;
    }

    public CPluginCustomization(Element element, Locator locator) {
        this.element = element;
        this.locator = locator;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }
}
