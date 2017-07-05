/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk;

import java.math.BigInteger;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A representation of the JDK version-string which contains a version
 * number optionally followed by pre-release and build information.
 *
 * <h2><a name="verNum">Version numbers</a></h2>
 *
 * A <em>version number</em>, {@code $VNUM}, is a non-empty sequence of
 * non-negative integer numerals, without leading or trailing zeroes,
 * separated by period characters (U+002E); i.e., it matches the regular
 * expression {@code ^[1-9][0-9]*(((\.0)*\.[1-9][0-9]*)*)*$}. The sequence may
 * be of arbitrary length but the first three elements are assigned specific
 * meanings, as follows:
 *
 * <blockquote><pre>
 *     $MAJOR.$MINOR.$SECURITY
 * </pre></blockquote>
 *
 * <ul>
 *
 * <li><p> <a name="major">{@code $MAJOR}</a> --- The major version number,
 * incremented for a major release that contains significant new features as
 * specified in a new edition of the Java&#160;SE Platform Specification,
 * <em>e.g.</em>, <a href="https://jcp.org/en/jsr/detail?id=337">JSR 337</a>
 * for Java&#160;SE&#160;8. Features may be removed in a major release, given
 * advance notice at least one major release ahead of time, and incompatible
 * changes may be made when justified. The {@code $MAJOR} version number of
 * JDK&#160;8 was {@code 8}; the {@code $MAJOR} version number of JDK&#160;9
 * is {@code 9}. </p></li>
 *
 * <li><p> <a name="minor">{@code $MINOR}</a> --- The minor version number,
 * incremented for a minor update release that may contain compatible bug
 * fixes, revisions to standard APIs mandated by a <a
 * href="https://jcp.org/en/procedures/jcp2#5.3">Maintenance Release</a> of
 * the relevant Platform Specification, and implementation features outside
 * the scope of that Specification such as new JDK-specific APIs, additional
 * service providers, new garbage collectors, and ports to new hardware
 * architectures. {@code $MINOR} is reset to zero when {@code $MAJOR} is
 * incremented. </p></li>
 *
 * <li><p> <a name="security">{@code $SECURITY}</a> --- The security level,
 * incremented for a security-update release that contains critical fixes
 * including those necessary to improve security. {@code $SECURITY} is reset
 * to zero <strong>only</strong> when {@code $MAJOR} is incremented. A higher
 * value of {@code $SECURITY} for a given {@code $MAJOR} value, therefore,
 * always indicates a more secure release, regardless of the value of {@code
 * $MINOR}. </p></li>
 *
 * </ul>
 *
 * <p> The fourth and later elements of a version number are free for use by
 * downstream consumers of the JDK code base.  Such a consumer may,
 * <em>e.g.</em>, use the fourth element to identify patch releases which
 * contain a small number of critical non-security fixes in addition to the
 * security fixes in the corresponding security release. </p>
 *
 * <p> The version number does not include trailing zero elements;
 * <em>i.e.</em>, {@code $SECURITY} is omitted if it has the value zero, and
 * {@code $MINOR} is omitted if both {@code $MINOR} and {@code $SECURITY} have
 * the value zero. </p>
 *
 * <p> The sequence of numerals in a version number is compared to another
 * such sequence in numerical, pointwise fashion; <em>e.g.</em>, {@code 9.9.1}
 * is less than {@code 9.10.0}. If one sequence is shorter than another then
 * the missing elements of the shorter sequence are considered to be zero;
 * <em>e.g.</em>, {@code 9.1.2} is equal to {@code 9.1.2.0} but less than
 * {@code 9.1.2.1}. </p>
 *
 * <h2><a name="verStr">Version strings</a></h2>
 *
 * <p> A <em>version string</em> {@code $VSTR} consists of a version number
 * {@code $VNUM}, as described above, optionally followed by pre-release and
 * build information, in the format </p>
 *
 * <blockquote><pre>
 *     $VNUM(-$PRE)?(\+($BUILD)?(-$OPT)?)?
 * </pre></blockquote>
 *
 * <p> where: </p>
 *
 * <ul>
 *
 * <li><p> <a name="pre">{@code $PRE}</a>, matching {@code ([a-zA-Z0-9]+)} ---
 * A pre-release identifier.  Typically {@code ea}, for an early-access
 * release that's under active development and potentially unstable, or {@code
 * internal}, for an internal developer build.
 *
 * <li><p> <a name="build">{@code $BUILD}</a>, matching {@code
 * (0|[1-9][0-9]*)} --- The build number, incremented for each promoted build.
 * {@code $BUILD} is reset to {@code 1} when any portion of {@code $VNUM} is
 * incremented. </p>
 *
 * <li><p> <a name="opt">{@code $OPT}</a>, matching {@code ([-a-zA-Z0-9\.]+)}
 * --- Additional build information, if desired.  In the case of an {@code
 * internal} build this will often contain the date and time of the
 * build. </p>
 *
 * </ul>
 *
 * <p> When comparing two version strings the value of {@code $OPT}, if
 * present, may or may not be significant depending on the chosen comparison
 * method.  The comparison methods {@link #compareTo(Version) compareTo()} and
 * {@link #compareToIgnoreOpt(Version) compareToIgnoreOpt{}} should be used
 * consistently with the corresponding methods {@link #equals(Object) equals()}
 * and {@link #equalsIgnoreOpt(Object) equalsIgnoreOpt()}.  </p>
 *
 * <p> A <em>short version string</em> ({@code $SVSTR}), often useful in less
 * formal contexts, is simply {@code $VNUM} optionally ended with {@code
 * -$PRE}. </p>
 *
 * @since  9
 */
public final class Version
    implements Comparable<Version>
{
    private final List<Integer>     version;
    private final Optional<String>  pre;
    private final Optional<Integer> build;
    private final Optional<String>  optional;

    private static Version current;

    // $VNUM(-$PRE)?(\+($BUILD)?(\-$OPT)?)?
    // RE limits the format of version strings
    // ([1-9][0-9]*(?:(?:\.0)*\.[1-9][0-9]*)*)(?:-([a-zA-Z0-9]+))?(?:(\+)(0|[1-9][0-9]*)?)?(?:-([-a-zA-Z0-9.]+))?

    private static final String VNUM
        = "(?<VNUM>[1-9][0-9]*(?:(?:\\.0)*\\.[1-9][0-9]*)*)";
    private static final String VNUM_GROUP  = "VNUM";

    private static final String PRE      = "(?:-(?<PRE>[a-zA-Z0-9]+))?";
    private static final String PRE_GROUP   = "PRE";

    private static final String BUILD
        = "(?:(?<PLUS>\\+)(?<BUILD>0|[1-9][0-9]*)?)?";
    private static final String PLUS_GROUP  = "PLUS";
    private static final String BUILD_GROUP = "BUILD";

    private static final String OPT      = "(?:-(?<OPT>[-a-zA-Z0-9.]+))?";
    private static final String OPT_GROUP   = "OPT";

    private static final String VSTR_FORMAT
        = "^" + VNUM + PRE + BUILD + OPT + "$";
    private static final Pattern VSTR_PATTERN = Pattern.compile(VSTR_FORMAT);

    /**
     * Constructs a valid JDK <a href="verStr">version string</a> containing a
     * <a href="#verNum">version number</a> followed by pre-release and build
     * information.
     *
     * @param  s
     *         A string to be interpreted as a version
     *
     * @throws  IllegalArgumentException
     *          If the given string cannot be interpreted a valid version
     *
     * @throws  NullPointerException
     *          If {@code s} is {@code null}
     *
     * @throws  NumberFormatException
     *          If an element of the version number or the build number cannot
     *          be represented as an {@link Integer}
     */
    private Version(String s) {
        if (s == null)
            throw new NullPointerException();

        Matcher m = VSTR_PATTERN.matcher(s);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid version string: '"
                                               + s + "'");

        // $VNUM is a dot-separated list of integers of arbitrary length
        List<Integer> list = new ArrayList<>();
        for (String i : m.group(VNUM_GROUP).split("\\."))
            list.add(Integer.parseInt(i));
        version = Collections.unmodifiableList(list);

        pre = Optional.ofNullable(m.group(PRE_GROUP));

        String b = m.group(BUILD_GROUP);
        // $BUILD is an integer
        build = (b == null)
             ? Optional.<Integer>empty()
             : Optional.ofNullable(Integer.parseInt(b));

        optional = Optional.ofNullable(m.group(OPT_GROUP));

        // empty '+'
        if ((m.group(PLUS_GROUP) != null) && !build.isPresent()) {
            if (optional.isPresent()) {
                if (pre.isPresent())
                    throw new IllegalArgumentException("'+' found with"
                        + " pre-release and optional components:'" + s + "'");
            } else {
                throw new IllegalArgumentException("'+' found with neither"
                    + " build or optional components: '" + s + "'");
            }
        }
    }

    /**
     * Parses the given string as a valid JDK <a
     * href="#verStr">version string</a> containing a <a
     * href="#verNum">version number</a> followed by pre-release and
     * build information.
     *
     * @param  s
     *         A string to interpret as a version
     *
     * @throws  IllegalArgumentException
     *          If the given string cannot be interpreted a valid version
     *
     * @throws  NullPointerException
     *          If the given string is {@code null}
     *
     * @throws  NumberFormatException
     *          If an element of the version number or the build number cannot
     *          be represented as an {@link Integer}
     *
     * @return  This version
     */
    public static Version parse(String s) {
        return new Version(s);
    }

    /**
     * Returns {@code System.getProperty("java.version")} as a Version.
     *
     * @throws  SecurityException
     *          If a security manager exists and its {@link
     *          SecurityManager#checkPropertyAccess(String)
     *          checkPropertyAccess} method does not allow access to the
     *          system property "java.version"
     *
     * @return  {@code System.getProperty("java.version")} as a Version
     */
    public static Version current() {
        if (current == null) {
            current = parse(AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public String run() {
                        return System.getProperty("java.version");
                    }
                }));
        }
        return current;
    }

    /**
     * Returns the <a href="#major">major</a> version number.
     *
     * @return  The major version number
     */
    public int major() {
        return version.get(0);
    }

    /**
     * Returns the <a href="#minor">minor</a> version number or zero if it was
     * not set.
     *
     * @return  The minor version number or zero if it was not set
     */
    public int minor() {
        return (version.size() > 1 ? version.get(1) : 0);
    }

    /**
     * Returns the <a href="#security">security</a> version number or zero if
     * it was not set.
     *
     * @return  The security version number or zero if it was not set
     */
    public int security() {
        return (version.size() > 2 ? version.get(2) : 0);
    }

    /**
     * Returns an unmodifiable {@link java.util.List List} of the
     * integer numerals contained in the <a href="#verNum">version
     * number</a>.  The {@code List} always contains at least one
     * element corresponding to the <a href="#major">major version
     * number</a>.
     *
     * @return  An unmodifiable list of the integer numerals
     *          contained in the version number
     */
    public List<Integer> version() {
        return version;
    }

    /**
     * Returns the optional <a href="#pre">pre-release</a> information.
     *
     * @return  The optional pre-release information as a String
     */
    public Optional<String> pre() {
        return pre;
    }

    /**
     * Returns the <a href="#build">build number</a>.
     *
     * @return The optional build number.
     */
    public Optional<Integer> build() {
        return build;
    }

    /**
     * Returns <a href="#opt">optional</a> additional identifying build
     * information.
     *
     * @return  Additional build information as a String
     */
    public Optional<String> optional() {
        return optional;
    }

    /**
     * Compares this version to another.
     *
     * <p> Each of the components in the <a href="#verStr">version</a> is
     * compared in the follow order of precedence: version numbers,
     * pre-release identifiers, build numbers, optional build information. </p>
     *
     * <p> Comparison begins by examining the sequence of version numbers.  If
     * one sequence is shorter than another, then the missing elements of the
     * shorter sequence are considered to be zero. </p>
     *
     * <p> A version with a pre-release identifier is always considered to be
     * less than a version without one.  Pre-release identifiers are compared
     * numerically when they consist only of digits, and lexicographically
     * otherwise.  Numeric identifiers are considered to be less than
     * non-numeric identifiers.  </p>
     *
     * <p> A version without a build number is always less than one with a
     * build number; otherwise build numbers are compared numerically. </p>
     *
     * <p> The optional build information is compared lexicographically.
     * During this comparison, a version with optional build information is
     * considered to be greater than a version without one. </p>
     *
     * <p> A version is not comparable to any other type of object.
     *
     * @param  ob
     *         The object to be compared
     *
     * @return  A negative integer, zero, or a positive integer if this
     *          {@code Version} is less than, equal to, or greater than the
     *          given {@code Version}
     *
     * @throws  NullPointerException
     *          If the given object is {@code null}
     */
    @Override
    public int compareTo(Version ob) {
        return compare(ob, false);
    }

    /**
     * Compares this version to another disregarding optional build
     * information.
     *
     * <p> Two versions are compared by examining the version string as
     * described in {@link #compareTo(Version)} with the exception that the
     * optional build information is always ignored. </p>
     *
     * <p> A version is not comparable to any other type of object.
     *
     * @param  ob
     *         The object to be compared
     *
     * @return  A negative integer, zero, or a positive integer if this
     *          {@code Version} is less than, equal to, or greater than the
     *          given {@code Version}
     *
     * @throws  NullPointerException
     *          If the given object is {@code null}
     */
    public int compareToIgnoreOpt(Version ob) {
        return compare(ob, true);
    }

    private int compare(Version ob, boolean ignoreOpt) {
        if (ob == null)
            throw new NullPointerException("Invalid argument");

        int ret = compareVersion(ob);
        if (ret != 0)
            return ret;

        ret = comparePre(ob);
        if (ret != 0)
            return ret;

        ret = compareBuild(ob);
        if (ret != 0)
            return ret;

        if (!ignoreOpt)
            return compareOpt(ob);

        return 0;
    }

    private int compareVersion(Version ob) {
        int size = version.size();
        int oSize = ob.version().size();
        int min = Math.min(size, oSize);
        for (int i = 0; i < min; i++) {
            Integer val = version.get(i);
            Integer oVal = ob.version().get(i);
            if (val != oVal)
                return val - oVal;
        }
        if (size != oSize)
            return size - oSize;
        return 0;
    }

    private int comparePre(Version ob) {
        Optional<String> oPre = ob.pre();
        if (!pre.isPresent()) {
            if (oPre.isPresent())
                return 1;
        } else {
            if (!oPre.isPresent())
                return -1;
            String val = pre.get();
            String oVal = oPre.get();
            if (val.matches("\\d+")) {
                return (oVal.matches("\\d+")
                        ? (new BigInteger(val)).compareTo(new BigInteger(oVal))
                        : -1);
            } else {
                return (oVal.matches("\\d+")
                        ? 1
                        : val.compareTo(oVal));
            }
        }
        return 0;
    }

    private int compareBuild(Version ob) {
        Optional<Integer> oBuild = ob.build();
        if (oBuild.isPresent()) {
            return (build.isPresent()
                   ? build.get().compareTo(oBuild.get())
                   : 1);
        } else if (build.isPresent()) {
            return -1;
        }
        return 0;
    }

    private int compareOpt(Version ob) {
        Optional<String> oOpt = ob.optional();
        if (!optional.isPresent()) {
            if (oOpt.isPresent())
                return -1;
        } else {
            if (!oOpt.isPresent())
                return 1;
            return optional.get().compareTo(oOpt.get());
        }
        return 0;
    }

    /**
     * Returns a string representation of this version.
     *
     * @return  The version string
     */
    @Override
    public String toString() {
        StringBuilder sb
            = new StringBuilder(version.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(".")));
        pre.ifPresent(v -> sb.append("-").append(v));

        if (build.isPresent()) {
            sb.append("+").append(build.get());
            if (optional.isPresent())
                sb.append("-").append(optional.get());
        } else {
            if (optional.isPresent()) {
                sb.append(pre.isPresent() ? "-" : "+-");
                sb.append(optional.get());
            }
        }

        return sb.toString();
    }

    /**
     * Determines whether this {@code Version} is equal to another object.
     *
     * <p> Two {@code Version}s are equal if and only if they represent the
     * same version string.
     *
     * <p> This method satisfies the general contract of the {@link
     * Object#equals(Object) Object.equals} method. </p>
     *
     * @param  ob
     *         The object to which this {@code Version} is to be compared
     *
     * @return  {@code true} if, and only if, the given object is a {@code
     *          Version} that is identical to this {@code Version}
     *
     */
    @Override
    public boolean equals(Object ob) {
        boolean ret = equalsIgnoreOpt(ob);
        if (!ret)
            return false;

        Version that = (Version)ob;
        return (this.optional().equals(that.optional()));
    }

    /**
     * Determines whether this {@code Version} is equal to another
     * disregarding optional build information.
     *
     * <p> Two {@code Version}s are equal if and only if they represent the
     * same version string disregarding the optional build information.
     *
     * @param  ob
     *         The object to which this {@code Version} is to be compared
     *
     * @return  {@code true} if, and only if, the given object is a {@code
     *          Version} that is identical to this {@code Version}
     *          ignoring the optinal build information
     *
     */
    public boolean equalsIgnoreOpt(Object ob) {
        if (this == ob)
            return true;
        if (!(ob instanceof Version))
            return false;

        Version that = (Version)ob;
        return (this.version().equals(that.version())
                && this.pre().equals(that.pre())
                && this.build().equals(that.build()));
    }

    /**
     * Returns the hash code of this version.
     *
     * <p> This method satisfies the general contract of the {@link
     * Object#hashCode Object.hashCode} method.
     *
     * @return  The hashcode of this version
     */
    @Override
    public int hashCode() {
        int h = 1;
        int p = 17;

        h = p * h + version.hashCode();
        h = p * h + pre.hashCode();
        h = p * h + build.hashCode();
        h = p * h + optional.hashCode();

        return h;
    }
}
