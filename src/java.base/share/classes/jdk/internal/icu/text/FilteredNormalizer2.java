// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2009-2014, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*/
package jdk.internal.icu.text;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Normalization filtered by a UnicodeSet.
 * Normalizes portions of the text contained in the filter set and leaves
 * portions not contained in the filter set unchanged.
 * Filtering is done via UnicodeSet.span(..., UnicodeSet.SpanCondition.SIMPLE).
 * Not-in-the-filter text is treated as "is normalized" and "quick check yes".
 * This class implements all of (and only) the Normalizer2 API.
 * An instance of this class is unmodifiable/immutable.
 * @stable ICU 4.4
 * @author Markus W. Scherer
 */
@SuppressWarnings("deprecation")
public class FilteredNormalizer2 extends Normalizer2 {
    /**
     * Constructs a filtered normalizer wrapping any Normalizer2 instance
     * and a filter set.
     * Both are aliased and must not be modified or deleted while this object
     * is used.
     * The filter set should be frozen; otherwise the performance will suffer greatly.
     * @param n2 wrapped Normalizer2 instance
     * @param filterSet UnicodeSet which determines the characters to be normalized
     * @stable ICU 4.4
     */
    public FilteredNormalizer2(Normalizer2 n2, UnicodeSet filterSet) {
        norm2=n2;
        set=filterSet;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public StringBuilder normalize(CharSequence src, StringBuilder dest) {
        if(dest==src) {
            throw new IllegalArgumentException();
        }
        dest.setLength(0);
        normalize(src, dest, UnicodeSet.SpanCondition.SIMPLE);
        return dest;
    }
    /**
     * {@inheritDoc}
     * @stable ICU 4.6
     */
    @Override
    public Appendable normalize(CharSequence src, Appendable dest) {
        if(dest==src) {
            throw new IllegalArgumentException();
        }
        return normalize(src, dest, UnicodeSet.SpanCondition.SIMPLE);
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public StringBuilder normalizeSecondAndAppend(
            StringBuilder first, CharSequence second) {
        return normalizeSecondAndAppend(first, second, true);
    }
    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public StringBuilder append(StringBuilder first, CharSequence second) {
        return normalizeSecondAndAppend(first, second, false);
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.6
     */
    @Override
    public String getDecomposition(int c) {
        return set.contains(c) ? norm2.getDecomposition(c) : null;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 49
     */
    @Override
    public String getRawDecomposition(int c) {
        return set.contains(c) ? norm2.getRawDecomposition(c) : null;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 49
     */
    @Override
    public int composePair(int a, int b) {
        return (set.contains(a) && set.contains(b)) ? norm2.composePair(a, b) : -1;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 49
     */
    @Override
    public int getCombiningClass(int c) {
        return set.contains(c) ? norm2.getCombiningClass(c) : 0;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public boolean isNormalized(CharSequence s) {
        UnicodeSet.SpanCondition spanCondition=UnicodeSet.SpanCondition.SIMPLE;
        for(int prevSpanLimit=0; prevSpanLimit<s.length();) {
            int spanLimit=set.span(s, prevSpanLimit, spanCondition);
            if(spanCondition==UnicodeSet.SpanCondition.NOT_CONTAINED) {
                spanCondition=UnicodeSet.SpanCondition.SIMPLE;
            } else {
                if(!norm2.isNormalized(s.subSequence(prevSpanLimit, spanLimit))) {
                    return false;
                }
                spanCondition=UnicodeSet.SpanCondition.NOT_CONTAINED;
            }
            prevSpanLimit=spanLimit;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public Normalizer.QuickCheckResult quickCheck(CharSequence s) {
        Normalizer.QuickCheckResult result=Normalizer.YES;
        UnicodeSet.SpanCondition spanCondition=UnicodeSet.SpanCondition.SIMPLE;
        for(int prevSpanLimit=0; prevSpanLimit<s.length();) {
            int spanLimit=set.span(s, prevSpanLimit, spanCondition);
            if(spanCondition==UnicodeSet.SpanCondition.NOT_CONTAINED) {
                spanCondition=UnicodeSet.SpanCondition.SIMPLE;
            } else {
                Normalizer.QuickCheckResult qcResult=
                    norm2.quickCheck(s.subSequence(prevSpanLimit, spanLimit));
                if(qcResult==Normalizer.NO) {
                    return qcResult;
                } else if(qcResult==Normalizer.MAYBE) {
                    result=qcResult;
                }
                spanCondition=UnicodeSet.SpanCondition.NOT_CONTAINED;
            }
            prevSpanLimit=spanLimit;
        }
        return result;
    }
    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public int spanQuickCheckYes(CharSequence s) {
        UnicodeSet.SpanCondition spanCondition=UnicodeSet.SpanCondition.SIMPLE;
        for(int prevSpanLimit=0; prevSpanLimit<s.length();) {
            int spanLimit=set.span(s, prevSpanLimit, spanCondition);
            if(spanCondition==UnicodeSet.SpanCondition.NOT_CONTAINED) {
                spanCondition=UnicodeSet.SpanCondition.SIMPLE;
            } else {
                int yesLimit=
                    prevSpanLimit+
                    norm2.spanQuickCheckYes(s.subSequence(prevSpanLimit, spanLimit));
                if(yesLimit<spanLimit) {
                    return yesLimit;
                }
                spanCondition=UnicodeSet.SpanCondition.NOT_CONTAINED;
            }
            prevSpanLimit=spanLimit;
        }
        return s.length();
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public boolean hasBoundaryBefore(int c) {
        return !set.contains(c) || norm2.hasBoundaryBefore(c);
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public boolean hasBoundaryAfter(int c) {
        return !set.contains(c) || norm2.hasBoundaryAfter(c);
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public boolean isInert(int c) {
        return !set.contains(c) || norm2.isInert(c);
    }

    // Internal: No argument checking, and appends to dest.
    // Pass as input spanCondition the one that is likely to yield a non-zero
    // span length at the start of src.
    // For set=[:age=3.2:], since almost all common characters were in Unicode 3.2,
    // UnicodeSet.SpanCondition.SIMPLE should be passed in for the start of src
    // and UnicodeSet.SpanCondition.NOT_CONTAINED should be passed in if we continue after
    // an in-filter prefix.
    private Appendable normalize(CharSequence src, Appendable dest,
                                 UnicodeSet.SpanCondition spanCondition) {
        // Don't throw away destination buffer between iterations.
        StringBuilder tempDest=new StringBuilder();
        try {
            for(int prevSpanLimit=0; prevSpanLimit<src.length();) {
                int spanLimit=set.span(src, prevSpanLimit, spanCondition);
                int spanLength=spanLimit-prevSpanLimit;
                if(spanCondition==UnicodeSet.SpanCondition.NOT_CONTAINED) {
                    if(spanLength!=0) {
                        dest.append(src, prevSpanLimit, spanLimit);
                    }
                    spanCondition=UnicodeSet.SpanCondition.SIMPLE;
                } else {
                    if(spanLength!=0) {
                        // Not norm2.normalizeSecondAndAppend() because we do not want
                        // to modify the non-filter part of dest.
                        dest.append(norm2.normalize(src.subSequence(prevSpanLimit, spanLimit), tempDest));
                    }
                    spanCondition=UnicodeSet.SpanCondition.NOT_CONTAINED;
                }
                prevSpanLimit=spanLimit;
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
        return dest;
    }

    private StringBuilder normalizeSecondAndAppend(StringBuilder first, CharSequence second,
                                                   boolean doNormalize) {
        if(first==second) {
            throw new IllegalArgumentException();
        }
        if(first.length()==0) {
            if(doNormalize) {
                return normalize(second, first);
            } else {
                return first.append(second);
            }
        }
        // merge the in-filter suffix of the first string with the in-filter prefix of the second
        int prefixLimit=set.span(second, 0, UnicodeSet.SpanCondition.SIMPLE);
        if(prefixLimit!=0) {
            CharSequence prefix=second.subSequence(0, prefixLimit);
            int suffixStart=set.spanBack(first, 0x7fffffff, UnicodeSet.SpanCondition.SIMPLE);
            if(suffixStart==0) {
                if(doNormalize) {
                    norm2.normalizeSecondAndAppend(first, prefix);
                } else {
                    norm2.append(first, prefix);
                }
            } else {
                StringBuilder middle=new StringBuilder(
                        first.subSequence(suffixStart, first.length()));
                if(doNormalize) {
                    norm2.normalizeSecondAndAppend(middle, prefix);
                } else {
                    norm2.append(middle, prefix);
                }
                first.delete(suffixStart, 0x7fffffff).append(middle);
            }
        }
        if(prefixLimit<second.length()) {
            CharSequence rest=second.subSequence(prefixLimit, second.length());
            if(doNormalize) {
                normalize(rest, first, UnicodeSet.SpanCondition.NOT_CONTAINED);
            } else {
                first.append(rest);
            }
        }
        return first;
    }

    private Normalizer2 norm2;
    private UnicodeSet set;
};
