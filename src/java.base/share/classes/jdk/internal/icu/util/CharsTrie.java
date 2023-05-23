// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2011-2014, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*   created on: 2011jan06
*   created by: Markus W. Scherer
*   ported from ICU4C ucharstrie.h/.cpp
*/

package jdk.internal.icu.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import jdk.internal.icu.text.UTF16;
import jdk.internal.icu.util.BytesTrie.Result;

/**
 * Light-weight, non-const reader class for a CharsTrie.
 * Traverses a char-serialized data structure with minimal state,
 * for mapping strings (16-bit-unit sequences) to non-negative integer values.
 *
 * <p>This class is not intended for public subclassing.
 *
 * @stable ICU 4.8
 * @author Markus W. Scherer
 */
public final class CharsTrie implements Cloneable, Iterable<CharsTrie.Entry> {
    /**
     * Constructs a CharsTrie reader instance.
     *
     * <p>The CharSequence must contain a copy of a char sequence from the CharsTrieBuilder,
     * with the offset indicating the first char of that sequence.
     * The CharsTrie object will not read more chars than
     * the CharsTrieBuilder generated in the corresponding build() call.
     *
     * <p>The CharSequence is not copied/cloned and must not be modified while
     * the CharsTrie object is in use.
     *
     * @param trieChars CharSequence that contains the serialized trie.
     * @param offset Root offset of the trie in the CharSequence.
     * @stable ICU 4.8
     */
    public CharsTrie(CharSequence trieChars, int offset) {
        chars_=trieChars;
        pos_=root_=offset;
        remainingMatchLength_=-1;
    }

    /**
     * Copy constructor.
     * Makes a shallow copy of the other trie reader object and its state.
     * Does not copy the char array which will be shared.
     * Same as clone() but without the throws clause.
     *
     * @stable ICU 64
     */
    public CharsTrie(CharsTrie other) {
        chars_ = other.chars_;
        root_ = other.root_;
        pos_ = other.pos_;
        remainingMatchLength_ = other.remainingMatchLength_;
    }

    /**
     * Clones this trie reader object and its state,
     * but not the char array which will be shared.
     * @return A shallow clone of this trie.
     * @stable ICU 4.8
     */
    @Override
    public CharsTrie clone() throws CloneNotSupportedException {
        return (CharsTrie) super.clone();  // A shallow copy is just what we need.
    }

    /**
     * Resets this trie to its initial state.
     * @return this
     * @stable ICU 4.8
     */
    public CharsTrie reset() {
        pos_=root_;
        remainingMatchLength_=-1;
        return this;
    }

    /**
     * Returns the state of this trie as a 64-bit integer.
     * The state value is never 0.
     *
     * @return opaque state value
     * @see #resetToState64
     * @stable ICU 64
     */
    public long getState64() {
        return ((long)remainingMatchLength_ << 32) | pos_;
    }

    /**
     * Resets this trie to the saved state.
     * Unlike {@link #resetToState(State)}, the 64-bit state value
     * must be from {@link #getState64()} from the same trie object or
     * from one initialized the exact same way.
     * Because of no validation, this method is faster.
     *
     * @param state The opaque trie state value from getState64().
     * @return this
     * @see #getState64
     * @see #resetToState
     * @see #reset
     * @stable ICU 64
     */
    public CharsTrie resetToState64(long state) {
        remainingMatchLength_ = (int)(state >> 32);
        pos_ = (int)state;
        return this;
    }

    /**
     * CharsTrie state object, for saving a trie's current state
     * and resetting the trie back to this state later.
     * @stable ICU 4.8
     */
    public static final class State {
        /**
         * Constructs an empty State.
         * @stable ICU 4.8
         */
        public State() {}
        private CharSequence chars;
        private int root;
        private int pos;
        private int remainingMatchLength;
    }

    /**
     * Saves the state of this trie.
     * @param state The State object to hold the trie's state.
     * @return this
     * @see #resetToState
     * @stable ICU 4.8
     */
    public CharsTrie saveState(State state) /*const*/ {
        state.chars=chars_;
        state.root=root_;
        state.pos=pos_;
        state.remainingMatchLength=remainingMatchLength_;
        return this;
    }

    /**
     * Resets this trie to the saved state.
     * Slower than {@link #resetToState64(long)} which does not validate the state value.
     *
     * @param state The State object which holds a saved trie state.
     * @return this
     * @throws IllegalArgumentException if the state object contains no state,
     *         or the state of a different trie
     * @see #saveState
     * @see #reset
     * @stable ICU 4.8
     */
    public CharsTrie resetToState(State state) {
        if(chars_==state.chars && chars_!=null && root_==state.root) {
            pos_=state.pos;
            remainingMatchLength_=state.remainingMatchLength;
        } else {
            throw new IllegalArgumentException("incompatible trie state");
        }
        return this;
    }

    /**
     * Determines whether the string so far matches, whether it has a value,
     * and whether another input char can continue a matching string.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result current() /*const*/ {
        int pos=pos_;
        if(pos<0) {
            return Result.NO_MATCH;
        } else {
            int node;
            return (remainingMatchLength_<0 && (node=chars_.charAt(pos))>=kMinValueLead) ?
                    valueResults_[node>>15] : Result.NO_VALUE;
        }
    }

    /**
     * Traverses the trie from the initial state for this input char.
     * Equivalent to reset().next(inUnit).
     * @param inUnit Input char value. Values below 0 and above 0xffff will never match.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result first(int inUnit) {
        remainingMatchLength_=-1;
        return nextImpl(root_, inUnit);
    }

    /**
     * Traverses the trie from the initial state for the
     * one or two UTF-16 code units for this input code point.
     * Equivalent to reset().nextForCodePoint(cp).
     * @param cp A Unicode code point 0..0x10ffff.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result firstForCodePoint(int cp) {
        return cp<=0xffff ?
            first(cp) :
            (first(UTF16.getLeadSurrogate(cp)).hasNext() ?
                next(UTF16.getTrailSurrogate(cp)) :
                Result.NO_MATCH);
    }

    /**
     * Traverses the trie from the current state for this input char.
     * @param inUnit Input char value. Values below 0 and above 0xffff will never match.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result next(int inUnit) {
        int pos=pos_;
        if(pos<0) {
            return Result.NO_MATCH;
        }
        int length=remainingMatchLength_;  // Actual remaining match length minus 1.
        if(length>=0) {
            // Remaining part of a linear-match node.
            if(inUnit==chars_.charAt(pos++)) {
                remainingMatchLength_=--length;
                pos_=pos;
                int node;
                return (length<0 && (node=chars_.charAt(pos))>=kMinValueLead) ?
                        valueResults_[node>>15] : Result.NO_VALUE;
            } else {
                stop();
                return Result.NO_MATCH;
            }
        }
        return nextImpl(pos, inUnit);
    }

    /**
     * Traverses the trie from the current state for the
     * one or two UTF-16 code units for this input code point.
     * @param cp A Unicode code point 0..0x10ffff.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result nextForCodePoint(int cp) {
        return cp<=0xffff ?
            next(cp) :
            (next(UTF16.getLeadSurrogate(cp)).hasNext() ?
                next(UTF16.getTrailSurrogate(cp)) :
                Result.NO_MATCH);
    }

    /**
     * Traverses the trie from the current state for this string.
     * Equivalent to
     * <pre>
     * Result result=current();
     * for(each c in s)
     *   if(!result.hasNext()) return Result.NO_MATCH;
     *   result=next(c);
     * return result;
     * </pre>
     * @param s Contains a string.
     * @param sIndex The start index of the string in s.
     * @param sLimit The (exclusive) end index of the string in s.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result next(CharSequence s, int sIndex, int sLimit) {
        if(sIndex>=sLimit) {
            // Empty input.
            return current();
        }
        int pos=pos_;
        if(pos<0) {
            return Result.NO_MATCH;
        }
        int length=remainingMatchLength_;  // Actual remaining match length minus 1.
        for(;;) {
            // Fetch the next input unit, if there is one.
            // Continue a linear-match node.
            char inUnit;
            for(;;) {
                if(sIndex==sLimit) {
                    remainingMatchLength_=length;
                    pos_=pos;
                    int node;
                    return (length<0 && (node=chars_.charAt(pos))>=kMinValueLead) ?
                            valueResults_[node>>15] : Result.NO_VALUE;
                }
                inUnit=s.charAt(sIndex++);
                if(length<0) {
                    remainingMatchLength_=length;
                    break;
                }
                if(inUnit!=chars_.charAt(pos)) {
                    stop();
                    return Result.NO_MATCH;
                }
                ++pos;
                --length;
            }
            int node=chars_.charAt(pos++);
            for(;;) {
                if(node<kMinLinearMatch) {
                    Result result=branchNext(pos, node, inUnit);
                    if(result==Result.NO_MATCH) {
                        return Result.NO_MATCH;
                    }
                    // Fetch the next input unit, if there is one.
                    if(sIndex==sLimit) {
                        return result;
                    }
                    if(result==Result.FINAL_VALUE) {
                        // No further matching units.
                        stop();
                        return Result.NO_MATCH;
                    }
                    inUnit=s.charAt(sIndex++);
                    pos=pos_;  // branchNext() advanced pos and wrote it to pos_ .
                    node=chars_.charAt(pos++);
                } else if(node<kMinValueLead) {
                    // Match length+1 units.
                    length=node-kMinLinearMatch;  // Actual match length minus 1.
                    if(inUnit!=chars_.charAt(pos)) {
                        stop();
                        return Result.NO_MATCH;
                    }
                    ++pos;
                    --length;
                    break;
                } else if((node&kValueIsFinal)!=0) {
                    // No further matching units.
                    stop();
                    return Result.NO_MATCH;
                } else {
                    // Skip intermediate value.
                    pos=skipNodeValue(pos, node);
                    node&=kNodeTypeMask;
                }
            }
        }
    }

    /**
     * Returns a matching string's value if called immediately after
     * current()/first()/next() returned Result.INTERMEDIATE_VALUE or Result.FINAL_VALUE.
     * getValue() can be called multiple times.
     *
     * Do not call getValue() after Result.NO_MATCH or Result.NO_VALUE!
     * @return The value for the string so far.
     * @stable ICU 4.8
     */
    public int getValue() /*const*/ {
        int pos=pos_;
        int leadUnit=chars_.charAt(pos++);
        assert(leadUnit>=kMinValueLead);
        return (leadUnit&kValueIsFinal)!=0 ?
            readValue(chars_, pos, leadUnit&0x7fff) : readNodeValue(chars_, pos, leadUnit);
    }

    /**
     * Determines whether all strings reachable from the current state
     * map to the same value, and if so, returns that value.
     * @return The unique value in bits 32..1 with bit 0 set,
     *         if all strings reachable from the current state
     *         map to the same value; otherwise returns 0.
     * @stable ICU 4.8
     */
    public long getUniqueValue() /*const*/ {
        int pos=pos_;
        if(pos<0) {
            return 0;
        }
        // Skip the rest of a pending linear-match node.
        long uniqueValue=findUniqueValue(chars_, pos+remainingMatchLength_+1, 0);
        // Ignore internally used bits 63..33; extend the actual value's sign bit from bit 32.
        return (uniqueValue<<31)>>31;
    }

    /**
     * Finds each char which continues the string from the current state.
     * That is, each char c for which it would be next(c)!=Result.NO_MATCH now.
     * @param out Each next char is appended to this object.
     *            (Only uses the out.append(c) method.)
     * @return The number of chars which continue the string from here.
     * @stable ICU 4.8
     */
    public int getNextChars(Appendable out) /*const*/ {
        int pos=pos_;
        if(pos<0) {
            return 0;
        }
        if(remainingMatchLength_>=0) {
            append(out, chars_.charAt(pos));  // Next unit of a pending linear-match node.
            return 1;
        }
        int node=chars_.charAt(pos++);
        if(node>=kMinValueLead) {
            if((node&kValueIsFinal)!=0) {
                return 0;
            } else {
                pos=skipNodeValue(pos, node);
                node&=kNodeTypeMask;
            }
        }
        if(node<kMinLinearMatch) {
            if(node==0) {
                node=chars_.charAt(pos++);
            }
            getNextBranchChars(chars_, pos, ++node, out);
            return node;
        } else {
            // First unit of the linear-match node.
            append(out, chars_.charAt(pos));
            return 1;
        }
    }

    /**
     * Iterates from the current state of this trie.
     * @return A new CharsTrie.Iterator.
     * @stable ICU 4.8
     */
    @Override
    public Iterator iterator() {
        return new Iterator(chars_, pos_, remainingMatchLength_, 0);
    }

    /**
     * Iterates from the current state of this trie.
     * @param maxStringLength If 0, the iterator returns full strings.
     *                        Otherwise, the iterator returns strings with this maximum length.
     * @return A new CharsTrie.Iterator.
     * @stable ICU 4.8
     */
    public Iterator iterator(int maxStringLength) {
        return new Iterator(chars_, pos_, remainingMatchLength_, maxStringLength);
    }

    /**
     * Iterates from the root of a char-serialized BytesTrie.
     * @param trieChars CharSequence that contains the serialized trie.
     * @param offset Root offset of the trie in the CharSequence.
     * @param maxStringLength If 0, the iterator returns full strings.
     *                        Otherwise, the iterator returns strings with this maximum length.
     * @return A new CharsTrie.Iterator.
     * @stable ICU 4.8
     */
    public static Iterator iterator(CharSequence trieChars, int offset, int maxStringLength) {
        return new Iterator(trieChars, offset, -1, maxStringLength);
    }

    /**
     * Return value type for the Iterator.
     * @stable ICU 4.8
     */
    public static final class Entry {
        /**
         * The string.
         * @stable ICU 4.8
         */
        public CharSequence chars;
        /**
         * The value associated with the string.
         * @stable ICU 4.8
         */
        public int value;

        private Entry() {
        }
    }

    /**
     * Iterator for all of the (string, value) pairs in a CharsTrie.
     * @stable ICU 4.8
     */
    public static final class Iterator implements java.util.Iterator<Entry> {
        private Iterator(CharSequence trieChars, int offset, int remainingMatchLength, int maxStringLength) {
            chars_=trieChars;
            pos_=initialPos_=offset;
            remainingMatchLength_=initialRemainingMatchLength_=remainingMatchLength;
            maxLength_=maxStringLength;
            int length=remainingMatchLength_;  // Actual remaining match length minus 1.
            if(length>=0) {
                // Pending linear-match node, append remaining bytes to str_.
                ++length;
                if(maxLength_>0 && length>maxLength_) {
                    length=maxLength_;  // This will leave remainingMatchLength>=0 as a signal.
                }
                str_.append(chars_, pos_, pos_+length);
                pos_+=length;
                remainingMatchLength_-=length;
            }
        }

        /**
         * Resets this iterator to its initial state.
         * @return this
         * @stable ICU 4.8
         */
        public Iterator reset() {
            pos_=initialPos_;
            remainingMatchLength_=initialRemainingMatchLength_;
            skipValue_=false;
            int length=remainingMatchLength_+1;  // Remaining match length.
            if(maxLength_>0 && length>maxLength_) {
                length=maxLength_;
            }
            str_.setLength(length);
            pos_+=length;
            remainingMatchLength_-=length;
            stack_.clear();
            return this;
        }

        /**
         * @return true if there are more elements.
         * @stable ICU 4.8
         */
        @Override
        public boolean hasNext() /*const*/ { return pos_>=0 || !stack_.isEmpty(); }

        /**
         * Finds the next (string, value) pair if there is one.
         *
         * If the string is truncated to the maximum length and does not
         * have a real value, then the value is set to -1.
         * In this case, this "not a real value" is indistinguishable from
         * a real value of -1.
         * @return An Entry with the string and value of the next element.
         * @throws NoSuchElementException - iteration has no more elements.
         * @stable ICU 4.8
         */
        @Override
        public Entry next() {
            int pos=pos_;
            if(pos<0) {
                if(stack_.isEmpty()) {
                    throw new NoSuchElementException();
                }
                // Pop the state off the stack and continue with the next outbound edge of
                // the branch node.
                long top=stack_.remove(stack_.size()-1);
                int length=(int)top;
                pos=(int)(top>>32);
                str_.setLength(length&0xffff);
                length>>>=16;
                if(length>1) {
                    pos=branchNext(pos, length);
                    if(pos<0) {
                        return entry_;  // Reached a final value.
                    }
                } else {
                    str_.append(chars_.charAt(pos++));
                }
            }
            if(remainingMatchLength_>=0) {
                // We only get here if we started in a pending linear-match node
                // with more than maxLength remaining units.
                return truncateAndStop();
            }
            for(;;) {
                int node=chars_.charAt(pos++);
                if(node>=kMinValueLead) {
                    if(skipValue_) {
                        pos=skipNodeValue(pos, node);
                        node&=kNodeTypeMask;
                        skipValue_=false;
                    } else {
                        // Deliver value for the string so far.
                        boolean isFinal=(node&kValueIsFinal)!=0;
                        if(isFinal) {
                            entry_.value=readValue(chars_, pos, node&0x7fff);
                        } else {
                            entry_.value=readNodeValue(chars_, pos, node);
                        }
                        if(isFinal || (maxLength_>0 && str_.length()==maxLength_)) {
                            pos_=-1;
                        } else {
                            // We cannot skip the value right here because it shares its
                            // lead unit with a match node which we have to evaluate
                            // next time.
                            // Instead, keep pos_ on the node lead unit itself.
                            pos_=pos-1;
                            skipValue_=true;
                        }
                        entry_.chars=str_;
                        return entry_;
                    }
                }
                if(maxLength_>0 && str_.length()==maxLength_) {
                    return truncateAndStop();
                }
                if(node<kMinLinearMatch) {
                    if(node==0) {
                        node=chars_.charAt(pos++);
                    }
                    pos=branchNext(pos, node+1);
                    if(pos<0) {
                        return entry_;  // Reached a final value.
                    }
                } else {
                    // Linear-match node, append length units to str_.
                    int length=node-kMinLinearMatch+1;
                    if(maxLength_>0 && str_.length()+length>maxLength_) {
                        str_.append(chars_, pos, pos+maxLength_-str_.length());
                        return truncateAndStop();
                    }
                    str_.append(chars_, pos, pos+length);
                    pos+=length;
                }
            }
        }

        /**
         * Iterator.remove() is not supported.
         * @throws UnsupportedOperationException (always)
         * @stable ICU 4.8
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private Entry truncateAndStop() {
            pos_=-1;
            // We reset entry_.chars every time we return entry_
            // just because the caller might have modified the Entry.
            entry_.chars=str_;
            entry_.value=-1;  // no real value for str
            return entry_;
        }

        private int branchNext(int pos, int length) {
            while(length>kMaxBranchLinearSubNodeLength) {
                ++pos;  // ignore the comparison unit
                // Push state for the greater-or-equal edge.
                stack_.add(((long)skipDelta(chars_, pos)<<32)|((length-(length>>1))<<16)|str_.length());
                // Follow the less-than edge.
                length>>=1;
                pos=jumpByDelta(chars_, pos);
            }
            // List of key-value pairs where values are either final values or jump deltas.
            // Read the first (key, value) pair.
            char trieUnit=chars_.charAt(pos++);
            int node=chars_.charAt(pos++);
            boolean isFinal=(node&kValueIsFinal)!=0;
            int value=readValue(chars_, pos, node&=0x7fff);
            pos=skipValue(pos, node);
            stack_.add(((long)pos<<32)|((length-1)<<16)|str_.length());
            str_.append(trieUnit);
            if(isFinal) {
                pos_=-1;
                entry_.chars=str_;
                entry_.value=value;
                return -1;
            } else {
                return pos+value;
            }
        }

        private CharSequence chars_;
        private int pos_;
        private int initialPos_;
        private int remainingMatchLength_;
        private int initialRemainingMatchLength_;
        private boolean skipValue_;  // Skip intermediate value which was already delivered.

        private StringBuilder str_=new StringBuilder();
        private int maxLength_;
        private Entry entry_=new Entry();

        // The stack stores longs for backtracking to another
        // outbound edge of a branch node.
        // Each long has the offset in chars_ in bits 62..32,
        // the str_.length() from before the node in bits 15..0,
        // and the remaining branch length in bits 31..16.
        // (We could store the remaining branch length minus 1 in bits 30..16 and not use bit 31,
        // but the code looks more confusing that way.)
        private ArrayList<Long> stack_=new ArrayList<>();
    }

    private void stop() {
        pos_=-1;
    }

    // Reads a compact 32-bit integer.
    // pos is already after the leadUnit, and the lead unit has bit 15 reset.
    private static int readValue(CharSequence chars, int pos, int leadUnit) {
        int value;
        if(leadUnit<kMinTwoUnitValueLead) {
            value=leadUnit;
        } else if(leadUnit<kThreeUnitValueLead) {
            value=((leadUnit-kMinTwoUnitValueLead)<<16)|chars.charAt(pos);
        } else {
            value=(chars.charAt(pos)<<16)|chars.charAt(pos+1);
        }
        return value;
    }
    private static int skipValue(int pos, int leadUnit) {
        if(leadUnit>=kMinTwoUnitValueLead) {
            if(leadUnit<kThreeUnitValueLead) {
                ++pos;
            } else {
                pos+=2;
            }
        }
        return pos;
    }
    private static int skipValue(CharSequence chars, int pos) {
        int leadUnit=chars.charAt(pos++);
        return skipValue(pos, leadUnit&0x7fff);
    }

    private static int readNodeValue(CharSequence chars, int pos, int leadUnit) {
        assert(kMinValueLead<=leadUnit && leadUnit<kValueIsFinal);
        int value;
        if(leadUnit<kMinTwoUnitNodeValueLead) {
            value=(leadUnit>>6)-1;
        } else if(leadUnit<kThreeUnitNodeValueLead) {
            value=(((leadUnit&0x7fc0)-kMinTwoUnitNodeValueLead)<<10)|chars.charAt(pos);
        } else {
            value=(chars.charAt(pos)<<16)|chars.charAt(pos+1);
        }
        return value;
    }
    private static int skipNodeValue(int pos, int leadUnit) {
        assert(kMinValueLead<=leadUnit && leadUnit<kValueIsFinal);
        if(leadUnit>=kMinTwoUnitNodeValueLead) {
            if(leadUnit<kThreeUnitNodeValueLead) {
                ++pos;
            } else {
                pos+=2;
            }
        }
        return pos;
    }

    private static int jumpByDelta(CharSequence chars, int pos) {
        int delta=chars.charAt(pos++);
        if(delta>=kMinTwoUnitDeltaLead) {
            if(delta==kThreeUnitDeltaLead) {
                delta=(chars.charAt(pos)<<16)|chars.charAt(pos+1);
                pos+=2;
            } else {
                delta=((delta-kMinTwoUnitDeltaLead)<<16)|chars.charAt(pos++);
            }
        }
        return pos+delta;
    }

    private static int skipDelta(CharSequence chars, int pos) {
        int delta=chars.charAt(pos++);
        if(delta>=kMinTwoUnitDeltaLead) {
            if(delta==kThreeUnitDeltaLead) {
                pos+=2;
            } else {
                ++pos;
            }
        }
        return pos;
    }

    private static Result[] valueResults_={ Result.INTERMEDIATE_VALUE, Result.FINAL_VALUE };

    // Handles a branch node for both next(unit) and next(string).
    private Result branchNext(int pos, int length, int inUnit) {
        // Branch according to the current unit.
        if(length==0) {
            length=chars_.charAt(pos++);
        }
        ++length;
        // The length of the branch is the number of units to select from.
        // The data structure encodes a binary search.
        while(length>kMaxBranchLinearSubNodeLength) {
            if(inUnit<chars_.charAt(pos++)) {
                length>>=1;
                pos=jumpByDelta(chars_, pos);
            } else {
                length=length-(length>>1);
                pos=skipDelta(chars_, pos);
            }
        }
        // Drop down to linear search for the last few units.
        // length>=2 because the loop body above sees length>kMaxBranchLinearSubNodeLength>=3
        // and divides length by 2.
        do {
            if(inUnit==chars_.charAt(pos++)) {
                Result result;
                int node=chars_.charAt(pos);
                if((node&kValueIsFinal)!=0) {
                    // Leave the final value for getValue() to read.
                    result=Result.FINAL_VALUE;
                } else {
                    // Use the non-final value as the jump delta.
                    ++pos;
                    // int delta=readValue(pos, node);
                    int delta;
                    if(node<kMinTwoUnitValueLead) {
                        delta=node;
                    } else if(node<kThreeUnitValueLead) {
                        delta=((node-kMinTwoUnitValueLead)<<16)|chars_.charAt(pos++);
                    } else {
                        delta=(chars_.charAt(pos)<<16)|chars_.charAt(pos+1);
                        pos+=2;
                    }
                    // end readValue()
                    pos+=delta;
                    node=chars_.charAt(pos);
                    result= node>=kMinValueLead ? valueResults_[node>>15] : Result.NO_VALUE;
                }
                pos_=pos;
                return result;
            }
            --length;
            pos=skipValue(chars_, pos);
        } while(length>1);
        if(inUnit==chars_.charAt(pos++)) {
            pos_=pos;
            int node=chars_.charAt(pos);
            return node>=kMinValueLead ? valueResults_[node>>15] : Result.NO_VALUE;
        } else {
            stop();
            return Result.NO_MATCH;
        }
    }

    // Requires remainingLength_<0.
    private Result nextImpl(int pos, int inUnit) {
        int node=chars_.charAt(pos++);
        for(;;) {
            if(node<kMinLinearMatch) {
                return branchNext(pos, node, inUnit);
            } else if(node<kMinValueLead) {
                // Match the first of length+1 units.
                int length=node-kMinLinearMatch;  // Actual match length minus 1.
                if(inUnit==chars_.charAt(pos++)) {
                    remainingMatchLength_=--length;
                    pos_=pos;
                    return (length<0 && (node=chars_.charAt(pos))>=kMinValueLead) ?
                            valueResults_[node>>15] : Result.NO_VALUE;
                } else {
                    // No match.
                    break;
                }
            } else if((node&kValueIsFinal)!=0) {
                // No further matching units.
                break;
            } else {
                // Skip intermediate value.
                pos=skipNodeValue(pos, node);
                node&=kNodeTypeMask;
            }
        }
        stop();
        return Result.NO_MATCH;
    }

    // Helper functions for getUniqueValue().
    // Recursively finds a unique value (or whether there is not a unique one)
    // from a branch.
    // uniqueValue: On input, same as for getUniqueValue()/findUniqueValue().
    // On return, if not 0, then bits 63..33 contain the updated non-negative pos.
    private static long findUniqueValueFromBranch(CharSequence chars, int pos, int length,
                                                  long uniqueValue) {
        while(length>kMaxBranchLinearSubNodeLength) {
            ++pos;  // ignore the comparison unit
            uniqueValue=findUniqueValueFromBranch(chars, jumpByDelta(chars, pos), length>>1, uniqueValue);
            if(uniqueValue==0) {
                return 0;
            }
            length=length-(length>>1);
            pos=skipDelta(chars, pos);
        }
        do {
            ++pos;  // ignore a comparison unit
            // handle its value
            int node=chars.charAt(pos++);
            boolean isFinal=(node&kValueIsFinal)!=0;
            node&=0x7fff;
            int value=readValue(chars, pos, node);
            pos=skipValue(pos, node);
            if(isFinal) {
                if(uniqueValue!=0) {
                    if(value!=(int)(uniqueValue>>1)) {
                        return 0;
                    }
                } else {
                    uniqueValue=((long)value<<1)|1;
                }
            } else {
                uniqueValue=findUniqueValue(chars, pos+value, uniqueValue);
                if(uniqueValue==0) {
                    return 0;
                }
            }
        } while(--length>1);
        // ignore the last comparison byte
        return ((long)(pos+1)<<33)|(uniqueValue&0x1ffffffffL);
    }
    // Recursively finds a unique value (or whether there is not a unique one)
    // starting from a position on a node lead unit.
    // uniqueValue: If there is one, then bits 32..1 contain the value and bit 0 is set.
    // Otherwise, uniqueValue is 0. Bits 63..33 are ignored.
    private static long findUniqueValue(CharSequence chars, int pos, long uniqueValue) {
        int node=chars.charAt(pos++);
        for(;;) {
            if(node<kMinLinearMatch) {
                if(node==0) {
                    node=chars.charAt(pos++);
                }
                uniqueValue=findUniqueValueFromBranch(chars, pos, node+1, uniqueValue);
                if(uniqueValue==0) {
                    return 0;
                }
                pos=(int)(uniqueValue>>>33);
                node=chars.charAt(pos++);
            } else if(node<kMinValueLead) {
                // linear-match node
                pos+=node-kMinLinearMatch+1;  // Ignore the match units.
                node=chars.charAt(pos++);
            } else {
                boolean isFinal=(node&kValueIsFinal)!=0;
                int value;
                if(isFinal) {
                    value=readValue(chars, pos, node&0x7fff);
                } else {
                    value=readNodeValue(chars, pos, node);
                }
                if(uniqueValue!=0) {
                    if(value!=(int)(uniqueValue>>1)) {
                        return 0;
                    }
                } else {
                    uniqueValue=((long)value<<1)|1;
                }
                if(isFinal) {
                    return uniqueValue;
                }
                pos=skipNodeValue(pos, node);
                node&=kNodeTypeMask;
            }
        }
    }

    // Helper functions for getNextChars().
    // getNextChars() when pos is on a branch node.
    private static void getNextBranchChars(CharSequence chars, int pos, int length, Appendable out) {
        while(length>kMaxBranchLinearSubNodeLength) {
            ++pos;  // ignore the comparison unit
            getNextBranchChars(chars, jumpByDelta(chars, pos), length>>1, out);
            length=length-(length>>1);
            pos=skipDelta(chars, pos);
        }
        do {
            append(out, chars.charAt(pos++));
            pos=skipValue(chars, pos);
        } while(--length>1);
        append(out, chars.charAt(pos));
    }
    private static void append(Appendable out, int c) {
        try {
            out.append((char)c);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // CharsTrie data structure
    //
    // The trie consists of a series of char-serialized nodes for incremental
    // Unicode string/char sequence matching. (char=16-bit unsigned integer)
    // The root node is at the beginning of the trie data.
    //
    // Types of nodes are distinguished by their node lead unit ranges.
    // After each node, except a final-value node, another node follows to
    // encode match values or continue matching further units.
    //
    // Node types:
    //  - Final-value node: Stores a 32-bit integer in a compact, variable-length format.
    //    The value is for the string/char sequence so far.
    //  - Match node, optionally with an intermediate value in a different compact format.
    //    The value, if present, is for the string/char sequence so far.
    //
    //  Aside from the value, which uses the node lead unit's high bits:
    //
    //  - Linear-match node: Matches a number of units.
    //  - Branch node: Branches to other nodes according to the current input unit.
    //    The node unit is the length of the branch (number of units to select from)
    //    minus 1. It is followed by a sub-node:
    //    - If the length is at most kMaxBranchLinearSubNodeLength, then
    //      there are length-1 (key, value) pairs and then one more comparison unit.
    //      If one of the key units matches, then the value is either a final value for
    //      the string so far, or a "jump" delta to the next node.
    //      If the last unit matches, then matching continues with the next node.
    //      (Values have the same encoding as final-value nodes.)
    //    - If the length is greater than kMaxBranchLinearSubNodeLength, then
    //      there is one unit and one "jump" delta.
    //      If the input unit is less than the sub-node unit, then "jump" by delta to
    //      the next sub-node which will have a length of length/2.
    //      (The delta has its own compact encoding.)
    //      Otherwise, skip the "jump" delta to the next sub-node
    //      which will have a length of length-length/2.

    // Match-node lead unit values, after masking off intermediate-value bits:

    // 0000..002f: Branch node. If node!=0 then the length is node+1, otherwise
    // the length is one more than the next unit.

    // For a branch sub-node with at most this many entries, we drop down
    // to a linear search.
    /*package*/ static final int kMaxBranchLinearSubNodeLength=5;

    // 0030..003f: Linear-match node, match 1..16 units and continue reading the next node.
    /*package*/ static final int kMinLinearMatch=0x30;
    /*package*/ static final int kMaxLinearMatchLength=0x10;

    // Match-node lead unit bits 14..6 for the optional intermediate value.
    // If these bits are 0, then there is no intermediate value.
    // Otherwise, see the *NodeValue* constants below.
    /*package*/ static final int kMinValueLead=kMinLinearMatch+kMaxLinearMatchLength;  // 0x0040
    /*package*/ static final int kNodeTypeMask=kMinValueLead-1;  // 0x003f

    // A final-value node has bit 15 set.
    /*package*/ static final int kValueIsFinal=0x8000;

    // Compact value: After testing and masking off bit 15, use the following thresholds.
    /*package*/ static final int kMaxOneUnitValue=0x3fff;

    /*package*/ static final int kMinTwoUnitValueLead=kMaxOneUnitValue+1;  // 0x4000
    /*package*/ static final int kThreeUnitValueLead=0x7fff;

    /*package*/ static final int kMaxTwoUnitValue=((kThreeUnitValueLead-kMinTwoUnitValueLead)<<16)-1;  // 0x3ffeffff

    // Compact intermediate-value integer, lead unit shared with a branch or linear-match node.
    /*package*/ static final int kMaxOneUnitNodeValue=0xff;
    /*package*/ static final int kMinTwoUnitNodeValueLead=kMinValueLead+((kMaxOneUnitNodeValue+1)<<6);  // 0x4040
    /*package*/ static final int kThreeUnitNodeValueLead=0x7fc0;

    /*package*/ static final int kMaxTwoUnitNodeValue=
        ((kThreeUnitNodeValueLead-kMinTwoUnitNodeValueLead)<<10)-1;  // 0xfdffff

    // Compact delta integers.
    /*package*/ static final int kMaxOneUnitDelta=0xfbff;
    /*package*/ static final int kMinTwoUnitDeltaLead=kMaxOneUnitDelta+1;  // 0xfc00
    /*package*/ static final int kThreeUnitDeltaLead=0xffff;

    /*package*/ static final int kMaxTwoUnitDelta=((kThreeUnitDeltaLead-kMinTwoUnitDeltaLead)<<16)-1;  // 0x03feffff

    // Fixed value referencing the CharsTrie words.
    private CharSequence chars_;
    private int root_;

    // Iterator variables.

    // Pointer to next trie unit to read. NULL if no more matches.
    private int pos_;
    // Remaining length of a linear-match node, minus 1. Negative if not in such a node.
    private int remainingMatchLength_;
}
