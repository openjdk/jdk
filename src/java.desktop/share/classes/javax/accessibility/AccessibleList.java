/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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
 */

package javax.accessibility;

/**
 *  This interface provides list specific data.
 *
 * @author Artem Semenov
 */
public interface AccessibleList {

    /**
     * A value for the selectionMode property: select one list index
     * at a time.
     */
    int SINGLE_SELECTION = 0;

    /**
     * A value for the selectionMode property: select one contiguous
     * range of indices at a time.
     */
    int SINGLE_INTERVAL_SELECTION = 1;

    /**
     * A value for the selectionMode property: select one or more
     * contiguous ranges of indices at a time.
     */
    int MULTIPLE_INTERVAL_SELECTION = 2;

    /**
     * Returns the length of the list.
     * @return the length of the list
     */
    int getSize();

    /**
     * Changes the selection to be between {@code index0} and {@code index1}
     *
     * @param index0 one end of the interval.
     * @param index1 other end of the interval
     */
    void setSelectionInterval(int index0, int index1);

    /**
     * Changes the selection to be the set union of the current selection
     * and the indices between {@code index0} and {@code index1} inclusive.
     *
     * @param index0 one end of the interval.
     * @param index1 other end of the interval
     */
    void addSelectionInterval(int index0, int index1);

    /**
     * Changes the selection to be the set difference of the current selection
     * and the indices between {@code index0} and {@code index1} inclusive.
     *
     * @param index0 one end of the interval.
     * @param index1 other end of the interval
     */
    void removeSelectionInterval(int index0, int index1);

    /**
     * Returns the first selected index or -1 if the selection is empty.
     *
     * @return the first selected index or -1 if the selection is empty.
     */
    int getMinSelectionIndex();

    /**
     * Returns the last selected index or -1 if the selection is empty.
     *
     * @return the last selected index or -1 if the selection is empty.
     */
    int getMaxSelectionIndex();


    /**
     * Returns true if the specified index is selected.
     *
     * @param index an index
     * @return {@code true} if the specified index is selected
     */
    boolean isSelectedIndex(int index);

    /**
     * Returns true if no indices are selected.
     *
     * @return {@code true} if no indices are selected.
     */
    boolean isSelectionEmpty();

    /**
     * Insert {@code length} indices beginning before/after {@code index}. This is typically
     * called to sync the selection model with a corresponding change
     * in the data model.
     *
     * @param index the beginning of the interval
     * @param length the length of the interval
     * @param before if {@code true}, interval inserts before the {@code index},
     *               otherwise, interval inserts after the {@code index}
     */
    void insertIndexInterval(int index, int length, boolean before);

    /**
     * Remove the indices in the interval {@code index0,index1} (inclusive) from
     * the selection model. This is typically called to sync the selection
     * model width a corresponding change in the data model.
     *
     * @param index0 the beginning of the interval
     * @param index1 the end of the interval
     */
    void removeIndexInterval(int index0, int index1);

    /**
     * Sets the selection mode. The following list describes the accepted
     * selection modes:
     * <ul>
     * <li>{@code AccessibleList.SINGLE_SELECTION} -
     *   Only one list index can be selected at a time. In this mode,
     *   {@code setSelectionInterval} and {@code addSelectionInterval} are
     *   equivalent, both replacing the current selection with the index
     *   represented by the second argument (the "lead").
     * <li>{@code AccessibleList.SINGLE_INTERVAL_SELECTION} -
     *   Only one contiguous interval can be selected at a time.
     *   In this mode, {@code addSelectionInterval} behaves like
     *   {@code setSelectionInterval} (replacing the current selection),
     *   unless the given interval is immediately adjacent to or overlaps
     *   the existing selection, and can therefore be used to grow it.
     * <li>{@code AccessibleList.MULTIPLE_INTERVAL_SELECTION} -
     *   In this mode, there's no restriction on what can be selected.
     * </ul>
     *
     * @param selectionMode the selection mode
     */
    void setSelectionMode(int selectionMode);

    /**
     * Returns the current selection mode.
     *
     * @return the current selection mode
     * @see #setSelectionMode
     */
    int getSelectionMode();

    /**
     * Returns an array of all of the selected indices in the selection model,
     * in increasing order.
     *
     * @return all of the selected indices, in increasing order,
     *         or an empty array if nothing is selected
     */
    int[] getSelectedIndices();

    /**
     * Returns the number of selected items.
     *
     * @return the number of selected items, 0 if no items are selected
     */
    int getSelectedItemsCount();
}
