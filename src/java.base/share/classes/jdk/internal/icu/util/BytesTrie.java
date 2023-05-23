// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2010-2014, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*   created on: 2010nov23
*   created by: Markus W. Scherer
*   ported from ICU4C bytestrie.h/.cpp
*/
package jdk.internal.icu.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * Light-weight, non-const reader class for a BytesTrie.
 * Traverses a byte-serialized data structure with minimal state,
 * for mapping byte sequences to non-negative integer values.
 *
 * <p>This class is not intended for public subclassing.
 *
 * @stable ICU 4.8
 * @author Markus W. Scherer
 */
public final class BytesTrie implements Cloneable, Iterable<BytesTrie.Entry> {
    /**
     * Constructs a BytesTrie reader instance.
     *
     * <p>The array must contain a copy of a byte sequence from the BytesTrieBuilder,
     * with the offset indicating the first byte of that sequence.
     * The BytesTrie object will not read more bytes than
     * the BytesTrieBuilder generated in the corresponding build() call.
     *
     * <p>The array is not copied/cloned and must not be modified while
     * the BytesTrie object is in use.
     *
     * @param trieBytes Bytes array that contains the serialized trie.
     * @param offset Root offset of the trie in the array.
     * @stable ICU 4.8
     */
    public BytesTrie(byte[] trieBytes, int offset) {
        bytes_=trieBytes;
        pos_=root_=offset;
        remainingMatchLength_=-1;
    }

    /**
     * Copy constructor.
     * Makes a shallow copy of the other trie reader object and its state.
     * Does not copy the byte array which will be shared.
     * Same as clone() but without the throws clause.
     *
     * @stable ICU 64
     */
    public BytesTrie(BytesTrie other) {
        bytes_ = other.bytes_;
        root_ = other.root_;
        pos_ = other.pos_;
        remainingMatchLength_ = other.remainingMatchLength_;
    }

    /**
     * Clones this trie reader object and its state,
     * but not the byte array which will be shared.
     * @return A shallow clone of this trie.
     * @stable ICU 4.8
     */
    @Override
    public BytesTrie clone() throws CloneNotSupportedException {
        return (BytesTrie) super.clone();  // A shallow copy is just what we need.
    }

    /**
     * Resets this trie to its initial state.
     * @return this
     * @stable ICU 4.8
     */
    public BytesTrie reset() {
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
    public BytesTrie resetToState64(long state) {
        remainingMatchLength_ = (int)(state >> 32);
        pos_ = (int)state;
        return this;
    }

    /**
     * BytesTrie state object, for saving a trie's current state
     * and resetting the trie back to this state later.
     * @stable ICU 4.8
     */
    public static final class State {
        /**
         * Constructs an empty State.
         * @stable ICU 4.8
         */
        public State() {}
        private byte[] bytes;
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
    public BytesTrie saveState(State state) /*const*/ {
        state.bytes=bytes_;
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
    public BytesTrie resetToState(State state) {
        if(bytes_==state.bytes && bytes_!=null && root_==state.root) {
            pos_=state.pos;
            remainingMatchLength_=state.remainingMatchLength;
        } else {
            throw new IllegalArgumentException("incompatible trie state");
        }
        return this;
    }

    /**
     * Return values for BytesTrie.next(), CharsTrie.next() and similar methods.
     * @stable ICU 4.8
     */
    public enum Result {
        /**
         * The input unit(s) did not continue a matching string.
         * Once current()/next() return NO_MATCH,
         * all further calls to current()/next() will also return NO_MATCH,
         * until the trie is reset to its original state or to a saved state.
         * @stable ICU 4.8
         */
        NO_MATCH,
        /**
         * The input unit(s) continued a matching string
         * but there is no value for the string so far.
         * (It is a prefix of a longer string.)
         * @stable ICU 4.8
         */
        NO_VALUE,
        /**
         * The input unit(s) continued a matching string
         * and there is a value for the string so far.
         * This value will be returned by getValue().
         * No further input byte/unit can continue a matching string.
         * @stable ICU 4.8
         */
        FINAL_VALUE,
        /**
         * The input unit(s) continued a matching string
         * and there is a value for the string so far.
         * This value will be returned by getValue().
         * Another input byte/unit can continue a matching string.
         * @stable ICU 4.8
         */
        INTERMEDIATE_VALUE;

        // Note: The following methods assume the particular order
        // of enum constants, treating the ordinal() values like bit sets.
        // Do not reorder the enum constants!

        /**
         * Same as (result!=NO_MATCH).
         * @return true if the input bytes/units so far are part of a matching string/byte sequence.
         * @stable ICU 4.8
         */
        public boolean matches() { return this!=NO_MATCH; }

        /**
         * Equivalent to (result==INTERMEDIATE_VALUE || result==FINAL_VALUE).
         * @return true if there is a value for the input bytes/units so far.
         * @see #getValue
         * @stable ICU 4.8
         */
        public boolean hasValue() { return ordinal()>=2; }

        /**
         * Equivalent to (result==NO_VALUE || result==INTERMEDIATE_VALUE).
         * @return true if another input byte/unit can continue a matching string.
         * @stable ICU 4.8
         */
        public boolean hasNext() { return (ordinal()&1)!=0; }
    }

    /**
     * Determines whether the byte sequence so far matches, whether it has a value,
     * and whether another input byte can continue a matching byte sequence.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result current() /*const*/ {
        int pos=pos_;
        if(pos<0) {
            return Result.NO_MATCH;
        } else {
            int node;
            return (remainingMatchLength_<0 && (node=bytes_[pos]&0xff)>=kMinValueLead) ?
                    valueResults_[node&kValueIsFinal] : Result.NO_VALUE;
        }
    }

    /**
     * Traverses the trie from the initial state for this input byte.
     * Equivalent to reset().next(inByte).
     * @param inByte Input byte value. Values -0x100..-1 are treated like 0..0xff.
     *               Values below -0x100 and above 0xff will never match.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result first(int inByte) {
        remainingMatchLength_=-1;
        if(inByte<0) {
            inByte+=0x100;
        }
        return nextImpl(root_, inByte);
    }

    /**
     * Traverses the trie from the current state for this input byte.
     * @param inByte Input byte value. Values -0x100..-1 are treated like 0..0xff.
     *               Values below -0x100 and above 0xff will never match.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result next(int inByte) {
        int pos=pos_;
        if(pos<0) {
            return Result.NO_MATCH;
        }
        if(inByte<0) {
            inByte+=0x100;
        }
        int length=remainingMatchLength_;  // Actual remaining match length minus 1.
        if(length>=0) {
            // Remaining part of a linear-match node.
            if(inByte==(bytes_[pos++]&0xff)) {
                remainingMatchLength_=--length;
                pos_=pos;
                int node;
                return (length<0 && (node=bytes_[pos]&0xff)>=kMinValueLead) ?
                        valueResults_[node&kValueIsFinal] : Result.NO_VALUE;
            } else {
                stop();
                return Result.NO_MATCH;
            }
        }
        return nextImpl(pos, inByte);
    }

    /**
     * Traverses the trie from the current state for this byte sequence.
     * Equivalent to
     * <pre>
     * Result result=current();
     * for(each c in s)
     *   if(!result.hasNext()) return Result.NO_MATCH;
     *   result=next(c);
     * return result;
     * </pre>
     * @param s Contains a string or byte sequence.
     * @param sIndex The start index of the byte sequence in s.
     * @param sLimit The (exclusive) end index of the byte sequence in s.
     * @return The match/value Result.
     * @stable ICU 4.8
     */
    public Result next(byte[] s, int sIndex, int sLimit) {
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
            // Fetch the next input byte, if there is one.
            // Continue a linear-match node.
            byte inByte;
            for(;;) {
                if(sIndex==sLimit) {
                    remainingMatchLength_=length;
                    pos_=pos;
                    int node;
                    return (length<0 && (node=(bytes_[pos]&0xff))>=kMinValueLead) ?
                            valueResults_[node&kValueIsFinal] : Result.NO_VALUE;
                }
                inByte=s[sIndex++];
                if(length<0) {
                    remainingMatchLength_=length;
                    break;
                }
                if(inByte!=bytes_[pos]) {
                    stop();
                    return Result.NO_MATCH;
                }
                ++pos;
                --length;
            }
            for(;;) {
                int node=bytes_[pos++]&0xff;
                if(node<kMinLinearMatch) {
                    Result result=branchNext(pos, node, inByte&0xff);
                    if(result==Result.NO_MATCH) {
                        return Result.NO_MATCH;
                    }
                    // Fetch the next input byte, if there is one.
                    if(sIndex==sLimit) {
                        return result;
                    }
                    if(result==Result.FINAL_VALUE) {
                        // No further matching bytes.
                        stop();
                        return Result.NO_MATCH;
                    }
                    inByte=s[sIndex++];
                    pos=pos_;  // branchNext() advanced pos and wrote it to pos_ .
                } else if(node<kMinValueLead) {
                    // Match length+1 bytes.
                    length=node-kMinLinearMatch;  // Actual match length minus 1.
                    if(inByte!=bytes_[pos]) {
                        stop();
                        return Result.NO_MATCH;
                    }
                    ++pos;
                    --length;
                    break;
                } else if((node&kValueIsFinal)!=0) {
                    // No further matching bytes.
                    stop();
                    return Result.NO_MATCH;
                } else {
                    // Skip intermediate value.
                    pos=skipValue(pos, node);
                    // The next node must not also be a value node.
                    assert((bytes_[pos]&0xff)<kMinValueLead);
                }
            }
        }
    }

    /**
     * Returns a matching byte sequence's value if called immediately after
     * current()/first()/next() returned Result.INTERMEDIATE_VALUE or Result.FINAL_VALUE.
     * getValue() can be called multiple times.
     *
     * Do not call getValue() after Result.NO_MATCH or Result.NO_VALUE!
     * @return The value for the byte sequence so far.
     * @stable ICU 4.8
     */
    public int getValue() /*const*/ {
        int pos=pos_;
        int leadByte=bytes_[pos++]&0xff;
        assert(leadByte>=kMinValueLead);
        return readValue(bytes_, pos, leadByte>>1);
    }

    /**
     * Determines whether all byte sequences reachable from the current state
     * map to the same value, and if so, returns that value.
     * @return The unique value in bits 32..1 with bit 0 set,
     *         if all byte sequences reachable from the current state
     *         map to the same value; otherwise returns 0.
     * @stable ICU 4.8
     */
    public long getUniqueValue() /*const*/ {
        int pos=pos_;
        if(pos<0) {
            return 0;
        }
        // Skip the rest of a pending linear-match node.
        long uniqueValue=findUniqueValue(bytes_, pos+remainingMatchLength_+1, 0);
        // Ignore internally used bits 63..33; extend the actual value's sign bit from bit 32.
        return (uniqueValue<<31)>>31;
    }

    /**
     * Finds each byte which continues the byte sequence from the current state.
     * That is, each byte b for which it would be next(b)!=Result.NO_MATCH now.
     * @param out Each next byte is 0-extended to a char and appended to this object.
     *            (Only uses the out.append(c) method.)
     * @return The number of bytes which continue the byte sequence from here.
     * @stable ICU 4.8
     */
    public int getNextBytes(Appendable out) /*const*/ {
        int pos=pos_;
        if(pos<0) {
            return 0;
        }
        if(remainingMatchLength_>=0) {
            append(out, bytes_[pos]&0xff);  // Next byte of a pending linear-match node.
            return 1;
        }
        int node=bytes_[pos++]&0xff;
        if(node>=kMinValueLead) {
            if((node&kValueIsFinal)!=0) {
                return 0;
            } else {
                pos=skipValue(pos, node);
                node=bytes_[pos++]&0xff;
                assert(node<kMinValueLead);
            }
        }
        if(node<kMinLinearMatch) {
            if(node==0) {
                node=bytes_[pos++]&0xff;
            }
            getNextBranchBytes(bytes_, pos, ++node, out);
            return node;
        } else {
            // First byte of the linear-match node.
            append(out, bytes_[pos]&0xff);
            return 1;
        }
    }

    /**
     * Iterates from the current state of this trie.
     * @return A new BytesTrie.Iterator.
     * @stable ICU 4.8
     */
    @Override
    public Iterator iterator() {
        return new Iterator(bytes_, pos_, remainingMatchLength_, 0);
    }

    /**
     * Iterates from the current state of this trie.
     * @param maxStringLength If 0, the iterator returns full strings/byte sequences.
     *                        Otherwise, the iterator returns strings with this maximum length.
     * @return A new BytesTrie.Iterator.
     * @stable ICU 4.8
     */
    public Iterator iterator(int maxStringLength) {
        return new Iterator(bytes_, pos_, remainingMatchLength_, maxStringLength);
    }

    /**
     * Iterates from the root of a byte-serialized BytesTrie.
     * @param trieBytes Bytes array that contains the serialized trie.
     * @param offset Root offset of the trie in the array.
     * @param maxStringLength If 0, the iterator returns full strings/byte sequences.
     *                        Otherwise, the iterator returns strings with this maximum length.
     * @return A new BytesTrie.Iterator.
     * @stable ICU 4.8
     */
    public static Iterator iterator(byte[] trieBytes, int offset, int maxStringLength) {
        return new Iterator(trieBytes, offset, -1, maxStringLength);
    }

    /**
     * Return value type for the Iterator.
     * @stable ICU 4.8
     */
    public static final class Entry {
        private Entry(int capacity) {
            bytes=new byte[capacity];
        }

        /**
         * @return The length of the byte sequence.
         * @stable ICU 4.8
         */
        public int bytesLength() { return length; }
        /**
         * Returns a byte of the byte sequence.
         * @param index An index into the byte sequence.
         * @return The index-th byte sequence byte.
         * @stable ICU 4.8
         */
        public byte byteAt(int index) { return bytes[index]; }
        /**
         * Copies the byte sequence into a byte array.
         * @param dest Destination byte array.
         * @param destOffset Starting offset to where in dest the byte sequence is copied.
         * @stable ICU 4.8
         */
        public void copyBytesTo(byte[] dest, int destOffset) {
            System.arraycopy(bytes, 0, dest, destOffset, length);
        }
        /**
         * @return The byte sequence as a read-only ByteBuffer.
         * @stable ICU 4.8
         */
        public ByteBuffer bytesAsByteBuffer() {
            return ByteBuffer.wrap(bytes, 0, length).asReadOnlyBuffer();
        }

        /**
         * The value associated with the byte sequence.
         * @stable ICU 4.8
         */
        public int value;

        private void ensureCapacity(int len) {
            if(bytes.length<len) {
                byte[] newBytes=new byte[Math.min(2*bytes.length, 2*len)];
                System.arraycopy(bytes, 0, newBytes, 0, length);
                bytes=newBytes;
            }
        }
        private void append(byte b) {
            ensureCapacity(length+1);
            bytes[length++]=b;
        }
        private void append(byte[] b, int off, int len) {
            ensureCapacity(length+len);
            System.arraycopy(b, off, bytes, length, len);
            length+=len;
        }
        private void truncateString(int newLength) { length=newLength; }

        private byte[] bytes;
        private int length;
    }

    /**
     * Iterator for all of the (byte sequence, value) pairs in a BytesTrie.
     * @stable ICU 4.8
     */
    public static final class Iterator implements java.util.Iterator<Entry> {
        private Iterator(byte[] trieBytes, int offset, int remainingMatchLength, int maxStringLength) {
            bytes_=trieBytes;
            pos_=initialPos_=offset;
            remainingMatchLength_=initialRemainingMatchLength_=remainingMatchLength;
            maxLength_=maxStringLength;
            entry_=new Entry(maxLength_!=0 ? maxLength_ : 32);
            int length=remainingMatchLength_;  // Actual remaining match length minus 1.
            if(length>=0) {
                // Pending linear-match node, append remaining bytes to entry_.
                ++length;
                if(maxLength_>0 && length>maxLength_) {
                    length=maxLength_;  // This will leave remainingMatchLength>=0 as a signal.
                }
                entry_.append(bytes_, pos_, length);
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
            int length=remainingMatchLength_+1;  // Remaining match length.
            if(maxLength_>0 && length>maxLength_) {
                length=maxLength_;
            }
            entry_.truncateString(length);
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
         * Finds the next (byte sequence, value) pair if there is one.
         *
         * If the byte sequence is truncated to the maximum length and does not
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
                entry_.truncateString(length&0xffff);
                length>>>=16;
                if(length>1) {
                    pos=branchNext(pos, length);
                    if(pos<0) {
                        return entry_;  // Reached a final value.
                    }
                } else {
                    entry_.append(bytes_[pos++]);
                }
            }
            if(remainingMatchLength_>=0) {
                // We only get here if we started in a pending linear-match node
                // with more than maxLength remaining bytes.
                return truncateAndStop();
            }
            for(;;) {
                int node=bytes_[pos++]&0xff;
                if(node>=kMinValueLead) {
                    // Deliver value for the byte sequence so far.
                    boolean isFinal=(node&kValueIsFinal)!=0;
                    entry_.value=readValue(bytes_, pos, node>>1);
                    if(isFinal || (maxLength_>0 && entry_.length==maxLength_)) {
                        pos_=-1;
                    } else {
                        pos_=skipValue(pos, node);
                    }
                    return entry_;
                }
                if(maxLength_>0 && entry_.length==maxLength_) {
                    return truncateAndStop();
                }
                if(node<kMinLinearMatch) {
                    if(node==0) {
                        node=bytes_[pos++]&0xff;
                    }
                    pos=branchNext(pos, node+1);
                    if(pos<0) {
                        return entry_;  // Reached a final value.
                    }
                } else {
                    // Linear-match node, append length bytes to entry_.
                    int length=node-kMinLinearMatch+1;
                    if(maxLength_>0 && entry_.length+length>maxLength_) {
                        entry_.append(bytes_, pos, maxLength_-entry_.length);
                        return truncateAndStop();
                    }
                    entry_.append(bytes_, pos, length);
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
            entry_.value=-1;  // no real value for str
            return entry_;
        }

        private int branchNext(int pos, int length) {
            while(length>kMaxBranchLinearSubNodeLength) {
                ++pos;  // ignore the comparison byte
                // Push state for the greater-or-equal edge.
                stack_.add(((long)skipDelta(bytes_, pos)<<32)|((length-(length>>1))<<16)|entry_.length);
                // Follow the less-than edge.
                length>>=1;
                pos=jumpByDelta(bytes_, pos);
            }
            // List of key-value pairs where values are either final values or jump deltas.
            // Read the first (key, value) pair.
            byte trieByte=bytes_[pos++];
            int node=bytes_[pos++]&0xff;
            boolean isFinal=(node&kValueIsFinal)!=0;
            int value=readValue(bytes_, pos, node>>1);
            pos=skipValue(pos, node);
            stack_.add(((long)pos<<32)|((length-1)<<16)|entry_.length);
            entry_.append(trieByte);
            if(isFinal) {
                pos_=-1;
                entry_.value=value;
                return -1;
            } else {
                return pos+value;
            }
        }

        private byte[] bytes_;
        private int pos_;
        private int initialPos_;
        private int remainingMatchLength_;
        private int initialRemainingMatchLength_;

        private int maxLength_;
        private Entry entry_;

        // The stack stores longs for backtracking to another
        // outbound edge of a branch node.
        // Each long has the offset from bytes_ in bits 62..32,
        // the entry_.stringLength() from before the node in bits 15..0,
        // and the remaining branch length in bits 24..16. (Bits 31..25 are unused.)
        // (We could store the remaining branch length minus 1 in bits 23..16 and not use bits 31..24,
        // but the code looks more confusing that way.)
        private ArrayList<Long> stack_=new ArrayList<>();
    }

    private void stop() {
        pos_=-1;
    }

    // Reads a compact 32-bit integer.
    // pos is already after the leadByte, and the lead byte is already shifted right by 1.
    private static int readValue(byte[] bytes, int pos, int leadByte) {
        int value;
        if(leadByte<kMinTwoByteValueLead) {
            value=leadByte-kMinOneByteValueLead;
        } else if(leadByte<kMinThreeByteValueLead) {
            value=((leadByte-kMinTwoByteValueLead)<<8)|(bytes[pos]&0xff);
        } else if(leadByte<kFourByteValueLead) {
            value=((leadByte-kMinThreeByteValueLead)<<16)|((bytes[pos]&0xff)<<8)|(bytes[pos+1]&0xff);
        } else if(leadByte==kFourByteValueLead) {
            value=((bytes[pos]&0xff)<<16)|((bytes[pos+1]&0xff)<<8)|(bytes[pos+2]&0xff);
        } else {
            value=(bytes[pos]<<24)|((bytes[pos+1]&0xff)<<16)|((bytes[pos+2]&0xff)<<8)|(bytes[pos+3]&0xff);
        }
        return value;
    }
    private static int skipValue(int pos, int leadByte) {
        assert(leadByte>=kMinValueLead);
        if(leadByte>=(kMinTwoByteValueLead<<1)) {
            if(leadByte<(kMinThreeByteValueLead<<1)) {
                ++pos;
            } else if(leadByte<(kFourByteValueLead<<1)) {
                pos+=2;
            } else {
                pos+=3+((leadByte>>1)&1);
            }
        }
        return pos;
    }
    private static int skipValue(byte[] bytes, int pos) {
        int leadByte=bytes[pos++]&0xff;
        return skipValue(pos, leadByte);
    }

    /**
     * Reads a jump delta and jumps.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static int jumpByDelta(byte[] bytes, int pos) {
        int delta=bytes[pos++]&0xff;
        if(delta<kMinTwoByteDeltaLead) {
            // nothing to do
        } else if(delta<kMinThreeByteDeltaLead) {
            delta=((delta-kMinTwoByteDeltaLead)<<8)|(bytes[pos++]&0xff);
        } else if(delta<kFourByteDeltaLead) {
            delta=((delta-kMinThreeByteDeltaLead)<<16)|((bytes[pos]&0xff)<<8)|(bytes[pos+1]&0xff);
            pos+=2;
        } else if(delta==kFourByteDeltaLead) {
            delta=((bytes[pos]&0xff)<<16)|((bytes[pos+1]&0xff)<<8)|(bytes[pos+2]&0xff);
            pos+=3;
        } else {
            delta=(bytes[pos]<<24)|((bytes[pos+1]&0xff)<<16)|((bytes[pos+2]&0xff)<<8)|(bytes[pos+3]&0xff);
            pos+=4;
        }
        return pos+delta;
    }

    private static int skipDelta(byte[] bytes, int pos) {
        int delta=bytes[pos++]&0xff;
        if(delta>=kMinTwoByteDeltaLead) {
            if(delta<kMinThreeByteDeltaLead) {
                ++pos;
            } else if(delta<kFourByteDeltaLead) {
                pos+=2;
            } else {
                pos+=3+(delta&1);
            }
        }
        return pos;
    }

    private static Result[] valueResults_={ Result.INTERMEDIATE_VALUE, Result.FINAL_VALUE };

    // Handles a branch node for both next(byte) and next(string).
    private Result branchNext(int pos, int length, int inByte) {
        // Branch according to the current byte.
        if(length==0) {
            length=bytes_[pos++]&0xff;
        }
        ++length;
        // The length of the branch is the number of bytes to select from.
        // The data structure encodes a binary search.
        while(length>kMaxBranchLinearSubNodeLength) {
            if(inByte<(bytes_[pos++]&0xff)) {
                length>>=1;
                pos=jumpByDelta(bytes_, pos);
            } else {
                length=length-(length>>1);
                pos=skipDelta(bytes_, pos);
            }
        }
        // Drop down to linear search for the last few bytes.
        // length>=2 because the loop body above sees length>kMaxBranchLinearSubNodeLength>=3
        // and divides length by 2.
        do {
            if(inByte==(bytes_[pos++]&0xff)) {
                Result result;
                int node=bytes_[pos]&0xff;
                assert(node>=kMinValueLead);
                if((node&kValueIsFinal)!=0) {
                    // Leave the final value for getValue() to read.
                    result=Result.FINAL_VALUE;
                } else {
                    // Use the non-final value as the jump delta.
                    ++pos;
                    // int delta=readValue(pos, node>>1);
                    node>>=1;
                    int delta;
                    if(node<kMinTwoByteValueLead) {
                        delta=node-kMinOneByteValueLead;
                    } else if(node<kMinThreeByteValueLead) {
                        delta=((node-kMinTwoByteValueLead)<<8)|(bytes_[pos++]&0xff);
                    } else if(node<kFourByteValueLead) {
                        delta=((node-kMinThreeByteValueLead)<<16)|((bytes_[pos]&0xff)<<8)|(bytes_[pos+1]&0xff);
                        pos+=2;
                    } else if(node==kFourByteValueLead) {
                        delta=((bytes_[pos]&0xff)<<16)|((bytes_[pos+1]&0xff)<<8)|(bytes_[pos+2]&0xff);
                        pos+=3;
                    } else {
                        delta=(bytes_[pos]<<24)|((bytes_[pos+1]&0xff)<<16)|((bytes_[pos+2]&0xff)<<8)|(bytes_[pos+3]&0xff);
                        pos+=4;
                    }
                    // end readValue()
                    pos+=delta;
                    node=bytes_[pos]&0xff;
                    result= node>=kMinValueLead ? valueResults_[node&kValueIsFinal] : Result.NO_VALUE;
                }
                pos_=pos;
                return result;
            }
            --length;
            pos=skipValue(bytes_, pos);
        } while(length>1);
        if(inByte==(bytes_[pos++]&0xff)) {
            pos_=pos;
            int node=bytes_[pos]&0xff;
            return node>=kMinValueLead ? valueResults_[node&kValueIsFinal] : Result.NO_VALUE;
        } else {
            stop();
            return Result.NO_MATCH;
        }
    }

    // Requires remainingLength_<0.
    private Result nextImpl(int pos, int inByte) {
        for(;;) {
            int node=bytes_[pos++]&0xff;
            if(node<kMinLinearMatch) {
                return branchNext(pos, node, inByte);
            } else if(node<kMinValueLead) {
                // Match the first of length+1 bytes.
                int length=node-kMinLinearMatch;  // Actual match length minus 1.
                if(inByte==(bytes_[pos++]&0xff)) {
                    remainingMatchLength_=--length;
                    pos_=pos;
                    return (length<0 && (node=bytes_[pos]&0xff)>=kMinValueLead) ?
                            valueResults_[node&kValueIsFinal] : Result.NO_VALUE;
                } else {
                    // No match.
                    break;
                }
            } else if((node&kValueIsFinal)!=0) {
                // No further matching bytes.
                break;
            } else {
                // Skip intermediate value.
                pos=skipValue(pos, node);
                // The next node must not also be a value node.
                assert((bytes_[pos]&0xff)<kMinValueLead);
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
    private static long findUniqueValueFromBranch(byte[] bytes, int pos, int length,
                                                  long uniqueValue) {
        while(length>kMaxBranchLinearSubNodeLength) {
            ++pos;  // ignore the comparison byte
            uniqueValue=findUniqueValueFromBranch(bytes, jumpByDelta(bytes, pos), length>>1, uniqueValue);
            if(uniqueValue==0) {
                return 0;
            }
            length=length-(length>>1);
            pos=skipDelta(bytes, pos);
        }
        do {
            ++pos;  // ignore a comparison byte
            // handle its value
            int node=bytes[pos++]&0xff;
            boolean isFinal=(node&kValueIsFinal)!=0;
            int value=readValue(bytes, pos, node>>1);
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
                uniqueValue=findUniqueValue(bytes, pos+value, uniqueValue);
                if(uniqueValue==0) {
                    return 0;
                }
            }
        } while(--length>1);
        // ignore the last comparison byte
        return ((long)(pos+1)<<33)|(uniqueValue&0x1ffffffffL);
    }
    // Recursively finds a unique value (or whether there is not a unique one)
    // starting from a position on a node lead byte.
    // uniqueValue: If there is one, then bits 32..1 contain the value and bit 0 is set.
    // Otherwise, uniqueValue is 0. Bits 63..33 are ignored.
    private static long findUniqueValue(byte[] bytes, int pos, long uniqueValue) {
        for(;;) {
            int node=bytes[pos++]&0xff;
            if(node<kMinLinearMatch) {
                if(node==0) {
                    node=bytes[pos++]&0xff;
                }
                uniqueValue=findUniqueValueFromBranch(bytes, pos, node+1, uniqueValue);
                if(uniqueValue==0) {
                    return 0;
                }
                pos=(int)(uniqueValue>>>33);
            } else if(node<kMinValueLead) {
                // linear-match node
                pos+=node-kMinLinearMatch+1;  // Ignore the match bytes.
            } else {
                boolean isFinal=(node&kValueIsFinal)!=0;
                int value=readValue(bytes, pos, node>>1);
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
                pos=skipValue(pos, node);
            }
        }
    }

    // Helper functions for getNextBytes().
    // getNextBytes() when pos is on a branch node.
    private static void getNextBranchBytes(byte[] bytes, int pos, int length, Appendable out) {
        while(length>kMaxBranchLinearSubNodeLength) {
            ++pos;  // ignore the comparison byte
            getNextBranchBytes(bytes, jumpByDelta(bytes, pos), length>>1, out);
            length=length-(length>>1);
            pos=skipDelta(bytes, pos);
        }
        do {
            append(out, bytes[pos++]&0xff);
            pos=skipValue(bytes, pos);
        } while(--length>1);
        append(out, bytes[pos]&0xff);
    }
    private static void append(Appendable out, int c) {
        try {
            out.append((char)c);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // BytesTrie data structure
    //
    // The trie consists of a series of byte-serialized nodes for incremental
    // string/byte sequence matching. The root node is at the beginning of the trie data.
    //
    // Types of nodes are distinguished by their node lead byte ranges.
    // After each node, except a final-value node, another node follows to
    // encode match values or continue matching further bytes.
    //
    // Node types:
    //  - Value node: Stores a 32-bit integer in a compact, variable-length format.
    //    The value is for the string/byte sequence so far.
    //    One node bit indicates whether the value is final or whether
    //    matching continues with the next node.
    //  - Linear-match node: Matches a number of bytes.
    //  - Branch node: Branches to other nodes according to the current input byte.
    //    The node byte is the length of the branch (number of bytes to select from)
    //    minus 1. It is followed by a sub-node:
    //    - If the length is at most kMaxBranchLinearSubNodeLength, then
    //      there are length-1 (key, value) pairs and then one more comparison byte.
    //      If one of the key bytes matches, then the value is either a final value for
    //      the string/byte sequence so far, or a "jump" delta to the next node.
    //      If the last byte matches, then matching continues with the next node.
    //      (Values have the same encoding as value nodes.)
    //    - If the length is greater than kMaxBranchLinearSubNodeLength, then
    //      there is one byte and one "jump" delta.
    //      If the input byte is less than the sub-node byte, then "jump" by delta to
    //      the next sub-node which will have a length of length/2.
    //      (The delta has its own compact encoding.)
    //      Otherwise, skip the "jump" delta to the next sub-node
    //      which will have a length of length-length/2.

    // Node lead byte values.

    // 00..0f: Branch node. If node!=0 then the length is node+1, otherwise
    // the length is one more than the next byte.

    // For a branch sub-node with at most this many entries, we drop down
    // to a linear search.
    /*package*/ static final int kMaxBranchLinearSubNodeLength=5;

    // 10..1f: Linear-match node, match 1..16 bytes and continue reading the next node.
    /*package*/ static final int kMinLinearMatch=0x10;
    /*package*/ static final int kMaxLinearMatchLength=0x10;

    // 20..ff: Variable-length value node.
    // If odd, the value is final. (Otherwise, intermediate value or jump delta.)
    // Then shift-right by 1 bit.
    // The remaining lead byte value indicates the number of following bytes (0..4)
    // and contains the value's top bits.
    /*package*/ static final int kMinValueLead=kMinLinearMatch+kMaxLinearMatchLength;  // 0x20
    // It is a final value if bit 0 is set.
    private static final int kValueIsFinal=1;

    // Compact value: After testing bit 0, shift right by 1 and then use the following thresholds.
    /*package*/ static final int kMinOneByteValueLead=kMinValueLead/2;  // 0x10
    /*package*/ static final int kMaxOneByteValue=0x40;  // At least 6 bits in the first byte.

    /*package*/ static final int kMinTwoByteValueLead=kMinOneByteValueLead+kMaxOneByteValue+1;  // 0x51
    /*package*/ static final int kMaxTwoByteValue=0x1aff;

    /*package*/ static final int kMinThreeByteValueLead=kMinTwoByteValueLead+(kMaxTwoByteValue>>8)+1;  // 0x6c
    /*package*/ static final int kFourByteValueLead=0x7e;

    // A little more than Unicode code points. (0x11ffff)
    /*package*/ static final int kMaxThreeByteValue=((kFourByteValueLead-kMinThreeByteValueLead)<<16)-1;

    /*package*/ static final int kFiveByteValueLead=0x7f;

    // Compact delta integers.
    /*package*/ static final int kMaxOneByteDelta=0xbf;
    /*package*/ static final int kMinTwoByteDeltaLead=kMaxOneByteDelta+1;  // 0xc0
    /*package*/ static final int kMinThreeByteDeltaLead=0xf0;
    /*package*/ static final int kFourByteDeltaLead=0xfe;
    /*package*/ static final int kFiveByteDeltaLead=0xff;

    /*package*/ static final int kMaxTwoByteDelta=((kMinThreeByteDeltaLead-kMinTwoByteDeltaLead)<<8)-1;  // 0x2fff
    /*package*/ static final int kMaxThreeByteDelta=((kFourByteDeltaLead-kMinThreeByteDeltaLead)<<16)-1;  // 0xdffff

    // Fixed value referencing the BytesTrie bytes.
    private byte[] bytes_;
    private int root_;

    // Iterator variables.

    // Index of next trie byte to read. Negative if no more matches.
    private int pos_;
    // Remaining length of a linear-match node, minus 1. Negative if not in such a node.
    private int remainingMatchLength_;
};
