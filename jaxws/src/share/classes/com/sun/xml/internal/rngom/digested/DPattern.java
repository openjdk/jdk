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
package com.sun.xml.internal.rngom.digested;

import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.parse.Parseable;
import org.xml.sax.Locator;

/**
 * Base class of all the patterns.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public abstract class DPattern implements ParsedPattern {
    Locator location;
    DAnnotation annotation;

    /**
     * Used to chain the child patterns in a doubly-linked list.
     */
    DPattern next;
    DPattern prev;

    /**
     * Returns where the pattern is defined in the source code.
     */
    public Locator getLocation() {
        return location;
    }

    /**
     * Returns the annotation associated with it.
     *
     * @return
     *      may be empty, but never be null.
     */
    public DAnnotation getAnnotation() {
        if(annotation==null)
            return DAnnotation.EMPTY;
        return annotation;
    }

    /**
     * Returns true if this pattern is nullable.
     *
     * A nullable pattern is a pattern that can match the empty sequence.
     */
    public abstract boolean isNullable();

    public abstract <V> V accept( DPatternVisitor<V> visitor );

    /**
     * Creates a {@link Parseable} object that reparses this pattern.
     */
    public Parseable createParseable() {
        return new PatternParseable(this);
    }

    /**
     * Returns true if this is {@link DElementPattern}.
     */
    public final boolean isElement() {
        return this instanceof DElementPattern;
    }

    /**
     * Returns true if this is {@link DAttributePattern}.
     */
    public final boolean isAttribute() {
        return this instanceof DAttributePattern;
    }
}
