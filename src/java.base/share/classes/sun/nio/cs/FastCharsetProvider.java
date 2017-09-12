/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.cs;

import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.Iterator;
import java.util.Map;


/**
 * Abstract base class for fast charset providers.
 *
 * @author Mark Reinhold
 */

public class FastCharsetProvider
    extends CharsetProvider
{

    // Maps canonical names to class names
    private Map<String,String> classMap;

    // Maps alias names to canonical names
    private Map<String,String> aliasMap;

    // Maps canonical names to cached instances
    private Map<String,Charset> cache;

    private String packagePrefix;

    protected FastCharsetProvider(String pp,
                                  Map<String,String> am,
                                  Map<String,String> cm,
                                  Map<String,Charset> c)
    {
        packagePrefix = pp;
        aliasMap = am;
        classMap = cm;
        cache = c;
    }

    private String canonicalize(String csn) {
        String acn = aliasMap.get(csn);
        return (acn != null) ? acn : csn;
    }

    // Private ASCII-only version, optimized for interpretation during startup
    //
    private static String toLower(String s) {
        int n = s.length();
        boolean allLower = true;
        for (int i = 0; i < n; i++) {
            int c = s.charAt(i);
            if (((c - 'A') | ('Z' - c)) >= 0) {
                allLower = false;
                break;
            }
        }
        if (allLower)
            return s;
        char[] ca = new char[n];
        for (int i = 0; i < n; i++) {
            int c = s.charAt(i);
            if (((c - 'A') | ('Z' - c)) >= 0)
                ca[i] = (char)(c + 0x20);
            else
                ca[i] = (char)c;
        }
        return new String(ca);
    }

    private Charset lookup(String charsetName) {

        String csn = canonicalize(toLower(charsetName));

        // Check cache first
        Charset cs = cache.get(csn);
        if (cs != null)
            return cs;

        // Do we even support this charset?
        String cln = classMap.get(csn);
        if (cln == null)
            return null;

        if (cln.equals("US_ASCII")) {
            cs = new US_ASCII();
            cache.put(csn, cs);
            return cs;
        }

        // Instantiate the charset and cache it
        try {
            @SuppressWarnings("deprecation")
            Object o= Class.forName(packagePrefix + "." + cln,
                                    true,
                                    this.getClass().getClassLoader()).newInstance();
            cs = (Charset)o;
            cache.put(csn, cs);
            return cs;
        } catch (ClassNotFoundException |
                 IllegalAccessException |
                 InstantiationException x) {
            return null;
        }
    }

    public final Charset charsetForName(String charsetName) {
        synchronized (this) {
            return lookup(canonicalize(charsetName));
        }
    }

    public final Iterator<Charset> charsets() {

        return new Iterator<Charset>() {

                Iterator<String> i = classMap.keySet().iterator();

                public boolean hasNext() {
                    return i.hasNext();
                }

                public Charset next() {
                    String csn = i.next();
                    return lookup(csn);
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }

            };

    }

}
