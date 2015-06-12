/*
 * Copyright (c) 1996, Oracle and/or its affiliates. All rights reserved.
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

package sun.net;

/**
 * Helper class to map URL "abbreviations" to real URLs.
 * The default implementation supports the following mappings:
 * <pre>{@code
 *   ftp.mumble.bar/... => ftp://ftp.mumble.bar/...
 *   gopher.mumble.bar/... => gopher://gopher.mumble.bar/...
 *   other.name.dom/... => http://other.name.dom/...
 *   /foo/... => file:/foo/...
 * }</pre>
 *
 * Full URLs (those including a protocol name) are passed through unchanged.
 *
 * Subclassers can override or extend this behavior to support different
 * or additional canonicalization policies.
 *
 * @author      Steve Byrne
 */

public class URLCanonicalizer {
    /**
     * Creates the default canonicalizer instance.
     */
    public URLCanonicalizer() { }

    /**
     * Given a possibly abbreviated URL (missing a protocol name, typically),
     * this method's job is to transform that URL into a canonical form,
     * by including a protocol name and additional syntax, if necessary.
     *
     * For a correctly formed URL, this method should just return its argument.
     */
    public String canonicalize(String simpleURL) {
        String resultURL = simpleURL;
        if (simpleURL.startsWith("ftp.")) {
            resultURL = "ftp://" + simpleURL;
        } else if (simpleURL.startsWith("gopher.")) {
            resultURL = "gopher://" + simpleURL;
        } else if (simpleURL.startsWith("/")) {
            resultURL = "file:" + simpleURL;
        } else if (!hasProtocolName(simpleURL)) {
            if (isSimpleHostName(simpleURL)) {
                simpleURL = "www." + simpleURL + ".com";
            }
            resultURL = "http://" + simpleURL;
        }

        return resultURL;
    }

    /**
     * Given a possibly abbreviated URL, this predicate function returns
     * true if it appears that the URL contains a protocol name
     */
    public boolean hasProtocolName(String url) {
        int index = url.indexOf(':');
        if (index <= 0) {       // treat ":foo" as not having a protocol spec
            return false;
        }

        for (int i = 0; i < index; i++) {
            char c = url.charAt(i);

            // REMIND: this is a guess at legal characters in a protocol --
            // need to be verified
            if ((c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c == '-')) {
                continue;
            }

            // found an illegal character
            return false;
        }

        return true;
    }

    /**
     * Returns true if the URL is just a single name, no periods or
     * slashes, false otherwise
     **/
    protected boolean isSimpleHostName(String url) {

        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);

            // REMIND: this is a guess at legal characters in a protocol --
            // need to be verified
            if ((c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || (c == '-')) {
                continue;
            }

            // found an illegal character
            return false;
        }

        return true;
    }
}
