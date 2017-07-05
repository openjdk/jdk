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

import com.sun.codemodel.internal.JPackage;

/**
 * Parent of a {@link CClassInfo}/{@link CElementInfo}.
 *
 * TODO: rename
 *
 * Either {@link CClassInfo} or {@link CClassInfoParent.Package}.
 */
public interface CClassInfoParent {
    /**
     * Returns the fully-qualified name.
     */
    String fullName();

    <T> T accept( Visitor<T> visitor );

    /**
     * Gets the nearest {@link JPackage}.
     */
    JPackage getOwnerPackage();

    /**
     * Visitor of {@link CClassInfoParent}
     */
    public static interface Visitor<T> {
        T onBean( CClassInfo bean );
        T onPackage( JPackage pkg );
        T onElement( CElementInfo element );
    }

    /**
     * {@link JPackage} as a {@link CClassInfoParent}.
     *
     * Use {@link Model#getPackage} to obtain an instance.
     */
    public static final class Package implements CClassInfoParent {
        public final JPackage pkg;

        public Package(JPackage pkg) {
            this.pkg = pkg;
        }

        public String fullName() {
            return pkg.name();
        }

        public <T> T accept(Visitor<T> visitor) {
            return visitor.onPackage(pkg);
        }

        public JPackage getOwnerPackage() {
            return pkg;
        }
    }
}
