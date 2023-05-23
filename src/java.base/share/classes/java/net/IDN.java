/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import jdk.internal.icu.impl.Punycode;
import jdk.internal.icu.impl.UTS46;
import jdk.internal.icu.text.IDNA;
import jdk.internal.icu.text.StringPrep;
import jdk.internal.icu.text.UCharacterIterator;

/**
 * Provides methods to convert internationalized domain names (IDNs) between
 * a normal Unicode representation and an ASCII Compatible Encoding (ACE) representation.
 * Internationalized domain names can use characters from the entire range of
 * Unicode, while traditional domain names are restricted to ASCII characters.
 * ACE is an encoding of Unicode strings that uses only ASCII characters and
 * can be used with software (such as the Domain Name System) that only
 * understands traditional domain names. xn--dmi-0na.fo is an example
 * of an ACE form (in fact, it even means example).
 *
 * <p>Internationalized domain names are defined in <a href="https://datatracker.ietf.org/doc/rfc5890/">RFC 5890</a> and <a href="https://datatracker.ietf.org/doc/rfc5891/">RFC 5891</a> which together supersede the earlier definition in RFC 3490.
 * They defines two main operations: ToASCII and ToUnicode. These two operations employ the
 * <a href="https://datatracker.ietf.org/doc/rfc3491/">Nameprep</a> algorithm (which is a
 * profile of <a href="https://datatracker.ietf.org/doc/rfc3454/">Stringprep</a>) and the
 * <a href="https://datatracker.ietf.org/doc/rfc3492.txt">Punycode</a> algorithm to convert
 * domain name string back and forth between the human-readable unicode form
 * and the ACE form.
 *
 * <p>The behavior of aforementioned conversion process can be adjusted by various flags:
 *   <ul>
 *     <li>If the USE_STD3_ASCII_RULES flag is used, ASCII strings are
 *     checked against
 *     <a href="https://datatracker.ietf.org/doc/rfc1122/">RFC 1122</a>
 *     and <a href="https://datatracker.ietf.org/doc/rfc1123/">RFC 1123</a>.
 *     <li>If the CHECK_CONTEXTJ flag is used, some joiner characters
 *     are checked and rejected if present in an inadmissible context,
 *     for example the U+200D ZERO WIDTH JOINER.
 *     <li>If the NONTRANSITIONAL_TO_ASCII flag is used, a few code
 *     points are treated in a manner incompatible with RFC3490.
 *     <li>If the NONTRANSITIONAL_TO_UNICODE flag is used, mapping
 *     from ACE to Unicode works as for NONTRANSITIONAL_TO_ASCII.
 *   </ul>
 * <p>These flags can be logically OR'ed together. The default is to use
 * {@code NONTRANSITIONAL_TO_ASCII|NONTRANSITIONAL_TO_UNICODE|CHECK_CONTEXTJ},
 * since that is the closest match for the major web browsers and other
 * programmering languages as of 2023.
 *
 * <p>The security consideration is important with respect to
 * internationalization domain name support. For example, domain names
 * may be <i>homographed</i> if that is allowed by the registry's
 * <a href="https://www.icann.org/resources/pages/second-level-lgr-2015-06-21-en">LGR policy.</a>
 * While top-level domain registries generally use well-considered
 * LGRs, registry-like services such as blogspot.com or github.com may allow
 * users to create confusable names, and of course domain owners
 * themselves can create confusable domain names.
 * <a href="http://www.unicode.org/reports/tr36/">Unicode Technical Report #36</a>
 * discusses security issues of IDN support as well as possible solutions.
 * Applications are responsible for taking adequate security measures.
 *
 * <p>Until 2023, Java used IDNA2003, which was based on Unicode 3.2
 * with almost no extensibility to grow along with Unicode. The lone
 * bit of extensibility was the {@code ALLOW_UNASSIGNED} flag, which
 * treated all newer codepoints as letterlike. Starting in 2023, Java uses
 * <a href="https://unicode.org/reports/tr46/">UTS#46</a>, which
 * supports all unicode versions up to the one installed on the JVM.
 * Because domain registries allow a subset of Inicode (the
 * <a href="https://www.icann.org/resources/pages/msr-2015-06-21-en">maximal starting repertoire</a>,
 * which contains 35515 code points, out of unicode's 13) and the vetting process for new
 * code points takes a while, the support remains complete for many
 * years. By way of example, the oldest version with complete support
 * for the MSR in 2023 is Java 12, released in 2019. Java 9-11 lacks
 * one code point (U+A7B9, lower-case u with stroke), which suggests
 * that one should avoid running a java version older than five years.
 *
 * @spec https://www.rfc-editor.org/info/rfc1122
 *      RFC 1122: Requirements for Internet Hosts - Communication Layers
 * @spec https://www.rfc-editor.org/info/rfc1123
 *      RFC 1123: Requirements for Internet Hosts - Application and Support
 * @spec https://www.rfc-editor.org/info/rfc3454
 *      RFC 3454: Preparation of Internationalized Strings ("stringprep")
 * @spec https://www.rfc-editor.org/info/rfc3491
 *      RFC 3491: Nameprep: A Stringprep Profile for Internationalized Domain Names (IDN)
 * @spec https://www.rfc-editor.org/info/rfc3492
 *      RFC 3492: Punycode: A Bootstring encoding of Unicode for Internationalized Domain Names in Applications (IDNA)
 * @spec https://www.rfc-editor.org/info/rfc5890
 *      RFC 5890: Internationalized Domain Names for Applications (IDNA): Definitions and Document Framework
 * @spec https://www.rfc-editor.org/info/rfc5891
 *      RFC 5890: Internationalized Domain Names for Applications (IDNA): Protocol
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
     * IDNA2003 (used by Java until 2023) allowed applications to
     * declare that IDN should support code points that hadn't been
     * assigned yet as of 2003, or not. The support assumed that new
     * code points would be essentially letterlike.
     *
     * IDNA2008 (used by Java starting in 2023) supports the same
     * version of Unicode as the {@code Character} class.
     *
     * Domain registries use IDNA2008 with extra restrictions.  For
     * example, the Indian registry allows only Indic and Latin, while
     * the .org registry allows tens of scripts to suit that domain's
     * worldwide audience. Both of them have further restrictions to
     * guard against homograph attacks. This takes time; a new code
     * point can't be used to register domains as soon as it's been
     * added to Unicode.
     *
     * An application that used {@code ALLOW_UNASSIGNED} should
     * instead be updated to the latest Java version at least every
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
     */
    public static final int CHECK_CONTEXTJ = 8;

    /**
     * IDNA option for nontransitional processing in ToASCII().
     * Nontransitional processing always follows RFC 5890, transitional instead
     * follows 3490 where there is a conflict between the two documents.
     *
     * <p>With nontranstional processing, IDNA treats the German ess-zet ligature
     * as distinct from ss, handles the word Sri correctly when written
     * in sinhala (the script used in Sri Lanka), and there are a few other differences
     * as well.
     *
     * <p>Note that domain registries enforce RFC 5890 compliance for
     * newly registered domains, and that as of 2023, all three major web
     * browsers have switched to RFC 5890. Transitional processing is rarely
     * desirable any more. It is provided for applications that have a
     * particular need for RFC 3490 compatibility.
     */
    public static final int NONTRANSITIONAL_TO_ASCII = 0x10;

    /**
     * IDNA option for nontransitional processing in ToUnicode(). The same
     * considerations apply as for NONTRANSITIONAL_TO_ASCII.
     */
    public static final int NONTRANSITIONAL_TO_UNICODE = 0x20;

    private static UTS46 singletons[] = new UTS46[0x20];

    private static UTS46 getUTS46(int flag) {
        flag = (flag & 0x3e) | IDNA.CHECK_BIDI;
        // ALLOW_UNASSIGNED is not meaningful for IDNA2008/UTS46, so
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
     * as defined by the ToASCII operation of <a href="https://datatracker.ietf.org/doc/rfc3490/">RFC 3490</a>.
     *
     * <p>ToASCII operation can fail. ToASCII fails if any step of it fails.
     * If ToASCII operation fails, an IllegalArgumentException will be thrown.
     * In this case, the input string should not be used in an internationalized domain name.
     *
     * <p> A label is an individual part of a domain name. The original ToASCII operation,
     * as defined in RFC 3490, only operates on a single label. This method can handle
     * both label and entire domain name, by assuming that labels in a domain name are
     * always separated by dots. The following characters are recognized as dots:
     * &#0092;u002E (full stop), &#0092;u3002 (ideographic full stop), &#0092;uFF0E (fullwidth full stop),
     * and &#0092;uFF61 (halfwidth ideographic full stop). if dots are
     * used as label separators, this method also changes all of them to &#0092;u002E (full stop)
     * in output translated string.
     *
     * @param input     the string to be processed
     * @param flag      process flag; can be 0 or any logical OR of possible flags
     *
     * @return          the translated {@code String}
     *
     * @throws IllegalArgumentException   if the input string doesn't conform to RFC 5890 specification
     * @spec https://www.rfc-editor.org/info/rfc3490
     *      RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     */
    public static String toASCII(String input, int flag)
    {
        StringBuilder result = new StringBuilder();
        IDNA.Info info = new IDNA.Info();
        try {
            UCharacterIterator iter = UCharacterIterator.getInstance(input);
	    getUTS46(flag).nameToASCII(input, result, info);
        } catch(Throwable t) {
            throw new IllegalArgumentException(t);
        }
        return result.toString();
    }


    /**
     * Translates a string from Unicode to ASCII Compatible Encoding (ACE),
     * as defined by the ToASCII operation of <a href="https://datatracker.ietf.org/doc/rfc3490/">RFC 3490</a>.
     *
     * <p> This convenience method works as if by invoking the
     * two-argument counterpart as follows:
     * <blockquote>
     * {@link #toASCII(String, int) toASCII}(input,&nbsp;IDN.CHECK_CONTEXTJ|IDN.USE_STD3_ASCII_RULES|IDN.NONTRANSITIONAL_TO_ASCII);
     * </blockquote>
     *
     * <p>This set of flags has been chosen to match the most popular three web browsers.
     *
     * @param input     the string to be processed
     *
     * @return          the translated {@code String}
     *
     * @throws IllegalArgumentException   if the input string doesn't conform to RFC 3490 specification
     * @spec https://www.rfc-editor.org/info/rfc3490
     *      RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     */
    public static String toASCII(String input) {
        return toASCII(input, CHECK_CONTEXTJ | USE_STD3_ASCII_RULES | NONTRANSITIONAL_TO_ASCII );
    }


    /**
     * Translates a string from ASCII Compatible Encoding (ACE) to Unicode,
     * as defined by the ToUnicode operation of <a href="https://datatracker.ietf.org/doc/rfc3490/">RFC 3490</a>.
     *
     * <p>ToUnicode never fails. In case of any error, the input string is returned unmodified.
     *
     * <p> A label is an individual part of a domain name. The original ToUnicode operation,
     * as defined in RFC 3490, only operates on a single label. This method can handle
     * both label and entire domain name, by assuming that labels in a domain name are
     * always separated by dots. The following characters are recognized as dots:
     * &#0092;u002E (full stop), &#0092;u3002 (ideographic full stop), &#0092;uFF0E (fullwidth full stop),
     * and &#0092;uFF61 (halfwidth ideographic full stop).
     *
     * @param input     the string to be processed
     * @param flag      process flag; can be 0 or any logical OR of possible flags
     *
     * @return          the translated {@code String}
     * @spec https://www.rfc-editor.org/info/rfc3490
     *      RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     */
    public static String toUnicode(String input, int flag) {
        StringBuilder result = new StringBuilder();
        IDNA.Info info = new IDNA.Info();
        try {
            getUTS46(flag).nameToUnicode(input, result, info);
        } catch(Throwable t) {
            throw new IllegalArgumentException(t);
        }
        return result.toString();
    }


    /**
     * Translates a string from ASCII Compatible Encoding (ACE) to Unicode,
     * as defined by the ToUnicode operation of <a href="https://datatracker.ietf.org/doc/rfc3490/">RFC 3490</a>.
     *
     * <p> This convenience method works as if by invoking the
     * two-argument counterpart as follows:
     * <blockquote>
     * {@link #toUnicode(String, int) toUnicode}(input,&nbsp;IDN.CHECK_CONTEXTJ|IDN.USE_STD3_ASCII_RULES|IDN.NONTRANSITIONAL_TO_ASCII);
     * </blockquote>
     *
     * <p>This set of flags has been chosen to match the most popular three web browsers.
     *
     * @param input     the string to be processed
     *
     * @return          the translated {@code String}
     * @spec https://www.rfc-editor.org/info/rfc3490
     *      RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     */
    public static String toUnicode(String input) {
        return toUnicode(input, CHECK_CONTEXTJ | USE_STD3_ASCII_RULES | NONTRANSITIONAL_TO_UNICODE );
    }


    /* ---------------- Private operations -------------- */


    //
    // to suppress the default zero-argument constructor
    //
    private IDN() {}
}
