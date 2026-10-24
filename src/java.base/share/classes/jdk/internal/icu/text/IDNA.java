/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 *******************************************************************************
 * Copyright (C) 2003-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package jdk.internal.icu.text;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import jdk.internal.icu.impl.UTS46;

/**
 * Abstract base class for IDNA processing.
 * See http://www.unicode.org/reports/tr46/
 * and http://www.ietf.org/rfc/rfc3490.txt
 * <p>
 * The IDNA class is not intended for public subclassing.
 * <p>
 * The non-static methods implement UTS #46 and IDNA2008.
 * IDNA2008 is implemented according to UTS #46, see {@link #getUTS46Instance(int)}.
 * <p>
 * IDNA2003 is obsolete. The static methods implement IDNA2003. They are all deprecated.
 * <p>
 * IDNA2003 API Overview:
 * <p>
 * The static IDNA API methods implement the IDNA protocol as defined in the
 * <a href="http://www.ietf.org/rfc/rfc3490.txt">IDNA RFC</a>.
 * The draft defines 2 operations: ToASCII and ToUnicode. Domain labels
 * containing non-ASCII code points are required to be processed by
 * ToASCII operation before passing it to resolver libraries. Domain names
 * that are obtained from resolver libraries are required to be processed by
 * ToUnicode operation before displaying the domain name to the user.
 * IDNA requires that implementations process input strings with
 * <a href="http://www.ietf.org/rfc/rfc3491.txt">Nameprep</a>,
 * which is a profile of <a href="http://www.ietf.org/rfc/rfc3454.txt">Stringprep</a> ,
 * and then with <a href="http://www.ietf.org/rfc/rfc3492.txt">Punycode</a>.
 * Implementations of IDNA MUST fully implement Nameprep and Punycode;
 * neither Nameprep nor Punycode are optional.
 * The input and output of ToASCII and ToUnicode operations are Unicode
 * and are designed to be chainable, i.e., applying ToASCII or ToUnicode operations
 * multiple times to an input string will yield the same result as applying the operation
 * once.
 * ToUnicode(ToUnicode(ToUnicode...(ToUnicode(string)))) == ToUnicode(string)
 * ToASCII(ToASCII(ToASCII...(ToASCII(string))) == ToASCII(string).
 *
 * @author Ram Viswanadha, Markus Scherer
 * @stable ICU 2.8
 */
public abstract class IDNA {
    /**
     * Default options value: UTS #46 nontransitional processing.
     * For use in static worker and factory methods.
     *
     * <p>Since ICU 76, this is the same as
     * {@link #NONTRANSITIONAL_TO_ASCII} | {@link #NONTRANSITIONAL_TO_UNICODE},
     * corresponding to Unicode 15.1 UTS #46 deprecating transitional processing.
     * (These options are ignored by the IDNA2003 implementation.)
     *
     * <p>Before ICU 76, this constant did not set any of the options.
     *
     * @stable ICU 2.8
     */
    public static final int DEFAULT = 0x30;
    /**
     * Option to allow unassigned code points in domain names and labels.
     * For use in static worker and factory methods.
     * <p>This option is ignored by the UTS46 implementation.
     * (UTS #46 disallows unassigned code points.)
     * @deprecated ICU 55 Use UTS 46 instead via {@link #getUTS46Instance(int)}.
     */
    @Deprecated
    public static final int ALLOW_UNASSIGNED = 1;
    /**
     * Option to check whether the input conforms to the STD3 ASCII rules,
     * for example the restriction of labels to LDH characters
     * (ASCII Letters, Digits and Hyphen-Minus).
     * For use in static worker and factory methods.
     * @stable ICU 2.8
     */
    public static final int USE_STD3_RULES = 2;
    /**
     * IDNA option to check for whether the input conforms to the BiDi rules.
     * For use in static worker and factory methods.
     * <p>This option is ignored by the IDNA2003 implementation.
     * (IDNA2003 always performs a BiDi check.)
     * @stable ICU 4.6
     */
    public static final int CHECK_BIDI = 4;
    /**
     * IDNA option to check for whether the input conforms to the CONTEXTJ rules.
     * For use in static worker and factory methods.
     * <p>This option is ignored by the IDNA2003 implementation.
     * (The CONTEXTJ check is new in IDNA2008.)
     * @stable ICU 4.6
     */
    public static final int CHECK_CONTEXTJ = 8;
    /**
     * IDNA option for nontransitional processing in ToASCII().
     * For use in static worker and factory methods.
     *
     * <p>By default, ToASCII() uses transitional processing.
     * Unicode 15.1 UTS #46 deprecated transitional processing.
     *
     * <p>This option is ignored by the IDNA2003 implementation.
     * (This is only relevant for compatibility of newer IDNA implementations with IDNA2003.)
     * @stable ICU 4.6
     */
    public static final int NONTRANSITIONAL_TO_ASCII = 0x10;
    /**
     * IDNA option for nontransitional processing in ToUnicode().
     * For use in static worker and factory methods.
     *
     * <p>By default, ToUnicode() uses transitional processing.
     * Unicode 15.1 UTS #46 deprecated transitional processing.
     *
     * <p>This option is ignored by the IDNA2003 implementation.
     * (This is only relevant for compatibility of newer IDNA implementations with IDNA2003.)
     * @stable ICU 4.6
     */
    public static final int NONTRANSITIONAL_TO_UNICODE = 0x20;
    /**
     * IDNA option to check for whether the input conforms to the CONTEXTO rules.
     * For use in static worker and factory methods.
     * <p>This option is ignored by the IDNA2003 implementation.
     * (The CONTEXTO check is new in IDNA2008.)
     * <p>This is for use by registries for IDNA2008 conformance.
     * UTS #46 does not require the CONTEXTO check.
     * @stable ICU 49
     */
    public static final int CHECK_CONTEXTO = 0x40;

    /**
     * Returns an IDNA instance which implements UTS #46.
     * Returns an unmodifiable instance, owned by the caller.
     * Cache it for multiple operations, and delete it when done.
     * The instance is thread-safe, that is, it can be used concurrently.
     * <p>
     * UTS #46 defines Unicode IDNA Compatibility Processing,
     * updated to the latest version of Unicode and compatible with both
     * IDNA2003 and IDNA2008.
     * <p>
     * The worker functions use transitional processing, including deviation mappings,
     * unless {@link #NONTRANSITIONAL_TO_ASCII} or {@link #NONTRANSITIONAL_TO_UNICODE}
     * is used in which case the deviation characters are passed through without change.
     * <b>Unicode 15.1 UTS #46 deprecated transitional processing.</b>
     * <p>
     * Disallowed characters are mapped to U+FFFD.
     * <p>
     * Operations with the UTS #46 instance do not support the
     * ALLOW_UNASSIGNED option.
     * <p>
     * By default, the UTS #46 implementation allows all ASCII characters (as valid or mapped).
     * When the USE_STD3_RULES option is used, ASCII characters other than
     * letters, digits, hyphen (LDH) and dot/full stop are disallowed and mapped to U+FFFD.
     *
     * @param options Bit set to modify the processing and error checking.
     *                These should include {@link IDNA#DEFAULT}, or
     *                {@link IDNA#NONTRANSITIONAL_TO_ASCII} | {@link IDNA#NONTRANSITIONAL_TO_UNICODE}.
     * @return the UTS #46 IDNA instance, if successful
     * @stable ICU 4.6
     */
    public static IDNA getUTS46Instance(int options) {
        return new UTS46(options);
    }

    /**
     * Converts a single domain name label into its ASCII form for DNS lookup.
     * If any processing step fails, then info.hasErrors() will be true and
     * the result might not be an ASCII string.
     * The label might be modified according to the types of errors.
     * Labels with severe errors will be left in (or turned into) their Unicode form.
     *
     * @param label Input domain name label
     * @param dest Destination string object
     * @param info Output container of IDNA processing details.
     * @return dest
     * @stable ICU 4.6
     */
    public abstract StringBuilder labelToASCII(CharSequence label, StringBuilder dest, Info info);

    /**
     * Converts a single domain name label into its Unicode form for human-readable display.
     * If any processing step fails, then info.hasErrors() will be true.
     * The label might be modified according to the types of errors.
     *
     * @param label Input domain name label
     * @param dest Destination string object
     * @param info Output container of IDNA processing details.
     * @return dest
     * @stable ICU 4.6
     */
    public abstract StringBuilder labelToUnicode(CharSequence label, StringBuilder dest, Info info);

    /**
     * Converts a whole domain name into its ASCII form for DNS lookup.
     * If any processing step fails, then info.hasErrors() will be true and
     * the result might not be an ASCII string.
     * The domain name might be modified according to the types of errors.
     * Labels with severe errors will be left in (or turned into) their Unicode form.
     *
     * @param name Input domain name
     * @param dest Destination string object
     * @param info Output container of IDNA processing details.
     * @return dest
     * @stable ICU 4.6
     */
    public abstract StringBuilder nameToASCII(CharSequence name, StringBuilder dest, Info info);

    /**
     * Converts a whole domain name into its Unicode form for human-readable display.
     * If any processing step fails, then info.hasErrors() will be true.
     * The domain name might be modified according to the types of errors.
     *
     * @param name Input domain name
     * @param dest Destination string object
     * @param info Output container of IDNA processing details.
     * @return dest
     * @stable ICU 4.6
     */
    public abstract StringBuilder nameToUnicode(CharSequence name, StringBuilder dest, Info info);

    /**
     * Output container for IDNA processing errors.
     * The Info class is not suitable for subclassing.
     * @stable ICU 4.6
     */
    public static final class Info {
        /**
         * Constructor.
         * @stable ICU 4.6
         */
        public Info() {
            errors=EnumSet.noneOf(Error.class);
            labelErrors=EnumSet.noneOf(Error.class);
            isTransDiff=false;
            isBiDi=false;
            isOkBiDi=true;
        }
        /**
         * Were there IDNA processing errors?
         * @return true if there were processing errors
         * @stable ICU 4.6
         */
        public boolean hasErrors() { return !errors.isEmpty(); }
        /**
         * Returns a set indicating IDNA processing errors.
         * @return set of processing errors (modifiable, and not null)
         * @stable ICU 4.6
         */
        public Set<Error> getErrors() { return errors; }
        /**
         * Returns true if transitional and nontransitional processing produce different results.
         * This is the case when the input label or domain name contains
         * one or more deviation characters outside a Punycode label (see UTS #46).
         * <ul>
         * <li>With nontransitional processing, such characters are
         * copied to the destination string.
         * <li>With transitional processing, such characters are
         * mapped (sharp s/sigma) or removed (joiner/nonjoiner).
         * </ul>
         * @return true if transitional and nontransitional processing produce different results
         * @stable ICU 4.6
         */
        public boolean isTransitionalDifferent() { return isTransDiff; }

        private void reset() {
            errors.clear();
            labelErrors.clear();
            isTransDiff=false;
            isBiDi=false;
            isOkBiDi=true;
        }

        private EnumSet<Error> errors, labelErrors;
        private boolean isTransDiff;
        private boolean isBiDi;
        private boolean isOkBiDi;
    }

    // The following protected methods give IDNA subclasses access to the private IDNAInfo fields.
    // The IDNAInfo also provides intermediate state that is publicly invisible,
    // avoiding the allocation of another worker object.
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static void resetInfo(Info info) {
        info.reset();
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static boolean hasCertainErrors(Info info, EnumSet<Error> errors) {
        return !info.errors.isEmpty() && !Collections.disjoint(info.errors, errors);
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static boolean hasCertainLabelErrors(Info info, EnumSet<Error> errors) {
        return !info.labelErrors.isEmpty() && !Collections.disjoint(info.labelErrors, errors);
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static void addLabelError(Info info, Error error) {
        info.labelErrors.add(error);
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static void promoteAndResetLabelErrors(Info info) {
        if(!info.labelErrors.isEmpty()) {
            info.errors.addAll(info.labelErrors);
            info.labelErrors.clear();
        }
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static void addError(Info info, Error error) {
        info.errors.add(error);
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static void setTransitionalDifferent(Info info) {
        info.isTransDiff=true;
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static void setBiDi(Info info) {
        info.isBiDi=true;
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static boolean isBiDi(Info info) {
        return info.isBiDi;
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static void setNotOkBiDi(Info info) {
        info.isOkBiDi=false;
    }
    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected static boolean isOkBiDi(Info info) {
        return info.isOkBiDi;
    }

    /**
     * IDNA error bit set values.
     * When a domain name or label fails a processing step or does not meet the
     * validity criteria, then one or more of these error bits are set.
     * @stable ICU 4.6
     */
    public static enum Error {
        /**
         * A non-final domain name label (or the whole domain name) is empty.
         * @stable ICU 4.6
         */
        EMPTY_LABEL,
        /**
         * A domain name label is longer than 63 bytes.
         * (See STD13/RFC1034 3.1. Name space specifications and terminology.)
         * This is only checked in ToASCII operations, and only if the output label is all-ASCII.
         * @stable ICU 4.6
         */
        LABEL_TOO_LONG,
        /**
         * A domain name is longer than 255 bytes in its storage form.
         * (See STD13/RFC1034 3.1. Name space specifications and terminology.)
         * This is only checked in ToASCII operations, and only if the output domain name is all-ASCII.
         * @stable ICU 4.6
         */
        DOMAIN_NAME_TOO_LONG,
        /**
         * A label starts with a hyphen-minus ('-').
         * @stable ICU 4.6
         */
        LEADING_HYPHEN,
        /**
         * A label ends with a hyphen-minus ('-').
         * @stable ICU 4.6
         */
        TRAILING_HYPHEN,
        /**
         * A label contains hyphen-minus ('-') in the third and fourth positions.
         * @stable ICU 4.6
         */
        HYPHEN_3_4,
        /**
         * A label starts with a combining mark.
         * @stable ICU 4.6
         */
        LEADING_COMBINING_MARK,
        /**
         * A label or domain name contains disallowed characters.
         * @stable ICU 4.6
         */
        DISALLOWED,
        /**
         * A label starts with "xn--" but does not contain valid Punycode.
         * That is, an xn-- label failed Punycode decoding.
         * @stable ICU 4.6
         */
        PUNYCODE,
        /**
         * A label contains a dot=full stop.
         * This can occur in an input string for a single-label function.
         * @stable ICU 4.6
         */
        LABEL_HAS_DOT,
        /**
         * An ACE label does not contain a valid label string.
         * The label was successfully ACE (Punycode) decoded but the resulting
         * string had severe validation errors. For example,
         * it might contain characters that are not allowed in ACE labels,
         * or it might not be normalized.
         * @stable ICU 4.6
         */
        INVALID_ACE_LABEL,
        /**
         * A label does not meet the IDNA BiDi requirements (for right-to-left characters).
         * @stable ICU 4.6
         */
        BIDI,
        /**
         * A label does not meet the IDNA CONTEXTJ requirements.
         * @stable ICU 4.6
         */
        CONTEXTJ,
        /**
         * A label does not meet the IDNA CONTEXTO requirements for punctuation characters.
         * Some punctuation characters "Would otherwise have been DISALLOWED"
         * but are allowed in certain contexts. (RFC 5892)
         * @stable ICU 49
         */
        CONTEXTO_PUNCTUATION,
        /**
         * A label does not meet the IDNA CONTEXTO requirements for digits.
         * Arabic-Indic Digits (U+066x) must not be mixed with Extended Arabic-Indic Digits (U+06Fx).
         * @stable ICU 49
         */
        CONTEXTO_DIGITS
    }

    /**
     * Sole constructor. (For invocation by subclass constructors, typically implicit.)
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected IDNA() {
    }

}
