/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package jdk.nashorn.internal.runtime.regexp.joni;

import static jdk.nashorn.internal.runtime.regexp.joni.Option.isFindLongest;

import jdk.nashorn.internal.runtime.regexp.joni.constants.AnchorType;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.IntHolder;

public abstract class Matcher extends IntHolder {
    protected final Regex regex;

    protected final char[] chars;
    protected final int str;
    protected final int end;

    protected int msaStart;
    protected int msaOptions;
    protected final Region msaRegion;
    protected int msaBestLen;
    protected int msaBestS;

    protected int msaBegin;
    protected int msaEnd;

    public Matcher(Regex regex, char[] chars) {
        this(regex, chars, 0, chars.length);
    }

    public Matcher(Regex regex, char[] chars, int p, int end) {
        this.regex = regex;

        this.chars = chars;
        this.str = p;
        this.end = end;

        this.msaRegion = regex.numMem == 0 ? null : new Region(regex.numMem + 1);
    }

    // main matching method
    protected abstract int matchAt(int range, int sstart, int sprev);

    public final Region getRegion() {
        return msaRegion;
    }

    public final int getBegin() {
        return msaBegin;
    }

    public final int getEnd() {
        return msaEnd;
    }

    protected final void msaInit(int option, int start) {
        msaOptions = option;
        msaStart = start;
        if (Config.USE_FIND_LONGEST_SEARCH_ALL_OF_RANGE) msaBestLen = -1;
    }

    public final int match(int at, int range, int option) {
        msaInit(option, at);

        int prev = EncodingHelper.prevCharHead(str, at);

        if (Config.USE_MATCH_RANGE_MUST_BE_INSIDE_OF_SPECIFIED_RANGE) {
            return matchAt(end /*range*/, at, prev);
        } else {
            return matchAt(range /*range*/, at, prev);
        }
    }

    int low, high; // these are the return values
    private boolean forwardSearchRange(char[] chars, int str, int end, int s, int range, IntHolder lowPrev) {
        int pprev = -1;
        int p = s;

        if (Config.DEBUG_SEARCH) {
            Config.log.println("forward_search_range: "+
                                "str: " + str +
                                ", end: " + end +
                                ", s: " + s +
                                ", range: " + range);
        }

        if (regex.dMin > 0) {
            p += regex.dMin;
        }

        retry:while (true) {
            p = regex.searchAlgorithm.search(regex, chars, p, end, range);

            if (p != -1 && p < range) {
                if (p - regex.dMin < s) {
                    // retry_gate:
                    pprev = p;
                    p++;
                    continue retry;
                }

                if (regex.subAnchor != 0) {
                    switch (regex.subAnchor) {
                    case AnchorType.BEGIN_LINE:
                        if (p != str) {
                            int prev = EncodingHelper.prevCharHead((pprev != -1) ? pprev : str, p);
                            if (!EncodingHelper.isNewLine(chars, prev, end)) {
                                // goto retry_gate;
                                pprev = p;
                                p++;
                                continue retry;
                            }
                        }
                        break;

                    case AnchorType.END_LINE:
                        if (p == end) {
                            if (!Config.USE_NEWLINE_AT_END_OF_STRING_HAS_EMPTY_LINE) {
                                int prev = EncodingHelper.prevCharHead((pprev != -1) ? pprev : str, p);
                                if (prev != -1 && EncodingHelper.isNewLine(chars, prev, end)) {
                                    // goto retry_gate;
                                    pprev = p;
                                    p++;
                                    continue retry;
                                }
                            }
                        } else if (!EncodingHelper.isNewLine(chars, p, end) && (!Config.USE_CRNL_AS_LINE_TERMINATOR || !EncodingHelper.isCrnl(chars, p, end))) {
                            //if () break;
                            // goto retry_gate;
                            pprev = p;
                            p++;
                            continue retry;
                        }
                        break;
                    } // switch
                }

                if (regex.dMax == 0) {
                    low = p;
                    if (lowPrev != null) { // ??? // remove null checks
                        if (low > s) {
                            lowPrev.value = EncodingHelper.prevCharHead(s, p);
                        } else {
                            lowPrev.value = EncodingHelper.prevCharHead((pprev != -1) ? pprev : str, p);
                        }
                    }
                } else {
                    if (regex.dMax != MinMaxLen.INFINITE_DISTANCE) {
                        low = p - regex.dMax;

                        if (low > s) {
                            low = EncodingHelper.rightAdjustCharHeadWithPrev(low, lowPrev);
                            if (lowPrev != null && lowPrev.value == -1) {
                                lowPrev.value = EncodingHelper.prevCharHead((pprev != -1) ? pprev : s, low);
                            }
                        } else {
                            if (lowPrev != null) {
                                lowPrev.value = EncodingHelper.prevCharHead((pprev != -1) ? pprev : str, low);
                            }
                        }
                    }
                }
                /* no needs to adjust *high, *high is used as range check only */
                high = p - regex.dMin;

                if (Config.DEBUG_SEARCH) {
                    Config.log.println("forward_search_range success: "+
                                        "low: " + (low - str) +
                                        ", high: " + (high - str) +
                                        ", dmin: " + regex.dMin +
                                        ", dmax: " + regex.dMax);
                }

                return true;    /* success */
            }

            return false;   /* fail */
        } //while
    }

    // low, high
    private boolean backwardSearchRange(char[] chars, int str, int end, int s, int range, int adjrange) {
        range += regex.dMin;
        int p = s;

        retry:while (true) {
            p = regex.searchAlgorithm.searchBackward(regex, chars, range, adjrange, end, p, s, range);

            if (p != -1) {
                if (regex.subAnchor != 0) {
                    switch (regex.subAnchor) {
                    case AnchorType.BEGIN_LINE:
                        if (p != str) {
                            int prev = EncodingHelper.prevCharHead(str, p);
                            if (!EncodingHelper.isNewLine(chars, prev, end)) {
                                p = prev;
                                continue retry;
                            }
                        }
                        break;

                    case AnchorType.END_LINE:
                        if (p == end) {
                            if (!Config.USE_NEWLINE_AT_END_OF_STRING_HAS_EMPTY_LINE) {
                                int prev = EncodingHelper.prevCharHead(adjrange, p);
                                if (prev == -1) return false;
                                if (EncodingHelper.isNewLine(chars, prev, end)) {
                                    p = prev;
                                    continue retry;
                                }
                            }
                        } else if (!EncodingHelper.isNewLine(chars, p, end) && (!Config.USE_CRNL_AS_LINE_TERMINATOR || !EncodingHelper.isCrnl(chars, p, end))) {
                            p = EncodingHelper.prevCharHead(adjrange, p);
                            if (p == -1) return false;
                            continue retry;
                        }
                        break;
                    } // switch
                }

                /* no needs to adjust *high, *high is used as range check only */
                if (regex.dMax != MinMaxLen.INFINITE_DISTANCE) {
                    low = p - regex.dMax;
                    high = p - regex.dMin;
                }

                if (Config.DEBUG_SEARCH) {
                    Config.log.println("backward_search_range: "+
                                        "low: " + (low - str) +
                                        ", high: " + (high - str));
                }

                return true;
            }

            if (Config.DEBUG_SEARCH) Config.log.println("backward_search_range: fail.");
            return false;
        } // while
    }

    // MATCH_AND_RETURN_CHECK
    private boolean matchCheck(int upperRange, int s, int prev) {
        if (Config.USE_MATCH_RANGE_MUST_BE_INSIDE_OF_SPECIFIED_RANGE) {
            if (Config.USE_FIND_LONGEST_SEARCH_ALL_OF_RANGE) {
                //range = upperRange;
                if (matchAt(upperRange, s, prev) != -1) {
                    if (!isFindLongest(regex.options)) return true;
                }
            } else {
                //range = upperRange;
                if (matchAt(upperRange, s, prev) != -1) return true;
            }
        } else {
            if (Config.USE_FIND_LONGEST_SEARCH_ALL_OF_RANGE) {
                if (matchAt(end, s, prev) != -1) {
                    //range = upperRange;
                    if (!isFindLongest(regex.options)) return true;
                }
            } else {
                //range = upperRange;
                if (matchAt(end, s, prev) != -1) return true;
            }
        }
        return false;
    }

    public final int search(int start, int range, int option) {
        int s, prev;
        int origStart = start;
        int origRange = range;

        if (Config.DEBUG_SEARCH) {
            Config.log.println("onig_search (entry point): "+
                    "str: " + str +
                    ", end: " + (end - str) +
                    ", start: " + (start - str) +
                    ", range " + (range - str));
        }

        if (start > end || start < str) return -1;

        /* anchor optimize: resume search range */
        if (regex.anchor != 0 && str < end) {
            int minSemiEnd, maxSemiEnd;

            if ((regex.anchor & AnchorType.BEGIN_POSITION) != 0) {
                /* search start-position only */
                // !begin_position:!
                if (range > start) {
                    range = start + 1;
                } else {
                    range = start;
                }
            } else if ((regex.anchor & AnchorType.BEGIN_BUF) != 0) {
                /* search str-position only */
                if (range > start) {
                    if (start != str) return -1; // mismatch_no_msa;
                    range = str + 1;
                } else {
                    if (range <= str) {
                        start = str;
                        range = str;
                    } else {
                        return -1; // mismatch_no_msa;
                    }
                }
            } else if ((regex.anchor & AnchorType.END_BUF) != 0) {
                minSemiEnd = maxSemiEnd = end;
                // !end_buf:!
                if (endBuf(start, range, minSemiEnd, maxSemiEnd)) return -1; // mismatch_no_msa;
            } else if ((regex.anchor & AnchorType.SEMI_END_BUF) != 0) {
                int preEnd = EncodingHelper.stepBack(str, end, 1);
                maxSemiEnd = end;
                if (EncodingHelper.isNewLine(chars, preEnd, end)) {
                    minSemiEnd = preEnd;
                    if (Config.USE_CRNL_AS_LINE_TERMINATOR) {
                        preEnd = EncodingHelper.stepBack(str, preEnd, 1);
                        if (preEnd != -1 && EncodingHelper.isCrnl(chars, preEnd, end)) {
                            minSemiEnd = preEnd;
                        }
                    }
                    if (minSemiEnd > str && start <= minSemiEnd) {
                        // !goto end_buf;!
                        if (endBuf(start, range, minSemiEnd, maxSemiEnd)) return -1; // mismatch_no_msa;
                    }
                } else {
                    minSemiEnd = end;
                    // !goto end_buf;!
                    if (endBuf(start, range, minSemiEnd, maxSemiEnd)) return -1; // mismatch_no_msa;
                }
            } else if ((regex.anchor & AnchorType.ANYCHAR_STAR_ML) != 0) {
                // goto !begin_position;!
                if (range > start) {
                    range = start + 1;
                } else {
                    range = start;
                }
            }

        } else if (str == end) { /* empty string */
            // empty address ?
            if (Config.DEBUG_SEARCH) {
                Config.log.println("onig_search: empty string.");
            }

            if (regex.thresholdLength == 0) {
                s = start = str;
                prev = -1;
                msaInit(option, start);

                if (matchCheck(end, s, prev)) return match(s);
                return mismatch();
            }
            return -1; // goto mismatch_no_msa;
        }

        if (Config.DEBUG_SEARCH) {
            Config.log.println("onig_search(apply anchor): " +
                                "end: " + (end - str) +
                                ", start " + (start - str) +
                                ", range " + (range - str));
        }

        msaInit(option, origStart);

        s = start;
        if (range > start) {    /* forward search */
            if (s > str) {
                prev = EncodingHelper.prevCharHead(str, s);
            } else {
                prev = 0; // -1
            }

            if (regex.searchAlgorithm != SearchAlgorithm.NONE) {
                int schRange = range;
                if (regex.dMax != 0) {
                    if (regex.dMax == MinMaxLen.INFINITE_DISTANCE) {
                        schRange = end;
                    } else {
                        schRange += regex.dMax;
                        if (schRange > end) schRange = end;
                    }
                }
                if ((end - start) < regex.thresholdLength) return mismatch();

                if (regex.dMax != MinMaxLen.INFINITE_DISTANCE) {
                    do {
                        if (!forwardSearchRange(chars, str, end, s, schRange, this)) return mismatch(); // low, high, lowPrev
                        if (s < low) {
                            s = low;
                            prev = value;
                        }
                        while (s <= high) {
                            if (matchCheck(origRange, s, prev)) return match(s); // ???
                            prev = s;
                            s++;
                        }
                    } while (s < range);
                    return mismatch();

                } else { /* check only. */
                    if (!forwardSearchRange(chars, str, end, s, schRange, null)) return mismatch();

                    if ((regex.anchor & AnchorType.ANYCHAR_STAR) != 0) {
                        do {
                            if (matchCheck(origRange, s, prev)) return match(s);
                            prev = s;
                            s++;
                        } while (s < range);
                        return mismatch();
                    }

                }
            }

            do {
                if (matchCheck(origRange, s, prev)) return match(s);
                prev = s;
                s++;
            } while (s < range);

            if (s == range) { /* because empty match with /$/. */
                if (matchCheck(origRange, s, prev)) return match(s);
            }
        } else { /* backward search */
            if (Config.USE_MATCH_RANGE_MUST_BE_INSIDE_OF_SPECIFIED_RANGE) {
                if (origStart < end) {
                    origStart++; // /* is upper range */
                }
            }

            if (regex.searchAlgorithm != SearchAlgorithm.NONE) {
                int adjrange;
                if (range < end) {
                    adjrange = range;
                } else {
                    adjrange = end;
                }
                if (regex.dMax != MinMaxLen.INFINITE_DISTANCE && (end - range) >= regex.thresholdLength) {
                    do {
                        int schStart = s + regex.dMax;
                        if (schStart > end) schStart = end;
                        if (!backwardSearchRange(chars, str, end, schStart, range, adjrange)) return mismatch(); // low, high
                        if (s > high) s = high;
                        while (s != -1 && s >= low) {
                            prev = EncodingHelper.prevCharHead(str, s);
                            if (matchCheck(origStart, s, prev)) return match(s);
                            s = prev;
                        }
                    } while (s >= range);
                    return mismatch();
                } else { /* check only. */
                    if ((end - range) < regex.thresholdLength) return mismatch();

                    int schStart = s;
                    if (regex.dMax != 0) {
                        if (regex.dMax == MinMaxLen.INFINITE_DISTANCE) {
                            schStart = end;
                        } else {
                            schStart += regex.dMax;
                            if (schStart > end) {
                                schStart = end;
                            }
                        }
                    }
                    if (!backwardSearchRange(chars, str, end, schStart, range, adjrange)) return mismatch();
                }
            }

            do {
                prev = EncodingHelper.prevCharHead(str, s);
                if (matchCheck(origStart, s, prev)) return match(s);
                s = prev;
            } while (s >= range);

        }
        return mismatch();
    }

    private boolean endBuf(int start, int range, int minSemiEnd, int maxSemiEnd) {
        if ((maxSemiEnd - str) < regex.anchorDmin) return true; // mismatch_no_msa;

        if (range > start) {
            if ((minSemiEnd - start) > regex.anchorDmax) {
                start = minSemiEnd - regex.anchorDmax;
                if (start >= end) {
                    /* match with empty at end */
                    start = EncodingHelper.prevCharHead(str, end);
                }
            }
            if ((maxSemiEnd - (range - 1)) < regex.anchorDmin) {
                range = maxSemiEnd - regex.anchorDmin + 1;
            }
            if (start >= range) return true; // mismatch_no_msa;
        } else {
            if ((minSemiEnd - range) > regex.anchorDmax) {
                range = minSemiEnd - regex.anchorDmax;
            }
            if ((maxSemiEnd - start) < regex.anchorDmin) {
                start = maxSemiEnd - regex.anchorDmin;
            }
            if (range > start) return true; // mismatch_no_msa;
        }
        return false;
    }

    private int match(int s) {
        return s - str; // sstart ???
    }

    private int mismatch() {
        if (Config.USE_FIND_LONGEST_SEARCH_ALL_OF_RANGE) {
            if (msaBestLen >= 0) {
                int s = msaBestS;
                return match(s);
            }
        }
        // falls through finish:
        return -1;
    }
}
