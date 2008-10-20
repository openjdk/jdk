/*
 * Portions Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * (C) Copyright IBM Corp. 1999-2003 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 */

/*
*
******************************************************************************
*   file name:  ubidi.c
*   encoding:   US-ASCII
*   tab size:   8 (not used)
*   indentation:4
*
*   created on: 1999jul27
*   created by: Markus W. Scherer
*/

/* set import/export definitions */
#ifndef U_COMMON_IMPLEMENTATION
#   define U_COMMON_IMPLEMENTATION
#endif

#include "cmemory.h"
#include "utypes.h"
#include "uchardir.h"
#include "ubidi.h"
#include "ubidiimp.h"

/*
 * General implementation notes:
 *
 * Throughout the implementation, there are comments like (W2) that refer to
 * rules of the BiDi algorithm in its version 5, in this example to the second
 * rule of the resolution of weak types.
 *
 * For handling surrogate pairs, where two UChar's form one "abstract" (or UTF-32)
 * character according to UTF-16, the second UChar gets the directional property of
 * the entire character assigned, while the first one gets a BN, a boundary
 * neutral, type, which is ignored by most of the algorithm according to
 * rule (X9) and the implementation suggestions of the BiDi algorithm.
 *
 * Later, adjustWSLevels() will set the level for each BN to that of the
 * following character (UChar), which results in surrogate pairs getting the
 * same level on each of their surrogates.
 *
 * In a UTF-8 implementation, the same thing could be done: the last byte of
 * a multi-byte sequence would get the "real" property, while all previous
 * bytes of that sequence would get BN.
 *
 * It is not possible to assign all those parts of a character the same real
 * property because this would fail in the resolution of weak types with rules
 * that look at immediately surrounding types.
 *
 * As a related topic, this implementation does not remove Boundary Neutral
 * types from the input, but ignores them whereever this is relevant.
 * For example, the loop for the resolution of the weak types reads
 * types until it finds a non-BN.
 * Also, explicit embedding codes are neither changed into BN nor removed.
 * They are only treated the same way real BNs are.
 * As stated before, adjustWSLevels() takes care of them at the end.
 * For the purpose of conformance, the levels of all these codes
 * do not matter.
 *
 * Note that this implementation never modifies the dirProps
 * after the initial setup.
 *
 *
 * In this implementation, the resolution of weak types (Wn),
 * neutrals (Nn), and the assignment of the resolved level (In)
 * are all done in one single loop, in resolveImplicitLevels().
 * Changes of dirProp values are done on the fly, without writing
 * them back to the dirProps array.
 *
 *
 * This implementation contains code that allows to bypass steps of the
 * algorithm that are not needed on the specific paragraph
 * in order to speed up the most common cases considerably,
 * like text that is entirely LTR, or RTL text without numbers.
 *
 * Most of this is done by setting a bit for each directional property
 * in a flags variable and later checking for whether there are
 * any LTR characters or any RTL characters, or both, whether
 * there are any explicit embedding codes, etc.
 *
 * If the (Xn) steps are performed, then the flags are re-evaluated,
 * because they will then not contain the embedding codes any more
 * and will be adjusted for override codes, so that subsequently
 * more bypassing may be possible than what the initial flags suggested.
 *
 * If the text is not mixed-directional, then the
 * algorithm steps for the weak type resolution are not performed,
 * and all levels are set to the paragraph level.
 *
 * If there are no explicit embedding codes, then the (Xn) steps
 * are not performed.
 *
 * If embedding levels are supplied as a parameter, then all
 * explicit embedding codes are ignored, and the (Xn) steps
 * are not performed.
 *
 * White Space types could get the level of the run they belong to,
 * and are checked with a test of (flags&MASK_EMBEDDING) to
 * consider if the paragraph direction should be considered in
 * the flags variable.
 *
 * If there are no White Space types in the paragraph, then
 * (L1) is not necessary in adjustWSLevels().
 */

/* prototypes --------------------------------------------------------------- */

static void
getDirProps(UBiDi *pBiDi, const UChar *text);

static UBiDiDirection
resolveExplicitLevels(UBiDi *pBiDi);

static UBiDiDirection
checkExplicitLevels(UBiDi *pBiDi, UErrorCode *pErrorCode);

static UBiDiDirection
directionFromFlags(Flags flags);

static void
resolveImplicitLevels(UBiDi *pBiDi,
                      int32_t start, int32_t limit,
                      DirProp sor, DirProp eor);

static void
adjustWSLevels(UBiDi *pBiDi);

/* to avoid some conditional statements, use tiny constant arrays */
static const Flags flagLR[2]={ DIRPROP_FLAG(L), DIRPROP_FLAG(R) };
static const Flags flagE[2]={ DIRPROP_FLAG(LRE), DIRPROP_FLAG(RLE) };
static const Flags flagO[2]={ DIRPROP_FLAG(LRO), DIRPROP_FLAG(RLO) };

#define DIRPROP_FLAG_LR(level) flagLR[(level)&1]
#define DIRPROP_FLAG_E(level) flagE[(level)&1]
#define DIRPROP_FLAG_O(level) flagO[(level)&1]

/* UBiDi object management -------------------------------------------------- */

U_CAPI UBiDi * U_EXPORT2
ubidi_open(void)
{
    UErrorCode errorCode=U_ZERO_ERROR;
    return ubidi_openSized(0, 0, &errorCode);
}

U_CAPI UBiDi * U_EXPORT2
ubidi_openSized(int32_t maxLength, int32_t maxRunCount, UErrorCode *pErrorCode) {
    UBiDi *pBiDi;

    /* check the argument values */
    if(pErrorCode==NULL || U_FAILURE(*pErrorCode)) {
        return NULL;
    } else if(maxLength<0 || maxRunCount<0) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
        return NULL;    /* invalid arguments */
    }

    /* allocate memory for the object */
    pBiDi=(UBiDi *)icu_malloc(sizeof(UBiDi));
    if(pBiDi==NULL) {
        *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
        return NULL;
    }

    /* reset the object, all pointers NULL, all flags FALSE, all sizes 0 */
    icu_memset(pBiDi, 0, sizeof(UBiDi));

    /* allocate memory for arrays as requested */
    if(maxLength>0) {
        if( !getInitialDirPropsMemory(pBiDi, maxLength) ||
            !getInitialLevelsMemory(pBiDi, maxLength)
        ) {
            *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
        }
    } else {
        pBiDi->mayAllocateText=TRUE;
    }

    if(maxRunCount>0) {
        if(maxRunCount==1) {
            /* use simpleRuns[] */
            pBiDi->runsSize=sizeof(Run);
        } else if(!getInitialRunsMemory(pBiDi, maxRunCount)) {
            *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
        }
    } else {
        pBiDi->mayAllocateRuns=TRUE;
    }

    if(U_SUCCESS(*pErrorCode)) {
        return pBiDi;
    } else {
        ubidi_close(pBiDi);
        return NULL;
    }
}

/*
 * We are allowed to allocate memory if memory==NULL or
 * mayAllocate==TRUE for each array that we need.
 * We also try to grow and shrink memory as needed if we
 * allocate it.
 *
 * Assume sizeNeeded>0.
 * If *pMemory!=NULL, then assume *pSize>0.
 *
 * ### this realloc() may unnecessarily copy the old data,
 * which we know we don't need any more;
 * is this the best way to do this??
 */
extern bool_t
ubidi_getMemory(void **pMemory, int32_t *pSize, bool_t mayAllocate, int32_t sizeNeeded) {
    /* check for existing memory */
    if(*pMemory==NULL) {
        /* we need to allocate memory */
        if(mayAllocate && (*pMemory=icu_malloc(sizeNeeded))!=NULL) {
            *pSize=sizeNeeded;
            return TRUE;
        } else {
            return FALSE;
        }
    } else {
        /* there is some memory, is it enough or too much? */
        if(sizeNeeded>*pSize && !mayAllocate) {
            /* not enough memory, and we must not allocate */
            return FALSE;
        } else if(sizeNeeded!=*pSize && mayAllocate) {
            /* we may try to grow or shrink */
            void *memory;

            if((memory=icu_realloc(*pMemory, sizeNeeded))!=NULL) {
                *pMemory=memory;
                *pSize=sizeNeeded;
                return TRUE;
            } else {
                /* we failed to grow */
                return FALSE;
            }
        } else {
            /* we have at least enough memory and must not allocate */
            return TRUE;
        }
    }
}

U_CAPI void U_EXPORT2
ubidi_close(UBiDi *pBiDi) {
    if(pBiDi!=NULL) {
        if(pBiDi->dirPropsMemory!=NULL) {
            icu_free(pBiDi->dirPropsMemory);
        }
        if(pBiDi->levelsMemory!=NULL) {
            icu_free(pBiDi->levelsMemory);
        }
        if(pBiDi->runsMemory!=NULL) {
            icu_free(pBiDi->runsMemory);
        }
        icu_free(pBiDi);
    }
}

/* set to approximate "inverse BiDi" ---------------------------------------- */

U_CAPI void U_EXPORT2
ubidi_setInverse(UBiDi *pBiDi, bool_t isInverse) {
    if(pBiDi!=NULL) {
        pBiDi->isInverse=isInverse;
    }
}

U_CAPI bool_t U_EXPORT2
ubidi_isInverse(UBiDi *pBiDi) {
    if(pBiDi!=NULL) {
        return pBiDi->isInverse;
    } else {
        return FALSE;
    }
}

/* ubidi_setPara ------------------------------------------------------------ */

U_CAPI void U_EXPORT2
ubidi_setPara(UBiDi *pBiDi, const UChar *text, int32_t length,
              UBiDiLevel paraLevel, UBiDiLevel *embeddingLevels,
              UErrorCode *pErrorCode) {
    UBiDiDirection direction;

    /* check the argument values */
    if(pErrorCode==NULL || U_FAILURE(*pErrorCode)) {
        return;
    } else if(pBiDi==NULL || text==NULL ||
              ((UBIDI_MAX_EXPLICIT_LEVEL<paraLevel) && !IS_DEFAULT_LEVEL(paraLevel)) ||
              length<-1
    ) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
        return;
    }

    if(length==-1) {
        // length=u_strlen(text);
                const UChar *p = text - 1;
                while(*++p);
                length = p - text;
    }

    /* initialize the UBiDi structure */
    pBiDi->text=text;
    pBiDi->length=length;
    pBiDi->paraLevel=paraLevel;
    pBiDi->direction=UBIDI_LTR;
    pBiDi->trailingWSStart=length;  /* the levels[] will reflect the WS run */

    pBiDi->dirProps=NULL;
    pBiDi->levels=NULL;
    pBiDi->runs=NULL;

    if(length==0) {
        /*
         * For an empty paragraph, create a UBiDi object with the paraLevel and
         * the flags and the direction set but without allocating zero-length arrays.
         * There is nothing more to do.
         */
        if(IS_DEFAULT_LEVEL(paraLevel)) {
            pBiDi->paraLevel&=1;
        }
        if(paraLevel&1) {
            pBiDi->flags=DIRPROP_FLAG(R);
            pBiDi->direction=UBIDI_RTL;
        } else {
            pBiDi->flags=DIRPROP_FLAG(L);
            pBiDi->direction=UBIDI_LTR;
        }

        pBiDi->runCount=0;
        return;
    }

    pBiDi->runCount=-1;

    /*
     * Get the directional properties,
     * the flags bit-set, and
     * determine the partagraph level if necessary.
     */
    if(getDirPropsMemory(pBiDi, length)) {
        pBiDi->dirProps=pBiDi->dirPropsMemory;
        getDirProps(pBiDi, text);
    } else {
        *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
        return;
    }

    if (getLevelsMemory(pBiDi, length)) {
        pBiDi->levels=pBiDi->levelsMemory;
        /* are explicit levels specified? */
        if(embeddingLevels==NULL) {
            /* no: determine explicit levels according to the (Xn) rules */
            direction=resolveExplicitLevels(pBiDi);
        } else {
            /* set BN for all explicit codes, check that all levels are paraLevel..UBIDI_MAX_EXPLICIT_LEVEL */
            icu_memcpy(pBiDi->levels, embeddingLevels, length);
            direction=checkExplicitLevels(pBiDi, pErrorCode);
            if(U_FAILURE(*pErrorCode)) {
                 return;
            }
        }
    } else {
        *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
        return;
    }

    /*
     * The steps after (X9) in the UBiDi algorithm are performed only if
     * the paragraph text has mixed directionality!
     */
    pBiDi->direction=direction;
    switch(direction) {
    case UBIDI_LTR:
        /* make sure paraLevel is even */
        pBiDi->paraLevel=(UBiDiLevel)((pBiDi->paraLevel+1)&~1);

        /* all levels are implicitly at paraLevel (important for ubidi_getLevels()) */
        pBiDi->trailingWSStart=0;
        break;
    case UBIDI_RTL:
        /* make sure paraLevel is odd */
        pBiDi->paraLevel|=1;

        /* all levels are implicitly at paraLevel (important for ubidi_getLevels()) */
        pBiDi->trailingWSStart=0;
        break;
    default:
        /*
         * If there are no external levels specified and there
         * are no significant explicit level codes in the text,
         * then we can treat the entire paragraph as one run.
         * Otherwise, we need to perform the following rules on runs of
         * the text with the same embedding levels. (X10)
         * "Significant" explicit level codes are ones that actually
         * affect non-BN characters.
         * Examples for "insignificant" ones are empty embeddings
         * LRE-PDF, LRE-RLE-PDF-PDF, etc.
         */
        if(embeddingLevels==NULL && !(pBiDi->flags&DIRPROP_FLAG_MULTI_RUNS)) {
            resolveImplicitLevels(pBiDi, 0, length,
                                    GET_LR_FROM_LEVEL(pBiDi->paraLevel),
                                    GET_LR_FROM_LEVEL(pBiDi->paraLevel));
        } else {
            /* sor, eor: start and end types of same-level-run */
            UBiDiLevel *levels=pBiDi->levels;
            int32_t start, limit=0;
            UBiDiLevel level, nextLevel;
            DirProp sor, eor;

            /* determine the first sor and set eor to it because of the loop body (sor=eor there) */
            level=pBiDi->paraLevel;
            nextLevel=levels[0];
            if(level<nextLevel) {
                eor=GET_LR_FROM_LEVEL(nextLevel);
            } else {
                eor=GET_LR_FROM_LEVEL(level);
            }

            do {
                /* determine start and limit of the run (end points just behind the run) */

                /* the values for this run's start are the same as for the previous run's end */
                sor=eor;
                start=limit;
                level=nextLevel;

                /* search for the limit of this run */
                while(++limit<length && levels[limit]==level) {}

                /* get the correct level of the next run */
                if(limit<length) {
                    nextLevel=levels[limit];
                } else {
                    nextLevel=pBiDi->paraLevel;
                }

                /* determine eor from max(level, nextLevel); sor is last run's eor */
                if((level&~UBIDI_LEVEL_OVERRIDE)<(nextLevel&~UBIDI_LEVEL_OVERRIDE)) {
                    eor=GET_LR_FROM_LEVEL(nextLevel);
                } else {
                    eor=GET_LR_FROM_LEVEL(level);
                }

                /* if the run consists of overridden directional types, then there
                   are no implicit types to be resolved */
                if(!(level&UBIDI_LEVEL_OVERRIDE)) {
                    resolveImplicitLevels(pBiDi, start, limit, sor, eor);
                } else {
                    /* remove the UBIDI_LEVEL_OVERRIDE flags */
                    do {
                        levels[start++]&=~UBIDI_LEVEL_OVERRIDE;
                    } while(start<limit);
                }
            } while(limit<length);
        }

        /* reset the embedding levels for some non-graphic characters (L1), (X9) */
        adjustWSLevels(pBiDi);

        /* for "inverse BiDi", ubidi_getRuns() modifies the levels of numeric runs following RTL runs */
        if(pBiDi->isInverse) {
            if(!ubidi_getRuns(pBiDi)) {
                *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
                return;
            }
        }
        break;
    }
}

/* perform (P2)..(P3) ------------------------------------------------------- */

/*
 * Get the directional properties for the text,
 * calculate the flags bit-set, and
 * determine the partagraph level if necessary.
 */
static void
getDirProps(UBiDi *pBiDi, const UChar *text) {
    DirProp *dirProps=pBiDi->dirPropsMemory;    /* pBiDi->dirProps is const */

    int32_t i=0, i0, i1, length=pBiDi->length;
    Flags flags=0;      /* collect all directionalities in the text */
    UChar uchar;
    DirProp dirProp;

    if(IS_DEFAULT_LEVEL(pBiDi->paraLevel)) {
        /* determine the paragraph level (P2..P3) */
        for(;;) {
            uchar=text[i];
            if(!IS_FIRST_SURROGATE(uchar) || i+1==length || !IS_SECOND_SURROGATE(text[i+1])) {
                /* not a surrogate pair */
                flags|=DIRPROP_FLAG(dirProps[i]=dirProp=u_charDirection(uchar));
            } else {
                /* a surrogate pair */
                dirProps[i++]=BN;   /* first surrogate in the pair gets the BN type */
                flags|=DIRPROP_FLAG(dirProps[i]=dirProp=u_surrogatePairDirection(uchar, text[i]))|DIRPROP_FLAG(BN);
            }
            ++i;
            if(dirProp==L) {
                pBiDi->paraLevel=0;
                break;
            } else if(dirProp==R || dirProp==AL) {
                pBiDi->paraLevel=1;
                break;
            } else if(i>=length) {
                /*
                 * see comment in ubidi.h:
                 * the DEFAULT_XXX values are designed so that
                 * their bit 0 alone yields the intended default
                 */
                pBiDi->paraLevel&=1;
                break;
            }
        }
    } else {
        flags|=DIRPROP_FLAG_LR(pBiDi->paraLevel);
    }

    /* get the rest of the directional properties and the flags bits */
    while(i<length) {
        uchar=text[i];
        if(!IS_FIRST_SURROGATE(uchar) || i+1==length || !IS_SECOND_SURROGATE(text[i+1])) {
            /* not a surrogate pair */
            flags|=DIRPROP_FLAG(dirProps[i]=u_charDirection(uchar));
        } else {
            /* a surrogate pair */
            dirProps[i++]=BN;   /* first surrogate in the pair gets the BN type */
            flags|=DIRPROP_FLAG(dirProps[i]=dirProp=u_surrogatePairDirection(uchar, text[i]))|DIRPROP_FLAG(BN);
        }
        ++i;
    }
    if(flags&MASK_EMBEDDING) {
        flags|=DIRPROP_FLAG_LR(pBiDi->paraLevel);
    }

    pBiDi->flags=flags;
}

/* perform (X1)..(X9) ------------------------------------------------------- */

/*
 * Resolve the explicit levels as specified by explicit embedding codes.
 * Recalculate the flags to have them reflect the real properties
 * after taking the explicit embeddings into account.
 *
 * The BiDi algorithm is designed to result in the same behavior whether embedding
 * levels are externally specified (from "styled text", supposedly the preferred
 * method) or set by explicit embedding codes (LRx, RLx, PDF) in the plain text.
 * That is why (X9) instructs to remove all explicit codes (and BN).
 * However, in a real implementation, this removal of these codes and their index
 * positions in the plain text is undesirable since it would result in
 * reallocated, reindexed text.
 * Instead, this implementation leaves the codes in there and just ignores them
 * in the subsequent processing.
 * In order to get the same reordering behavior, positions with a BN or an
 * explicit embedding code just get the same level assigned as the last "real"
 * character.
 *
 * Some implementations, not this one, then overwrite some of these
 * directionality properties at "real" same-level-run boundaries by
 * L or R codes so that the resolution of weak types can be performed on the
 * entire paragraph at once instead of having to parse it once more and
 * perform that resolution on same-level-runs.
 * This limits the scope of the implicit rules in effectively
 * the same way as the run limits.
 *
 * Instead, this implementation does not modify these codes.
 * On one hand, the paragraph has to be scanned for same-level-runs, but
 * on the other hand, this saves another loop to reset these codes,
 * or saves making and modifying a copy of dirProps[].
 *
 *
 * Note that (Pn) and (Xn) changed significantly from version 4 of the BiDi algorithm.
 *
 *
 * Handling the stack of explicit levels (Xn):
 *
 * With the BiDi stack of explicit levels,
 * as pushed with each LRE, RLE, LRO, and RLO and popped with each PDF,
 * the explicit level must never exceed UBIDI_MAX_EXPLICIT_LEVEL==61.
 *
 * In order to have a correct push-pop semantics even in the case of overflows,
 * there are two overflow counters:
 * - countOver60 is incremented with each LRx at level 60
 * - from level 60, one RLx increases the level to 61
 * - countOver61 is incremented with each LRx and RLx at level 61
 *
 * Popping levels with PDF must work in the opposite order so that level 61
 * is correct at the correct point. Underflows (too many PDFs) must be checked.
 *
 * This implementation assumes that UBIDI_MAX_EXPLICIT_LEVEL is odd.
 */

static UBiDiDirection
resolveExplicitLevels(UBiDi *pBiDi) {
    const DirProp *dirProps=pBiDi->dirProps;
    UBiDiLevel *levels=pBiDi->levels;

    int32_t i=0, length=pBiDi->length;
    Flags flags=pBiDi->flags;       /* collect all directionalities in the text */
    DirProp dirProp;
    UBiDiLevel level=pBiDi->paraLevel;

    UBiDiDirection direction;

    /* determine if the text is mixed-directional or single-directional */
    direction=directionFromFlags(flags);

    /* we may not need to resolve any explicit levels */
    if(direction!=UBIDI_MIXED) {
        /* not mixed directionality: levels don't matter - trailingWSStart will be 0 */
    } else if(!(flags&MASK_EXPLICIT) || pBiDi->isInverse) {
        /* mixed, but all characters are at the same embedding level */
        /* or we are in "inverse BiDi" */
        /* set all levels to the paragraph level */
        for(i=0; i<length; ++i) {
            levels[i]=level;
        }
    } else {
        /* continue to perform (Xn) */

        /* (X1) level is set for all codes, embeddingLevel keeps track of the push/pop operations */
        /* both variables may carry the UBIDI_LEVEL_OVERRIDE flag to indicate the override status */
        UBiDiLevel embeddingLevel=level, newLevel, stackTop=0;

        UBiDiLevel stack[UBIDI_MAX_EXPLICIT_LEVEL];        /* we never push anything >=UBIDI_MAX_EXPLICIT_LEVEL */
        uint32_t countOver60=0, countOver61=0;  /* count overflows of explicit levels */

        /* recalculate the flags */
        flags=0;

        /* since we assume that this is a single paragraph, we ignore (X8) */
        for(i=0; i<length; ++i) {
            dirProp=dirProps[i];
            switch(dirProp) {
            case LRE:
            case LRO:
                /* (X3, X5) */
                newLevel=(UBiDiLevel)((embeddingLevel+2)&~(UBIDI_LEVEL_OVERRIDE|1)); /* least greater even level */
                if(newLevel<=UBIDI_MAX_EXPLICIT_LEVEL) {
                    stack[stackTop]=embeddingLevel;
                    ++stackTop;
                    embeddingLevel=newLevel;
                    if(dirProp==LRO) {
                        embeddingLevel|=UBIDI_LEVEL_OVERRIDE;
                    } else {
                        embeddingLevel&=~UBIDI_LEVEL_OVERRIDE;
                    }
                } else if((embeddingLevel&~UBIDI_LEVEL_OVERRIDE)==UBIDI_MAX_EXPLICIT_LEVEL) {
                    ++countOver61;
                } else /* (embeddingLevel&~UBIDI_LEVEL_OVERRIDE)==UBIDI_MAX_EXPLICIT_LEVEL-1 */ {
                    ++countOver60;
                }
                flags|=DIRPROP_FLAG(BN);
                break;
            case RLE:
            case RLO:
                /* (X2, X4) */
                newLevel=(UBiDiLevel)(((embeddingLevel&~UBIDI_LEVEL_OVERRIDE)+1)|1); /* least greater odd level */
                if(newLevel<=UBIDI_MAX_EXPLICIT_LEVEL) {
                    stack[stackTop]=embeddingLevel;
                    ++stackTop;
                    embeddingLevel=newLevel;
                    if(dirProp==RLO) {
                        embeddingLevel|=UBIDI_LEVEL_OVERRIDE;
                    } else {
                        embeddingLevel&=~UBIDI_LEVEL_OVERRIDE;
                    }
                } else {
                    ++countOver61;
                }
                flags|=DIRPROP_FLAG(BN);
                break;
            case PDF:
                /* (X7) */
                /* handle all the overflow cases first */
                if(countOver61>0) {
                    --countOver61;
                } else if(countOver60>0 && (embeddingLevel&~UBIDI_LEVEL_OVERRIDE)!=UBIDI_MAX_EXPLICIT_LEVEL) {
                    /* handle LRx overflows from level 60 */
                    --countOver60;
                } else if(stackTop>0) {
                    /* this is the pop operation; it also pops level 61 while countOver60>0 */
                    --stackTop;
                    embeddingLevel=stack[stackTop];
                /* } else { (underflow) */
                }
                flags|=DIRPROP_FLAG(BN);
                break;
            case B:
                /*
                 * We do not really expect to see a paragraph separator (B),
                 * but we should do something reasonable with it,
                 * especially at the end of the text.
                 */
                stackTop=0;
                countOver60=countOver61=0;
                embeddingLevel=level=pBiDi->paraLevel;
                flags|=DIRPROP_FLAG(B);
                break;
            case BN:
                /* BN, LRE, RLE, and PDF are supposed to be removed (X9) */
                /* they will get their levels set correctly in adjustWSLevels() */
                flags|=DIRPROP_FLAG(BN);
                break;
            default:
                /* all other types get the "real" level */
                if(level!=embeddingLevel) {
                    level=embeddingLevel;
                    if(level&UBIDI_LEVEL_OVERRIDE) {
                        flags|=DIRPROP_FLAG_O(level)|DIRPROP_FLAG_MULTI_RUNS;
                    } else {
                        flags|=DIRPROP_FLAG_E(level)|DIRPROP_FLAG_MULTI_RUNS;
                    }
                }
                if(!(level&UBIDI_LEVEL_OVERRIDE)) {
                    flags|=DIRPROP_FLAG(dirProp);
                }
                break;
            }

            /*
             * We need to set reasonable levels even on BN codes and
             * explicit codes because we will later look at same-level runs (X10).
             */
            levels[i]=level;
        }
        if(flags&MASK_EMBEDDING) {
            flags|=DIRPROP_FLAG_LR(pBiDi->paraLevel);
        }

        /* subsequently, ignore the explicit codes and BN (X9) */

        /* again, determine if the text is mixed-directional or single-directional */
        pBiDi->flags=flags;
        direction=directionFromFlags(flags);
    }
    return direction;
}

/*
 * Use a pre-specified embedding levels array:
 *
 * Adjust the directional properties for overrides (->LEVEL_OVERRIDE),
 * ignore all explicit codes (X9),
 * and check all the preset levels.
 *
 * Recalculate the flags to have them reflect the real properties
 * after taking the explicit embeddings into account.
 */
static UBiDiDirection
checkExplicitLevels(UBiDi *pBiDi, UErrorCode *pErrorCode) {
    const DirProp *dirProps=pBiDi->dirProps;
    UBiDiLevel *levels=pBiDi->levels;

    int32_t i, length=pBiDi->length;
    Flags flags=0;  /* collect all directionalities in the text */
    UBiDiLevel level, paraLevel=pBiDi->paraLevel;

    for(i=0; i<length; ++i) {
      // dlf: we special case levels array for java, 0 means base level, not actually 0
      if (levels[i] == 0) {
        levels[i] = paraLevel;
      }
        level=levels[i];
        if(level&UBIDI_LEVEL_OVERRIDE) {
            /* keep the override flag in levels[i] but adjust the flags */
            level&=~UBIDI_LEVEL_OVERRIDE;     /* make the range check below simpler */
            flags|=DIRPROP_FLAG_O(level);
        } else {
            /* set the flags */
            flags|=DIRPROP_FLAG_E(level)|DIRPROP_FLAG(dirProps[i]);
        }
        if(level<paraLevel || UBIDI_MAX_EXPLICIT_LEVEL<level) {
            /* level out of bounds */
            *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
            return UBIDI_LTR;
        }
    }
    if(flags&MASK_EMBEDDING) {
        flags|=DIRPROP_FLAG_LR(pBiDi->paraLevel);
    }

    /* determine if the text is mixed-directional or single-directional */
    pBiDi->flags=flags;
    return directionFromFlags(flags);
}

/* determine if the text is mixed-directional or single-directional */
static UBiDiDirection
directionFromFlags(Flags flags) {
    /* if the text contains AN and neutrals, then some neutrals may become RTL */
    if(!(flags&MASK_RTL || ((flags&DIRPROP_FLAG(AN)) && (flags&MASK_POSSIBLE_N)))) {
        return UBIDI_LTR;
    } else if(!(flags&MASK_LTR)) {
        return UBIDI_RTL;
    } else {
        return UBIDI_MIXED;
    }
}

/* perform rules (Wn), (Nn), and (In) on a run of the text ------------------ */

/*
 * This implementation of the (Wn) rules applies all rules in one pass.
 * In order to do so, it needs a look-ahead of typically 1 character
 * (except for W5: sequences of ET) and keeps track of changes
 * in a rule Wp that affect a later Wq (p<q).
 *
 * historyOfEN is a variable-saver: it contains 4 boolean states;
 * a bit in it set to 1 means:
 *  bit 0: the current code is an EN after W2
 *  bit 1: the current code is an EN after W4
 *  bit 2: the previous code was an EN after W2
 *  bit 3: the previous code was an EN after W4
 * In other words, b0..1 have transitions of EN in the current iteration,
 * while b2..3 have the transitions of EN in the previous iteration.
 * A simple historyOfEN<<=2 suffices for the propagation.
 *
 * The (Nn) and (In) rules are also performed in that same single loop,
 * but effectively one iteration behind for white space.
 *
 * Since all implicit rules are performed in one step, it is not necessary
 * to actually store the intermediate directional properties in dirProps[].
 */

#define EN_SHIFT 2
#define EN_AFTER_W2 1
#define EN_AFTER_W4 2
#define EN_ALL 3
#define PREV_EN_AFTER_W2 4
#define PREV_EN_AFTER_W4 8

static void
resolveImplicitLevels(UBiDi *pBiDi,
                      int32_t start, int32_t limit,
                      DirProp sor, DirProp eor) {
    const DirProp *dirProps=pBiDi->dirProps;
    UBiDiLevel *levels=pBiDi->levels;

    int32_t i, next, neutralStart=-1;
    DirProp prevDirProp, dirProp, nextDirProp, lastStrong, beforeNeutral=L;
    UBiDiLevel numberLevel;
    uint8_t historyOfEN;

    /* initialize: current at sor, next at start (it is start<limit) */
    next=start;
    dirProp=lastStrong=sor;
    nextDirProp=dirProps[next];
    historyOfEN=0;

    if(pBiDi->isInverse) {
        /*
         * For "inverse BiDi", we set the levels of numbers just like for
         * regular L characters, plus a flag that ubidi_getRuns() will use
         * to set a similar flag on the corresponding output run.
         */
        numberLevel=levels[start];
        if(numberLevel&1) {
            ++numberLevel;
        }
    } else {
        /* normal BiDi: least greater even level */
        numberLevel=(UBiDiLevel)((levels[start]+2)&~1);
    }

    /*
     * In all steps of this implementation, BN and explicit embedding codes
     * must be treated as if they didn't exist (X9).
     * They will get levels set before a non-neutral character, and remain
     * undefined before a neutral one, but adjustWSLevels() will take care
     * of all of them.
     */
    while(DIRPROP_FLAG(nextDirProp)&MASK_BN_EXPLICIT) {
        if(++next<limit) {
            nextDirProp=dirProps[next];
        } else {
            nextDirProp=eor;
            break;
        }
    }

    /*
     * Note: at the end of this file, there is a prototype
     * of a version of this function that uses a statetable
     * at the core of this state machine.
     * If you make changes to this state machine,
     * please update that prototype as well.
     */

    /* loop for entire run */
    while(next<limit) {
        /* advance */
        prevDirProp=dirProp;
        dirProp=nextDirProp;
        i=next;
        do {
            if(++next<limit) {
                nextDirProp=dirProps[next];
            } else {
                nextDirProp=eor;
                break;
            }
        } while(DIRPROP_FLAG(nextDirProp)&MASK_BN_EXPLICIT);
        historyOfEN<<=EN_SHIFT;

        /* (W1..W7) */
        switch(dirProp) {
        case L:
            lastStrong=L;
            break;
        case R:
            lastStrong=R;
            break;
        case AL:
            /* (W3) */
            lastStrong=AL;
            dirProp=R;
            break;
        case EN:
            /* we have to set historyOfEN correctly */
            if(lastStrong==AL) {
                /* (W2) */
                dirProp=AN;
            } else {
                if(lastStrong==L) {
                    /* (W7) */
                    dirProp=L;
                }
                /* this EN stays after (W2) and (W4) - at least before (W7) */
                historyOfEN|=EN_ALL;
            }
            break;
        case ES:
            if( historyOfEN&PREV_EN_AFTER_W2 &&     /* previous was EN before (W4) */
                nextDirProp==EN && lastStrong!=AL   /* next is EN and (W2) won't make it AN */
            ) {
                /* (W4) */
                if(lastStrong!=L) {
                    dirProp=EN;
                } else {
                    /* (W7) */
                    dirProp=L;
                }
                historyOfEN|=EN_AFTER_W4;
            } else {
                /* (W6) */
                dirProp=ON;
            }
            break;
        case CS:
            if( historyOfEN&PREV_EN_AFTER_W2 &&     /* previous was EN before (W4) */
                nextDirProp==EN && lastStrong!=AL   /* next is EN and (W2) won't make it AN */
            ) {
                /* (W4) */
                if(lastStrong!=L) {
                    dirProp=EN;
                } else {
                    /* (W7) */
                    dirProp=L;
                }
                historyOfEN|=EN_AFTER_W4;
            } else if(prevDirProp==AN &&                    /* previous was AN */
                      (nextDirProp==AN ||                   /* next is AN */
                      (nextDirProp==EN && lastStrong==AL))  /* or (W2) will make it one */
            ) {
                /* (W4) */
                dirProp=AN;
            } else {
                /* (W6) */
                dirProp=ON;
            }
            break;
        case ET:
            /* get sequence of ET; advance only next, not current, previous or historyOfEN */
            if(next<limit) {
                while(DIRPROP_FLAG(nextDirProp)&MASK_ET_NSM_BN /* (W1), (X9) */) {
                    if(++next<limit) {
                        nextDirProp=dirProps[next];
                    } else {
                        nextDirProp=eor;
                        break;
                    }
                }
            }

            /* now process the sequence of ET like a single ET */
            if((historyOfEN&PREV_EN_AFTER_W4) ||     /* previous was EN before (W5) */
                (nextDirProp==EN && lastStrong!=AL)   /* next is EN and (W2) won't make it AN */
            ) {
                /* (W5) */
                if(lastStrong!=L) {
                    dirProp=EN;
                } else {
                    /* (W7) */
                    dirProp=L;
                }
            } else {
                /* (W6) */
                dirProp=ON;
            }

            /* apply the result of (W1), (W5)..(W7) to the entire sequence of ET */
            break;
        case NSM:
            /* (W1) */
            dirProp=prevDirProp;
            /* set historyOfEN back to prevDirProp's historyOfEN */
            historyOfEN>>=EN_SHIFT;
            /*
             * Technically, this should be done before the switch() in the form
             *      if(nextDirProp==NSM) {
             *          dirProps[next]=nextDirProp=dirProp;
             *      }
             *
             * - effectively one iteration ahead.
             * However, whether the next dirProp is NSM or is equal to the current dirProp
             * does not change the outcome of any condition in (W2)..(W7).
             */
            break;
        default:
            break;
        }

        /* here, it is always [prev,this,next]dirProp!=BN; it may be next>i+1 */

        /* perform (Nn) - here, only L, R, EN, AN, and neutrals are left */
        /* for "inverse BiDi", treat neutrals like L */
        /* this is one iteration late for the neutrals */
        if(DIRPROP_FLAG(dirProp)&MASK_N) {
            if(neutralStart<0) {
                /* start of a sequence of neutrals */
                neutralStart=i;
                beforeNeutral=prevDirProp;
            }
        } else /* not a neutral, can be only one of { L, R, EN, AN } */ {
            /*
             * Note that all levels[] values are still the same at this
             * point because this function is called for an entire
             * same-level run.
             * Therefore, we need to read only one actual level.
             */
            UBiDiLevel level=levels[i];

            if(neutralStart>=0) {
                UBiDiLevel final;
                /* end of a sequence of neutrals (dirProp is "afterNeutral") */
                if(!(pBiDi->isInverse)) {
                    if(beforeNeutral==L) {
                        if(dirProp==L) {
                            final=0;                /* make all neutrals L (N1) */
                        } else {
                            final=level;            /* make all neutrals "e" (N2) */
                        }
                    } else /* beforeNeutral is one of { R, EN, AN } */ {
                        if(dirProp==L) {
                            final=level;            /* make all neutrals "e" (N2) */
                        } else {
                            final=1;                /* make all neutrals R (N1) */
                        }
                    }
                } else {
                    /* "inverse BiDi": collapse [before]dirProps L, EN, AN into L */
                    if(beforeNeutral!=R) {
                        if(dirProp!=R) {
                            final=0;                /* make all neutrals L (N1) */
                        } else {
                            final=level;            /* make all neutrals "e" (N2) */
                        }
                    } else /* beforeNeutral is one of { R, EN, AN } */ {
                        if(dirProp!=R) {
                            final=level;            /* make all neutrals "e" (N2) */
                        } else {
                            final=1;                /* make all neutrals R (N1) */
                        }
                    }
                }
                /* perform (In) on the sequence of neutrals */
                if((level^final)&1) {
                    /* do something only if we need to _change_ the level */
                    do {
                        ++levels[neutralStart];
                    } while(++neutralStart<i);
                }
                neutralStart=-1;
            }

            /* perform (In) on the non-neutral character */
            /*
             * in the cases of (W5), processing a sequence of ET,
             * and of (X9), skipping BN,
             * there may be multiple characters from i to <next
             * that all get (virtually) the same dirProp and (really) the same level
             */
            if(dirProp==L) {
                if(level&1) {
                    ++level;
                } else {
                    i=next;     /* we keep the levels */
                }
            } else if(dirProp==R) {
                if(!(level&1)) {
                    ++level;
                } else {
                    i=next;     /* we keep the levels */
                }
            } else /* EN or AN */ {
                /* this level depends on whether we do "inverse BiDi" */
                level=numberLevel;
            }

            /* apply the new level to the sequence, if necessary */
            while(i<next) {
                levels[i++]=level;
            }
        }
    }

    /* perform (Nn) - here,
       the character after the the neutrals is eor, which is either L or R */
    /* this is one iteration late for the neutrals */
    if(neutralStart>=0) {
        /*
         * Note that all levels[] values are still the same at this
         * point because this function is called for an entire
         * same-level run.
         * Therefore, we need to read only one actual level.
         */
        UBiDiLevel level=levels[neutralStart], final;

        /* end of a sequence of neutrals (eor is "afterNeutral") */
        if(!(pBiDi->isInverse)) {
            if(beforeNeutral==L) {
                if(eor==L) {
                    final=0;                /* make all neutrals L (N1) */
                } else {
                    final=level;            /* make all neutrals "e" (N2) */
                }
            } else /* beforeNeutral is one of { R, EN, AN } */ {
                if(eor==L) {
                    final=level;            /* make all neutrals "e" (N2) */
                } else {
                    final=1;                /* make all neutrals R (N1) */
                }
            }
        } else {
            /* "inverse BiDi": collapse [before]dirProps L, EN, AN into L */
            if(beforeNeutral!=R) {
                if(eor!=R) {
                    final=0;                /* make all neutrals L (N1) */
                } else {
                    final=level;            /* make all neutrals "e" (N2) */
                }
            } else /* beforeNeutral is one of { R, EN, AN } */ {
                if(eor!=R) {
                    final=level;            /* make all neutrals "e" (N2) */
                } else {
                    final=1;                /* make all neutrals R (N1) */
                }
            }
        }
        /* perform (In) on the sequence of neutrals */
        if((level^final)&1) {
            /* do something only if we need to _change_ the level */
            do {
                ++levels[neutralStart];
            } while(++neutralStart<limit);
        }
    }
}

/* perform (L1) and (X9) ---------------------------------------------------- */

/*
 * Reset the embedding levels for some non-graphic characters (L1).
 * This function also sets appropriate levels for BN, and
 * explicit embedding types that are supposed to have been removed
 * from the paragraph in (X9).
 */
static void
adjustWSLevels(UBiDi *pBiDi) {
    const DirProp *dirProps=pBiDi->dirProps;
    UBiDiLevel *levels=pBiDi->levels;
    int32_t i;

    if(pBiDi->flags&MASK_WS) {
        UBiDiLevel paraLevel=pBiDi->paraLevel;
        Flags flag;

        i=pBiDi->trailingWSStart;
        while(i>0) {
            /* reset a sequence of WS/BN before eop and B/S to the paragraph paraLevel */
            while(i>0 && DIRPROP_FLAG(dirProps[--i])&MASK_WS) {
                levels[i]=paraLevel;
            }

            /* reset BN to the next character's paraLevel until B/S, which restarts above loop */
            /* here, i+1 is guaranteed to be <length */
            while(i>0) {
                flag=DIRPROP_FLAG(dirProps[--i]);
                if(flag&MASK_BN_EXPLICIT) {
                    levels[i]=levels[i+1];
                } else if(flag&MASK_B_S) {
                    levels[i]=paraLevel;
                    break;
                }
            }
        }
    }

    /* now remove the UBIDI_LEVEL_OVERRIDE flags, if any */
    /* (a separate loop can be optimized more easily by a compiler) */
    if(pBiDi->flags&MASK_OVERRIDE) {
        for(i=pBiDi->trailingWSStart; i>0;) {
            levels[--i]&=~UBIDI_LEVEL_OVERRIDE;
        }
    }
}

/* -------------------------------------------------------------------------- */

U_CAPI UBiDiDirection U_EXPORT2
ubidi_getDirection(const UBiDi *pBiDi) {
    if(pBiDi!=NULL) {
        return pBiDi->direction;
    } else {
        return UBIDI_LTR;
    }
}

U_CAPI const UChar * U_EXPORT2
ubidi_getText(const UBiDi *pBiDi) {
    if(pBiDi!=NULL) {
        return pBiDi->text;
    } else {
        return NULL;
    }
}

U_CAPI int32_t U_EXPORT2
ubidi_getLength(const UBiDi *pBiDi) {
    if(pBiDi!=NULL) {
        return pBiDi->length;
    } else {
        return 0;
    }
}

U_CAPI UBiDiLevel U_EXPORT2
ubidi_getParaLevel(const UBiDi *pBiDi) {
    if(pBiDi!=NULL) {
        return pBiDi->paraLevel;
    } else {
        return 0;
    }
}

/* statetable prototype ----------------------------------------------------- */

/*
 * This is here for possible future
 * performance work and is not compiled right now.
 */

#if 0
/*
 * This is a piece of code that could be part of ubidi.c/resolveImplicitLevels().
 * It replaces in the (Wn) state machine the switch()-if()-cascade with
 * just a few if()s and a state table.
 */

/* use the state table only for the following dirProp's */
#define MASK_W_TABLE (FLAG(L)|FLAG(R)|FLAG(AL)|FLAG(EN)|FLAG(ES)|FLAG(CS)|FLAG(ET)|FLAG(AN))

/*
 * inputs:
 *
 * 0..1 historyOfEN - 2b
 * 2    prevDirProp==AN - 1b
 * 3..4 lastStrong, one of { L, R, AL, none } - 2b
 * 5..7 dirProp, one of { L, R, AL, EN, ES, CS, ET, AN } - 3b
 * 8..9 nextDirProp, one of { EN, AN, other }
 *
 * total: 10b=1024 states
 */
enum { _L, _R, _AL, _EN, _ES, _CS, _ET, _AN, _OTHER };  /* lastStrong, dirProp */
enum { __EN, __AN, __OTHER };                           /* nextDirProp */

#define LAST_STRONG_SHIFT 3
#define DIR_PROP_SHIFT 5
#define NEXT_DIR_PROP_SHIFT 8

/* masks after shifting */
#define LAST_STRONG_MASK 3
#define DIR_PROP_MASK 7
#define STATE_MASK 0x1f

/* convert dirProp into _dirProp (above enum) */
static DirProp inputDirProp[dirPropCount]={ _X<<DIR_PROP_SHIFT, ... };

/* convert dirProp into __dirProp (above enum) */
static DirProp inputNextDirProp[dirPropCount]={ __X<<NEXT_DIR_PROP_SHIFT, ... };

/*
 * outputs:
 *
 * dirProp, one of { L, R, EN, AN, ON } - 3b
 *
 * 0..1 historyOfEN - 2b
 * 2    prevDirProp==AN - 1b
 * 3..4 lastStrong, one of { L, R, AL, none } - 2b
 * 5..7 new dirProp, one of { L, R, EN, AN, ON }
 *
 * total: 8 bits=1 byte per state
 */
enum { ___L, ___R, ___EN, ___AN, ___ON, ___count };

/* convert ___dirProp into dirProp (above enum) */
static DirProp outputDirProp[___count]={ X, ... };

/* state table */
static uint8_t wnTable[1024]={ /* calculate with switch()-if()-cascade */ };

static void
resolveImplicitLevels(BiDi *pBiDi,
                      Index start, Index end,
                      DirProp sor, DirProp eor) {
    /* new variable */
    uint8_t state;

    /* remove variable lastStrong */

    /* set initial state (set lastStrong, the rest is 0) */
    state= sor==L ? 0 : _R<<LAST_STRONG_SHIFT;

    while(next<limit) {
        /* advance */
        prevDirProp=dirProp;
        dirProp=nextDirProp;
        i=next;
        do {
            if(++next<limit) {
                nextDirProp=dirProps[next];
            } else {
                nextDirProp=eor;
                break;
            }
        } while(FLAG(nextDirProp)&MASK_BN_EXPLICIT);

        /* (W1..W7) */
        /* ### This may be more efficient with a switch(dirProp). */
        if(FLAG(dirProp)&MASK_W_TABLE) {
            state=wnTable[
                    ((int)state)|
                    inputDirProp[dirProp]|
                    inputNextDirProp[nextDirProp]
            ];
            dirProp=outputDirProp[state>>DIR_PROP_SHIFT];
            state&=STATE_MASK;
        } else if(dirProp==ET) {
            /* get sequence of ET; advance only next, not current, previous or historyOfEN */
            while(next<limit && FLAG(nextDirProp)&MASK_ET_NSM_BN /* (W1), (X9) */) {
                if(++next<limit) {
                    nextDirProp=dirProps[next];
                } else {
                    nextDirProp=eor;
                    break;
                }
            }

            state=wnTable[
                    ((int)state)|
                    _ET<<DIR_PROP_SHIFT|
                    inputNextDirProp[nextDirProp]
            ];
            dirProp=outputDirProp[state>>DIR_PROP_SHIFT];
            state&=STATE_MASK;

            /* apply the result of (W1), (W5)..(W7) to the entire sequence of ET */
        } else if(dirProp==NSM) {
            /* (W1) */
            dirProp=prevDirProp;
            /* keep prevDirProp's EN and AN states! */
        } else /* other */ {
            /* set EN and AN states to 0 */
            state&=LAST_STRONG_MASK<<LAST_STRONG_SHIFT;
        }

        /* perform (Nn) and (In) as usual */
    }
    /* perform (Nn) and (In) as usual */
}
#endif
