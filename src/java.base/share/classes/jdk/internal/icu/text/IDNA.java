// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
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
 * The IDNA class is not intended for public subclassing. This is a
 * highly abbreviated copy of the original class from ICU, containing
 * only that which java.net.IDN needs.
 *
 * @author Ram Viswanadha, Markus Scherer
 * @stable ICU 2.8
 */
public abstract class IDNA {
    /**
     * Default options value: None of the other options are set.
     * For use in static worker and factory methods.
     * @stable ICU 2.8
     */
    public static final int DEFAULT = 0;
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
     * <p>By default, ToASCII() uses transitional processing.
     * <p>This option is ignored by the IDNA2003 implementation.
     * (This is only relevant for compatibility of newer IDNA implementations with IDNA2003.)
     * @stable ICU 4.6
     */
    public static final int NONTRANSITIONAL_TO_ASCII = 0x10;
    /**
     * IDNA option for nontransitional processing in ToUnicode().
     * For use in static worker and factory methods.
     * <p>By default, ToUnicode() uses transitional processing.
     * <p>This option is ignored by the IDNA2003 implementation.
     * (This is only relevant for compatibility of newer IDNA implementations with IDNA2003.)
     * @stable ICU 4.6
     */
    public static final int NONTRANSITIONAL_TO_UNICODE = 0x20;
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
     */
    protected static void resetInfo(Info info) {
        info.reset();
    }
    /**
     * @internal
     */
    protected static boolean hasCertainErrors(Info info, EnumSet<Error> errors) {
        return !info.errors.isEmpty() && !Collections.disjoint(info.errors, errors);
    }
    /**
     * @internal
     */
    protected static boolean hasCertainLabelErrors(Info info, EnumSet<Error> errors) {
        return !info.labelErrors.isEmpty() && !Collections.disjoint(info.labelErrors, errors);
    }
    /**
     * @internal
     */
    protected static void addLabelError(Info info, Error error) {
        info.labelErrors.add(error);
    }
    /**
     * @internal
     */
    protected static void promoteAndResetLabelErrors(Info info) {
        if(!info.labelErrors.isEmpty()) {
            info.errors.addAll(info.labelErrors);
            info.labelErrors.clear();
        }
    }
    /**
     * @internal
     */
    protected static void addError(Info info, Error error) {
        info.errors.add(error);
    }
    /**
     * @internal
     */
    protected static void setTransitionalDifferent(Info info) {
        info.isTransDiff=true;
    }
    /**
     * @internal
     */
    protected static void setBiDi(Info info) {
        info.isBiDi=true;
    }
    /**
     * @internal
     */
    protected static boolean isBiDi(Info info) {
        return info.isBiDi;
    }
    /**
     * @internal
     */
    protected static void setNotOkBiDi(Info info) {
        info.isOkBiDi=false;
    }
    /**
     * @internal
     */
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
