/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

/**
 * Represents a MIME type for the purposes of validation and matching. For
 * now this class is implemented using the javax.activation.MimeType class but
 * this dependency can easily be eliminated when required.
 */

public class MimeType {
    private final javax.activation.MimeType type;

    private MimeType(javax.activation.MimeType type) {
        this.type = type;
    }

    /**
     * Parses the given string as a MIME type.
     *
     * @throws  IllegalArgumentException
     *          If the string is not a valid MIME type
     */
    public static MimeType parse(String type) {
        try {
            return new MimeType(new javax.activation.MimeType(type));
        } catch (javax.activation.MimeTypeParseException x) {
            throw new IllegalArgumentException(x);
        }
    }

    /**
     * Returns {@code true} if this MIME type has parameters.
     */
    public boolean hasParameters() {
        return !type.getParameters().isEmpty();
    }

    /**
     * Matches this MIME type against a given MIME type. This method returns
     * true if the given string is a MIME type and it matches this type.
     */
    public boolean match(String other) {
        try {
            return type.match(other);
        } catch (javax.activation.MimeTypeParseException x) {
            return false;
        }
    }
}
