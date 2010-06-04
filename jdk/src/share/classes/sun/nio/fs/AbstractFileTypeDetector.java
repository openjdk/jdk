/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import java.nio.file.FileRef;
import java.nio.file.spi.FileTypeDetector;
import java.util.Locale;
import java.io.IOException;

/**
 * Base implementation of FileTypeDetector
 */

public abstract class AbstractFileTypeDetector
    extends FileTypeDetector
{
    protected AbstractFileTypeDetector() {
        super();
    }

    /**
     * Invokes the appropriate probe method to guess a file's content type,
     * and checks that the content type's syntax is valid.
     */
    @Override
    public final String probeContentType(FileRef file) throws IOException {
        if (file == null)
            throw new NullPointerException("'file' is null");
        String result = implProbeContentType(file);
        return (result == null) ? null : parse(result);
    }

    /**
     * Probes the given file to guess its content type.
     */
    protected abstract String implProbeContentType(FileRef file)
        throws IOException;

    /**
     * Parses a candidate content type into its type and subtype, returning
     * null if either token is invalid.
     */
    private static String parse(String s) {
        int slash = s.indexOf('/');
        int semicolon = s.indexOf(';');
        if (slash < 0)
            return null;  // no subtype
        String type = s.substring(0, slash).trim().toLowerCase(Locale.ENGLISH);
        if (!isValidToken(type))
            return null;  // invalid type
        String subtype = (semicolon < 0) ? s.substring(slash + 1) :
            s.substring(slash + 1, semicolon);
        subtype = subtype.trim().toLowerCase(Locale.ENGLISH);
        if (!isValidToken(subtype))
            return null;  // invalid subtype
        StringBuilder sb = new StringBuilder(type.length() + subtype.length() + 1);
        sb.append(type);
        sb.append('/');
        sb.append(subtype);
        return sb.toString();
    }

    /**
     * Special characters
     */
    private static final String TSPECIALS = "()<>@,;:/[]?=\\\"";

    /**
     * Returns true if the character is a valid token character.
     */
    private static boolean isTokenChar(char c) {
        return (c > 040) && (c < 0177) && (TSPECIALS.indexOf(c) < 0);
    }

    /**
     * Returns true if the given string is a legal type or subtype.
     */
    private static boolean isValidToken(String s) {
        int len = s.length();
        if (len == 0)
            return false;
        for (int i = 0; i < len; i++) {
            if (!isTokenChar(s.charAt(i)))
                return false;
        }
        return true;
    }
}
