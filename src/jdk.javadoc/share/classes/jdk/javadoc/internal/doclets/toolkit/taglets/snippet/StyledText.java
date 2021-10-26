/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A mutable sequence of individually styleable characters.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class StyledText {

    private Map<String, StyledText> bookmarks;
    private StringBuilder chars;
    private Styles styles;
    private List<WeakReference<SubText>> subtexts;

    public StyledText() {
        init();
    }

    /*
     * This method should be overridden to be no-op by a subclass that wants to
     * inherit the interface but not the implementation, which includes
     * unnecessary internal objects. If this is done, then all public methods
     * should be overridden too, otherwise they will not work.
     *
     * An alternative design would be to provide an interface for styled text;
     * but I ruled that out as unnecessarily heavyweight.
     */
    protected void init() {
        this.bookmarks = new HashMap<>();
        this.chars = new StringBuilder();
        this.styles = new Styles();
        this.subtexts = new ArrayList<>();
    }

    /*
     * For each character of this text adds the provided objects to a set of
     * objects associated with that character.
     */
    public void addStyle(Set<? extends Style> additionalStyles) {
        styles.add(0, length(), additionalStyles);
    }

    public int length() {
        return chars.length();
    }

    /*
     * Replaces all characters of this text with the provided sequence of
     * characters, each of which is associated with all the provided objects.
     */
    public void replace(Set<? extends Style> styles, CharSequence plaintext) {
        replace(0, length(), styles, plaintext);
    }

    /*
     * A multi-purpose operation that can be used to replace, insert or delete
     * text. The effect on a text is as if [start, end) were deleted and
     * then plaintext inserted at start.
     */
    private void replace(int start, int end, Set<? extends Style> styles, CharSequence plaintext) {
        chars.replace(start, end, plaintext.toString());
        this.styles.delete(start, end);
        this.styles.insert(start, plaintext.length(), styles);
        // The number of subtexts is not expected to be big; hence no
        // optimizations are applied
        var iterator = subtexts.iterator();
        while (iterator.hasNext()) {
            WeakReference<SubText> ref = iterator.next();
            SubText txt = ref.get();
            if (txt == null) {
                iterator.remove(); // a stale ref
            } else {
                update(start, end, plaintext.length(), txt);
            }
        }
    }

    /*
     * Updates the text given the scope of the change to reflect text continuity.
     */
    private void update(int start, int end, int newLength, SubText text) {
        assert start <= end;
        assert text.start <= text.end;
        assert newLength >= 0;
        if (text.start == text.end && start == text.start) {
            // insertion into empty text; special-cased for simplicity
            text.end += newLength;
            return;
        }
        if (end <= text.start) { // the change is on the left-hand side of the text
            int diff = newLength - (end - start);
            text.start += diff;
            text.end += diff;
        } else if (text.end <= start) { // the change is on the right-hand side of the text
            // no-op; explicit "if" for clarity
        } else { // the change intersects with the text
            if (text.start <= start && end <= text.end) { // the change is within the text
                text.end += newLength - (end - start);
            } else {
                int intersectionLen = min(end, text.end) - max(start, text.start);
                int oldLen = text.end - text.start;
                if (start <= text.start) {
                    text.start = start + newLength;
                }
                text.end = text.start + oldLen - intersectionLen;
            }
        }
    }

    private void addStyle(int start, int end, Set<? extends Style> additionalStyles) {
        styles.add(start, end, additionalStyles);
    }

    public StyledText getBookmarkedText(String bookmark) {
        return bookmarks.get(Objects.requireNonNull(bookmark));
    }

    /*
     * Maps the provided name to this text, using a flat namespace. A flat
     * namespace means that this text (t), as well as any subtext derived from
     * either t or t's subtext, share the naming map.
     */
    public void bookmark(String name) {
        bookmark(name, 0, length());
    }

    private void bookmark(String name, int start, int end) {
        bookmarks.put(Objects.requireNonNull(name), subText(start, end));
    }

    /*
     * Selects a view of the portion of this text starting from start
     * (inclusive) to end (exclusive).
     *
     * In contrast with java.util.List.subList, returned views provide extra
     * consistency: they reflect structural changes happening to the underlying
     * text and other views thereof.
     */
    public StyledText subText(int start, int end) {
        Objects.checkFromToIndex(start, end, length());
        var s = new SubText(start, end);
        subtexts.add(new WeakReference<>(s));
        return s;
    }

    /*
     * Returns plaintext version of this text. This method is to be used for
     * algorithms that accept String or CharSequence to map the result back to
     * this text.
     *
     * There are no extensible "mutable string" interface. java.lang.Appendable
     * does not support replacements and insertions. StringBuilder/StringBuffer
     * is not extensible. Even if it were extensible, not many general-purpose
     * string algorithms accept it.
     */
    public CharSequence asCharSequence() {
        return chars;
    }

    /*
     * Provides text to the consumer efficiently. The text always calls the
     * consumer at least once; even if the text is empty.
     */
    public void consumeBy(StyledText.Consumer consumer) {
        consumeBy(consumer, 0, length());
    }

    private void consumeBy(StyledText.Consumer consumer, int start, int end) {
        Objects.checkFromToIndex(start, end, length());
        styles.consumeBy(consumer, chars, start, end);
    }

    public StyledText append(Set<? extends Style> styles, CharSequence sequence) {
        subText(length(), length()).replace(styles, sequence);
        return this;
    }

    public StyledText append(StyledText fragment) {
        fragment.consumeBy((style, sequence) -> subText(length(), length()).replace(style, sequence));
        return this;
    }

    @FunctionalInterface
    public interface Consumer {

        void consume(Set<? extends Style> style, CharSequence sequence);
    }

    /*
     * A structure that stores character styles.
     */
    private static final class Styles {

        // Although this structure optimizes neither memory use nor object
        // allocation, it is simple both to implement and reason about.

        // list is a reference to ArrayList because this class accesses list by
        // index, so this is important that the list is RandomAccess, which
        // ArrayList is
        private final ArrayList<Set<Style>> list = new ArrayList<>();

        private void delete(int fromIndex, int toIndex) {
            list.subList(fromIndex, toIndex).clear();
        }

        private void insert(int fromIndex, int length, Set<? extends Style> s) {
            list.addAll(fromIndex, Collections.nCopies(length, Set.copyOf(s)));
        }

        private void add(int fromIndex, int toIndex, Set<? extends Style> additional) {
            Set<Style> copyOfAdditional = Set.copyOf(additional);
            list.subList(fromIndex, toIndex).replaceAll(current -> sum(current, copyOfAdditional));
        }

        private Set<Style> sum(Set<? extends Style> a, Set<Style> b) {
            // assumption: until there are complex texts, the most common
            // scenario is the one where `a` is empty while `b` is not
            if (a.isEmpty()) {
                return b;
            } else {
                Set<Style> c = new HashSet<>(a);
                c.addAll(b);
                return Set.copyOf(c);
            }
        }

        private void consumeBy(StyledText.Consumer consumer, CharSequence seq, int start, int end) {
            if (start == end) {
                // an empty region doesn't have an associated set; special-cased
                // for simplicity to avoid more complicated implementation of
                // this method using a do-while loop
                consumer.consume(Set.of(), "");
            } else {
                for (int i = start, j = i + 1; i < end; i = j) {
                    var ith = list.get(i);
                    while (j < end && ith.equals(list.get(j))) {
                        j++;
                    }
                    consumer.consume(ith, seq.subSequence(i, j));
                }
            }
        }
    }

    final class SubText extends StyledText {

        int start, end;

        private SubText(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected void init() {
            // no-op
        }

        @Override
        public void addStyle(Set<? extends Style> additionalStyles) {
            StyledText.this.addStyle(start, end, additionalStyles);
        }

        @Override
        public int length() {
            return end - start;
        }

        @Override
        public void replace(Set<? extends Style> styles, CharSequence plaintext) {
            // If the "replace" operation affects this text's size, which it
            // can, then that size will be updated along with all other sizes
            // during the bulk "update" operation in tracking text instance.
            StyledText.this.replace(start, end, styles, plaintext);
        }

        @Override
        public StyledText getBookmarkedText(String bookmark) {
            return StyledText.this.getBookmarkedText(bookmark);
        }

        @Override
        public void bookmark(String name) {
            StyledText.this.bookmark(name, start, end);
        }

        @Override
        public StyledText subText(int start, int end) {
            return StyledText.this.subText(this.start + start, this.start + end);
        }

        @Override
        public CharSequence asCharSequence() {
            return StyledText.this.asCharSequence().subSequence(start, end);
        }

        @Override
        public void consumeBy(StyledText.Consumer consumer) {
            StyledText.this.consumeBy(consumer, start, end);
        }
    }
}
