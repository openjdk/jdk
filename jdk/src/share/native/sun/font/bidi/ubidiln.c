/*
 * Portions Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
*   file name:  ubidiln.c
*   encoding:   US-ASCII
*   tab size:   8 (not used)
*   indentation:4
*
*   created on: 1999aug06
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
 * General remarks about the functions in this file:
 *
 * These functions deal with the aspects of potentially mixed-directional
 * text in a single paragraph or in a line of a single paragraph
 * which has already been processed according to
 * the Unicode 3.0 BiDi algorithm as defined in
 * http://www.unicode.org/unicode/reports/tr9/ , version 5,
 * also described in The Unicode Standard, Version 3.0 .
 *
 * This means that there is a UBiDi object with a levels
 * and a dirProps array.
 * paraLevel and direction are also set.
 * Only if the length of the text is zero, then levels==dirProps==NULL.
 *
 * The overall directionality of the paragraph
 * or line is used to bypass the reordering steps if possible.
 * Even purely RTL text does not need reordering there because
 * the ubidi_getLogical/VisualIndex() functions can compute the
 * index on the fly in such a case.
 *
 * The implementation of the access to same-level-runs and of the reordering
 * do attempt to provide better performance and less memory usage compared to
 * a direct implementation of especially rule (L2) with an array of
 * one (32-bit) integer per text character.
 *
 * Here, the levels array is scanned as soon as necessary, and a vector of
 * same-level-runs is created. Reordering then is done on this vector.
 * For each run of text positions that were resolved to the same level,
 * only 8 bytes are stored: the first text position of the run and the visual
 * position behind the run after reordering.
 * One sign bit is used to hold the directionality of the run.
 * This is inefficient if there are many very short runs. If the average run
 * length is <2, then this uses more memory.
 *
 * In a further attempt to save memory, the levels array is never changed
 * after all the resolution rules (Xn, Wn, Nn, In).
 * Many functions have to consider the field trailingWSStart:
 * if it is less than length, then there is an implicit trailing run
 * at the paraLevel,
 * which is not reflected in the levels array.
 * This allows a line UBiDi object to use the same levels array as
 * its paragraph parent object.
 *
 * When a UBiDi object is created for a line of a paragraph, then the
 * paragraph's levels and dirProps arrays are reused by way of setting
 * a pointer into them, not by copying. This again saves memory and forbids to
 * change the now shared levels for (L1).
 */

/* prototypes --------------------------------------------------------------- */

static void
setTrailingWSStart(UBiDi *pBiDi);

static void
getSingleRun(UBiDi *pBiDi, UBiDiLevel level);

static void
reorderLine(UBiDi *pBiDi, UBiDiLevel minLevel, UBiDiLevel maxLevel);

static bool_t
prepareReorder(const UBiDiLevel *levels, int32_t length,
               int32_t *indexMap,
               UBiDiLevel *pMinLevel, UBiDiLevel *pMaxLevel);

/* ubidi_setLine ------------------------------------------------------------ */

U_CAPI void U_EXPORT2
ubidi_setLine(const UBiDi *pParaBiDi,
              int32_t start, int32_t limit,
              UBiDi *pLineBiDi,
              UErrorCode *pErrorCode) {
    int32_t length;

    /* check the argument values */
    if(pErrorCode==NULL || U_FAILURE(*pErrorCode)) {
        return;
    } else if(pParaBiDi==NULL || pLineBiDi==NULL) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
        return;
    } else if(start<0 || start>limit || limit>pParaBiDi->length) {
        *pErrorCode=U_INDEX_OUTOFBOUNDS_ERROR;
        return;
    }

    /* set the values in pLineBiDi from its pParaBiDi parent */
    pLineBiDi->text=pParaBiDi->text+start;
    length=pLineBiDi->length=limit-start;
    pLineBiDi->paraLevel=pParaBiDi->paraLevel;

    pLineBiDi->runs=NULL;
    pLineBiDi->flags=0;

    if(length>0) {
        pLineBiDi->dirProps=pParaBiDi->dirProps+start;
        pLineBiDi->levels=pParaBiDi->levels+start;
        pLineBiDi->runCount=-1;

        if(pParaBiDi->direction!=UBIDI_MIXED) {
            /* the parent is already trivial */
            pLineBiDi->direction=pParaBiDi->direction;

            /*
             * The parent's levels are all either
             * implicitly or explicitly ==paraLevel;
             * do the same here.
             */
            if(pParaBiDi->trailingWSStart<=start) {
                pLineBiDi->trailingWSStart=0;
            } else if(pParaBiDi->trailingWSStart<limit) {
                pLineBiDi->trailingWSStart=pParaBiDi->trailingWSStart-start;
            } else {
                pLineBiDi->trailingWSStart=length;
            }
        } else {
            const UBiDiLevel *levels=pLineBiDi->levels;
            int32_t i, trailingWSStart;
            UBiDiLevel level;

            setTrailingWSStart(pLineBiDi);
            trailingWSStart=pLineBiDi->trailingWSStart;

            /* recalculate pLineBiDi->direction */
            if(trailingWSStart==0) {
                /* all levels are at paraLevel */
                pLineBiDi->direction=(UBiDiDirection)(pLineBiDi->paraLevel&1);
            } else {
                /* get the level of the first character */
                level=(UBiDiLevel)(levels[0]&1);

                /* if there is anything of a different level, then the line is mixed */
                if(trailingWSStart<length && (pLineBiDi->paraLevel&1)!=level) {
                    /* the trailing WS is at paraLevel, which differs from levels[0] */
                    pLineBiDi->direction=UBIDI_MIXED;
                } else {
                    /* see if levels[1..trailingWSStart-1] have the same direction as levels[0] and paraLevel */
                    i=1;
                    for(;;) {
                        if(i==trailingWSStart) {
                            /* the direction values match those in level */
                            pLineBiDi->direction=(UBiDiDirection)level;
                            break;
                        } else if((levels[i]&1)!=level) {
                            pLineBiDi->direction=UBIDI_MIXED;
                            break;
                        }
                        ++i;
                    }
                }
            }

            switch(pLineBiDi->direction) {
            case UBIDI_LTR:
                /* make sure paraLevel is even */
                pLineBiDi->paraLevel=(UBiDiLevel)((pLineBiDi->paraLevel+1)&~1);

                /* all levels are implicitly at paraLevel (important for ubidi_getLevels()) */
                pLineBiDi->trailingWSStart=0;
                break;
            case UBIDI_RTL:
                /* make sure paraLevel is odd */
                pLineBiDi->paraLevel|=1;

                /* all levels are implicitly at paraLevel (important for ubidi_getLevels()) */
                pLineBiDi->trailingWSStart=0;
                break;
            default:
                break;
            }
        }
    } else {
        /* create an object for a zero-length line */
        pLineBiDi->direction=pLineBiDi->paraLevel&1 ? UBIDI_RTL : UBIDI_LTR;
        pLineBiDi->trailingWSStart=pLineBiDi->runCount=0;

        pLineBiDi->dirProps=NULL;
        pLineBiDi->levels=NULL;
    }
    return;
}

U_CAPI UBiDiLevel U_EXPORT2
ubidi_getLevelAt(const UBiDi *pBiDi, int32_t charIndex) {
    /* return paraLevel if in the trailing WS run, otherwise the real level */
    if(pBiDi==NULL || charIndex<0 || pBiDi->length<=charIndex) {
        return 0;
    } else if(pBiDi->direction!=UBIDI_MIXED || charIndex>=pBiDi->trailingWSStart) {
        return pBiDi->paraLevel;
    } else {
        return pBiDi->levels[charIndex];
    }
}

U_CAPI const UBiDiLevel * U_EXPORT2
ubidi_getLevels(UBiDi *pBiDi, UErrorCode *pErrorCode) {
    int32_t start, length;

    if(pErrorCode==NULL || U_FAILURE(*pErrorCode)) {
        return NULL;
    } else if(pBiDi==NULL || (length=pBiDi->length)<=0) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
        return NULL;
    }

    if((start=pBiDi->trailingWSStart)==length) {
        /* the current levels array reflects the WS run */
        return pBiDi->levels;
    }

    /*
     * After the previous if(), we know that the levels array
     * has an implicit trailing WS run and therefore does not fully
     * reflect itself all the levels.
     * This must be a UBiDi object for a line, and
     * we need to create a new levels array.
     */

    if(getLevelsMemory(pBiDi, length)) {
        UBiDiLevel *levels=pBiDi->levelsMemory;

        if(start>0 && levels!=pBiDi->levels) {
            icu_memcpy(levels, pBiDi->levels, start);
        }
        icu_memset(levels+start, pBiDi->paraLevel, length-start);

        /* this new levels array is set for the line and reflects the WS run */
        pBiDi->trailingWSStart=length;
        return pBiDi->levels=levels;
    } else {
        /* out of memory */
        *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
        return NULL;
    }
}

U_CAPI void U_EXPORT2
ubidi_getLogicalRun(const UBiDi *pBiDi, int32_t logicalStart,
                    int32_t *pLogicalLimit, UBiDiLevel *pLevel) {
    int32_t length;

    if(pBiDi==NULL || logicalStart<0 || (length=pBiDi->length)<=logicalStart) {
        return;
    }

    if(pBiDi->direction!=UBIDI_MIXED || logicalStart>=pBiDi->trailingWSStart) {
        if(pLogicalLimit!=NULL) {
            *pLogicalLimit=length;
        }
        if(pLevel!=NULL) {
            *pLevel=pBiDi->paraLevel;
        }
    } else {
        UBiDiLevel *levels=pBiDi->levels;
        UBiDiLevel level=levels[logicalStart];

        /* search for the end of the run */
        length=pBiDi->trailingWSStart;
        while(++logicalStart<length && level==levels[logicalStart]) {}

        if(pLogicalLimit!=NULL) {
            *pLogicalLimit=logicalStart;
        }
        if(pLevel!=NULL) {
            *pLevel=level;
        }
    }
}

/* handle trailing WS (L1) -------------------------------------------------- */

/*
 * setTrailingWSStart() sets the start index for a trailing
 * run of WS in the line. This is necessary because we do not modify
 * the paragraph's levels array that we just point into.
 * Using trailingWSStart is another form of performing (L1).
 *
 * To make subsequent operations easier, we also include the run
 * before the WS if it is at the paraLevel - we merge the two here.
 */
static void
setTrailingWSStart(UBiDi *pBiDi) {
    /* pBiDi->direction!=UBIDI_MIXED */

    const DirProp *dirProps=pBiDi->dirProps;
    UBiDiLevel *levels=pBiDi->levels;
    int32_t start=pBiDi->length;
    UBiDiLevel paraLevel=pBiDi->paraLevel;

    /* go backwards across all WS, BN, explicit codes */
    while(start>0 && DIRPROP_FLAG(dirProps[start-1])&MASK_WS) {
        --start;
    }

    /* if the WS run can be merged with the previous run then do so here */
    while(start>0 && levels[start-1]==paraLevel) {
        --start;
    }

    pBiDi->trailingWSStart=start;
}

/* runs API functions ------------------------------------------------------- */

U_CAPI int32_t U_EXPORT2
ubidi_countRuns(UBiDi *pBiDi, UErrorCode *pErrorCode) {
    if(pErrorCode==NULL || U_FAILURE(*pErrorCode)) {
        return -1;
    } else if(pBiDi==NULL || (pBiDi->runCount<0 && !ubidi_getRuns(pBiDi))) {
        *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
        return -1;
    } else {
        return pBiDi->runCount;
    }
}

U_CAPI UBiDiDirection U_EXPORT2
ubidi_getVisualRun(UBiDi *pBiDi, int32_t runIndex,
                   int32_t *pLogicalStart, int32_t *pLength) {
    if( pBiDi==NULL || runIndex<0 ||
        (pBiDi->runCount==-1 && !ubidi_getRuns(pBiDi)) ||
        runIndex>=pBiDi->runCount
    ) {
        return UBIDI_LTR;
    } else {
        int32_t start=pBiDi->runs[runIndex].logicalStart;
        if(pLogicalStart!=NULL) {
            *pLogicalStart=GET_INDEX(start);
        }
        if(pLength!=NULL) {
            if(runIndex>0) {
                *pLength=pBiDi->runs[runIndex].visualLimit-
                         pBiDi->runs[runIndex-1].visualLimit;
            } else {
                *pLength=pBiDi->runs[0].visualLimit;
            }
        }
        return (UBiDiDirection)GET_ODD_BIT(start);
    }
}

/* compute the runs array --------------------------------------------------- */

/*
 * Compute the runs array from the levels array.
 * After ubidi_getRuns() returns TRUE, runCount is guaranteed to be >0
 * and the runs are reordered.
 * Odd-level runs have visualStart on their visual right edge and
 * they progress visually to the left.
 */
U_CFUNC bool_t
ubidi_getRuns(UBiDi *pBiDi) {
    if(pBiDi->direction!=UBIDI_MIXED) {
        /* simple, single-run case - this covers length==0 */
        getSingleRun(pBiDi, pBiDi->paraLevel);
    } else /* UBIDI_MIXED, length>0 */ {
        /* mixed directionality */
        int32_t length=pBiDi->length, limit;

        /*
         * If there are WS characters at the end of the line
         * and the run preceding them has a level different from
         * paraLevel, then they will form their own run at paraLevel (L1).
         * Count them separately.
         * We need some special treatment for this in order to not
         * modify the levels array which a line UBiDi object shares
         * with its paragraph parent and its other line siblings.
         * In other words, for the trailing WS, it may be
         * levels[]!=paraLevel but we have to treat it like it were so.
         */
        limit=pBiDi->trailingWSStart;
        if(limit==0) {
            /* there is only WS on this line */
            getSingleRun(pBiDi, pBiDi->paraLevel);
        } else {
            UBiDiLevel *levels=pBiDi->levels;
            int32_t i, runCount;
            UBiDiLevel level=UBIDI_DEFAULT_LTR;   /* initialize with no valid level */

            /* count the runs, there is at least one non-WS run, and limit>0 */
            runCount=0;
            for(i=0; i<limit; ++i) {
                /* increment runCount at the start of each run */
                if(levels[i]!=level) {
                    ++runCount;
                    level=levels[i];
                }
            }

            /*
             * We don't need to see if the last run can be merged with a trailing
             * WS run because setTrailingWSStart() would have done that.
             */
            if(runCount==1 && limit==length) {
                /* There is only one non-WS run and no trailing WS-run. */
                getSingleRun(pBiDi, levels[0]);
            } else /* runCount>1 || limit<length */ {
                /* allocate and set the runs */
                Run *runs;
                int32_t runIndex, start;
                UBiDiLevel minLevel=UBIDI_MAX_EXPLICIT_LEVEL+1, maxLevel=0;

                /* now, count a (non-mergable) WS run */
                if(limit<length) {
                    ++runCount;
                }

                /* runCount>1 */
                if(getRunsMemory(pBiDi, runCount)) {
                    runs=pBiDi->runsMemory;
                } else {
                    return FALSE;
                }

                /* set the runs */
                /* this could be optimized, e.g.: 464->444, 484->444, 575->555, 595->555 */
                /* however, that would take longer and make other functions more complicated */
                runIndex=0;

                /* search for the run limits and initialize visualLimit values with the run lengths */
                i=0;
                do {
                    /* prepare this run */
                    start=i;
                    level=levels[i];
                    if(level<minLevel) {
                        minLevel=level;
                    }
                    if(level>maxLevel) {
                        maxLevel=level;
                    }

                    /* look for the run limit */
                    while(++i<limit && levels[i]==level) {}

                    /* i is another run limit */
                    runs[runIndex].logicalStart=start;
                    runs[runIndex].visualLimit=i-start;
                    ++runIndex;
                } while(i<limit);

                if(limit<length) {
                    /* there is a separate WS run */
                    runs[runIndex].logicalStart=limit;
                    runs[runIndex].visualLimit=length-limit;
                    if(pBiDi->paraLevel<minLevel) {
                        minLevel=pBiDi->paraLevel;
                    }
                }

                /* set the object fields */
                pBiDi->runs=runs;
                pBiDi->runCount=runCount;

                reorderLine(pBiDi, minLevel, maxLevel);

                /* now add the direction flags and adjust the visualLimit's to be just that */
                ADD_ODD_BIT_FROM_LEVEL(runs[0].logicalStart, levels[runs[0].logicalStart]);
                limit=runs[0].visualLimit;
                for(i=1; i<runIndex; ++i) {
                    ADD_ODD_BIT_FROM_LEVEL(runs[i].logicalStart, levels[runs[i].logicalStart]);
                    limit=runs[i].visualLimit+=limit;
                }

                /* same for the trailing WS run */
                if(runIndex<runCount) {
                    ADD_ODD_BIT_FROM_LEVEL(runs[i].logicalStart, pBiDi->paraLevel);
                    runs[runIndex].visualLimit+=limit;
                }
            }
        }
    }
    return TRUE;
}

/* in trivial cases there is only one trivial run; called by ubidi_getRuns() */
static void
getSingleRun(UBiDi *pBiDi, UBiDiLevel level) {
    /* simple, single-run case */
    pBiDi->runs=pBiDi->simpleRuns;
    pBiDi->runCount=1;

    /* fill and reorder the single run */
    pBiDi->runs[0].logicalStart=MAKE_INDEX_ODD_PAIR(0, level);
    pBiDi->runs[0].visualLimit=pBiDi->length;
}

/* reorder the runs array (L2) ---------------------------------------------- */

/*
 * Reorder the same-level runs in the runs array.
 * Here, runCount>1 and maxLevel>=minLevel>=paraLevel.
 * All the visualStart fields=logical start before reordering.
 * The "odd" bits are not set yet.
 *
 * Reordering with this data structure lends itself to some handy shortcuts:
 *
 * Since each run is moved but not modified, and since at the initial maxLevel
 * each sequence of same-level runs consists of only one run each, we
 * don't need to do anything there and can predecrement maxLevel.
 * In many simple cases, the reordering is thus done entirely in the
 * index mapping.
 * Also, reordering occurs only down to the lowest odd level that occurs,
 * which is minLevel|1. However, if the lowest level itself is odd, then
 * in the last reordering the sequence of the runs at this level or higher
 * will be all runs, and we don't need the elaborate loop to search for them.
 * This is covered by ++minLevel instead of minLevel|=1 followed
 * by an extra reorder-all after the reorder-some loop.
 * About a trailing WS run:
 * Such a run would need special treatment because its level is not
 * reflected in levels[] if this is not a paragraph object.
 * Instead, all characters from trailingWSStart on are implicitly at
 * paraLevel.
 * However, for all maxLevel>paraLevel, this run will never be reordered
 * and does not need to be taken into account. maxLevel==paraLevel is only reordered
 * if minLevel==paraLevel is odd, which is done in the extra segment.
 * This means that for the main reordering loop we don't need to consider
 * this run and can --runCount. If it is later part of the all-runs
 * reordering, then runCount is adjusted accordingly.
 */
static void
reorderLine(UBiDi *pBiDi, UBiDiLevel minLevel, UBiDiLevel maxLevel) {
    Run *runs;
    UBiDiLevel *levels;
    int32_t firstRun, endRun, limitRun, runCount,
    temp;

    /* nothing to do? */
    if(maxLevel<=(minLevel|1)) {
        return;
    }

    /*
     * Reorder only down to the lowest odd level
     * and reorder at an odd minLevel in a separate, simpler loop.
     * See comments above for why minLevel is always incremented.
     */
    ++minLevel;

    runs=pBiDi->runs;
    levels=pBiDi->levels;
    runCount=pBiDi->runCount;

    /* do not include the WS run at paraLevel<=old minLevel except in the simple loop */
    if(pBiDi->trailingWSStart<pBiDi->length) {
        --runCount;
    }

    while(--maxLevel>=minLevel) {
        firstRun=0;

        /* loop for all sequences of runs */
        for(;;) {
            /* look for a sequence of runs that are all at >=maxLevel */
            /* look for the first run of such a sequence */
            while(firstRun<runCount && levels[runs[firstRun].logicalStart]<maxLevel) {
                ++firstRun;
            }
            if(firstRun>=runCount) {
                break;  /* no more such runs */
            }

            /* look for the limit run of such a sequence (the run behind it) */
            for(limitRun=firstRun; ++limitRun<runCount && levels[runs[limitRun].logicalStart]>=maxLevel;) {}

            /* Swap the entire sequence of runs from firstRun to limitRun-1. */
            endRun=limitRun-1;
            while(firstRun<endRun) {
                temp=runs[firstRun].logicalStart;
                runs[firstRun].logicalStart=runs[endRun].logicalStart;
                runs[endRun].logicalStart=temp;

                temp=runs[firstRun].visualLimit;
                runs[firstRun].visualLimit=runs[endRun].visualLimit;
                runs[endRun].visualLimit=temp;

                ++firstRun;
                --endRun;
            }

            if(limitRun==runCount) {
                break;  /* no more such runs */
            } else {
                firstRun=limitRun+1;
            }
        }
    }

    /* now do maxLevel==old minLevel (==odd!), see above */
    if(!(minLevel&1)) {
        firstRun=0;

        /* include the trailing WS run in this complete reordering */
        if(pBiDi->trailingWSStart==pBiDi->length) {
            --runCount;
        }

        /* Swap the entire sequence of all runs. (endRun==runCount) */
        while(firstRun<runCount) {
            temp=runs[firstRun].logicalStart;
            runs[firstRun].logicalStart=runs[runCount].logicalStart;
            runs[runCount].logicalStart=temp;

            temp=runs[firstRun].visualLimit;
            runs[firstRun].visualLimit=runs[runCount].visualLimit;
            runs[runCount].visualLimit=temp;

            ++firstRun;
            --runCount;
        }
    }
}

/* reorder a line based on a levels array (L2) ------------------------------ */

U_CAPI void U_EXPORT2
ubidi_reorderLogical(const UBiDiLevel *levels, int32_t length, int32_t *indexMap) {
    int32_t start, limit, sumOfSosEos;
    UBiDiLevel minLevel, maxLevel;

    if(indexMap==NULL || !prepareReorder(levels, length, indexMap, &minLevel, &maxLevel)) {
        return;
    }

    /* nothing to do? */
    if(minLevel==maxLevel && (minLevel&1)==0) {
        return;
    }

    /* reorder only down to the lowest odd level */
    minLevel|=1;

    /* loop maxLevel..minLevel */
    do {
        start=0;

        /* loop for all sequences of levels to reorder at the current maxLevel */
        for(;;) {
            /* look for a sequence of levels that are all at >=maxLevel */
            /* look for the first index of such a sequence */
            while(start<length && levels[start]<maxLevel) {
                ++start;
            }
            if(start>=length) {
                break;  /* no more such sequences */
            }

            /* look for the limit of such a sequence (the index behind it) */
            for(limit=start; ++limit<length && levels[limit]>=maxLevel;) {}

            /*
             * sos=start of sequence, eos=end of sequence
             *
             * The closed (inclusive) interval from sos to eos includes all the logical
             * and visual indexes within this sequence. They are logically and
             * visually contiguous and in the same range.
             *
             * For each run, the new visual index=sos+eos-old visual index;
             * we pre-add sos+eos into sumOfSosEos ->
             * new visual index=sumOfSosEos-old visual index;
             */
            sumOfSosEos=start+limit-1;

            /* reorder each index in the sequence */
            do {
                indexMap[start]=sumOfSosEos-indexMap[start];
            } while(++start<limit);

            /* start==limit */
            if(limit==length) {
                break;  /* no more such sequences */
            } else {
                start=limit+1;
            }
        }
    } while(--maxLevel>=minLevel);
}

U_CAPI void U_EXPORT2
ubidi_reorderVisual(const UBiDiLevel *levels, int32_t length, int32_t *indexMap) {
    int32_t start, end, limit, temp;
    UBiDiLevel minLevel, maxLevel;

    if(indexMap==NULL || !prepareReorder(levels, length, indexMap, &minLevel, &maxLevel)) {
        return;
    }

    /* nothing to do? */
    if(minLevel==maxLevel && (minLevel&1)==0) {
        return;
    }

    /* reorder only down to the lowest odd level */
    minLevel|=1;

    /* loop maxLevel..minLevel */
    do {
        start=0;

        /* loop for all sequences of levels to reorder at the current maxLevel */
        for(;;) {
            /* look for a sequence of levels that are all at >=maxLevel */
            /* look for the first index of such a sequence */
            while(start<length && levels[start]<maxLevel) {
                ++start;
            }
            if(start>=length) {
                break;  /* no more such runs */
            }

            /* look for the limit of such a sequence (the index behind it) */
            for(limit=start; ++limit<length && levels[limit]>=maxLevel;) {}

            /*
             * Swap the entire interval of indexes from start to limit-1.
             * We don't need to swap the levels for the purpose of this
             * algorithm: the sequence of levels that we look at does not
             * move anyway.
             */
            end=limit-1;
            while(start<end) {
                temp=indexMap[start];
                indexMap[start]=indexMap[end];
                indexMap[end]=temp;

                ++start;
                --end;
            }

            if(limit==length) {
                break;  /* no more such sequences */
            } else {
                start=limit+1;
            }
        }
    } while(--maxLevel>=minLevel);
}

static bool_t
prepareReorder(const UBiDiLevel *levels, int32_t length,
               int32_t *indexMap,
               UBiDiLevel *pMinLevel, UBiDiLevel *pMaxLevel) {
    int32_t start;
    UBiDiLevel level, minLevel, maxLevel;

    if(levels==NULL || length<=0) {
        return FALSE;
    }

    /* determine minLevel and maxLevel */
    minLevel=UBIDI_MAX_EXPLICIT_LEVEL+1;
    maxLevel=0;
    for(start=length; start>0;) {
        level=levels[--start];
        if(level>UBIDI_MAX_EXPLICIT_LEVEL+1) {
            return FALSE;
        }
        if(level<minLevel) {
            minLevel=level;
        }
        if(level>maxLevel) {
            maxLevel=level;
        }
    }
    *pMinLevel=minLevel;
    *pMaxLevel=maxLevel;

    /* initialize the index map */
    for(start=length; start>0;) {
        --start;
        indexMap[start]=start;
    }

    return TRUE;
}

/* API functions for logical<->visual mapping ------------------------------- */

U_CAPI int32_t U_EXPORT2
ubidi_getVisualIndex(UBiDi *pBiDi, int32_t logicalIndex, UErrorCode *pErrorCode) {
    if(pErrorCode==NULL || U_FAILURE(*pErrorCode)) {
        return 0;
    } else if(pBiDi==NULL) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    } else if(logicalIndex<0 || pBiDi->length<=logicalIndex) {
        *pErrorCode=U_INDEX_OUTOFBOUNDS_ERROR;
        return 0;
    } else {
        /* we can do the trivial cases without the runs array */
        switch(pBiDi->direction) {
        case UBIDI_LTR:
            return logicalIndex;
        case UBIDI_RTL:
            return pBiDi->length-logicalIndex-1;
        default:
            if(pBiDi->runCount<0 && !ubidi_getRuns(pBiDi)) {
                *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
                return 0;
            } else {
                Run *runs=pBiDi->runs;
                int32_t i, visualStart=0, offset, length;

                /* linear search for the run, search on the visual runs */
                for(i=0;; ++i) {
                    length=runs[i].visualLimit-visualStart;
                    offset=logicalIndex-GET_INDEX(runs[i].logicalStart);
                    if(offset>=0 && offset<length) {
                        if(IS_EVEN_RUN(runs[i].logicalStart)) {
                            /* LTR */
                            return visualStart+offset;
                        } else {
                            /* RTL */
                            return visualStart+length-offset-1;
                        }
                    }
                    visualStart+=length;
                }
            }
        }
    }
}

U_CAPI int32_t U_EXPORT2
ubidi_getLogicalIndex(UBiDi *pBiDi, int32_t visualIndex, UErrorCode *pErrorCode) {
    if(pErrorCode==NULL || U_FAILURE(*pErrorCode)) {
        return 0;
    } else if(pBiDi==NULL) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    } else if(visualIndex<0 || pBiDi->length<=visualIndex) {
        *pErrorCode=U_INDEX_OUTOFBOUNDS_ERROR;
        return 0;
    } else {
        /* we can do the trivial cases without the runs array */
        switch(pBiDi->direction) {
        case UBIDI_LTR:
            return visualIndex;
        case UBIDI_RTL:
            return pBiDi->length-visualIndex-1;
        default:
            if(pBiDi->runCount<0 && !ubidi_getRuns(pBiDi)) {
                *pErrorCode=U_MEMORY_ALLOCATION_ERROR;
                return 0;
            } else {
                Run *runs=pBiDi->runs;
                int32_t i, runCount=pBiDi->runCount, start;

                if(runCount<=10) {
                    /* linear search for the run */
                    for(i=0; visualIndex>=runs[i].visualLimit; ++i) {}
                } else {
                    /* binary search for the run */
                    int32_t begin=0, limit=runCount;

                    /* the middle if() will guaranteed find the run, we don't need a loop limit */
                    for(;;) {
                        i=(begin+limit)/2;
                        if(visualIndex>=runs[i].visualLimit) {
                            begin=i+1;
                        } else if(i==0 || visualIndex>=runs[i-1].visualLimit) {
                            break;
                        } else {
                            limit=i;
                        }
                    }
                }

                start=runs[i].logicalStart;
                if(IS_EVEN_RUN(start)) {
                    /* LTR */
                    /* the offset in runs[i] is visualIndex-runs[i-1].visualLimit */
                    if(i>0) {
                        visualIndex-=runs[i-1].visualLimit;
                    }
                    return GET_INDEX(start)+visualIndex;
                } else {
                    /* RTL */
                    return GET_INDEX(start)+runs[i].visualLimit-visualIndex-1;
                }
            }
        }
    }
}

U_CAPI void U_EXPORT2
ubidi_getLogicalMap(UBiDi *pBiDi, int32_t *indexMap, UErrorCode *pErrorCode) {
    UBiDiLevel *levels;

    /* ubidi_getLevels() checks all of its and our arguments */
    if((levels=(UBiDiLevel *)ubidi_getLevels(pBiDi, pErrorCode))==NULL) {
        /* no op */
    } else if(indexMap==NULL) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
    } else {
        ubidi_reorderLogical(levels, pBiDi->length, indexMap);
    }
}

U_CAPI void U_EXPORT2
ubidi_getVisualMap(UBiDi *pBiDi, int32_t *indexMap, UErrorCode *pErrorCode) {
    /* ubidi_countRuns() checks all of its and our arguments */
    if(ubidi_countRuns(pBiDi, pErrorCode)<=0) {
        /* no op */
    } else if(indexMap==NULL) {
        *pErrorCode=U_ILLEGAL_ARGUMENT_ERROR;
    } else {
        /* fill a visual-to-logical index map using the runs[] */
        Run *runs=pBiDi->runs, *runsLimit=runs+pBiDi->runCount;
        int32_t logicalStart, visualStart, visualLimit;

        visualStart=0;
        for(; runs<runsLimit; ++runs) {
            logicalStart=runs->logicalStart;
            visualLimit=runs->visualLimit;
            if(IS_EVEN_RUN(logicalStart)) {
                do { /* LTR */
                    *indexMap++ = logicalStart++;
                } while(++visualStart<visualLimit);
            } else {
                REMOVE_ODD_BIT(logicalStart);
                logicalStart+=visualLimit-visualStart;  /* logicalLimit */
                do { /* RTL */
                    *indexMap++ = --logicalStart;
                } while(++visualStart<visualLimit);
            }
            /* visualStart==visualLimit; */
        }
    }
}

U_CAPI void U_EXPORT2
ubidi_invertMap(const int32_t *srcMap, int32_t *destMap, int32_t length) {
    if(srcMap!=NULL && destMap!=NULL) {
        srcMap+=length;
        while(length>0) {
            destMap[*--srcMap]=--length;
        }
    }
}
