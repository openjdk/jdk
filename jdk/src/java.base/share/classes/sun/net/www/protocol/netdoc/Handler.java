/*
 * Copyright (c) 1996, 1998, Oracle and/or its affiliates. All rights reserved.
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

/*-
 * netdoc urls point either into the local filesystem or externally
 * through an http url, with network documents being preferred.  Useful for
 * FAQs & other documents which are likely to be changing over time at the
 * central site, and where the user will want the most recent edition.
 *
 * @author Steven B. Byrne
 */

package sun.net.www.protocol.netdoc;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.net.URLStreamHandler;
import java.io.InputStream;
import java.io.IOException;
import sun.security.action.GetPropertyAction;

public class Handler extends URLStreamHandler {
    static URL base;

    /*
     * Attempt to find a load the given url using the default (network)
     * documentation location.  If that fails, use the local copy
     */
    public synchronized URLConnection openConnection(URL u)
        throws IOException
    {
        URLConnection uc = null;
        URL ru;

        boolean localonly = Boolean.parseBoolean(
                GetPropertyAction.getProperty("newdoc.localonly"));

        String docurl = GetPropertyAction.getProperty("doc.url");

        String file = u.getFile();
        if (!localonly) {
            try {
                if (base == null) {
                    base = new URL(docurl);
                }
                ru = new URL(base, file);
            } catch (MalformedURLException e) {
                ru = null;
            }
            if (ru != null) {
                uc = ru.openConnection();
            }
        }

        if (uc == null) {
            try {
                ru = new URL("file", "~", file);

                uc = ru.openConnection();
                InputStream is = uc.getInputStream();   // Check for success.
            } catch (MalformedURLException e) {
                uc = null;
            } catch (IOException e) {
                uc = null;
            }
        }

        if (uc == null) {
            throw new IOException("Can't find file for URL: "
                                  +u.toExternalForm());
        }
        return uc;
    }
}
