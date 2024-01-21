/*
 * Copyright (c) 1996, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import javax.security.auth.Subject;

/**
 * This interface represents the abstract notion of a {@code Principal}, which
 * can be used to represent any entity, such as an individual, a
 * corporation, and a login id.
 *
 * @see java.security.cert.X509Certificate
 *
 * @author Li Gong
 * @since 1.1
 */
public interface Principal {

    /**
     * Compares this {@code Principal} to the specified object.
     * Returns {@code true}
     * if the object passed in matches the {@code Principal} represented by
     * the implementation of this interface.
     *
     * @param another {@code Principal} to compare with.
     *
     * @return {@code true} if the {@code Principal} passed in is the same as
     * that encapsulated by this {@code Principal}, and {@code false} otherwise.
     */
    @Override
    boolean equals(Object another);

    /**
     * Returns a string representation of this {@code Principal}.
     *
     * @return a string representation of this {@code Principal}.
     */
    String toString();

    /**
     * {@return a hashcode for this {@code Principal}}
     */
    @Override
    int hashCode();

    /**
     * Returns the name of this {@code Principal}.
     *
     * @return the name of this {@code Principal}.
     */
    String getName();

    /**
     * Returns {@code true} if the specified subject is implied by this
     * {@code Principal}.
     *
     * @implSpec
     * The default implementation of this method returns {@code true} if
     * {@code subject} is non-null and contains at least one
     * {@code Principal} that is equal to this {@code Principal}.
     *
     * <p>Subclasses may override this with a different implementation, if
     * necessary.
     *
     * @param subject the {@code Subject}
     * @return {@code true} if {@code subject} is non-null and is
     *              implied by this {@code Principal}, or false otherwise.
     * @since 1.8
     */
    default boolean implies(Subject subject) {
        if (subject == null)
            return false;
        return subject.getPrincipals().contains(this);
    }
}
