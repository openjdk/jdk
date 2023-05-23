// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
* Copyright (C) 2010-2014, International Business Machines
* Corporation and others.  All Rights Reserved.
*******************************************************************************
*/
package jdk.internal.icu.impl;

import java.util.EnumSet;
import java.text.ParseException;

import jdk.internal.icu.impl.Normalizer2Impl.UTF16Plus;
import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.lang.UCharacterCategory;
import jdk.internal.icu.lang.UCharacterDirection;
import jdk.internal.icu.text.IDNA;
import jdk.internal.icu.text.Normalizer2;
import jdk.internal.icu.util.ICUException;

// Note about tests for IDNA.Error.DOMAIN_NAME_TOO_LONG:
//
// The domain name length limit is 255 octets in an internal DNS representation
// where the last ("root") label is the empty label
// represented by length byte 0 alone.
// In a conventional string, this translates to 253 characters, or 254
// if there is a trailing dot for the root label.

/**
 * UTS #46 (IDNA2008) implementation.
 * @author Markus Scherer
 * @since 2010jul09
 */
public final class UTS46 extends IDNA {
    @SuppressWarnings("deprecation")
    public UTS46(int options) {
        this.options=options;
    }

    public StringBuilder labelToASCII(CharSequence label, StringBuilder dest, Info info) {
        return process(label, true, true, dest, info);
    }

    public StringBuilder labelToUnicode(CharSequence label, StringBuilder dest, Info info) {
        return process(label, true, false, dest, info);
    }

    public StringBuilder nameToASCII(CharSequence name, StringBuilder dest, Info info) {
        process(name, false, true, dest, info);
        if( dest.length()>=254 && !info.getErrors().contains(Error.DOMAIN_NAME_TOO_LONG) &&
            isASCIIString(dest) &&
            (dest.length()>254 || dest.charAt(253)!='.')
        ) {
            addError(info, Error.DOMAIN_NAME_TOO_LONG);
        }
        return dest;
    }

    public StringBuilder nameToUnicode(CharSequence name, StringBuilder dest, Info info) {
        return process(name, false, false, dest, info);
    }

    private static final Normalizer2 uts46Norm2=
        Normalizer2.getInstance(null, "uts46", Normalizer2.Mode.COMPOSE);  // uts46.nrm
    final int options;

    // Severe errors which usually result in a U+FFFD replacement character in the result string.
    private static final EnumSet<Error> severeErrors=EnumSet.of(
        Error.LEADING_COMBINING_MARK,
        Error.DISALLOWED,
        Error.PUNYCODE,
        Error.LABEL_HAS_DOT,
        Error.INVALID_ACE_LABEL);

    private static boolean
    isASCIIString(CharSequence dest) {
        int length=dest.length();
        for(int i=0; i<length; ++i) {
            if(dest.charAt(i)>0x7f) {
                return false;
            }
        }
        return true;
    }

    // UTS #46 data for ASCII characters.
    // The normalizer (using uts46.nrm) maps uppercase ASCII letters to lowercase
    // and passes through all other ASCII characters.
    // If USE_STD3_RULES is set, then non-LDH characters are disallowed
    // using this data.
    // The ASCII fastpath also uses this data.
    // Values: -1=disallowed  0==valid  1==mapped (lowercase)
    private static final byte asciiData[]={
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        // 002D..002E; valid  #  HYPHEN-MINUS..FULL STOP
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  0,  0, -1,
        // 0030..0039; valid  #  DIGIT ZERO..DIGIT NINE
         0,  0,  0,  0,  0,  0,  0,  0,  0,  0, -1, -1, -1, -1, -1, -1,
        // 0041..005A; mapped  #  LATIN CAPITAL LETTER A..LATIN CAPITAL LETTER Z
        -1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,
         1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1, -1, -1, -1, -1, -1,
        // 0061..007A; valid  #  LATIN SMALL LETTER A..LATIN SMALL LETTER Z
        -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
         0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, -1, -1, -1, -1, -1
    };

    private StringBuilder
    process(CharSequence src,
            boolean isLabel, boolean toASCII,
            StringBuilder dest,
            Info info) {
        // uts46Norm2.normalize() would do all of this error checking and setup,
        // but with the ASCII fastpath we do not always call it, and do not
        // call it first.
        if(dest==src) {
            throw new IllegalArgumentException();
        }
        // Arguments are fine, reset output values.
        dest.delete(0, 0x7fffffff);
        resetInfo(info);
        int srcLength=src.length();
        if(srcLength==0) {
            addError(info, Error.EMPTY_LABEL);
            return dest;
        }
        // ASCII fastpath
        boolean disallowNonLDHDot=(options&USE_STD3_RULES)!=0;
        int labelStart=0;
        int i;
        for(i=0;; ++i) {
            if(i==srcLength) {
                if(toASCII) {
                    if((i-labelStart)>63) {
                        addLabelError(info, Error.LABEL_TOO_LONG);
                    }
                    // There is a trailing dot if labelStart==i.
                    if(!isLabel && i>=254 && (i>254 || labelStart<i)) {
                        addError(info, Error.DOMAIN_NAME_TOO_LONG);
                    }
                }
                promoteAndResetLabelErrors(info);
                return dest;
            }
            char c=src.charAt(i);
            if(c>0x7f) {
                break;
            }
            int cData=asciiData[c];
            if(cData>0) {
                dest.append((char)(c+0x20));  // Lowercase an uppercase ASCII letter.
            } else if(cData<0 && disallowNonLDHDot) {
                break;  // Replacing with U+FFFD can be complicated for toASCII.
            } else {
                dest.append(c);
                if(c=='-') {  // hyphen
                    if(i==(labelStart+3) && src.charAt(i-1)=='-') {
                        // "??--..." is Punycode or forbidden.
                        ++i;  // '-' was copied to dest already
                        break;
                    }
                    if(i==labelStart) {
                        // label starts with "-"
                        addLabelError(info, Error.LEADING_HYPHEN);
                    }
                    if((i+1)==srcLength || src.charAt(i+1)=='.') {
                        // label ends with "-"
                        addLabelError(info, Error.TRAILING_HYPHEN);
                    }
                } else if(c=='.') {  // dot
                    if(isLabel) {
                        // Replacing with U+FFFD can be complicated for toASCII.
                        ++i;  // '.' was copied to dest already
                        break;
                    }
                    if(i==labelStart) {
                        addLabelError(info, Error.EMPTY_LABEL);
                    }
                    if(toASCII && (i-labelStart)>63) {
                        addLabelError(info, Error.LABEL_TOO_LONG);
                    }
                    promoteAndResetLabelErrors(info);
                    labelStart=i+1;
                }
            }
        }
        promoteAndResetLabelErrors(info);
        processUnicode(src, labelStart, i, isLabel, toASCII, dest, info);
        if( isBiDi(info) && !hasCertainErrors(info, severeErrors) &&
            (!isOkBiDi(info) || (labelStart>0 && !isASCIIOkBiDi(dest, labelStart)))
        ) {
            addError(info, Error.BIDI);
        }
        return dest;
    }

    private StringBuilder
    processUnicode(CharSequence src,
                   int labelStart, int mappingStart,
                   boolean isLabel, boolean toASCII,
                   StringBuilder dest,
                   Info info) {
        if(mappingStart==0) {
            uts46Norm2.normalize(src, dest);
        } else {
            uts46Norm2.normalizeSecondAndAppend(dest, src.subSequence(mappingStart, src.length()));
        }
        boolean doMapDevChars=
            toASCII ? (options&NONTRANSITIONAL_TO_ASCII)==0 :
                      (options&NONTRANSITIONAL_TO_UNICODE)==0;
        int destLength=dest.length();
        int labelLimit=labelStart;
        while(labelLimit<destLength) {
            char c=dest.charAt(labelLimit);
            if(c=='.' && !isLabel) {
                int labelLength=labelLimit-labelStart;
                int newLength=processLabel(dest, labelStart, labelLength,
                                                toASCII, info);
                promoteAndResetLabelErrors(info);
                destLength+=newLength-labelLength;
                labelLimit=labelStart+=newLength+1;
                continue;
            } else if(c<0xdf) {
                // pass
            } else if(c<=0x200d && (c==0xdf || c==0x3c2 || c>=0x200c)) {
                setTransitionalDifferent(info);
                if(doMapDevChars) {
                    destLength=mapDevChars(dest, labelStart, labelLimit);
                    // All deviation characters have been mapped, no need to check for them again.
                    doMapDevChars=false;
                    // Do not increment labelLimit in case c was removed.
                    continue;
                }
            } else if(Character.isSurrogate(c)) {
                if(UTF16Plus.isSurrogateLead(c) ?
                        (labelLimit+1)==destLength ||
                            !Character.isLowSurrogate(dest.charAt(labelLimit+1)) :
                        labelLimit==labelStart ||
                            !Character.isHighSurrogate(dest.charAt(labelLimit-1))) {
                    // Map an unpaired surrogate to U+FFFD before normalization so that when
                    // that removes characters we do not turn two unpaired ones into a pair.
                    addLabelError(info, Error.DISALLOWED);
                    dest.setCharAt(labelLimit, '\ufffd');
                }
            }
            ++labelLimit;
        }
        // Permit an empty label at the end (0<labelStart==labelLimit==destLength is ok)
        // but not an empty label elsewhere nor a completely empty domain name.
        // processLabel() sets UIDNA_ERROR_EMPTY_LABEL when labelLength==0.
        if(0==labelStart || labelStart<labelLimit) {
            processLabel(dest, labelStart, labelLimit-labelStart, toASCII, info);
            promoteAndResetLabelErrors(info);
        }
        return dest;
    }

    // returns the new dest.length()
    private int
    mapDevChars(StringBuilder dest, int labelStart, int mappingStart) {
        int length=dest.length();
        boolean didMapDevChars=false;
        for(int i=mappingStart; i<length;) {
            char c=dest.charAt(i);
            switch(c) {
            case 0xdf:
                // Map sharp s to ss.
                didMapDevChars=true;
                dest.setCharAt(i++, 's');
                dest.insert(i++, 's');
                ++length;
                break;
            case 0x3c2:  // Map final sigma to nonfinal sigma.
                didMapDevChars=true;
                dest.setCharAt(i++, '\u03c3');
                break;
            case 0x200c:  // Ignore/remove ZWNJ.
            case 0x200d:  // Ignore/remove ZWJ.
                didMapDevChars=true;
                dest.delete(i, i+1);
                --length;
                break;
            default:
                ++i;
                break;
            }
        }
        if(didMapDevChars) {
            // Mapping deviation characters might have resulted in an un-NFC string.
            // We could use either the NFC or the UTS #46 normalizer.
            // By using the UTS #46 normalizer again, we avoid having to load a second .nrm data file.
            String normalized=uts46Norm2.normalize(dest.subSequence(labelStart, dest.length()));
            dest.replace(labelStart, 0x7fffffff, normalized);
            return dest.length();
        }
        return length;
    }
    // Some non-ASCII characters are equivalent to sequences with
    // non-LDH ASCII characters. To find them:
    // grep disallowed_STD3_valid IdnaMappingTable.txt (or uts46.txt)
    private static boolean
    isNonASCIIDisallowedSTD3Valid(int c) {
        return c==0x2260 || c==0x226E || c==0x226F;
    }


    // Replace the label in dest with the label string, if the label was modified.
    // If label==dest then the label was modified in-place and labelLength
    // is the new label length, different from label.length().
    // If label!=dest then labelLength==label.length().
    // Returns labelLength (= the new label length).
    private static int
    replaceLabel(StringBuilder dest, int destLabelStart, int destLabelLength,
                 CharSequence label, int labelLength) {
        if(label!=dest) {
            dest.delete(destLabelStart, destLabelStart+destLabelLength).insert(destLabelStart, label);
            // or dest.replace(destLabelStart, destLabelStart+destLabelLength, label.toString());
            // which would create a String rather than moving characters in the StringBuilder.
        }
        return labelLength;
    }

    // returns the new label length
    private int
    processLabel(StringBuilder dest,
                 int labelStart, int labelLength,
                 boolean toASCII,
                 Info info) {
        StringBuilder fromPunycode;
        StringBuilder labelString;
        int destLabelStart=labelStart;
        int destLabelLength=labelLength;
        boolean wasPunycode;
        if( labelLength>=4 &&
            dest.charAt(labelStart)=='x' && dest.charAt(labelStart+1)=='n' &&
            dest.charAt(labelStart+2)=='-' && dest.charAt(labelStart+3)=='-'
        ) {
            // Label starts with "xn--", try to un-Punycode it.
            // In IDNA2008, labels like "xn--" (decodes to an empty string) and
            // "xn--ASCII-" (decodes to just "ASCII") fail the round-trip validation from
            // comparing the ToUnicode input with the back-to-ToASCII output.
            // They are alternate encodings of the respective ASCII labels.
            // Ignore "xn---" here: It will fail Punycode.decode() which logically comes before
            // the round-trip verification.
            if(labelLength==4 || (labelLength>5 && dest.charAt(labelStart+labelLength-1)=='-')) {
                addLabelError(info, Error.INVALID_ACE_LABEL);
                return markBadACELabel(dest, labelStart, labelLength, toASCII, info);
            }
            wasPunycode=true;
            try {
                fromPunycode=Punycode.decode(dest.subSequence(labelStart+4, labelStart+labelLength), null);
            } catch (ParseException e) {
                addLabelError(info, Error.PUNYCODE);
                return markBadACELabel(dest, labelStart, labelLength, toASCII, info);
            }
            // Check for NFC, and for characters that are not
            // valid or deviation characters according to the normalizer.
            // If there is something wrong, then the string will change.
            // Note that the normalizer passes through non-LDH ASCII and deviation characters.
            // Deviation characters are ok in Punycode even in transitional processing.
            // In the code further below, if we find non-LDH ASCII and we have UIDNA_USE_STD3_RULES
            // then we will set UIDNA_ERROR_INVALID_ACE_LABEL there too.
            boolean isValid=uts46Norm2.isNormalized(fromPunycode);
            if(!isValid) {
                addLabelError(info, Error.INVALID_ACE_LABEL);
                return markBadACELabel(dest, labelStart, labelLength, toASCII, info);
            }
            labelString=fromPunycode;
            labelStart=0;
            labelLength=fromPunycode.length();
        } else {
            wasPunycode=false;
            labelString=dest;
        }
        // Validity check
        if(labelLength==0) {
            addLabelError(info, Error.EMPTY_LABEL);
            return replaceLabel(dest, destLabelStart, destLabelLength, labelString, labelLength);
        }
        // labelLength>0
        if(labelLength>=4 && labelString.charAt(labelStart+2)=='-' && labelString.charAt(labelStart+3)=='-') {
            // label starts with "??--"
            addLabelError(info, Error.HYPHEN_3_4);
        }
        if(labelString.charAt(labelStart)=='-') {
            // label starts with "-"
            addLabelError(info, Error.LEADING_HYPHEN);
        }
        if(labelString.charAt(labelStart+labelLength-1)=='-') {
            // label ends with "-"
            addLabelError(info, Error.TRAILING_HYPHEN);
        }
        // If the label was not a Punycode label, then it was the result of
        // mapping, normalization and label segmentation.
        // If the label was in Punycode, then we mapped it again above
        // and checked its validity.
        // Now we handle the STD3 restriction to LDH characters (if set)
        // and we look for U+FFFD which indicates disallowed characters
        // in a non-Punycode label or U+FFFD itself in a Punycode label.
        // We also check for dots which can come from the input to a single-label function.
        // Ok to cast away const because we own the UnicodeString.
        int i=labelStart;
        int limit=labelStart+labelLength;
        char oredChars=0;
        // If we enforce STD3 rules, then ASCII characters other than LDH and dot are disallowed.
        boolean disallowNonLDHDot=(options&USE_STD3_RULES)!=0;
        do {
            char c=labelString.charAt(i);
            if(c<=0x7f) {
                if(c=='.') {
                    addLabelError(info, Error.LABEL_HAS_DOT);
                    labelString.setCharAt(i, '\ufffd');
                } else if(disallowNonLDHDot && asciiData[c]<0) {
                    addLabelError(info, Error.DISALLOWED);
                    labelString.setCharAt(i, '\ufffd');
                }
            } else {
                oredChars|=c;
                if(disallowNonLDHDot && isNonASCIIDisallowedSTD3Valid(c)) {
                    addLabelError(info, Error.DISALLOWED);
                    labelString.setCharAt(i, '\ufffd');
                } else if(c==0xfffd) {
                    addLabelError(info, Error.DISALLOWED);
                }
            }
            ++i;
        } while(i<limit);
        // Check for a leading combining mark after other validity checks
        // so that we don't report IDNA.Error.DISALLOWED for the U+FFFD from here.
        int c;
        // "Unsafe" is ok because unpaired surrogates were mapped to U+FFFD.
        c=labelString.codePointAt(labelStart);
        if((U_GET_GC_MASK(c)&U_GC_M_MASK)!=0) {
            addLabelError(info, Error.LEADING_COMBINING_MARK);
            labelString.setCharAt(labelStart, '\ufffd');
            if(c>0xffff) {
                // Remove c's trail surrogate.
                labelString.deleteCharAt(labelStart+1);
                --labelLength;
                if(labelString==dest) {
                    --destLabelLength;
                }
            }
        }
        if(!hasCertainLabelErrors(info, severeErrors)) {
            // Do contextual checks only if we do not have U+FFFD from a severe error
            // because U+FFFD can make these checks fail.
            if((options&CHECK_BIDI)!=0 && (!isBiDi(info) || isOkBiDi(info))) {
                checkLabelBiDi(labelString, labelStart, labelLength, info);
            }
            if( (options&CHECK_CONTEXTJ)!=0 && (oredChars&0x200c)==0x200c &&
                !isLabelOkContextJ(labelString, labelStart, labelLength)
            ) {
                addLabelError(info, Error.CONTEXTJ);
            }
            if(toASCII) {
                if(wasPunycode) {
                    // Leave a Punycode label unchanged if it has no severe errors.
                    if(destLabelLength>63) {
                        addLabelError(info, Error.LABEL_TOO_LONG);
                    }
                    return destLabelLength;
                } else if(oredChars>=0x80) {
                    // Contains non-ASCII characters.
                    StringBuilder punycode;
                    try {
                        punycode=Punycode.encode(labelString.subSequence(labelStart, labelStart+labelLength), null);
                    } catch (ParseException e) {
                        throw new ICUException(e);  // unexpected
                    }
                    punycode.insert(0, "xn--");
                    if(punycode.length()>63) {
                        addLabelError(info, Error.LABEL_TOO_LONG);
                    }
                    return replaceLabel(dest, destLabelStart, destLabelLength,
                                        punycode, punycode.length());
                } else {
                    // all-ASCII label
                    if(labelLength>63) {
                        addLabelError(info, Error.LABEL_TOO_LONG);
                    }
                }
            }
        } else {
            // If a Punycode label has severe errors,
            // then leave it but make sure it does not look valid.
            if(wasPunycode) {
                addLabelError(info, Error.INVALID_ACE_LABEL);
                return markBadACELabel(dest, destLabelStart, destLabelLength, toASCII, info);
            }
        }
        return replaceLabel(dest, destLabelStart, destLabelLength, labelString, labelLength);
    }
    private int
    markBadACELabel(StringBuilder dest,
                    int labelStart, int labelLength,
                    boolean toASCII, Info info) {
        boolean disallowNonLDHDot=(options&USE_STD3_RULES)!=0;
        boolean isASCII=true;
        boolean onlyLDH=true;
        int limit=labelStart+labelLength;
        // Start after the initial "xn--".
        for(int i=labelStart+4; i<limit; ++i) {
            char c=dest.charAt(i);
            if(c<=0x7f) {
                if(c=='.') {
                    addLabelError(info, Error.LABEL_HAS_DOT);
                    dest.setCharAt(i, '\ufffd');
                    isASCII=onlyLDH=false;
                } else if(asciiData[c]<0) {
                    onlyLDH=false;
                    if(disallowNonLDHDot) {
                        dest.setCharAt(i, '\ufffd');
                        isASCII=false;
                    }
                }
            } else {
                isASCII=onlyLDH=false;
            }
        }
        if(onlyLDH) {
            dest.insert(labelStart+labelLength, '\ufffd');
            ++labelLength;
        } else {
            if(toASCII && isASCII && labelLength>63) {
                addLabelError(info, Error.LABEL_TOO_LONG);
            }
        }
        return labelLength;
    }

    private static final int L_MASK=U_MASK(UCharacterDirection.LEFT_TO_RIGHT);
    private static final int R_AL_MASK=
        U_MASK(UCharacterDirection.RIGHT_TO_LEFT)|
        U_MASK(UCharacterDirection.RIGHT_TO_LEFT_ARABIC);
    private static final int L_R_AL_MASK=L_MASK|R_AL_MASK;

    private static final int R_AL_AN_MASK=R_AL_MASK|U_MASK(UCharacterDirection.ARABIC_NUMBER);

    private static final int EN_AN_MASK=
        U_MASK(UCharacterDirection.EUROPEAN_NUMBER)|
        U_MASK(UCharacterDirection.ARABIC_NUMBER);
    private static final int R_AL_EN_AN_MASK=R_AL_MASK|EN_AN_MASK;
    private static final int L_EN_MASK=L_MASK|U_MASK(UCharacterDirection.EUROPEAN_NUMBER);

    private static final int ES_CS_ET_ON_BN_NSM_MASK=
        U_MASK(UCharacterDirection.EUROPEAN_NUMBER_SEPARATOR)|
        U_MASK(UCharacterDirection.COMMON_NUMBER_SEPARATOR)|
        U_MASK(UCharacterDirection.EUROPEAN_NUMBER_TERMINATOR)|
        U_MASK(UCharacterDirection.OTHER_NEUTRAL)|
        U_MASK(UCharacterDirection.BOUNDARY_NEUTRAL)|
        U_MASK(UCharacterDirection.DIR_NON_SPACING_MARK);
    private static final int L_EN_ES_CS_ET_ON_BN_NSM_MASK=L_EN_MASK|ES_CS_ET_ON_BN_NSM_MASK;
    private static final int R_AL_AN_EN_ES_CS_ET_ON_BN_NSM_MASK=R_AL_MASK|EN_AN_MASK|ES_CS_ET_ON_BN_NSM_MASK;

    // We scan the whole label and check both for whether it contains RTL characters
    // and whether it passes the BiDi Rule.
    // In a BiDi domain name, all labels must pass the BiDi Rule, but we might find
    // that a domain name is a BiDi domain name (has an RTL label) only after
    // processing several earlier labels.
    private void
    checkLabelBiDi(CharSequence label, int labelStart, int labelLength, Info info) {
        // IDNA2008 BiDi rule
        // Get the directionality of the first character.
        int c;
        int i=labelStart;
        c=Character.codePointAt(label, i);
        i+=Character.charCount(c);
        int firstMask=U_MASK(UBiDiProps.INSTANCE.getClass(c));
        // 1. The first character must be a character with BIDI property L, R
        // or AL.  If it has the R or AL property, it is an RTL label; if it
        // has the L property, it is an LTR label.
        if((firstMask&~L_R_AL_MASK)!=0) {
            setNotOkBiDi(info);
        }
        // Get the directionality of the last non-NSM character.
        int lastMask;
        int labelLimit=labelStart+labelLength;
        for(;;) {
            if(i>=labelLimit) {
                lastMask=firstMask;
                break;
            }
            c=Character.codePointBefore(label, labelLimit);
            labelLimit-=Character.charCount(c);
            int dir=UBiDiProps.INSTANCE.getClass(c);
            if(dir!=UCharacterDirection.DIR_NON_SPACING_MARK) {
                lastMask=U_MASK(dir);
                break;
            }
        }
        // 3. In an RTL label, the end of the label must be a character with
        // BIDI property R, AL, EN or AN, followed by zero or more
        // characters with BIDI property NSM.
        // 6. In an LTR label, the end of the label must be a character with
        // BIDI property L or EN, followed by zero or more characters with
        // BIDI property NSM.
        if( (firstMask&L_MASK)!=0 ?
                (lastMask&~L_EN_MASK)!=0 :
                (lastMask&~R_AL_EN_AN_MASK)!=0
        ) {
            setNotOkBiDi(info);
        }
        // Add the directionalities of the intervening characters.
        int mask=firstMask|lastMask;
        while(i<labelLimit) {
            c=Character.codePointAt(label, i);
            i+=Character.charCount(c);
            mask|=U_MASK(UBiDiProps.INSTANCE.getClass(c));
        }
        if((firstMask&L_MASK)!=0) {
            // 5. In an LTR label, only characters with the BIDI properties L, EN,
            // ES, CS, ET, ON, BN and NSM are allowed.
            if((mask&~L_EN_ES_CS_ET_ON_BN_NSM_MASK)!=0) {
                setNotOkBiDi(info);
            }
        } else {
            // 2. In an RTL label, only characters with the BIDI properties R, AL,
            // AN, EN, ES, CS, ET, ON, BN and NSM are allowed.
            if((mask&~R_AL_AN_EN_ES_CS_ET_ON_BN_NSM_MASK)!=0) {
                setNotOkBiDi(info);
            }
            // 4. In an RTL label, if an EN is present, no AN may be present, and
            // vice versa.
            if((mask&EN_AN_MASK)==EN_AN_MASK) {
                setNotOkBiDi(info);
            }
        }
        // An RTL label is a label that contains at least one character of type
        // R, AL or AN. [...]
        // A "BIDI domain name" is a domain name that contains at least one RTL
        // label. [...]
        // The following rule, consisting of six conditions, applies to labels
        // in BIDI domain names.
        if((mask&R_AL_AN_MASK)!=0) {
            setBiDi(info);
        }
    }

    // Special code for the ASCII prefix of a BiDi domain name.
    // The ASCII prefix is all-LTR.

    // IDNA2008 BiDi rule, parts relevant to ASCII labels:
    // 1. The first character must be a character with BIDI property L [...]
    // 5. In an LTR label, only characters with the BIDI properties L, EN,
    // ES, CS, ET, ON, BN and NSM are allowed.
    // 6. In an LTR label, the end of the label must be a character with
    // BIDI property L or EN [...]

    // UTF-16 version, called for mapped ASCII prefix.
    // Cannot contain uppercase A-Z.
    // s[length-1] must be the trailing dot.
    private static boolean
    isASCIIOkBiDi(CharSequence s, int length) {
        int labelStart=0;
        for(int i=0; i<length; ++i) {
            char c=s.charAt(i);
            if(c=='.') {  // dot
                if(i>labelStart) {
                    c=s.charAt(i-1);
                    if(!('a'<=c && c<='z') && !('0'<=c && c<='9')) {
                        // Last character in the label is not an L or EN.
                        return false;
                    }
                }
                labelStart=i+1;
            } else if(i==labelStart) {
                if(!('a'<=c && c<='z')) {
                    // First character in the label is not an L.
                    return false;
                }
            } else {
                if(c<=0x20 && (c>=0x1c || (9<=c && c<=0xd))) {
                    // Intermediate character in the label is a B, S or WS.
                    return false;
                }
            }
        }
        return true;
    }

    private boolean
    isLabelOkContextJ(CharSequence label, int labelStart, int labelLength) {
        // [IDNA2008-Tables]
        // 200C..200D  ; CONTEXTJ    # ZERO WIDTH NON-JOINER..ZERO WIDTH JOINER
        int labelLimit=labelStart+labelLength;
        for(int i=labelStart; i<labelLimit; ++i) {
            if(label.charAt(i)==0x200c) {
                // Appendix A.1. ZERO WIDTH NON-JOINER
                // Rule Set:
                //  False;
                //  If Canonical_Combining_Class(Before(cp)) .eq.  Virama Then True;
                //  If RegExpMatch((Joining_Type:{L,D})(Joining_Type:T)*\u200C
                //     (Joining_Type:T)*(Joining_Type:{R,D})) Then True;
                if(i==labelStart) {
                    return false;
                }
                int c;
                int j=i;
                c=Character.codePointBefore(label, j);
                j-=Character.charCount(c);
                if(uts46Norm2.getCombiningClass(c)==9) {
                    continue;
                }
                // check precontext (Joining_Type:{L,D})(Joining_Type:T)*
                for(;;) {
                    /* UJoiningType */ int type=UBiDiProps.INSTANCE.getJoiningType(c);
                    if(type==UCharacter.JoiningType.TRANSPARENT) {
                        if(j==0) {
                            return false;
                        }
                        c=Character.codePointBefore(label, j);
                        j-=Character.charCount(c);
                    } else if(type==UCharacter.JoiningType.LEFT_JOINING || type==UCharacter.JoiningType.DUAL_JOINING) {
                        break;  // precontext fulfilled
                    } else {
                        return false;
                    }
                }
                // check postcontext (Joining_Type:T)*(Joining_Type:{R,D})
                for(j=i+1;;) {
                    if(j==labelLimit) {
                        return false;
                    }
                    c=Character.codePointAt(label, j);
                    j+=Character.charCount(c);
                    /* UJoiningType */ int type=UBiDiProps.INSTANCE.getJoiningType(c);
                    if(type==UCharacter.JoiningType.TRANSPARENT) {
                        // just skip this character
                    } else if(type==UCharacter.JoiningType.RIGHT_JOINING || type==UCharacter.JoiningType.DUAL_JOINING) {
                        break;  // postcontext fulfilled
                    } else {
                        return false;
                    }
                }
            } else if(label.charAt(i)==0x200d) {
                // Appendix A.2. ZERO WIDTH JOINER (U+200D)
                // Rule Set:
                //  False;
                //  If Canonical_Combining_Class(Before(cp)) .eq.  Virama Then True;
                if(i==labelStart) {
                    return false;
                }
                int c=Character.codePointBefore(label, i);
                if(uts46Norm2.getCombiningClass(c)!=9) {
                    return false;
                }
            }
        }
        return true;
    }

    // TODO: make public(?) -- in C, these are public in uchar.h
    private static int U_MASK(int x) {
        return 1<<x;
    }
    private static int U_GET_GC_MASK(int c) {
        return (1<<UCharacter.getType(c));
    }
    private static int U_GC_M_MASK=
        U_MASK(UCharacterCategory.NON_SPACING_MARK)|
        U_MASK(UCharacterCategory.ENCLOSING_MARK)|
        U_MASK(UCharacterCategory.COMBINING_SPACING_MARK);
}
