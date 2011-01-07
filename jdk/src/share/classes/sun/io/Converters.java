/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.io;

import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.util.Properties;

/**
 * Package-private utility class that caches the default converter classes and
 * provides other logic common to both the ByteToCharConverter and
 * CharToByteConverter classes.
 *
 * @author   Mark Reinhold
 * @since    1.2
 *
 * @deprecated Replaced by {@link java.nio.charset}.  THIS API WILL BE
 * REMOVED IN J2SE 1.6.
 */
@Deprecated
public class Converters {

    private Converters() { }    /* To prevent instantiation */

    /* Lock for all static fields in this class */
    private static Object lock = Converters.class;

    /* Cached values of system properties */
    private static String converterPackageName = null;  /* file.encoding.pkg */
    private static String defaultEncoding = null;       /* file.encoding */

    /* Converter type constants and names */
    public static final int BYTE_TO_CHAR = 0;
    public static final int CHAR_TO_BYTE = 1;
    private static final String[] converterPrefix = { "ByteToChar",
                                                      "CharToByte" };


    // -- Converter class cache --

    private static final int CACHE_SIZE = 3;

    /* For the default charset, whatever it turns out to be */
    private static final Object DEFAULT_NAME = new Object();

    /* Cached converter classes, CACHE_SIZE per converter type.  Each cache
     * entry is a soft reference to a two-object array; the first element of
     * the array is the converter class, the second is an object (typically a
     * string) representing the encoding name that was used to request the
     * converter, e.g.,
     *
     *     ((Object[])classCache[CHAR_TO_BYTE][i].get())[0]
     *
     * will be a CharToByteConverter and
     *
     *     ((Object[])classCache[CHAR_TO_BYTE][i].get())[1]
     *
     * will be the string encoding name used to request it, assuming that cache
     * entry i is valid.
     *
     * Ordinarily we'd do this with a private static utility class, but since
     * this code can be involved in the startup sequence it's important to keep
     * the footprint down.
     */
    @SuppressWarnings("unchecked")
    private static SoftReference<Object[]>[][] classCache
        = (SoftReference<Object[]>[][]) new SoftReference<?>[][] {
            new SoftReference<?>[CACHE_SIZE],
            new SoftReference<?>[CACHE_SIZE]
        };

    private static void moveToFront(Object[] oa, int i) {
        Object ob = oa[i];
        for (int j = i; j > 0; j--)
            oa[j] = oa[j - 1];
        oa[0] = ob;
    }

    private static Class<?> cache(int type, Object encoding) {
        SoftReference<Object[]>[] srs = classCache[type];
        for (int i = 0; i < CACHE_SIZE; i++) {
            SoftReference<Object[]> sr = srs[i];
            if (sr == null)
                continue;
            Object[] oa = sr.get();
            if (oa == null) {
                srs[i] = null;
                continue;
            }
            if (oa[1].equals(encoding)) {
                moveToFront(srs, i);
                return (Class<?>)oa[0];
            }
        }
        return null;
    }

    private static Class<?> cache(int type, Object encoding, Class<?> c) {
        SoftReference<Object[]>[] srs = classCache[type];
        srs[CACHE_SIZE - 1] = new SoftReference<>(new Object[] { c, encoding });
        moveToFront(srs, CACHE_SIZE - 1);
        return c;
    }

    /* Used to avoid doing expensive charset lookups for charsets that are not
     * yet directly supported by NIO.
     */
    public static boolean isCached(int type, String encoding) {
        synchronized (lock) {
            SoftReference<Object[]>[] srs = classCache[type];
            for (int i = 0; i < CACHE_SIZE; i++) {
                SoftReference<Object[]> sr = srs[i];
                if (sr == null)
                    continue;
                Object[] oa = sr.get();
                if (oa == null) {
                    srs[i] = null;
                    continue;
                }
                if (oa[1].equals(encoding))
                    return true;
            }
            return false;
        }
    }



    /** Get the name of the converter package */
    private static String getConverterPackageName() {
        String cp = converterPackageName;
        if (cp != null) return cp;
        java.security.PrivilegedAction<String> pa =
            new sun.security.action.GetPropertyAction("file.encoding.pkg");
        cp = java.security.AccessController.doPrivileged(pa);
        if (cp != null) {
            /* Property is set, so take it as the true converter package */
            converterPackageName = cp;
        } else {
            /* Fall back to sun.io */
            cp = "sun.io";
        }
        return cp;
    }

    public static String getDefaultEncodingName() {
        synchronized (lock) {
            if (defaultEncoding == null) {
                java.security.PrivilegedAction<String> pa =
                    new sun.security.action.GetPropertyAction("file.encoding");
                defaultEncoding = java.security.AccessController.doPrivileged(pa);
            }
        }
        return defaultEncoding;
    }

    public static void resetDefaultEncodingName() {
        // This method should only be called during VM initialization.
        if (sun.misc.VM.isBooted())
            return;

        synchronized (lock) {
            defaultEncoding = "ISO-8859-1";
            Properties p = System.getProperties();
            p.setProperty("file.encoding", defaultEncoding);
            System.setProperties(p);
        }
    }

    /**
     * Get the class that implements the given type of converter for the named
     * encoding, or throw an UnsupportedEncodingException if no such class can
     * be found
     */
    private static Class<?> getConverterClass(int type, String encoding)
        throws UnsupportedEncodingException
    {
        String enc = null;

        /* "ISO8859_1" is the canonical name for the ISO-Latin-1 encoding.
           Native code in the JDK commonly uses the alias "8859_1" instead of
           "ISO8859_1".  We hardwire this alias here in order to avoid loading
           the full alias table just for this case. */
        if (!encoding.equals("ISO8859_1")) {
            if (encoding.equals("8859_1")) {
                enc = "ISO8859_1";
            /*
             * On Solaris with nl_langinfo() called in GetJavaProperties():
             *
             *   locale undefined -> NULL -> hardcoded default
             *   "C" locale       -> "" -> hardcoded default    (on 2.6)
             *   "C" locale       -> "646"                      (on 2.7)
             *   "en_US" locale -> "ISO8859-1"
             *   "en_GB" locale -> "ISO8859-1"                  (on 2.7)
             *   "en_UK" locale -> "ISO8859-1"                  (on 2.6)
             */
            } else if (encoding.equals("ISO8859-1")) {
                enc = "ISO8859_1";
            } else if (encoding.equals("646")) {
                enc = "ASCII";
            } else {
                enc = CharacterEncoding.aliasName(encoding);
            }
        }
        if (enc == null) {
            enc = encoding;
        }

        try {
            return Class.forName(getConverterPackageName()
                                 + "." + converterPrefix[type] + enc);
        } catch(ClassNotFoundException e) {
            throw new UnsupportedEncodingException(enc);
        }

    }

    /**
     * Instantiate the given converter class, or throw an
     * UnsupportedEncodingException if it cannot be instantiated
     */
    private static Object newConverter(String enc, Class<?> c)
        throws UnsupportedEncodingException
    {
        try {
            return c.newInstance();
        } catch(InstantiationException e) {
            throw new UnsupportedEncodingException(enc);
        } catch(IllegalAccessException e) {
            throw new UnsupportedEncodingException(enc);
        }
    }

    /**
     * Create a converter object that implements the given type of converter
     * for the given encoding, or throw an UnsupportedEncodingException if no
     * appropriate converter class can be found and instantiated
     */
    static Object newConverter(int type, String enc)
        throws UnsupportedEncodingException
    {
        Class<?> c;
        synchronized (lock) {
            c = cache(type, enc);
            if (c == null) {
                c = getConverterClass(type, enc);
                if (!c.getName().equals("sun.io.CharToByteUTF8"))
                    cache(type, enc, c);
            }
        }
        return newConverter(enc, c);
    }

    /**
     * Find the class that implements the given type of converter for the
     * default encoding.  If the default encoding cannot be determined or is
     * not yet defined, return a class that implements the fallback default
     * encoding, which is just ISO 8859-1.
     */
    private static Class<?> getDefaultConverterClass(int type) {
        boolean fillCache = false;
        Class<?> c;

        /* First check the class cache */
        c = cache(type, DEFAULT_NAME);
        if (c != null)
            return c;

        /* Determine the encoding name */
        String enc = getDefaultEncodingName();
        if (enc != null) {
            /* file.encoding has been set, so cache the converter class */
            fillCache = true;
        } else {
            /* file.encoding has not been set, so use a default encoding which
               will not be cached */
            enc = "ISO8859_1";
        }

        /* We have an encoding name; try to find its class */
        try {
            c = getConverterClass(type, enc);
            if (fillCache) {
                cache(type, DEFAULT_NAME, c);
            }
        } catch (UnsupportedEncodingException x) {
            /* Can't find the default class, so fall back to ISO 8859-1 */
            try {
                c = getConverterClass(type, "ISO8859_1");
            } catch (UnsupportedEncodingException y) {
                throw new InternalError("Cannot find default "
                                        + converterPrefix[type]
                                        + " converter class");
            }
        }
        return c;

    }

    /**
     * Create a converter object that implements the given type of converter
     * for the default encoding, falling back to ISO 8859-1 if the default
     * encoding cannot be determined.
     */
    static Object newDefaultConverter(int type) {
        Class<?> c;
        synchronized (lock) {
            c = getDefaultConverterClass(type);
        }
        try {
            return newConverter("", c);
        } catch (UnsupportedEncodingException x) {
            throw new InternalError("Cannot instantiate default converter"
                                    + " class " + c.getName());
        }
    }

}
