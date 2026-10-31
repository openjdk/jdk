/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.net;

import java.util.Set;

import jdk.internal.icu.impl.UTS46;
import jdk.internal.icu.text.IDNA;

/**
 * Provides methods to convert internationalized domain names (IDNs) between
 * a normal Unicode representation and an ASCII Compatible Encoding (ACE) representation.
 * Internationalized domain names can use characters from the entire range of
 * Unicode, while traditional domain names are restricted to ASCII characters.
 * ACE is an encoding of Unicode strings that uses only ASCII characters and
 * can be used with software (such as the Domain Name System) that only
 * understands traditional domain names. xn--dmi-0na.fo is an example
 * of an ACE form.
 *
 * <p>Internationalized domain names are defined in <a href="https://www.rfc-editor.org/info/rfc5890/">RFC 5890</a> and <a href="https://www.rfc-editor.org/info/rfc5891/">RFC 5891</a> which together supersede the earlier definition in RFC 3490.
 * They define two main operations: ToASCII and ToUnicode. These two operations employ the
 * <a href="https://www.rfc-editor.org/info/rfc3492/">Punycode</a> algorithm to convert
 * domain name string back and forth between the human-readable unicode form
 * and the ACE form.
 *
 * <p>The behavior of the aforementioned conversion processes can be
 * adjusted by various flags:
 *   <ul>
 *     <li>If the USE_STD3_ASCII_RULES flag is used, ASCII strings are
 *     checked against
 *     <a href="https://www.rfc-editor.org/info/rfc1122/">RFC 1122</a>
 *     and <a href="https://www.rfc-editor.org/info/rfc1123/">RFC 1123</a>.
 *     <li>If the CHECK_CONTEXTJ flag is used, some joiner characters
 *     are checked and rejected if present in an inadmissible context,
 *     for example the U+200D (zero width joiner).
 *     <li>If the NONTRANSITIONAL_TO_ASCII flag is used, a few code
 *     points are treated in a manner incompatible with RFC 3490.
 *     <li>If the NONTRANSITIONAL_TO_UNICODE flag is used, mapping
 *     from ACE to Unicode works as for NONTRANSITIONAL_TO_ASCII.
 *   </ul>
 * <p>These flags can be logically OR'ed together. The single-argument
 * static functions use
 * {@code NONTRANSITIONAL_TO_…|CHECK_CONTEXTJ},
 * since that is the closest match for the major web browsers and other
 * programming languages. They leave out USE_STD3_ASCII_RULES, both
 * because some major browsers do and because these functions have long
 * accepted wildcarded names such as {@code *.example.com}.
 *
 * <p>Security considerations are important with respect to
 * internationalization domain name support. For example, domain names
 * may be <i>homographed</i> if that is allowed by the registry.
 * While top-level domain registries generally use well-considered
 * rules, registry-like services such as blogspot.com may allow
 * users to create confusable names, and of course domain owners
 * themselves can create confusable domain names.
 * <a href="https://www.unicode.org/reports/tr36/">Unicode Technical Report #36</a>
 * discusses security issues of IDN support as well as possible solutions.
 * Applications are responsible for taking adequate security measures.
 *
 * <p>Before Java 28, Java used IDNA2003, which was based on Unicode 3.2
 * with almost no extensibility to grow along with Unicode. The lone
 * bit of extensibility was the {@code ALLOW_UNASSIGNED} flag, which
 * treated all newer codepoints as letterlike. Starting in Java 28, Java uses
 * <a href="https://www.unicode.org/reports/tr46/">UTS#46</a>, which
 * supports all unicode versions up to the one installed on the JVM.
 *
 * <p>Domain registries allow a subset of Unicode. As of 2026, the
 * most permissive registries permit 33553 code points, of which 102
 * are newer than 16 years and none newer than four years. Therefore,
 * upgrading Java every 3-5 years should be enough to have complete
 * knowledge, or very nearly complete, of the code points used in the
 * DNS.
 *
 * <p>Unless otherwise specified, passing a {@code null} argument to any method
 * in this class will cause a {@link NullPointerException} to be thrown.
 *
 * @spec https://www.rfc-editor.org/info/rfc1122/
 *      RFC 1122: Requirements for Internet Hosts - Communication Layers
 * @spec https://www.rfc-editor.org/info/rfc1123/
 *      RFC 1123: Requirements for Internet Hosts - Application and Support
 * @spec https://www.rfc-editor.org/info/rfc3492/
 *      RFC 3492: Punycode: A Bootstring encoding of Unicode for Internationalized Domain Names in Applications (IDNA)
 * @spec https://www.rfc-editor.org/info/rfc5890/
 *      RFC 5890: Internationalized Domain Names for Applications (IDNA): Definitions and Document Framework
 * @spec https://www.rfc-editor.org/info/rfc5891/
 *      RFC 5891: Internationalized Domain Names for Applications (IDNA): Protocol
 * @spec https://www.unicode.org/reports/tr36
 *      Unicode Security Considerations
 * @spec https://www.unicode.org/reports/tr46
 *      Unicode IDNA Compatibility Processing
 * @author Edward Wang
 * @since 1.6
 *
 */
public final class IDN {
    /**
     * Flag to allow processing of code points that weren't assigned
     * in Unicode 3.2. This is now ignored.
     *
     * <p>IDNA2003 (used until Java 27) allowed applications to
     * declare that IDN should support code points that hadn't been
     * assigned yet as of 2003, or not. The support assumed that new
     * code points would be essentially letterlike.
     *
     * <p>UTS#46/IDNA2008 (used starting in Java 28) supports
     * the same version of Unicode as the {@code Character} class.
     *
     * <p>Domain registries use IDNA2008 with extra restrictions.  For
     * example, the Indian registry allows only Indic and Latin, while
     * the .org registry allows tens of scripts to suit that domain's
     * worldwide audience. Both of them have further restrictions to
     * guard against homograph attacks. This takes time; a new code
     * point can't be used to register domains on the day it's been
     * added to Unicode.
     *
     * <p>An application that used {@code ALLOW_UNASSIGNED} should
     * therefore be updated to the latest Java version at least every
     * five years, to ensure that it supports the entire repertoire
     * that can be used to register domains.
     */
    public static final int ALLOW_UNASSIGNED = 0x01;

    /**
     * Flag to turn on the check against STD-3 ASCII rules.
     */
    public static final int USE_STD3_ASCII_RULES = 0x02;

    /**
     * IDNA option to check for whether the input conforms to the
     * CONTEXTJ rules. CONTEXTJ covers joining characters that may be
     * problematic in general, but have to be allowed in some
     * contexts.
     *
     * <p>U+200C (zero width non-joiner) is a good example. ZWNJ means
     * "do not render my left/right neighbours together" ("do not show
     * fi as fi-ligature") and is invisible between most letters. It
     * is therefore a good way to create equal/inequal strings such as
     * lookalike domains, and is permitted only in a few contexts.
     *
     * <p>This flag applies only to nontransitional processing.
     * Transitional processing silently removes these code points from
     * the output, whether the flag is set or not.
     *
     * <p>If CONTEXTJ is checked, ZWNJ etc. are accepted in their
     * defined contexts and cause an exception elsewhere. If not,
     * they are accepted everywhere and preserved in the output.
     * @since 28
     */
    public static final int CHECK_CONTEXTJ = 8;

    /**
     * IDNA option for nontransitional processing in ToASCII().
     * Nontransitional processing always follows RFC 5890, transitional instead
     * follows RFC 3490 where there is a conflict between the two documents.
     *
     * <p>With nontransitional processing, IDNA treats the German
     * ess-zet ligature as distinct from ss, handles the word Sri
     * correctly when written in sinhala (the script used in Sri
     * Lanka), and there are a few other differences as well.
     *
     * <p>Note that domain registries enforce RFC 5890 compliance for
     * newly registered domains, and that as of 2026, all three major
     * web browsers have switched to RFC 5890. Transitional processing
     * is provided for applications that have a particular need for
     * RFC 3490 compatibility.
     * @since 28
     */
    public static final int NONTRANSITIONAL_TO_ASCII = 0x10;

    /**
     * IDNA option for nontransitional processing in ToUnicode(). The same
     * considerations apply as for NONTRANSITIONAL_TO_ASCII.
     * @since 28
     */
    public static final int NONTRANSITIONAL_TO_UNICODE = 0x20;

    private static UTS46[] singletons = new UTS46[0x20];

    private static UTS46 getUTS46(int flag) {
        flag = (flag & 0x3e) | IDNA.CHECK_BIDI;
        // ALLOW_UNASSIGNED is not meaningful for IDNA2008 or UTS#46, so
        // it is forced to false. The old code always behaved as if
        // IDNA.CHECK_BIDI==true, so it is forced to true. The other
        // flags are used as-is. Note that STD3_ASCII_RULES equals
        // IDNA.USE_STD3_RULES by value.
        int index = flag / 2;
        synchronized(singletons) {
            if (singletons[index] == null)
                singletons[index] = new UTS46(flag);
            return singletons[index];
        }
    }


    /**
     * Translates a string from Unicode to ASCII Compatible Encoding (ACE),
     * as originally defined by the ToASCII operation of
     * <a href="https://www.rfc-editor.org/info/rfc3490/">RFC 3490</a> and
     * adjusted by <a href="https://www.unicode.org/reports/tr46/">UTS#46</a>
     * and <a href="https://www.rfc-editor.org/info/rfc5890/">RFC 5890</a>.
     *
     * <p>The ToASCII operation can fail, and throws
     * IllegalArgumentException when it does. In that case the input
     * string should not be used in an internationalized domain name.
     *
     * <p>Two failures are tolerated rather than reported. The root
     * ("" or ".") is returned unchanged, and so is a label with
     * hyphens in the third and fourth position, which
     * <a href="https://www.rfc-editor.org/info/rfc5891/">RFC 5891</a>
     * 4.2.3.1 reserves for tagged forms such as the a-label. Java
     * accepted both before it used UTS#46, and rejecting them now
     * would break host names that resolved in the past.
     *
     * <p> A label is an individual part of a domain name. The original ToASCII operation,
     * as defined in RFC 3490, only operates on a single label. This method can handle
     * both label and entire domain name, by assuming that labels in a domain name are
     * always separated by dots. The following characters are recognized as dots:
     * U+002E (full stop), U+3002 (ideographic full stop), U+FF0E (fullwidth full stop),
     * and U+FF61 (halfwidth ideographic full stop). If dots are
     * used as label separators, this method also changes all of them to U+002E (full stop)
     * in the output string.
     *
     * @param input     the string to be processed
     * @param flag      process flag; can be 0 or any logical OR of possible flags
     *
     * @return          the translated {@code String}
     *
     * @throws IllegalArgumentException   if the input string doesn't conform to UTS#46,
     *                                    apart from the two tolerated failures above
     * @spec https://www.unicode.org/reports/tr46
     *      Unicode IDNA Compatibility Processing
     */
    public static String toASCII(String input, int flag)
    {
        if (input.isEmpty() || input.equals(".")) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        IDNA.Info info = new IDNA.Info();
        try {
            getUTS46(flag).nameToASCII(input, result, info);
        } catch(Throwable t) {
            throw new IllegalArgumentException(t);
        }
        Set<IDNA.Error> errors = fatalErrors(info);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(input + ": " + errors);
        }
        return result.toString();
    }


    /**
     * Translates a string from Unicode to ASCII Compatible Encoding (ACE),
     * as originally defined by the ToASCII operation of
     * <a href="https://www.rfc-editor.org/info/rfc3490/">RFC 3490</a> and
     * adjusted by <a href="https://www.unicode.org/reports/tr46/">UTS#46</a>
     * and <a href="https://www.rfc-editor.org/info/rfc5890/">RFC 5890</a>.
     *
     * <p> This convenience method works as if by invoking the
     * two-argument counterpart as follows:
     * <blockquote>
     * {@link #toASCII(String, int) toASCII}(input,&nbsp;IDN.CHECK_CONTEXTJ|IDN.NONTRANSITIONAL_TO_ASCII);
     * </blockquote>
     *
     * <p>This set of flags has been chosen to match the most popular three web browsers.
     *
     * @param input     the string to be processed
     *
     * @return          the translated {@code String}
     *
     * @throws IllegalArgumentException   if the input string doesn't conform to UTS#46
     * @spec https://www.unicode.org/reports/tr46
     *      Unicode IDNA Compatibility Processing
     */
    public static String toASCII(String input) {
        // No USE_STD3_ASCII_RULES: it rejects '*', and callers such as
        // sun.security.util.HostnameChecker pass wildcard certificate
        // names through here and treat any exception as a mismatch.
        return toASCII(input, CHECK_CONTEXTJ | NONTRANSITIONAL_TO_ASCII);
    }


    /**
     * Translates a string from ASCII Compatible Encoding (ACE) to Unicode,
     * as originally defined by the ToUnicode operation of
     * <a href="https://www.rfc-editor.org/info/rfc3490/">RFC 3490</a> and
     * adjusted by <a href="https://www.unicode.org/reports/tr46/">UTS#46</a>
     * and <a href="https://www.rfc-editor.org/info/rfc5890/">RFC 5890</a>.
     *
     * <p>ToUnicode never fails. In case of any error, the input
     * string is returned either unmodified or partly modified.
     *
     * <p> A label is an individual part of a domain name. The original ToUnicode operation,
     * as defined in RFC 3490, only operates on a single label. This method can handle
     * both label and entire domain name, by assuming that labels in a domain name are
     * always separated by dots. The following characters are recognized as dots:
     * U+002E (full stop), U+3002 (ideographic full stop), U+FF0E (fullwidth full stop),
     * and U+FF61 (halfwidth ideographic full stop).
     *
     * <p>In case a label cannot be converted to unicode, toUnicode()
     * leaves that label unchanged (while converting any convertible
     * labels).
     *
     * @param input     the string to be processed
     * @param flag      process flag; can be 0 or any logical OR of possible flags
     *
     * @return          the translated {@code String}
     * @spec https://www.unicode.org/reports/tr46
     *      Unicode IDNA Compatibility Processing
     */
    public static String toUnicode(String input, int flag) {
        UTS46 uts46 = getUTS46(flag);
        String converted = convertToUnicode(uts46, input);
        if (converted != null) {
            return converted;
        }
        StringBuilder result = new StringBuilder();
        int start = 0;
        while (start <= input.length()) {
            int end = start;
            while (end < input.length() && !isLabelSeparator(input.charAt(end))) {
                end++;
            }
            String label = input.substring(start, end);
            converted = convertToUnicode(uts46, label);
            result.append(converted == null ? label : converted);
            if (end < input.length()) {
                result.append('.');
            }
            start = end + 1;
        }
        return result.toString();
    }


    private static String convertToUnicode(UTS46 uts46, String input) {
        StringBuilder result = new StringBuilder();
        IDNA.Info info = new IDNA.Info();
        try {
            uts46.nameToUnicode(input, result, info);
        } catch (Throwable t) {
            return null;
        }
        return fatalErrors(info).isEmpty() ? result.toString() : null;
    }


    /*
     * RFC5891 4.2.3.1 reserves labels with hyphens in the third and
     * fourth position for future tagged forms, of which the a-label
     * is so far the only one, and UTS#46 reports HYPHEN_3_4 for the
     * rest. That rule applies to registries, not to everyone. The owner
     * of example.com is able to create a name such as
     * ab--cd.example.com even if registering ab--cd in a public TLD
     * is forbidden.  IDN converted such names before it used UTS#46,
     * and sun.security.util.HostnameChecker reads any exception from
     * toASCII as a certificate name mismatch, so enforcing the rule
     * here would turn a legacy name into a TLS failure.
     */
    private static Set<IDNA.Error> fatalErrors(IDNA.Info info) {
        Set<IDNA.Error> errors = info.getErrors();
        errors.remove(IDNA.Error.HYPHEN_3_4);
        return errors;
    }


    private static boolean isLabelSeparator(char c) {
        return c == '.' || c == '\u3002' || c == '\uFF0E' || c == '\uFF61';
    }


    /**
     * Translates a string from ASCII Compatible Encoding (ACE) to Unicode,
     * as originally defined by the ToUnicode operation of
     * <a href="https://www.rfc-editor.org/info/rfc3490/">RFC 3490</a> and
     * adjusted by <a href="https://www.unicode.org/reports/tr46/">UTS#46</a>
     * and <a href="https://www.rfc-editor.org/info/rfc5890/">RFC 5890</a>.
     *
     * <p> This convenience method works as if by invoking the
     * two-argument counterpart as follows:
     * <blockquote>
     * {@link #toUnicode(String, int) toUnicode}(input,&nbsp;IDN.CHECK_CONTEXTJ|IDN.NONTRANSITIONAL_TO_UNICODE);
     * </blockquote>
     *
     * <p>This set of flags has been chosen to match the most popular three web browsers.
     *
     * @param input     the string to be processed
     *
     * @return          the translated {@code String}
     * @spec https://www.unicode.org/reports/tr46
     *      Unicode IDNA Compatibility Processing
     */
    public static String toUnicode(String input) {
        return toUnicode(input, CHECK_CONTEXTJ | NONTRANSITIONAL_TO_UNICODE);
    }


    /* ---------------- Private operations -------------- */


    //
    // to suppress the default zero-argument constructor
    //
    private IDN() {}
}
