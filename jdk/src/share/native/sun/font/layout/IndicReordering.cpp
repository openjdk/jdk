/*
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
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "OpenTypeUtilities.h"
#include "IndicReordering.h"
#include "LEGlyphStorage.h"
#include "MPreFixups.h"

U_NAMESPACE_BEGIN

#define initFeatureTag LE_INIT_FEATURE_TAG
#define nuktFeatureTag LE_NUKT_FEATURE_TAG
#define akhnFeatureTag LE_AKHN_FEATURE_TAG
#define rphfFeatureTag LE_RPHF_FEATURE_TAG
#define blwfFeatureTag LE_BLWF_FEATURE_TAG
#define halfFeatureTag LE_HALF_FEATURE_TAG
#define pstfFeatureTag LE_PSTF_FEATURE_TAG
#define vatuFeatureTag LE_VATU_FEATURE_TAG
#define presFeatureTag LE_PRES_FEATURE_TAG
#define blwsFeatureTag LE_BLWS_FEATURE_TAG
#define abvsFeatureTag LE_ABVS_FEATURE_TAG
#define pstsFeatureTag LE_PSTS_FEATURE_TAG
#define halnFeatureTag LE_HALN_FEATURE_TAG

#define blwmFeatureTag LE_BLWM_FEATURE_TAG
#define abvmFeatureTag LE_ABVM_FEATURE_TAG
#define distFeatureTag LE_DIST_FEATURE_TAG

#define rphfFeatureMask 0x80000000UL
#define blwfFeatureMask 0x40000000UL
#define halfFeatureMask 0x20000000UL
#define pstfFeatureMask 0x10000000UL
#define nuktFeatureMask 0x08000000UL
#define akhnFeatureMask 0x04000000UL
#define vatuFeatureMask 0x02000000UL
#define presFeatureMask 0x01000000UL
#define blwsFeatureMask 0x00800000UL
#define abvsFeatureMask 0x00400000UL
#define pstsFeatureMask 0x00200000UL
#define halnFeatureMask 0x00100000UL
#define blwmFeatureMask 0x00080000UL
#define abvmFeatureMask 0x00040000UL
#define distFeatureMask 0x00020000UL
#define initFeatureMask 0x00010000UL

class ReorderingOutput : public UMemory {
private:
    le_int32   fOutIndex;
    LEUnicode *fOutChars;

    LEGlyphStorage &fGlyphStorage;

    LEUnicode   fMpre;
    le_int32    fMpreIndex;

    LEUnicode   fMbelow;
    le_int32    fMbelowIndex;

    LEUnicode   fMabove;
    le_int32    fMaboveIndex;

    LEUnicode   fMpost;
    le_int32    fMpostIndex;

    LEUnicode   fLengthMark;
    le_int32    fLengthMarkIndex;

    LEUnicode   fVirama;
    le_int32    fViramaIndex;

    FeatureMask fMatraFeatures;

    le_int32    fMPreOutIndex;
    MPreFixups *fMPreFixups;

    LEUnicode   fVMabove;
    LEUnicode   fVMpost;
    le_int32    fVMIndex;
    FeatureMask fVMFeatures;

    LEUnicode   fSMabove;
    LEUnicode   fSMbelow;
    le_int32    fSMIndex;
    FeatureMask fSMFeatures;

    void saveMatra(LEUnicode matra, le_int32 matraIndex, IndicClassTable::CharClass matraClass)
    {
        // FIXME: check if already set, or if not a matra...
        if (IndicClassTable::isLengthMark(matraClass)) {
            fLengthMark = matra;
            fLengthMarkIndex = matraIndex;
        } else if (IndicClassTable::isVirama(matraClass)) {
            fVirama = matra;
            fViramaIndex = matraIndex;
        } else {
            switch (matraClass & CF_POS_MASK) {
            case CF_POS_BEFORE:
                fMpre = matra;
                fMpreIndex = matraIndex;
                break;

            case CF_POS_BELOW:
                fMbelow = matra;
                fMbelowIndex = matraIndex;
                break;

            case CF_POS_ABOVE:
                fMabove = matra;
                fMaboveIndex = matraIndex;
                break;

            case CF_POS_AFTER:
                fMpost = matra;
                fMpostIndex = matraIndex;
                break;

            default:
                // can't get here...
                break;
           }
        }
    }

public:
    ReorderingOutput(LEUnicode *outChars, LEGlyphStorage &glyphStorage, MPreFixups *mpreFixups)
        : fOutIndex(0), fOutChars(outChars), fGlyphStorage(glyphStorage),
          fMpre(0), fMpreIndex(0), fMbelow(0), fMbelowIndex(0), fMabove(0), fMaboveIndex(0),
          fMpost(0), fMpostIndex(0), fLengthMark(0), fLengthMarkIndex(0), fVirama(0), fViramaIndex(0),
          fMatraFeatures(0), fMPreOutIndex(-1), fMPreFixups(mpreFixups),
          fVMabove(0), fVMpost(0), fVMIndex(0), fVMFeatures(0),
          fSMabove(0), fSMbelow(0), fSMIndex(0), fSMFeatures(0)
    {
        // nothing else to do...
    }

    ~ReorderingOutput()
    {
        // nothing to do here...
    }

    void reset()
    {
        fMpre = fMbelow = fMabove = fMpost = fLengthMark = fVirama = 0;
        fMPreOutIndex = -1;

        fVMabove = fVMpost  = 0;
        fSMabove = fSMbelow = 0;
    }

    void writeChar(LEUnicode ch, le_uint32 charIndex, FeatureMask charFeatures)
    {
        LEErrorCode success = LE_NO_ERROR;

        fOutChars[fOutIndex] = ch;

        fGlyphStorage.setCharIndex(fOutIndex, charIndex, success);
        fGlyphStorage.setAuxData(fOutIndex, charFeatures, success);

        fOutIndex += 1;
    }

    le_bool noteMatra(const IndicClassTable *classTable, LEUnicode matra, le_uint32 matraIndex, FeatureMask matraFeatures, le_bool wordStart)
    {
        IndicClassTable::CharClass matraClass = classTable->getCharClass(matra);

        fMatraFeatures  = matraFeatures;

        if (wordStart) {
            fMatraFeatures |= initFeatureMask;
        }

        if (IndicClassTable::isMatra(matraClass)) {
            if (IndicClassTable::isSplitMatra(matraClass)) {
                const SplitMatra *splitMatra = classTable->getSplitMatra(matraClass);
                int i;

                for (i = 0; i < 3 && (*splitMatra)[i] != 0; i += 1) {
                    LEUnicode piece = (*splitMatra)[i];
                    IndicClassTable::CharClass pieceClass = classTable->getCharClass(piece);

                    saveMatra(piece, matraIndex, pieceClass);
                }
            } else {
                saveMatra(matra, matraIndex, matraClass);
            }

            return TRUE;
        }

        return FALSE;
    }

    void noteVowelModifier(const IndicClassTable *classTable, LEUnicode vowelModifier, le_uint32 vowelModifierIndex, FeatureMask vowelModifierFeatures)
    {
        IndicClassTable::CharClass vmClass = classTable->getCharClass(vowelModifier);

        fVMIndex = vowelModifierIndex;
        fVMFeatures  = vowelModifierFeatures;

        if (IndicClassTable::isVowelModifier(vmClass)) {
           switch (vmClass & CF_POS_MASK) {
           case CF_POS_ABOVE:
               fVMabove = vowelModifier;
               break;

           case CF_POS_AFTER:
               fVMpost = vowelModifier;
               break;

           default:
               // FIXME: this is an error...
               break;
           }
        }
    }

    void noteStressMark(const IndicClassTable *classTable, LEUnicode stressMark, le_uint32 stressMarkIndex, FeatureMask stressMarkFeatures)
    {
       IndicClassTable::CharClass smClass = classTable->getCharClass(stressMark);

        fSMIndex = stressMarkIndex;
        fSMFeatures  = stressMarkFeatures;

        if (IndicClassTable::isStressMark(smClass)) {
            switch (smClass & CF_POS_MASK) {
            case CF_POS_ABOVE:
                fSMabove = stressMark;
                break;

            case CF_POS_BELOW:
                fSMbelow = stressMark;
                break;

            default:
                // FIXME: this is an error...
                break;
           }
        }
    }

    void noteBaseConsonant()
    {
        if (fMPreFixups != NULL && fMPreOutIndex >= 0) {
            fMPreFixups->add(fOutIndex, fMPreOutIndex);
        }
    }

    // Handles virama in Sinhala split vowels.
    void writeVirama()
    {
        if (fVirama != 0) {
            writeChar(fVirama, fViramaIndex, fMatraFeatures);
        }
    }

    void writeMpre()
    {
        if (fMpre != 0) {
            fMPreOutIndex = fOutIndex;
            writeChar(fMpre, fMpreIndex, fMatraFeatures);
        }
    }

    void writeMbelow()
    {
        if (fMbelow != 0) {
            writeChar(fMbelow, fMbelowIndex, fMatraFeatures);
        }
    }

    void writeMabove()
    {
        if (fMabove != 0) {
            writeChar(fMabove, fMaboveIndex, fMatraFeatures);
        }
    }

    void writeMpost()
    {
        if (fMpost != 0) {
            writeChar(fMpost, fMpostIndex, fMatraFeatures);
        }
    }

    void writeLengthMark()
    {
        if (fLengthMark != 0) {
            writeChar(fLengthMark, fLengthMarkIndex, fMatraFeatures);
        }
    }

    void writeVMabove()
    {
        if (fVMabove != 0) {
            writeChar(fVMabove, fVMIndex, fVMFeatures);
        }
    }

    void writeVMpost()
    {
        if (fVMpost != 0) {
            writeChar(fVMpost, fVMIndex, fVMFeatures);
        }
    }

    void writeSMabove()
    {
        if (fSMabove != 0) {
            writeChar(fSMabove, fSMIndex, fSMFeatures);
        }
    }

    void writeSMbelow()
    {
        if (fSMbelow != 0) {
            writeChar(fSMbelow, fSMIndex, fSMFeatures);
        }
    }

    le_int32 getOutputIndex()
    {
        return fOutIndex;
    }
};

enum
{
    C_DOTTED_CIRCLE = 0x25CC
};

// TODO: Find better names for these!
#define tagArray4 (nuktFeatureMask | akhnFeatureMask | vatuFeatureMask | presFeatureMask | blwsFeatureMask | abvsFeatureMask | pstsFeatureMask | halnFeatureMask | blwmFeatureMask | abvmFeatureMask | distFeatureMask)
#define tagArray3 (pstfFeatureMask | tagArray4)
#define tagArray2 (halfFeatureMask | tagArray3)
#define tagArray1 (blwfFeatureMask | tagArray2)
#define tagArray0 (rphfFeatureMask | tagArray1)

static const FeatureMap featureMap[] =
{
    {initFeatureTag, initFeatureMask},
    {nuktFeatureTag, nuktFeatureMask},
    {akhnFeatureTag, akhnFeatureMask},
    {rphfFeatureTag, rphfFeatureMask},
    {blwfFeatureTag, blwfFeatureMask},
    {halfFeatureTag, halfFeatureMask},
    {pstfFeatureTag, pstfFeatureMask},
    {vatuFeatureTag, vatuFeatureMask},
    {presFeatureTag, presFeatureMask},
    {blwsFeatureTag, blwsFeatureMask},
    {abvsFeatureTag, abvsFeatureMask},
    {pstsFeatureTag, pstsFeatureMask},
    {halnFeatureTag, halnFeatureMask},
    {blwmFeatureTag, blwmFeatureMask},
    {abvmFeatureTag, abvmFeatureMask},
    {distFeatureTag, distFeatureMask}
};

static const le_int32 featureCount = LE_ARRAY_SIZE(featureMap);

static const le_int8 stateTable[][CC_COUNT] =
{
//   xx  vm  sm  iv  i2  i3  ct  cn  nu  dv  s1  s2  s3  vr  zw
    { 1,  1,  1,  5,  8, 11,  3,  2,  1,  5,  9,  5,  1,  1,  1}, //  0 - ground state
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, //  1 - exit state
    {-1,  6,  1, -1, -1, -1, -1, -1, -1,  5,  9,  5,  5,  4, -1}, //  2 - consonant with nukta
    {-1,  6,  1, -1, -1, -1, -1, -1,  2,  5,  9,  5,  5,  4, -1}, //  3 - consonant
    {-1, -1, -1, -1, -1, -1,  3,  2, -1, -1, -1, -1, -1, -1,  7}, //  4 - consonant virama
    {-1,  6,  1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, //  5 - dependent vowels
    {-1, -1,  1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, //  6 - vowel mark
    {-1, -1, -1, -1, -1, -1,  3,  2, -1, -1, -1, -1, -1, -1, -1}, //  7 - ZWJ, ZWNJ
    {-1,  6,  1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  4, -1}, //  8 - independent vowels that can take a virama
    {-1,  6,  1, -1, -1, -1, -1, -1, -1, -1, -1, 10,  5, -1, -1}, //  9 - first part of split vowel
    {-1,  6,  1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  5, -1, -1}, // 10 - second part of split vowel
    {-1,  6,  1, -1, -1, -1, -1, -1, -1,  5,  9,  5,  5,  4, -1}  // 11 - independent vowels that can take an iv
};


const FeatureMap *IndicReordering::getFeatureMap(le_int32 &count)
{
    count = featureCount;

    return featureMap;
}

le_int32 IndicReordering::findSyllable(const IndicClassTable *classTable, const LEUnicode *chars, le_int32 prev, le_int32 charCount)
{
    le_int32 cursor = prev;
    le_int8 state = 0;

    while (cursor < charCount) {
        IndicClassTable::CharClass charClass = classTable->getCharClass(chars[cursor]);

        state = stateTable[state][charClass & CF_CLASS_MASK];

        if (state < 0) {
            break;
        }

        cursor += 1;
    }

    return cursor;
}

le_int32 IndicReordering::reorder(const LEUnicode *chars, le_int32 charCount, le_int32 scriptCode,
                                  LEUnicode *outChars, LEGlyphStorage &glyphStorage,
                                  MPreFixups **outMPreFixups)
{
    MPreFixups *mpreFixups = NULL;
    const IndicClassTable *classTable = IndicClassTable::getScriptClassTable(scriptCode);

    if (classTable->scriptFlags & SF_MPRE_FIXUP) {
        mpreFixups = new MPreFixups(charCount);
    }

    ReorderingOutput output(outChars, glyphStorage, mpreFixups);
    le_int32 i, prev = 0;
    le_bool lastInWord = FALSE;

    while (prev < charCount) {
        le_int32 syllable = findSyllable(classTable, chars, prev, charCount);
        le_int32 matra, markStart = syllable;

        output.reset();

        if (classTable->isStressMark(chars[markStart - 1])) {
            markStart -= 1;
            output.noteStressMark(classTable, chars[markStart], markStart, tagArray1);
        }

        if (classTable->isVowelModifier(chars[markStart - 1])) {
            markStart -= 1;
            output.noteVowelModifier(classTable, chars[markStart], markStart, tagArray1);
        }

        matra = markStart - 1;

        while (output.noteMatra(classTable, chars[matra], matra, tagArray1, !lastInWord) && matra != prev) {
            matra -= 1;
        }

        lastInWord = TRUE;

        switch (classTable->getCharClass(chars[prev]) & CF_CLASS_MASK) {
        case CC_RESERVED:
            lastInWord = FALSE;
            /* fall through */

        case CC_INDEPENDENT_VOWEL:
        case CC_ZERO_WIDTH_MARK:
            for (i = prev; i < syllable; i += 1) {
                output.writeChar(chars[i], i, tagArray1);
            }

            break;

        case CC_NUKTA:
        case CC_VIRAMA:
            output.writeChar(C_DOTTED_CIRCLE, prev, tagArray1);
            output.writeChar(chars[prev], prev, tagArray1);
            break;

        case CC_DEPENDENT_VOWEL:
        case CC_SPLIT_VOWEL_PIECE_1:
        case CC_SPLIT_VOWEL_PIECE_2:
        case CC_SPLIT_VOWEL_PIECE_3:
        case CC_VOWEL_MODIFIER:
        case CC_STRESS_MARK:
            output.writeMpre();

            output.writeChar(C_DOTTED_CIRCLE, prev, tagArray1);

            output.writeMbelow();
            output.writeSMbelow();
            output.writeMabove();

            if ((classTable->scriptFlags & SF_MATRAS_AFTER_BASE) != 0) {
                output.writeMpost();
            }

            if ((classTable->scriptFlags & SF_REPH_AFTER_BELOW) != 0) {
                output.writeVMabove();
                output.writeSMabove(); // FIXME: there are no SM's in these scripts...
            }

            if ((classTable->scriptFlags & SF_MATRAS_AFTER_BASE) == 0) {
                output.writeMpost();
            }

            output.writeLengthMark();
            output.writeVirama();

            if ((classTable->scriptFlags & SF_REPH_AFTER_BELOW) == 0) {
                output.writeVMabove();
                output.writeSMabove();
            }

            output.writeVMpost();
            break;

        case CC_INDEPENDENT_VOWEL_2:
        case CC_INDEPENDENT_VOWEL_3:
        case CC_CONSONANT:
        case CC_CONSONANT_WITH_NUKTA:
        {
            le_uint32 length = markStart - prev;
            le_int32  lastConsonant = markStart - 1;
            le_int32  baseLimit = prev;

            // Check for REPH at front of syllable
            if (length > 2 && classTable->isReph(chars[prev]) && classTable->isVirama(chars[prev + 1])) {
                baseLimit += 2;

                // Check for eyelash RA, if the script supports it
                if ((classTable->scriptFlags & SF_EYELASH_RA) != 0 &&
                    chars[baseLimit] == C_SIGN_ZWJ) {
                    if (length > 3) {
                        baseLimit += 1;
                    } else {
                        baseLimit -= 2;
                    }
                }
            }

            while (lastConsonant > baseLimit && !classTable->isConsonant(chars[lastConsonant])) {
                lastConsonant -= 1;
            }

            le_int32 baseConsonant = lastConsonant;
            le_int32 postBase = lastConsonant + 1;
            le_int32 postBaseLimit = classTable->scriptFlags & SF_POST_BASE_LIMIT_MASK;
            le_bool  seenVattu = FALSE;
            le_bool  seenBelowBaseForm = FALSE;

            if (postBase < markStart && classTable->isNukta(chars[postBase])) {
                postBase += 1;
            }

            while (baseConsonant > baseLimit) {
                IndicClassTable::CharClass charClass = classTable->getCharClass(chars[baseConsonant]);

                if (IndicClassTable::isConsonant(charClass)) {
                    if (postBaseLimit == 0 || seenVattu ||
                        (baseConsonant > baseLimit && !classTable->isVirama(chars[baseConsonant - 1])) ||
                        !IndicClassTable::hasPostOrBelowBaseForm(charClass)) {
                        break;
                    }

                    seenVattu = IndicClassTable::isVattu(charClass);

                    if (IndicClassTable::hasPostBaseForm(charClass)) {
                        if (seenBelowBaseForm) {
                            break;
                        }

                        postBase = baseConsonant;
                    } else if (IndicClassTable::hasBelowBaseForm(charClass)) {
                        seenBelowBaseForm = TRUE;
                    }

                    postBaseLimit -= 1;
                }

                baseConsonant -= 1;
            }

            // Write Mpre
            output.writeMpre();

            // Write eyelash RA
            // NOTE: baseLimit == prev + 3 iff eyelash RA present...
            if (baseLimit == prev + 3) {
                output.writeChar(chars[prev], prev, tagArray2);
                output.writeChar(chars[prev + 1], prev + 1, tagArray2);
                output.writeChar(chars[prev + 2], prev + 2, tagArray2);
            }

            // write any pre-base consonants
            le_bool supressVattu = TRUE;

            for (i = baseLimit; i < baseConsonant; i += 1) {
                LEUnicode ch = chars[i];
                // Don't put 'blwf' on first consonant.
                FeatureMask features = (i == baseLimit? tagArray2 : tagArray1);
                IndicClassTable::CharClass charClass = classTable->getCharClass(ch);

                if (IndicClassTable::isConsonant(charClass)) {
                    if (IndicClassTable::isVattu(charClass) && supressVattu) {
                        features = tagArray4;
                    }

                    supressVattu = IndicClassTable::isVattu(charClass);
                } else if (IndicClassTable::isVirama(charClass) && chars[i + 1] == C_SIGN_ZWNJ)
                {
                    features = tagArray4;
                }

                output.writeChar(ch, i, features);
            }

            le_int32 bcSpan = baseConsonant + 1;

            if (bcSpan < markStart && classTable->isNukta(chars[bcSpan])) {
                bcSpan += 1;
            }

            if (baseConsonant == lastConsonant && bcSpan < markStart && classTable->isVirama(chars[bcSpan])) {
                bcSpan += 1;

                if (bcSpan < markStart && chars[bcSpan] == C_SIGN_ZWNJ) {
                    bcSpan += 1;
                }
            }

            // note the base consonant for post-GSUB fixups
            output.noteBaseConsonant();

            // write base consonant
            for (i = baseConsonant; i < bcSpan; i += 1) {
                output.writeChar(chars[i], i, tagArray4);
            }

            if ((classTable->scriptFlags & SF_MATRAS_AFTER_BASE) != 0) {
                output.writeMbelow();
                output.writeSMbelow(); // FIXME: there are no SMs in these scripts...
                output.writeMabove();
                output.writeMpost();
            }

            // write below-base consonants
            if (baseConsonant != lastConsonant) {
                for (i = bcSpan + 1; i < postBase; i += 1) {
                    output.writeChar(chars[i], i, tagArray1);
                }

                if (postBase > lastConsonant) {
                    // write halant that was after base consonant
                    output.writeChar(chars[bcSpan], bcSpan, tagArray1);
                }
            }

            // write Mbelow, SMbelow, Mabove
            if ((classTable->scriptFlags & SF_MATRAS_AFTER_BASE) == 0) {
                output.writeMbelow();
                output.writeSMbelow();
                output.writeMabove();
            }

            if ((classTable->scriptFlags & SF_REPH_AFTER_BELOW) != 0) {
                if (baseLimit == prev + 2) {
                    output.writeChar(chars[prev], prev, tagArray0);
                    output.writeChar(chars[prev + 1], prev + 1, tagArray0);
                }

                output.writeVMabove();
                output.writeSMabove(); // FIXME: there are no SM's in these scripts...
            }

            // write post-base consonants
            // FIXME: does this put the right tags on post-base consonants?
            if (baseConsonant != lastConsonant) {
                if (postBase <= lastConsonant) {
                    for (i = postBase; i <= lastConsonant; i += 1) {
                        output.writeChar(chars[i], i, tagArray3);
                    }

                    // write halant that was after base consonant
                    output.writeChar(chars[bcSpan], bcSpan, tagArray1);
                }

                // write the training halant, if there is one
                if (lastConsonant < matra && classTable->isVirama(chars[matra])) {
                    output.writeChar(chars[matra], matra, tagArray4);
                }
            }

            // write Mpost
            if ((classTable->scriptFlags & SF_MATRAS_AFTER_BASE) == 0) {
                output.writeMpost();
            }

            output.writeLengthMark();
            output.writeVirama();

            // write reph
            if ((classTable->scriptFlags & SF_REPH_AFTER_BELOW) == 0) {
                if (baseLimit == prev + 2) {
                    output.writeChar(chars[prev], prev, tagArray0);
                    output.writeChar(chars[prev + 1], prev + 1, tagArray0);
                }

                output.writeVMabove();
                output.writeSMabove();
            }

            output.writeVMpost();

            break;
        }

        default:
            break;
        }

        prev = syllable;
    }

    *outMPreFixups = mpreFixups;

    return output.getOutputIndex();
}

void IndicReordering::adjustMPres(MPreFixups *mpreFixups, LEGlyphStorage &glyphStorage)
{
    if (mpreFixups != NULL) {
        mpreFixups->apply(glyphStorage);

        delete mpreFixups;
    }
}

U_NAMESPACE_END
