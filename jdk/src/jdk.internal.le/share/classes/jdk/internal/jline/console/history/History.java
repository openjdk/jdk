/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.history;

import java.util.Iterator;
import java.util.ListIterator;

/**
 * Console history.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public interface History
    extends Iterable<History.Entry>
{
    int size();

    boolean isEmpty();

    int index();

    void clear();

    CharSequence get(int index);

    void add(CharSequence line);

    /**
     * Set the history item at the given index to the given CharSequence.
     *
     * @param index the index of the history offset
     * @param item the new item
     * @since 2.7
     */
    void set(int index, CharSequence item);

    /**
     * Remove the history element at the given index.
     *
     * @param i the index of the element to remove
     * @return the removed element
     * @since 2.7
     */
    CharSequence remove(int i);

    /**
     * Remove the first element from history.
     *
     * @return the removed element
     * @since 2.7
     */
    CharSequence removeFirst();

    /**
     * Remove the last element from history
     *
     * @return the removed element
     * @since 2.7
     */
    CharSequence removeLast();

    void replace(CharSequence item);

    //
    // Entries
    //

    interface Entry
    {
        int index();

        CharSequence value();
    }

    ListIterator<Entry> entries(int index);

    ListIterator<Entry> entries();

    Iterator<Entry> iterator();

    //
    // Navigation
    //

    CharSequence current();

    boolean previous();

    boolean next();

    boolean moveToFirst();

    boolean moveToLast();

    boolean moveTo(int index);

    void moveToEnd();
}
