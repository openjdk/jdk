/*
 * Copyright 1995-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*-
 *      news stream opener
 */

package sun.net.www;

import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** An RFC 844 or MIME message header.  Includes methods
    for parsing headers from incoming streams, fetching
    values, setting values, and printing headers.
    Key values of null are legal: they indicate lines in
    the header that don't have a valid key, but do have
    a value (this isn't legal according to the standard,
    but lines like this are everywhere). */
public
class MessageHeader {
    private String keys[];
    private String values[];
    private int nkeys;

    public MessageHeader () {
        grow();
    }

    public MessageHeader (InputStream is) throws java.io.IOException {
        parseHeader(is);
    }

    /**
     * Reset a message header (all key/values removed)
     */
    public synchronized void reset() {
        keys = null;
        values = null;
        nkeys = 0;
        grow();
    }

    /**
     * Find the value that corresponds to this key.
     * It finds only the first occurrence of the key.
     * @param k the key to find.
     * @return null if not found.
     */
    public synchronized String findValue(String k) {
        if (k == null) {
            for (int i = nkeys; --i >= 0;)
                if (keys[i] == null)
                    return values[i];
        } else
            for (int i = nkeys; --i >= 0;) {
                if (k.equalsIgnoreCase(keys[i]))
                    return values[i];
            }
        return null;
    }

    // return the location of the key
    public synchronized int getKey(String k) {
        for (int i = nkeys; --i >= 0;)
            if ((keys[i] == k) ||
                (k != null && k.equalsIgnoreCase(keys[i])))
                return i;
        return -1;
    }

    public synchronized String getKey(int n) {
        if (n < 0 || n >= nkeys) return null;
        return keys[n];
    }

    public synchronized String getValue(int n) {
        if (n < 0 || n >= nkeys) return null;
        return values[n];
    }

    /** Deprecated: Use multiValueIterator() instead.
     *
     *  Find the next value that corresponds to this key.
     *  It finds the first value that follows v. To iterate
     *  over all the values of a key use:
     *  <pre>
     *          for(String v=h.findValue(k); v!=null; v=h.findNextValue(k, v)) {
     *              ...
     *          }
     *  </pre>
     */
    public synchronized String findNextValue(String k, String v) {
        boolean foundV = false;
        if (k == null) {
            for (int i = nkeys; --i >= 0;)
                if (keys[i] == null)
                    if (foundV)
                        return values[i];
                    else if (values[i] == v)
                        foundV = true;
        } else
            for (int i = nkeys; --i >= 0;)
                if (k.equalsIgnoreCase(keys[i]))
                    if (foundV)
                        return values[i];
                    else if (values[i] == v)
                        foundV = true;
        return null;
    }

    class HeaderIterator implements Iterator {
        int index = 0;
        int next = -1;
        String key;
        boolean haveNext = false;
        Object lock;

        public HeaderIterator (String k, Object lock) {
            key = k;
            this.lock = lock;
        }
        public boolean hasNext () {
            synchronized (lock) {
                if (haveNext) {
                    return true;
                }
                while (index < nkeys) {
                    if (key.equalsIgnoreCase (keys[index])) {
                        haveNext = true;
                        next = index++;
                        return true;
                    }
                    index ++;
                }
                return false;
            }
        }
        public Object next() {
            synchronized (lock) {
                if (haveNext) {
                    haveNext = false;
                    return values [next];
                }
                if (hasNext()) {
                    return next();
                } else {
                    throw new NoSuchElementException ("No more elements");
                }
            }
        }
        public void remove () {
            throw new UnsupportedOperationException ("remove not allowed");
        }
    }

    /**
     * return an Iterator that returns all values of a particular
     * key in sequence
     */
    public Iterator multiValueIterator (String k) {
        return new HeaderIterator (k, this);
    }

    public synchronized Map getHeaders() {
        return getHeaders(null);
    }

    public synchronized Map getHeaders(String[] excludeList) {
        boolean skipIt = false;
        Map m = new HashMap();
        for (int i = nkeys; --i >= 0;) {
            if (excludeList != null) {
                // check if the key is in the excludeList.
                // if so, don't include it in the Map.
                for (int j = 0; j < excludeList.length; j++) {
                    if ((excludeList[j] != null) &&
                        (excludeList[j].equalsIgnoreCase(keys[i]))) {
                        skipIt = true;
                        break;
                    }
                }
            }
            if (!skipIt) {
                List l = (List)m.get(keys[i]);
                if (l == null) {
                    l = new ArrayList();
                    m.put(keys[i], l);
                }
                l.add(values[i]);
            } else {
                // reset the flag
                skipIt = false;
            }
        }

        Set keySet = m.keySet();
        for (Iterator i = keySet.iterator(); i.hasNext();) {
            Object key = i.next();
            List l = (List)m.get(key);
            m.put(key, Collections.unmodifiableList(l));
        }

        return Collections.unmodifiableMap(m);
    }

    /** Prints the key-value pairs represented by this
        header.  Also prints the RFC required blank line
        at the end. Omits pairs with a null key. */
    public synchronized void print(PrintStream p) {
        for (int i = 0; i < nkeys; i++)
            if (keys[i] != null) {
                p.print(keys[i] +
                    (values[i] != null ? ": "+values[i]: "") + "\r\n");
            }
        p.print("\r\n");
        p.flush();
    }

    /** Adds a key value pair to the end of the
        header.  Duplicates are allowed */
    public synchronized void add(String k, String v) {
        grow();
        keys[nkeys] = k;
        values[nkeys] = v;
        nkeys++;
    }

    /** Prepends a key value pair to the beginning of the
        header.  Duplicates are allowed */
    public synchronized void prepend(String k, String v) {
        grow();
        for (int i = nkeys; i > 0; i--) {
            keys[i] = keys[i-1];
            values[i] = values[i-1];
        }
        keys[0] = k;
        values[0] = v;
        nkeys++;
    }

    /** Overwrite the previous key/val pair at location 'i'
     * with the new k/v.  If the index didn't exist before
     * the key/val is simply tacked onto the end.
     */

    public synchronized void set(int i, String k, String v) {
        grow();
        if (i < 0) {
            return;
        } else if (i >= nkeys) {
            add(k, v);
        } else {
            keys[i] = k;
            values[i] = v;
        }
    }


    /** grow the key/value arrays as needed */

    private void grow() {
        if (keys == null || nkeys >= keys.length) {
            String[] nk = new String[nkeys + 4];
            String[] nv = new String[nkeys + 4];
            if (keys != null)
                System.arraycopy(keys, 0, nk, 0, nkeys);
            if (values != null)
                System.arraycopy(values, 0, nv, 0, nkeys);
            keys = nk;
            values = nv;
        }
    }

    /**
     * Remove the key from the header. If there are multiple values under
     * the same key, they are all removed.
     * Nothing is done if the key doesn't exist.
     * After a remove, the other pairs' order are not changed.
     * @param k the key to remove
     */
    public synchronized void remove(String k) {
        if(k == null) {
            for (int i = 0; i < nkeys; i++) {
                while (keys[i] == null && i < nkeys) {
                    for(int j=i; j<nkeys-1; j++) {
                        keys[j] = keys[j+1];
                        values[j] = values[j+1];
                    }
                    nkeys--;
                }
            }
        } else {
            for (int i = 0; i < nkeys; i++) {
                while (k.equalsIgnoreCase(keys[i]) && i < nkeys) {
                    for(int j=i; j<nkeys-1; j++) {
                        keys[j] = keys[j+1];
                        values[j] = values[j+1];
                    }
                    nkeys--;
                }
            }
        }
    }

    /** Sets the value of a key.  If the key already
        exists in the header, it's value will be
        changed.  Otherwise a new key/value pair will
        be added to the end of the header. */
    public synchronized void set(String k, String v) {
        for (int i = nkeys; --i >= 0;)
            if (k.equalsIgnoreCase(keys[i])) {
                values[i] = v;
                return;
            }
        add(k, v);
    }

    /** Set's the value of a key only if there is no
     *  key with that value already.
     */

    public synchronized void setIfNotSet(String k, String v) {
        if (findValue(k) == null) {
            add(k, v);
        }
    }

    /** Convert a message-id string to canonical form (strips off
        leading and trailing <>s) */
    public static String canonicalID(String id) {
        if (id == null)
            return "";
        int st = 0;
        int len = id.length();
        boolean substr = false;
        int c;
        while (st < len && ((c = id.charAt(st)) == '<' ||
                            c <= ' ')) {
            st++;
            substr = true;
        }
        while (st < len && ((c = id.charAt(len - 1)) == '>' ||
                            c <= ' ')) {
            len--;
            substr = true;
        }
        return substr ? id.substring(st, len) : id;
    }

    /** Parse a MIME header from an input stream. */
    public void parseHeader(InputStream is) throws java.io.IOException {
        synchronized (this) {
            nkeys = 0;
        }
        mergeHeader(is);
    }

    /** Parse and merge a MIME header from an input stream. */
    public void mergeHeader(InputStream is) throws java.io.IOException {
        if (is == null)
            return;
        char s[] = new char[10];
        int firstc = is.read();
        while (firstc != '\n' && firstc != '\r' && firstc >= 0) {
            int len = 0;
            int keyend = -1;
            int c;
            boolean inKey = firstc > ' ';
            s[len++] = (char) firstc;
    parseloop:{
                while ((c = is.read()) >= 0) {
                    switch (c) {
                      case ':':
                        if (inKey && len > 0)
                            keyend = len;
                        inKey = false;
                        break;
                      case '\t':
                        c = ' ';
                      case ' ':
                        inKey = false;
                        break;
                      case '\r':
                      case '\n':
                        firstc = is.read();
                        if (c == '\r' && firstc == '\n') {
                            firstc = is.read();
                            if (firstc == '\r')
                                firstc = is.read();
                        }
                        if (firstc == '\n' || firstc == '\r' || firstc > ' ')
                            break parseloop;
                        /* continuation */
                        c = ' ';
                        break;
                    }
                    if (len >= s.length) {
                        char ns[] = new char[s.length * 2];
                        System.arraycopy(s, 0, ns, 0, len);
                        s = ns;
                    }
                    s[len++] = (char) c;
                }
                firstc = -1;
            }
            while (len > 0 && s[len - 1] <= ' ')
                len--;
            String k;
            if (keyend <= 0) {
                k = null;
                keyend = 0;
            } else {
                k = String.copyValueOf(s, 0, keyend);
                if (keyend < len && s[keyend] == ':')
                    keyend++;
                while (keyend < len && s[keyend] <= ' ')
                    keyend++;
            }
            String v;
            if (keyend >= len)
                v = new String();
            else
                v = String.copyValueOf(s, keyend, len - keyend);
            add(k, v);
        }
    }

    public synchronized String toString() {
        String result = super.toString() + nkeys + " pairs: ";
        for (int i = 0; i < keys.length && i < nkeys; i++) {
            result += "{"+keys[i]+": "+values[i]+"}";
        }
        return result;
    }
}
