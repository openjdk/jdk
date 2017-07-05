/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.namespace.QName;

/**
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
public enum NamespaceVersion {
    v1_2("http://schemas.xmlsoap.org/ws/2004/09/policy", "wsp1_2", new XmlToken[] {
    XmlToken.Policy,
    XmlToken.ExactlyOne,
    XmlToken.All,
    XmlToken.PolicyReference,
    XmlToken.UsingPolicy,
    XmlToken.Name,
    XmlToken.Optional,
    XmlToken.Ignorable,
    XmlToken.PolicyUris,
    XmlToken.Uri,
    XmlToken.Digest,
    XmlToken.DigestAlgorithm
    }),
    v1_5("http://www.w3.org/ns/ws-policy", "wsp", new XmlToken[] {
    XmlToken.Policy,
    XmlToken.ExactlyOne,
    XmlToken.All,
    XmlToken.PolicyReference,
    XmlToken.UsingPolicy,
    XmlToken.Name,
    XmlToken.Optional,
    XmlToken.Ignorable,
    XmlToken.PolicyUris,
    XmlToken.Uri,
    XmlToken.Digest,
    XmlToken.DigestAlgorithm
    });

    /**
     * Resolves URI represented as a String into an enumeration value. If the URI
     * doesn't represent any existing enumeration value, method returns
     * {@code null}.
     *
     * @param uri WS-Policy namespace URI
     * @return Enumeration value that represents given URI or {@code null} if
     * no enumeration value exists for given URI.
     */
    public static NamespaceVersion resolveVersion(String uri) {
        for (NamespaceVersion namespaceVersion : NamespaceVersion.values()) {
            if (namespaceVersion.toString().equalsIgnoreCase(uri)) {
                return namespaceVersion;
            }
        }

        return null;
    }

     /**
     * Resolves fully qualified name defined in the WS-Policy namespace into an
      * enumeration value. If the URI in the name doesn't represent any existing
      * enumeration value, method returns {@code null}
     *
     * @param name fully qualified name defined in the WS-Policy namespace
     * @return Enumeration value that represents given namespace or {@code null} if
     * no enumeration value exists for given namespace.
     */
    public static NamespaceVersion resolveVersion(QName name) {
        return resolveVersion(name.getNamespaceURI());
    }

    /**
     * Returns latest supported version of the policy namespace
     *
     * @return latest supported policy namespace version.
     */
    public static NamespaceVersion getLatestVersion() {
        return v1_5;
    }

    /**
     * Resolves FQN into a policy XML token. The version of the token can be determined
     * by invoking {@link #resolveVersion(QName)}.
     *
     * @param name fully qualified name defined in the WS-Policy namespace
     * @return XML token enumeration that represents this fully qualified name.
     * If the token or the namespace is not resolved {@link XmlToken#UNKNOWN} value
     * is returned.
     */
    public static XmlToken resolveAsToken(QName name) {
        NamespaceVersion nsVersion = resolveVersion(name);
        if (nsVersion != null) {
            XmlToken token = XmlToken.resolveToken(name.getLocalPart());
            if (nsVersion.tokenToQNameCache.containsKey(token)) {
                return token;
            }
        }
        return XmlToken.UNKNOWN;
    }

    private final String nsUri;
    private final String defaultNsPrefix;
    private final Map<XmlToken, QName> tokenToQNameCache;

    private NamespaceVersion(String uri, String prefix, XmlToken... supportedTokens) {
        nsUri = uri;
        defaultNsPrefix = prefix;

        Map<XmlToken, QName> temp = new HashMap<XmlToken, QName>();
        for (XmlToken token : supportedTokens) {
            temp.put(token, new QName(nsUri, token.toString()));
        }
        tokenToQNameCache = Collections.unmodifiableMap(temp);
    }

    /**
     * Method returns default namespace prefix for given namespace version.
     *
     * @return default namespace prefix for given namespace version
     */
    public String getDefaultNamespacePrefix() {
        return defaultNsPrefix;
    }

    /**
     * Resolves XML token into a fully qualified name within given namespace version.
     *
     * @param token XML token enumeration value.
     * @return fully qualified name of the {@code token} within given namespace
     * version. Method returns {@code null} in case the token is not supported in
     * given namespace version or in case {@link XmlToken#UNKNOWN} was used as
     * an input parameter.
     */
    public QName asQName(XmlToken token) throws IllegalArgumentException {
        return tokenToQNameCache.get(token);
    }

    @Override
    public String toString() {
        return nsUri;
    }
}
