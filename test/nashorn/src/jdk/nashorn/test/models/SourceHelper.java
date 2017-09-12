/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.test.models;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import jdk.nashorn.internal.runtime.Source;

/**
 * Helper class to facilitate script access of nashorn Source class.
 */
@SuppressWarnings("javadoc")
public final class SourceHelper {
    private SourceHelper() {}

    public static String baseURL(final URL url) {
        return Source.baseURL(url);
    }

    public static String readFully(final File file) throws IOException {
        return new String(Source.readFully(file));
    }

    public static String readFully(final URL url) throws IOException {
        return Source.sourceFor(url.toString(), url).getString();
    }

    public static String readFully(final Reader reader) throws IOException {
        return new String(Source.readFully(reader));
    }
}
