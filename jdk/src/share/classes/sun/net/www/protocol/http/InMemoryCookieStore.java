/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.www.protocol.http;

import java.net.URI;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A simple in-memory java.net.CookieStore implementation
 *
 * @author Edward Wang
 * @since 1.6
 */
public class InMemoryCookieStore implements CookieStore {
    // the in-memory representation of cookies
    private List<HttpCookie> cookieJar = null;

    // the cookies are indexed by its domain and associated uri (if present)
    // CAUTION: when a cookie removed from main data structure (i.e. cookieJar),
    //          it won't be cleared in domainIndex & uriIndex. Double-check the
    //          presence of cookie when retrieve one form index store.
    private Map<String, List<HttpCookie>> domainIndex = null;
    private Map<URI, List<HttpCookie>> uriIndex = null;

    // use ReentrantLock instead of syncronized for scalability
    private ReentrantLock lock = null;


    /**
     * The default ctor
     */
    public InMemoryCookieStore() {
        cookieJar = new ArrayList<HttpCookie>();
        domainIndex = new HashMap<String, List<HttpCookie>>();
        uriIndex = new HashMap<URI, List<HttpCookie>>();

        lock = new ReentrantLock(false);
    }

    /**
     * Add one cookie into cookie store.
     */
    public void add(URI uri, HttpCookie cookie) {
        // pre-condition : argument can't be null
        if (cookie == null) {
            throw new NullPointerException("cookie is null");
        }


        lock.lock();
        try {
            // remove the ole cookie if there has had one
            cookieJar.remove(cookie);

            // add new cookie if it has a non-zero max-age
            if (cookie.getMaxAge() != 0) {
                cookieJar.add(cookie);
                // and add it to domain index
                addIndex(domainIndex, cookie.getDomain(), cookie);
                // add it to uri index, too
                addIndex(uriIndex, getEffectiveURI(uri), cookie);
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * Get all cookies, which:
     *  1) given uri domain-matches with, or, associated with
     *     given uri when added to the cookie store.
     *  3) not expired.
     * See RFC 2965 sec. 3.3.4 for more detail.
     */
    public List<HttpCookie> get(URI uri) {
        // argument can't be null
        if (uri == null) {
            throw new NullPointerException("uri is null");
        }

        List<HttpCookie> cookies = new ArrayList<HttpCookie>();
        lock.lock();
        try {
            // check domainIndex first
            getInternal(cookies, domainIndex, new DomainComparator(uri.getHost()));
            // check uriIndex then
            getInternal(cookies, uriIndex, getEffectiveURI(uri));
        } finally {
            lock.unlock();
        }

        return cookies;
    }

    /**
     * Get all cookies in cookie store, except those have expired
     */
    public List<HttpCookie> getCookies() {
        List<HttpCookie> rt;

        lock.lock();
        try {
            Iterator<HttpCookie> it = cookieJar.iterator();
            while (it.hasNext()) {
                if (it.next().hasExpired()) {
                    it.remove();
                }
            }
        } finally {
            rt = Collections.unmodifiableList(cookieJar);
            lock.unlock();
        }

        return rt;
    }

    /**
     * Get all URIs, which are associated with at least one cookie
     * of this cookie store.
     */
    public List<URI> getURIs() {
        List<URI> uris = new ArrayList<URI>();

        lock.lock();
        try {
            Iterator<URI> it = uriIndex.keySet().iterator();
            while (it.hasNext()) {
                URI uri = it.next();
                List<HttpCookie> cookies = uriIndex.get(uri);
                if (cookies == null || cookies.size() == 0) {
                    // no cookies list or an empty list associated with
                    // this uri entry, delete it
                    it.remove();
                }
            }
        } finally {
            uris.addAll(uriIndex.keySet());
            lock.unlock();
        }

        return uris;
    }


    /**
     * Remove a cookie from store
     */
    public boolean remove(URI uri, HttpCookie ck) {
        // argument can't be null
        if (ck == null) {
            throw new NullPointerException("cookie is null");
        }

        boolean modified = false;
        lock.lock();
        try {
            modified = cookieJar.remove(ck);
        } finally {
            lock.unlock();
        }

        return modified;
    }


    /**
     * Remove all cookies in this cookie store.
     */
    public boolean removeAll() {
        lock.lock();
        try {
            cookieJar.clear();
            domainIndex.clear();
            uriIndex.clear();
        } finally {
            lock.unlock();
        }

        return true;
    }


    /* ---------------- Private operations -------------- */


    static class DomainComparator implements Comparable<String> {
        String host = null;

        public DomainComparator(String host) {
            this.host = host;
        }

        public int compareTo(String domain) {
            if (HttpCookie.domainMatches(domain, host)) {
                return 0;
            } else {
                return -1;
            }
        }
    }

    // @param cookies           [OUT] contains the found cookies
    // @param cookieIndex       the index
    // @param comparator        the prediction to decide whether or not
    //                          a cookie in index should be returned
    private <T> void getInternal(List<HttpCookie> cookies,
                                Map<T, List<HttpCookie>> cookieIndex,
                                Comparable<T> comparator)
    {
        for (T index : cookieIndex.keySet()) {
            if (comparator.compareTo(index) == 0) {
                List<HttpCookie> indexedCookies = cookieIndex.get(index);
                // check the list of cookies associated with this domain
                if (indexedCookies != null) {
                    Iterator<HttpCookie> it = indexedCookies.iterator();
                    while (it.hasNext()) {
                        HttpCookie ck = it.next();
                        if (cookieJar.indexOf(ck) != -1) {
                            // the cookie still in main cookie store
                            if (!ck.hasExpired()) {
                                // don't add twice
                                if (!cookies.contains(ck))
                                    cookies.add(ck);
                            } else {
                                it.remove();
                                cookieJar.remove(ck);
                            }
                        } else {
                            // the cookie has beed removed from main store,
                            // so also remove it from domain indexed store
                            it.remove();
                        }
                    }
                } // end of indexedCookies != null
            } // end of comparator.compareTo(index) == 0
        } // end of cookieIndex iteration
    }

    // add 'cookie' indexed by 'index' into 'indexStore'
    private <T> void addIndex(Map<T, List<HttpCookie>> indexStore,
                              T index,
                              HttpCookie cookie)
    {
        if (index != null) {
            List<HttpCookie> cookies = indexStore.get(index);
            if (cookies != null) {
                // there may already have the same cookie, so remove it first
                cookies.remove(cookie);

                cookies.add(cookie);
            } else {
                cookies = new ArrayList<HttpCookie>();
                cookies.add(cookie);
                indexStore.put(index, cookies);
            }
        }
    }


    //
    // for cookie purpose, the effective uri should only be scheme://authority
    // the path will be taken into account when path-match algorithm applied
    //
    private URI getEffectiveURI(URI uri) {
        URI effectiveURI = null;
        try {
            effectiveURI = new URI(uri.getScheme(),
                                   uri.getAuthority(),
                                   null,  // path component
                                   null,  // query component
                                   null   // fragment component
                                  );
        } catch (URISyntaxException ignored) {
            effectiveURI = uri;
        }

        return effectiveURI;
    }
}
