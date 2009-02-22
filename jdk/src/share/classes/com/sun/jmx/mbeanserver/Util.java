/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.mbeanserver;

import com.sun.jmx.defaults.JmxProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Level;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.loading.ClassLoaderRepository;
import static javax.management.namespace.JMXNamespaces.NAMESPACE_SEPARATOR;

public class Util {
    private final static int NAMESPACE_SEPARATOR_LENGTH =
            NAMESPACE_SEPARATOR.length();
    public final static char[] ILLEGAL_MBEANSERVER_NAME_CHARS=";:*?".
            toCharArray();


    static <K, V> Map<K, V> newMap() {
        return new HashMap<K, V>();
    }

    static <K, V> Map<K, V> newSynchronizedMap() {
        return Collections.synchronizedMap(Util.<K, V>newMap());
    }

    static <K, V> IdentityHashMap<K, V> newIdentityHashMap() {
        return new IdentityHashMap<K, V>();
    }

    static <K, V> Map<K, V> newSynchronizedIdentityHashMap() {
        Map<K, V> map = newIdentityHashMap();
        return Collections.synchronizedMap(map);
    }

    static <K, V> SortedMap<K, V> newSortedMap() {
        return new TreeMap<K, V>();
    }

    static <K, V> SortedMap<K, V> newSortedMap(Comparator<? super K> comp) {
        return new TreeMap<K, V>(comp);
    }

    static <K, V> Map<K, V> newInsertionOrderMap() {
        return new LinkedHashMap<K, V>();
    }

    static <K, V> WeakHashMap<K, V> newWeakHashMap() {
        return new WeakHashMap<K, V>();
    }

    static <E> Set<E> newSet() {
        return new HashSet<E>();
    }

    static <E> Set<E> newSet(Collection<E> c) {
        return new HashSet<E>(c);
    }

    static <E> List<E> newList() {
        return new ArrayList<E>();
    }

    static <E> List<E> newList(Collection<E> c) {
        return new ArrayList<E>(c);
    }

    /* This method can be used by code that is deliberately violating the
     * allowed checked casts.  Rather than marking the whole method containing
     * the code with @SuppressWarnings, you can use a call to this method for
     * the exact place where you need to escape the constraints.  Typically
     * you will "import static" this method and then write either
     *    X x = cast(y);
     * or, if that doesn't work (e.g. X is a type variable)
     *    Util.<X>cast(y);
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object x) {
        return (T) x;
    }

    /**
     * Computes a descriptor hashcode from its names and values.
     * @param names  the sorted array of descriptor names.
     * @param values the array of descriptor values.
     * @return a hash code value, as described in {@link #hashCode(Descriptor)}
     */
    public static int hashCode(String[] names, Object[] values) {
        int hash = 0;
        for (int i = 0; i < names.length; i++) {
            Object v = values[i];
            int h;
            if (v == null) {
                h = 0;
            } else if (v instanceof Object[]) {
                h = Arrays.deepHashCode((Object[]) v);
            } else if (v.getClass().isArray()) {
                h = Arrays.deepHashCode(new Object[]{v}) - 31;
            // hashcode of a list containing just v is
            // v.hashCode() + 31, see List.hashCode()
            } else {
                h = v.hashCode();
            }
            hash += names[i].toLowerCase().hashCode() ^ h;
        }
        return hash;
    }

    /** Match a part of a string against a shell-style pattern.
        The only pattern characters recognized are <code>?</code>,
        standing for any one character,
        and <code>*</code>, standing for any string of
        characters, including the empty string. For instance,
        {@code wildmatch("sandwich","sa?d*ch",1,4,1,4)} will match
        {@code "and"} against {@code "a?d"}.

        @param str  the string containing the sequence to match.
        @param pat  a string containing a pattern to match the sub string
                    against.
        @param stri   the index in the string at which matching should begin.
        @param strend the index in the string at which the matching should
                      end.
        @param pati   the index in the pattern at which matching should begin.
        @param patend the index in the pattern at which the matching should
                      end.

        @return true if and only if the string matches the pattern.
    */
    /* The algorithm is a classical one.  We advance pointers in
       parallel through str and pat.  If we encounter a star in pat,
       we remember its position and continue advancing.  If at any
       stage we get a mismatch between str and pat, we look to see if
       there is a remembered star.  If not, we fail.  If so, we
       retreat pat to just past that star and str to the position
       after the last one we tried, and we let the match advance
       again.

       Even though there is only one remembered star position, the
       algorithm works when there are several stars in the pattern.
       When we encounter the second star, we forget the first one.
       This is OK, because if we get to the second star in A*B*C
       (where A etc are arbitrary strings), we have already seen AXB.
       We're therefore setting up a match of *C against the remainder
       of the string, which will match if that remainder looks like
       YC, so the whole string looks like AXBYC.
    */
    private static boolean wildmatch(final String str, final String pat,
            int stri, final int strend, int pati, final int patend) {

        // System.out.println("matching "+pat.substring(pati,patend)+
        //        " against "+str.substring(stri, strend));
        int starstri; // index for backtrack if "*" attempt fails
        int starpati; // index for backtrack if "*" attempt fails, +1

        starstri = starpati = -1;

        /* On each pass through this loop, we either advance pati,
           or we backtrack pati and advance starstri.  Since starstri
           is only ever assigned from pati, the loop must terminate.  */
        while (true) {
            if (pati < patend) {
                final char patc = pat.charAt(pati);
                switch (patc) {
                case '?':
                    if (stri == strend)
                        break;
                    stri++;
                    pati++;
                    continue;
                case '*':
                    pati++;
                    starpati = pati;
                    starstri = stri;
                    continue;
                default:
                    if (stri < strend && str.charAt(stri) == patc) {
                        stri++;
                        pati++;
                        continue;
                    }
                    break;
                }
            } else if (stri == strend)
                return true;

            // Mismatched, can we backtrack to a "*"?
            if (starpati < 0 || starstri == strend)
                return false;

            // Retry the match one position later in str
            pati = starpati;
            starstri++;
            stri = starstri;
        }
    }

    /** Match a string against a shell-style pattern.  The only pattern
        characters recognized are <code>?</code>, standing for any one
        character, and <code>*</code>, standing for any string of
        characters, including the empty string.

        @param str the string to match.
        @param pat the pattern to match the string against.

        @return true if and only if the string matches the pattern.
    */
    public static boolean wildmatch(String str, String pat) {
        return wildmatch(str,pat,0,str.length(),0,pat.length());
    }

    /**
     * Matches a string against a pattern, as a name space path.
     * This is a special matching where * and ?? don't match //.
     * The string is split in sub-strings separated by //, and the
     * pattern is split in sub-patterns separated by //. Each sub-string
     * is matched against its corresponding sub-pattern.
     * so <elt-1>//<elt2>//...//<elt-n> matches <pat-1>//<pat-2>//...//<pat-q>
     * only if n==q and for ( i = 1 => n) elt-i matches pat-i.
     *
     * In addition, if we encounter a pattern element which is exactly
     * **, it can match any number of path-elements - but it must match at
     * least one element.
     * When we encounter such a meta-wildcard, we remember its position
     * and the position in the string path, and we advance both the pattern
     * and the string. Later, if we encounter a mismatch in pattern & string,
     * we rewind the position in pattern to just after the meta-wildcard,
     * and we backtrack the string to i+1 element after the position
     * we had when we first encountered the meta-wildcard, i being the
     * position when we last backtracked the string.
     *
     * The backtracking logic is an adaptation of the logic in wildmatch
     * above.
     * See test/javax/mangement/ObjectName/ApplyWildcardTest.java
     *
     * Note: this thing is called 'wild' - and that's for a reason ;-)
     **/
    public static boolean wildpathmatch(String str, String pat) {
        final int strlen = str.length();
        final int patlen = pat.length();
        int stri = 0;
        int pati = 0;

        int starstri; // index for backtrack if "**" attempt fails
        int starpati; // index for backtrack if "**" attempt fails

        starstri = starpati = -1;

        while (true) {
            // System.out.println("pati="+pati+", stri="+stri);
            final int strend = str.indexOf(NAMESPACE_SEPARATOR, stri);
            final int patend = pat.indexOf(NAMESPACE_SEPARATOR, pati);

            // no // remaining in either string or pattern: simple wildmatch
            // until end of string.
            if (strend == -1 && patend == -1) {
                // System.out.println("last sub pattern, last sub element...");
                // System.out.println("wildmatch("+str.substring(stri,strlen)+
                //    ","+pat.substring(pati,patlen)+")");
                return wildmatch(str,pat,stri,strlen,pati,patlen);
            }

            // no // remaining in string, but at least one remaining in
            // pattern
            // => no match
            if (strend == -1) {
                // System.out.println("pattern has more // than string...");
                return false;
            }

            // strend is != -1, but patend might.
            // detect wildcard **
            if (patend == pati+2 && pat.charAt(pati)=='*' &&
                    pat.charAt(pati+1)=='*') {
                // if we reach here we know that neither strend nor patend are
                // equals to -1.
                stri     = strend + NAMESPACE_SEPARATOR_LENGTH;
                pati     = patend + NAMESPACE_SEPARATOR_LENGTH;
                starpati = pati; // position just after **// in pattern
                starstri = stri; // we eat 1 element in string, and remember
                                 // the position for backtracking and eating
                                 // one more element if needed.
                // System.out.println("starpati="+pati);
                continue;
            }

            // This is a bit hacky: * can match // when // is at the end
            // of the string, so we include the // delimiter in the pattern
            // matching. Either we're in the middle of the path, so including
            // // both at the end of the pattern and at the end of the string
            // has no effect - match(*//,dfsd//) is equivalent to match(*,dfsd)
            // or we're at the end of the pattern path, in which case
            // including // at the end of the string will have the desired
            // effect (provided that we detect the end of matching correctly,
            // see further on).
            //
            final int endpat =
                    ((patend > -1)?patend+NAMESPACE_SEPARATOR_LENGTH:patlen);
            final int endstr =
                    ((strend > -1)?strend+NAMESPACE_SEPARATOR_LENGTH:strlen);

            // if we reach the end of the pattern, or if elt-i & pat-i
            // don't match, we have a mismatch.

            // Note: we know that strend != -1, therefore patend==-1
            //       indicates a mismatch unless pattern can match
            //       a // at the end, and strend+2=strlen.
            // System.out.println("wildmatch("+str.substring(stri,endstr)+","+
            //        pat.substring(pati,endpat)+")");
            if (!wildmatch(str,pat,stri,endstr,pati,endpat)) {

                // System.out.println("nomatch");
                // if we have a mismatch and didn't encounter any meta-wildcard,
                // we return false. String & pattern don't match.
                if (starpati < 0) return false;

                // If we reach here, we had a meta-wildcard.
                // We need to backtrack to the wildcard, and make it eat an
                // additional string element.
                //
                stri = str.indexOf(NAMESPACE_SEPARATOR, starstri);
                // System.out.println("eating one additional element? "+stri);

                // If there's no more elements to eat, string and pattern
                // don't match => return false.
                if (stri == -1) return false;

                // Backtrack to where we were when we last matched against
                // the meta-wildcard, make it eat an additional path element,
                // remember the new positions, and continue from there...
                //
                stri = stri + NAMESPACE_SEPARATOR_LENGTH;
                starstri = stri;
                pati = starpati;
                // System.out.println("skiping to stri="+stri);
                continue;
            }

            // Here we know that strend > -1 but we can have patend == -1.
            //
            // So if we reach here, we know pat-i+//? has matched
            // elt-i+//
            //
            // If patend==-1, we know that there was no delimiter
            // at the end of the pattern, that we are at the last pattern,
            // and therefore that pat-i has matched elt-i+//
            //
            // In that case we can consider that we have a match only if
            // elt-i is also the last path element in the string, which is
            // equivalent to saying that strend+2==strlen.
            //
            if (patend == -1 && starpati == -1)
                return (strend+NAMESPACE_SEPARATOR_LENGTH==strlen);

            // patend != -1, or starpati > -1 so there remains something
            // to match.

            // go to next pair: elt-(i+1) pat-(i+1);
            stri = strend + NAMESPACE_SEPARATOR_LENGTH;
            pati = (patend==-1)?pati:(patend + NAMESPACE_SEPARATOR_LENGTH);
        }
    }

    /**
     * Returns true if the ObjectName's {@code domain} is selected by the
     * given {@code pattern}.
     */
    public static boolean isDomainSelected(String domain, String pattern) {
        if  (domain == null || pattern == null)
            throw new IllegalArgumentException("null");
        return Util.wildpathmatch(domain,pattern);
    }

    /**
     * Filters a set of ObjectName according to a given pattern.
     *
     * @param pattern the pattern that the returned names must match.
     * @param all     the set of names to filter.
     * @return a set of ObjectName from which non matching names
     *         have been removed.
     */
    public static Set<ObjectName> filterMatchingNames(ObjectName pattern,
                                        Set<ObjectName> all) {
        // If no pattern, just return all names
        if (pattern == null
                || all.isEmpty()
                || ObjectName.WILDCARD.equals(pattern))
            return all;

        // If there's a pattern, do the matching.
        final Set<ObjectName> res = equivalentEmptySet(all);
        for (ObjectName n : all) if (pattern.apply(n)) res.add(n);
        return res;
    }


    /**
     * Filters a set of ObjectInstance according to a given pattern.
     *
     * @param pattern the pattern that the returned names must match.
     * @param all     the set of instances to filter.
     * @return a set of ObjectInstance from which non matching instances
     *         have been removed.
     */
    public static Set<ObjectInstance>
            filterMatchingInstances(ObjectName pattern,
                                        Set<ObjectInstance> all) {
        // If no pattern, just return all names
        if (pattern == null
                || all.isEmpty()
                || ObjectName.WILDCARD.equals(pattern))
            return all;

        // If there's a pattern, do the matching.
        final Set<ObjectInstance> res = equivalentEmptySet(all);
        for (ObjectInstance n : all) {
            if (n == null) continue;
            if (pattern.apply(n.getObjectName()))
                res.add(n);
        }
        return res;
    }

    /**
     * An abstract ClassLoaderRepository that contains a single class loader.
     **/
    private final static class SingleClassLoaderRepository
            implements ClassLoaderRepository {
        private final ClassLoader singleLoader;

        SingleClassLoaderRepository(ClassLoader loader) {
            this.singleLoader = loader;
        }

        ClassLoader getSingleClassLoader() {
           return singleLoader;
        }

        private Class<?> loadClass(String className, ClassLoader loader)
                throws ClassNotFoundException {
            return Class.forName(className, false, loader);
        }

        public Class<?> loadClass(String className)
                throws ClassNotFoundException {
            return loadClass(className, getSingleClassLoader());
        }

        public Class<?> loadClassWithout(ClassLoader exclude,
                String className) throws ClassNotFoundException {
            final ClassLoader loader = getSingleClassLoader();
            if (exclude != null && exclude.equals(loader))
                throw new ClassNotFoundException(className);
            return loadClass(className, loader);
        }

        public Class<?> loadClassBefore(ClassLoader stop, String className)
                throws ClassNotFoundException {
            return loadClassWithout(stop, className);
        }
    }

    /**
     * Returns a ClassLoaderRepository that contains a single class loader.
     * @param loader the class loader contained in the returned repository.
     * @return a ClassLoaderRepository that contains the single loader.
     */
    public static ClassLoaderRepository getSingleClassLoaderRepository(
            final ClassLoader loader) {
        return new SingleClassLoaderRepository(loader);
    }

    /**
     * Returns the name of the given MBeanServer that should be put in a
     * permission you need.
     * This corresponds to the
     * {@code *[;mbeanServerName=<mbeanServerName>[;*]]} property
     * embedded in the MBeanServerId attribute of the
     * server's {@link MBeanServerDelegate}.
     *
     * @param server The MBean server
     * @return the name of the MBeanServer, or "*" if the name couldn't be
     *         obtained, or {@value MBeanServerFactory#DEFAULT_MBEANSERVER_NAME}
     *         if there was no name.
     */
    public static String getMBeanServerSecurityName(MBeanServer server) {
        final String notfound = "*";
        try {
            final String mbeanServerId = (String)
                    server.getAttribute(MBeanServerDelegate.DELEGATE_NAME,
                    "MBeanServerId");
            final String found = extractMBeanServerName(mbeanServerId);
            if (found.length()==0)
                return MBeanServerFactory.DEFAULT_MBEANSERVER_NAME;
            return found;
        } catch (Exception x) {
            logshort("Failed to retrieve MBeanServerName for server, " +
                    "using \"*\"",x);
            return notfound;
        }
    }

    /**
     * Returns the name of the MBeanServer embedded in the given
     * mbeanServerId. If the given mbeanServerId doesn't contain any name,
     * an empty String is returned.
     * The MBeanServerId is expected to be of the form:
     * {@code *[;mbeanServerName=<mbeanServerName>[;*]]}
     * @param mbeanServerId The MBean server ID
     * @return the name of the MBeanServer if found, or "" if the name was
     *         not present in the mbeanServerId.
     */
    public static String extractMBeanServerName(String mbeanServerId) {
        if (mbeanServerId==null) return "";
        final String beginMarker=";mbeanServerName=";
        final String endMarker=";";
        final int found = mbeanServerId.indexOf(beginMarker);
        if (found < 0) return "";
        final int start = found + beginMarker.length();
        final int stop = mbeanServerId.indexOf(endMarker, start);
        return mbeanServerId.substring(start,
                (stop < 0 ? mbeanServerId.length() : stop));
    }

    /**
     * Insert the given mbeanServerName into the given mbeanServerId.
     * If mbeanServerName is null, empty, or equals to "-", the returned
     * mbeanServerId will not contain any mbeanServerName.
     * @param mbeanServerId    The mbeanServerId in which to insert
     *                         mbeanServerName
     * @param mbeanServerName  The mbeanServerName
     * @return an mbeanServerId containing the given mbeanServerName
     * @throws IllegalArgumentException if mbeanServerId already contains
     *         a different name, or if the given mbeanServerName is not valid.
     */
    public static String insertMBeanServerName(String mbeanServerId,
            String mbeanServerName) {
        final String found = extractMBeanServerName(mbeanServerId);
        if (found.length() > 0 &&
                found.equals(checkServerName(mbeanServerName)))
            return mbeanServerId;
        if (found.length() > 0 && !isMBeanServerNameUndefined(found))
            throw new IllegalArgumentException(
                    "MBeanServerName already defined");
        if (isMBeanServerNameUndefined(mbeanServerName))
            return mbeanServerId;
        final String beginMarker=";mbeanServerName=";
        return mbeanServerId+beginMarker+checkServerName(mbeanServerName);
    }

    /**
     * Returns true if the given mbeanServerName corresponds to an
     * undefined MBeanServerName.
     * The mbeanServerName is considered undefined if it is one of:
     * {@code null} or {@value MBeanServerFactory#DEFAULT_MBEANSERVER_NAME}.
     * @param mbeanServerName The mbeanServerName, as returned by
     *        {@link #extractMBeanServerName(String)}.
     * @return true if the given name corresponds to one of the forms that
     *         denotes an undefined MBeanServerName.
     */
    public static boolean isMBeanServerNameUndefined(String mbeanServerName) {
        return mbeanServerName == null ||
           MBeanServerFactory.DEFAULT_MBEANSERVER_NAME.equals(mbeanServerName);
    }
    /**
     * Check that the provided mbeanServername is syntactically valid.
     * @param mbeanServerName An mbeanServerName, or {@code null}.
     * @return mbeanServerName, or {@value
     * MBeanServerFactory#DEFAULT_MBEANSERVER_NAME} if {@code mbeanServerName}
     * is {@code null}.
     * @throws IllegalArgumentException if mbeanServerName contains illegal
     *         characters, or is empty, or is {@code "-"}.
     *         Illegal characters are {@link #ILLEGAL_MBEANSERVER_NAME_CHARS}.
     */
    public static String checkServerName(String mbeanServerName) {
        if ("".equals(mbeanServerName))
            throw new IllegalArgumentException(
                    "\"\" is not a valid MBean server name");
        if ("-".equals(mbeanServerName))
            throw new IllegalArgumentException(
                    "\"-\" is not a valid MBean server name");
        if (isMBeanServerNameUndefined(mbeanServerName))
            return MBeanServerFactory.DEFAULT_MBEANSERVER_NAME;
        for (char c : ILLEGAL_MBEANSERVER_NAME_CHARS) {
            if (mbeanServerName.indexOf(c) >= 0)
                throw new IllegalArgumentException(
                        "invalid character in MBeanServer name: "+c);
        }
        return mbeanServerName;
    }

    /**
     * Get the MBeanServer name that should be put in a permission you need.
     *
     * @param delegate The MBeanServerDelegate
     * @return The MBeanServer name - or {@value
     * MBeanServerFactory#DEFAULT_MBEANSERVER_NAME} if there was no name.
     */
    public static String getMBeanServerSecurityName(
            MBeanServerDelegate delegate) {
        try {
            final String serverName = delegate.getMBeanServerName();
            if (isMBeanServerNameUndefined(serverName))
                return MBeanServerFactory.DEFAULT_MBEANSERVER_NAME;
            return serverName;
        } catch (Exception x) {
            logshort("Failed to retrieve MBeanServerName from delegate, " +
                    "using \"*\"",x);
            return "*";
        }
    }

    // Log the exception and its causes without logging the stack trace.
    // Use with care - it is usually preferable to log the whole stack trace!
    // We don't want to log the whole stack trace here: logshort() is
    // called in those cases where the exception might not be abnormal.
    private static void logshort(String msg, Throwable t) {
        if (JmxProperties.MISC_LOGGER.isLoggable(Level.FINE)) {
            StringBuilder toprint = new StringBuilder(msg);
            do {
                toprint.append("\nCaused By: ").append(String.valueOf(t));
            } while ((t=t.getCause())!=null);
            JmxProperties.MISC_LOGGER.fine(toprint.toString());
       }
    }

    public static <T> Set<T> cloneSet(Set<T> set) {
        if (set instanceof SortedSet<?>) {
            @SuppressWarnings("unchecked")
            SortedSet<T> sset = (SortedSet<T>) set;
            set = new TreeSet<T>(sset.comparator());
            set.addAll(sset);
        } else
            set = new HashSet<T>(set);
        return set;
    }

    public static <T> Set<T> equivalentEmptySet(Set<T> set) {
        if (set instanceof SortedSet<?>) {
            @SuppressWarnings("unchecked")
            SortedSet<T> sset = (SortedSet<T>) set;
            set = new TreeSet<T>(sset.comparator());
        } else
            set = new HashSet<T>();
        return set;
    }

    // This exception is used when wrapping a class that throws IOException
    // in a class that doesn't.
    // The typical example for this are JMXNamespaces, when the sub
    // MBeanServer can be remote.
    //
    public static RuntimeException newRuntimeIOException(IOException io) {
        final String msg = "Communication failed with underlying resource: "+
                io.getMessage();
        return new RuntimeException(msg,io);
    }
}
