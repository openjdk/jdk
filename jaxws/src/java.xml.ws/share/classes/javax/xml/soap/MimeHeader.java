/*
 * Copyright (c) 2004, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.soap;


/**
 * An object that stores a MIME header name and its value. One or more
 * {@code MimeHeader} objects may be contained in a {@code MimeHeaders}
 * object.
 *
 * @see MimeHeaders
 * @since 1.6
 */
public class MimeHeader {

   private String name;
   private String value;

   /**
    * Constructs a {@code MimeHeader} object initialized with the given
    * name and value.
    *
    * @param name a {@code String} giving the name of the header
    * @param value a {@code String} giving the value of the header
    */
    public MimeHeader(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name of this {@code MimeHeader} object.
     *
     * @return the name of the header as a {@code String}
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the value of this {@code MimeHeader} object.
     *
     * @return  the value of the header as a {@code String}
     */
    public String getValue() {
        return value;
    }
}
