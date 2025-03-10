/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.renderer.html;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * Allows http, https and mailto protocols for url.
 * Also allows protocol relative urls, and relative urls.
 * Implementation based on https://github.com/OWASP/java-html-sanitizer/blob/f07e44b034a45d94d6fd010279073c38b6933072/src/main/java/org/owasp/html/FilterUrlByProtocolAttributePolicy.java
 */
public class DefaultUrlSanitizer implements UrlSanitizer {
    private Set<String> protocols;

    public DefaultUrlSanitizer() {
        this(Arrays.asList("http", "https", "mailto"));
    }

    public DefaultUrlSanitizer(Collection<String> protocols) {
        this.protocols = new HashSet<>(protocols);
    }

    @Override
    public String sanitizeLinkUrl(String url) {
        url = stripHtmlSpaces(url);
        protocol_loop:
        for (int i = 0, n = url.length(); i < n; ++i) {
            switch (url.charAt(i)) {
                case '/':
                case '#':
                case '?':  // No protocol.
                    break protocol_loop;
                case ':':
                    String protocol = url.substring(0, i).toLowerCase();
                    if (!protocols.contains(protocol)) {
                        return "";
                    }
                    break protocol_loop;
            }
        }
        return url;
    }


    @Override
    public String sanitizeImageUrl(String url) {
        return sanitizeLinkUrl(url);
    }

    private String stripHtmlSpaces(String s) {
        int i = 0, n = s.length();
        for (; n > i; --n) {
            if (!isHtmlSpace(s.charAt(n - 1))) {
                break;
            }
        }
        for (; i < n; ++i) {
            if (!isHtmlSpace(s.charAt(i))) {
                break;
            }
        }
        if (i == 0 && n == s.length()) {
            return s;
        }
        return s.substring(i, n);
    }

    private boolean isHtmlSpace(int ch) {
        switch (ch) {
            case ' ':
            case '\t':
            case '\n':
            case '\u000c':
            case '\r':
                return true;
            default:
                return false;

        }
    }
}
