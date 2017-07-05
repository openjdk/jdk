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
package com.sun.xml.internal.rngom.nc;

import com.sun.xml.internal.rngom.ast.om.ParsedNameClass;

import javax.xml.namespace.QName;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Name class is a set of {@link QName}s.
 */
public abstract class NameClass implements ParsedNameClass, Serializable {
    static final int SPECIFICITY_NONE = -1;
    static final int SPECIFICITY_ANY_NAME = 0;
    static final int SPECIFICITY_NS_NAME = 1;
    static final int SPECIFICITY_NAME = 2;

    /**
     * Returns true if the given {@link QName} is a valid name
     * for this QName.
     */
    public abstract boolean contains(QName name);

    public abstract int containsSpecificity(QName name);

    /**
     * Visitor pattern support.
     */
    public abstract <V> V accept(NameClassVisitor<V> visitor);

    /**
     * Returns true if the name class accepts infinite number of
     * {@link QName}s.
     *
     * <p>
     * Intuitively, this method returns true if the name class is
     * some sort of wildcard.
     */
    public abstract boolean isOpen();

    /**
     * If the name class is closed (IOW !{@link #isOpen()}),
     * return the set of names in this name class. Otherwise the behavior
     * is undefined.
     */
    public Set<QName> listNames() {
        final Set<QName> names = new HashSet<QName>();
        accept(new NameClassWalker() {
            public Void visitName(QName name) {
                names.add(name);
                return null;
            }
        });
        return names;
    }

    /**
     * Returns true if the intersection between this name class
     * and the specified name class is non-empty.
     */
    public final boolean hasOverlapWith( NameClass nc2 ) {
        return OverlapDetector.overlap(this,nc2);
    }


    /** Sigleton instance that represents "anyName". */
    public static final NameClass ANY = new AnyNameClass();

    /**
     * Sigleton instance that accepts no name.
     *
     * <p>
     * This instance is useful when doing boolean arithmetic over
     * name classes (such as computing an inverse of a given name class, etc),
     * even though it can never appear in a RELAX NG surface syntax.
     *
     * <p>
     * Internally, this instance is also used for:
     * <ol>
     *  <li>Used to recover from errors during parsing.
     *  <li>Mark element patterns with &lt;notAllowed/> content model.
     * </ol>
     */
    public static final NameClass NULL = new NullNameClass();
}
