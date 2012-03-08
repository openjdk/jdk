/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.sourcemodel.wspolicy;

/**
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
public enum XmlToken {
    Policy("Policy", true),
    ExactlyOne("ExactlyOne", true),
    All("All", true),
    PolicyReference("PolicyReference", true),
    UsingPolicy("UsingPolicy", true),
    Name("Name", false),
    Optional("Optional", false),
    Ignorable("Ignorable", false),
    PolicyUris("PolicyURIs", false),
    Uri("URI", false),
    Digest("Digest", false),
    DigestAlgorithm("DigestAlgorithm", false),

    UNKNOWN("", true);



    /**
     * Resolves URI represented as a String into an enumeration value. If the URI
     * doesn't represent any existing enumeration value, method returns {@code null}
     *
     * @param uri
     * @return Enumeration value that represents given URI or {@code null} if
     * no enumeration value exists for given URI.
     */
    public static XmlToken resolveToken(String name) {
        for (XmlToken token : XmlToken.values()) {
            if (token.toString().equals(name)) {
                return token;
            }
        }

        return UNKNOWN;
    }

    private String tokenName;
    private boolean element;

    private XmlToken(final String name, boolean element) {
        this.tokenName = name;
        this.element = element;
    }

    public boolean isElement() {
        return element;
    }

    @Override
    public String toString() {
        return tokenName;
    }
}
