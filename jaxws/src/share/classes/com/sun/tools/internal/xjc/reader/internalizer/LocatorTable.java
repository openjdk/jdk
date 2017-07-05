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
package com.sun.tools.internal.xjc.reader.internalizer;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;
import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;

/**
 * Stores {@link Locator} objects for every {@link Element}.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class LocatorTable {
    /** Locations of the start element. */
    private final Map startLocations = new HashMap();

    /** Locations of the end element. */
    private final Map endLocations = new HashMap();

    public void storeStartLocation( Element e, Locator loc ) {
        startLocations.put(e,new LocatorImpl(loc));
    }

    public void storeEndLocation( Element e, Locator loc ) {
        endLocations.put(e,new LocatorImpl(loc));
    }

    public Locator getStartLocation( Element e ) {
        return (Locator)startLocations.get(e);
    }

    public Locator getEndLocation( Element e ) {
        return (Locator)endLocations.get(e);
    }
}
