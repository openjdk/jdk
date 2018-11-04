/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package xmlkit; // -*- mode: java; indent-tabs-mode: nil -*-

import java.util.*;

/**
 * A List of Strings each representing a word or token.
 * This object itself is a CharSequence whose characters consist
 * of all the tokens, separated by blanks.
 *
 * @author jrose
 */
public class TokenList extends ArrayList<String> implements CharSequence {

    protected String separator;
    protected boolean frozen;

    public TokenList() {
        this.separator = " ";
    }

    public TokenList(Collection<? extends Object> tokens) {
        super(tokens.size());
        this.separator = " ";
        addTokens(tokens);
    }

    public TokenList(Collection<? extends Object> tokens, String separator) {
        super(tokens.size());
        this.separator = separator;
        addTokens(tokens);
    }

    public TokenList(Object[] tokens) {
        super(tokens.length);
        this.separator = " ";
        addTokens(tokens, 0, tokens.length);
    }

    public TokenList(Object[] tokens, int beg, int end) {
        super(end - beg);  // capacity
        this.separator = " ";
        addTokens(tokens, beg, end);
    }

    public TokenList(Object[] tokens, int beg, int end, String separator) {
        super(end - beg);  // capacity
        this.separator = separator;
        addTokens(tokens, beg, end);
    }

    public TokenList(String tokenStr) {
        this(tokenStr, " ", false);
    }

    public TokenList(String tokenStr, String separator) {
        this(tokenStr, separator, true);
    }

    public TokenList(String tokenStr, String separator, boolean allowNulls) {
        super(tokenStr.length() / 5);
        this.separator = separator;
        addTokens(tokenStr, allowNulls);
    }
    static public final TokenList EMPTY;

    static {
        TokenList tl = new TokenList(new Object[0]);
        tl.freeze();
        EMPTY = tl;
    }

    public void freeze() {
        if (!frozen) {
            for (ListIterator<String> i = listIterator(); i.hasNext();) {
                i.set(i.next().toString());
            }
            trimToSize();
            frozen = true;
        }
    }

    public boolean isFrozen() {
        return frozen;
    }

    void checkNotFrozen() {
        if (isFrozen()) {
            throw new UnsupportedOperationException("cannot modify frozen TokenList");
        }
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        checkNotFrozen();
        this.separator = separator;
    }

    /// All normal List mutators must check the frozen bit:
    public String set(int index, String o) {
        checkNotFrozen();
        return super.set(index, o);
    }

    public boolean add(String o) {
        checkNotFrozen();
        return super.add(o);
    }

    public void add(int index, String o) {
        checkNotFrozen();
        super.add(index, o);
    }

    public boolean addAll(Collection<? extends String> c) {
        checkNotFrozen();
        return super.addAll(c);
    }

    public boolean addAll(int index, Collection<? extends String> c) {
        checkNotFrozen();
        return super.addAll(index, c);
    }

    public boolean remove(Object o) {
        checkNotFrozen();
        return super.remove(o);
    }

    public String remove(int index) {
        checkNotFrozen();
        return super.remove(index);
    }

    public void clear() {
        checkNotFrozen();
        super.clear();
    }

    /** Add a collection of tokens to the list, applying toString to each. */
    public boolean addTokens(Collection<? extends Object> tokens) {
        // Note that if this sequence is empty, no tokens are added.
        // This is different from adding a null string, which is
        // a single token.
        boolean added = false;
        for (Object token : tokens) {
            add(token.toString());
            added = true;
        }
        return added;
    }

    public boolean addTokens(Object[] tokens, int beg, int end) {
        boolean added = false;
        for (int i = beg; i < end; i++) {
            add(tokens[i].toString());
            added = true;
        }
        return added;
    }

    public boolean addTokens(String tokenStr) {
        return addTokens(tokenStr, false);
    }

    public boolean addTokens(String tokenStr, boolean allowNulls) {
        boolean added = false;
        int pos = 0, limit = tokenStr.length(), sep = limit;
        while (pos < limit) {
            sep = tokenStr.indexOf(separator, pos);
            if (sep < 0) {
                sep = limit;
            }
            if (sep == pos) {
                if (allowNulls) {
                    add("");
                    added = true;
                }
                pos += separator.length();
            } else {
                add(tokenStr.substring(pos, sep));
                added = true;
                pos = sep + separator.length();
            }
        }
        if (allowNulls && sep < limit) {
            // Input was something like "tok1 tok2 ".
            add("");
            added = true;
        }
        return added;
    }

    public boolean addToken(Object token) {
        return add(token.toString());
    }

    /** Format the token string, using quotes and escapes.
     *  Quotes must contain an odd number of 3 or more elements,
     *  a sequence of begin/end quote pairs, plus a superquote.
     *  For each token, the first begin/end pair is used for
     *  which the end quote does not occur in the token.
     *  If the token contains all end quotes, the last pair
     *  is used, with all occurrences of the end quote replaced
     *  by the superquote.  If an end quote is the empty string,
     *  the separator is used instead.
     */
    public String format(String separator, String[] quotes) {
        return ""; //@@
    }
    protected int[] lengths;
    protected static final int MODC = 0, HINT = 1, BEG0 = 2, END0 = 3;

    // Layout of lengths:
    //   { modCount, hint, -1==beg[0], end[0]==beg[1], ..., length }
    // Note that each beg[i]..end[i] span includes a leading separator,
    // which is not part of the corresponding token.
    protected final CharSequence getCS(int i) {
        return (CharSequence) get(i);
    }

    // Produce (and cache) an table of indexes for each token.
    protected int[] getLengths() {
        int[] lengths = this.lengths;
        ;
        int sepLength = separator.length();
        if (lengths == null || lengths[MODC] != modCount) {
            int size = this.size();
            lengths = new int[END0 + size + (size == 0 ? 1 : 0)];
            lengths[MODC] = modCount;
            int end = -sepLength;  // cancels leading separator
            lengths[BEG0] = end;
            for (int i = 0; i < size; i++) {
                end += sepLength;  // count leading separator
                end += getCS(i).length();
                lengths[END0 + i] = end;
            }
            this.lengths = lengths;
        }
        return lengths;
    }

    public int length() {
        int[] lengths = getLengths();
        return lengths[lengths.length - 1];
    }

    // Which token does the given index belong to?
    protected int which(int i) {
        if (i < 0) {
            return -1;
        }
        int[] lengths = getLengths();
        for (int hint = lengths[HINT];; hint = 0) {
            for (int wh = hint; wh < lengths.length - END0; wh++) {
                int beg = lengths[BEG0 + wh];
                int end = lengths[END0 + wh];
                if (i >= beg && i < end) {
                    lengths[HINT] = wh;
                    return wh;
                }
            }
            if (hint == 0) {
                return size();  // end of the line
            }
        }
    }

    public char charAt(int i) {
        if (i < 0) {
            return "".charAt(i);
        }
        int wh = which(i);
        int beg = lengths[BEG0 + wh];
        int j = i - beg;
        int sepLength = separator.length();
        if (j < sepLength) {
            return separator.charAt(j);
        }
        return getCS(wh).charAt(j - sepLength);
    }

    public CharSequence subSequence(int beg, int end) {
        //System.out.println("i: "+beg+".."+end);
        if (beg == end) {
            return "";
        }
        if (beg < 0) {
            charAt(beg);  // raise exception
        }
        if (beg > end) {
            charAt(-1);   // raise exception
        }
        int begWh = which(beg);
        int endWh = which(end);
        if (endWh == size() || end == lengths[BEG0 + endWh]) {
            --endWh;
        }
        //System.out.println("wh: "+begWh+".."+endWh);
        int begBase = lengths[BEG0 + begWh];
        int endBase = lengths[BEG0 + endWh];
        int sepLength = separator.length();
        int begFrag = 0;
        if ((beg - begBase) < sepLength) {
            begFrag = sepLength - (beg - begBase);
            beg += begFrag;
        }
        int endFrag = 0;
        if ((end - endBase) < sepLength) {
            endFrag = (end - endBase);
            end = endBase;
            endBase = lengths[BEG0 + --endWh];
        }
        if (false) {
            System.out.print("beg[wbf]end[wbf]");
            int pr[] = {begWh, begBase, begFrag, beg, endWh, endBase, endFrag, end};
            for (int k = 0; k < pr.length; k++) {
                System.out.print((k == 4 ? "   " : " ") + (pr[k]));
            }
            System.out.println();
        }
        if (begFrag > 0 && (end + endFrag) - begBase <= sepLength) {
            // Special case:  Slice the separator.
            beg -= begFrag;
            end += endFrag;
            return separator.substring(beg - begBase, end - begBase);
        }
        if (begWh == endWh && (begFrag + endFrag) == 0) {
            // Special case:  Slice a single token.
            return getCS(begWh).subSequence(beg - begBase - sepLength,
                    end - endBase - sepLength);
        }
        Object[] subTokens = new Object[1 + (endWh - begWh) + 1];
        int fillp = 0;
        if (begFrag == sepLength) {
            // Insert a leading null token to force an initial separator.
            subTokens[fillp++] = "";
            begFrag = 0;
        }
        for (int wh = begWh; wh <= endWh; wh++) {
            CharSequence cs = getCS(wh);
            if (wh == begWh || wh == endWh) {
                // Slice it.
                int csBeg = (wh == begWh) ? (beg - begBase) - sepLength : 0;
                int csEnd = (wh == endWh) ? (end - endBase) - sepLength : cs.length();
                cs = cs.subSequence(csBeg, csEnd);
                if (begFrag > 0 && wh == begWh) {
                    cs = separator.substring(sepLength - begFrag) + cs;
                }
                if (endFrag > 0 && wh == endWh) {
                    cs = cs.toString() + separator.substring(0, endFrag);
                }
            }
            subTokens[fillp++] = cs;
        }
        return new TokenList(subTokens, 0, fillp, separator);
    }

    /** Returns the concatenation of all tokens,
     *  with intervening separator characters.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder(length());
        int size = this.size();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                buf.append(separator);
            }
            buf.append(get(i));
        }
        return buf.toString();
    }

    /*---- TESTING CODE ----
    public static void main(String[] av) {
    if (av.length == 0)  av = new String[]{"one", "2", "", "four"};
    TokenList ts = new TokenList();
    final String SEP = ", ";
    ts.setSeparator(SEP);
    for (int i = -1; i < av.length; i++) {
    if (i >= 0)  ts.addToken(av[i]);
    {
    TokenList tsCopy = new TokenList(ts.toString(), SEP);
    if (!tsCopy.equals(ts)) {
    tsCopy.setSeparator(")(");
    System.out.println("!= ("+tsCopy+")");
    }
    }
    {
    TokenList tsBar = new TokenList(ts, "|");
    tsBar.add(0, "[");
    tsBar.add("]");
    System.out.println(tsBar);
    }
    if (false) {
    int[] ls = ts.getLengths();
    System.out.println("ts: "+ts);
    System.out.print("ls: {");
    for (int j = 0; j < ls.length; j++)  System.out.print(" "+ls[j]);
    System.out.println(" }");
    }
    assert0(ts.size() == i+1);
    assert0(i < 0 || ts.get(i) == av[i]);
    String tss = ts.toString();
    int tslen = tss.length();
    assert0(ts.length() == tss.length());
    for (int n = 0; n < tslen; n++) {
    assert0(ts.charAt(n) == tss.charAt(n));
    }
    for (int j = 0; j < tslen; j++) {
    for (int k = tslen; k >= j; k--) {
    CharSequence sub = ts.subSequence(j, k);
    //System.out.println("|"+sub+"|");
    assert0(sub.toString().equals(tss.substring(j, k)));
    }
    }
    }
    }
    static void assert0(boolean z) {
    if (!z)  throw new RuntimeException("assert failed");
    }
    // ---- TESTING CODE ----*/
}
