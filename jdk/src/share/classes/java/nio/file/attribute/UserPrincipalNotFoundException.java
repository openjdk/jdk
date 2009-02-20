/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.file.attribute;

import java.io.IOException;

/**
 * Checked exception thrown when a lookup of {@link UserPrincipal} fails because
 * the principal does not exist.
 *
 * @since 1.7
 */

public class UserPrincipalNotFoundException
    extends IOException
{
    static final long serialVersionUID = -5369283889045833024L;

    private final String name;

    /**
     * Constructs an instance of this class.
     *
     * @param   name
     *          The principal name; may be {@code null}
     */
    public UserPrincipalNotFoundException(String name) {
        super();
        this.name = name;
    }

    /**
     * Returns the user principal name if this exception was created with the
     * user principal name that was not found, otherwise <tt>null</tt>.
     *
     * @return  The user principal name or {@code null}
     */
    public String getName() {
        return name;
    }
}
