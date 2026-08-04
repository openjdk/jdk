/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 */

package org.openjdk.bench.valhalla.sandbox.corelibs.corelibs;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/**
 * An inline cursor is a reference to an existing or non-existent element
 * of a collection.
 * <p>
 * Cursor values are immutable, the reference to an element
 * does not change but the state of the collection can change
 * so the element is no longer accessible.
 * Calling {@link #get()} throws a {@link ConcurrentModificationException}.
 * Iterating through a Collection proceeds by creating new Cursor
 * from the Collection or advancing to the next or retreating to previous elements.
 * Advancing past the end of the Collection or retreating before the beginning
 * results in Cursor values that are non-existent.
 * A Cursor for an empty Collection does not refer to an element and
 * throws {@link NoSuchElementException}.
 * Modifications to the Collection invalidate every Cursor that was created
 * before the modification.
 * The typical traversal pattern is:
 * <pre>{@code
 *  Collection<T> c = ...;
 *  for (var cursor = c.cursor(); cursor.exists(); cursor = cursor.advance()) {
 *      var el = cursor.get();
 *  }
 * }
 * </pre>
 * <p>
 * Cursors can be used to {@link #remove()} remove an element from the collection.
 * Removing an element modifies the collection making that cursor invalid.
 * The cursor returned from the {@link #remove()} method is a placeholder
 * for the position, the element occupied, between the next and previous elements.
 * It can be moved to the next or previous element to continue the iteration.
 * <p>
 * The typical traversal and remove pattern follows; when an element is
 * removed, the cursor returned from the remove is used to continue the iteration:
 * <pre>{@code
 *  Collection<T> c = ...;
 *  for (var cursor = c.cursor(); cursor.exists(); cursor = cursor.advance()) {
 *      var el = cursor.get();
 *      if (el.equals(...)) {
 *          cursor = cursor.remove();
 *      }
 *  }
 * }
 * </pre>
 * <p>
 * @param <T> the type of the element.
 */
public interface InlineCursor<T> {
    /**
     * Return true if the Cursor refers to an element.
     *
     * If the collection has been modified since the Cursor was created
     * the element can not be known to exist.
     * This method does not throw {@link ConcurrentModificationException}
     * if the collection has been modified but returns false.
     *
     * @return  true if this Cursor refers to an element in the collection and
     *          the collection has not been modified since the cursor was created;
     *          false otherwise
     */
    boolean exists();

    /**
     * Return a Cursor for the next element after the current element.
     * If there is no element following this element the returned
     * Cursor will be non-existent. To wit: {@code Cursor.exists() == false}.
     *
     * @return return a cursor for the next element after this element
     * @throws ConcurrentModificationException if the collection
     *         has been modified since this Cursor was created
     */
    InlineCursor<T> advance();

    /**
     * Return the current element referred to by the Cursor.
     *
     * The behavior must be consistent with {@link #exists()}
     * as long as the collection has not been modified.
     *
     * @return  return the element in the collection if the collection
     *          has not been modified since the cursor was created
     * @throws NoSuchElementException if the referenced element does not exist
     *         or no longer exists
     * @throws ConcurrentModificationException if the collection
     *         has been modified since this Cursor was created
     */
    T get();
}
