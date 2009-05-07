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
package com.sun.xml.internal.rngom.ast.builder;

import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;

/**
 * The container that can have &lt;define> elements.
 * <p>
 * {@link Div}, {@link Grammar}, {@link Include}, or {@link IncludedGrammar}.
 */
public interface GrammarSection<
    P extends ParsedPattern,
    E extends ParsedElementAnnotation,
    L extends Location,
    A extends Annotations<E,L,CL>,
    CL extends CommentList<L>> {

    static final class Combine {
        private final String name;
        private Combine(String name) {
            this.name = name;
        }
        final public String toString() {
            return name;
        }
    }

    static final Combine COMBINE_CHOICE = new Combine("choice");
    static final Combine COMBINE_INTERLEAVE = new Combine("interleave");

    // using \u0000 guarantees that the name will be never used as
    // a user-defined pattern name.
    static final String START = "\u0000#start\u0000";

    /**
     * Called when a pattern is defined.
     *
     * @param name
     *      Name of the pattern. For the definition by a &lt;start/> element,
     *      this parameter is the same as {@link #START}.
     *      to test if it's a named pattern definition or the start pattern definition.
     * @param combine
     *      null or {@link #COMBINE_CHOICE} or {@link #COMBINE_INTERLEAVE} depending
     *      on the value of the combine attribute.
     * @param pattern
     *      The pattern to be defined.
     */
    void define( String name, Combine combine, P pattern, L loc, A anno) throws BuildException;

    /**
     * Called when an annotation is found.
     */
    void topLevelAnnotation(E ea) throws BuildException;

    /**
     * Called when a comment is found.
     */
    void topLevelComment(CL comments) throws BuildException;

    /**
     * Called when &lt;div> is found.
     *
     * @return
     *      the returned {@link Div} object will receive callbacks for structures
     *      inside the &lt;div> element.
     */
    Div<P,E,L,A,CL> makeDiv();

    /**
     * Returns null if already in an include.
     */
    Include<P,E,L,A,CL> makeInclude();
}
