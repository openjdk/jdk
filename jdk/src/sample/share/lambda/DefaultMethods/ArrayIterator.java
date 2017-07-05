/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The code sample illustrates the usage of default methods in the JDK 8. Most
 * implementations of {@link Iterator} don't provide a useful
 * {@link Iterator#remove()} method, however,
 * they still have to implement this method to throw
 * an UnsupportedOperationException. With the default method, the same
 * default behavior in interface Iterator itself can be provided.
 */
public class ArrayIterator {

    /** Close the constructor because ArrayIterator is part of the utility
     * class.
     */
    protected ArrayIterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns an iterator that goes over the elements in the array.
     *
     * @param <E> type of an array element
     * @param array source array to iterate over it
     * @return an iterator that goes over the elements in the array
     */
    public static <E> Iterator<E> iterator(final E[] array) {
        return new Iterator<E>() {
            /**
             * Index of the current position
             *
             */
            private int index = 0;

            /**
             * Returns the next element in the iteration
             *
             * @return the next element in the iteration
             * @throws NoSuchElementException if the iteration has no more
             * elements
             */
            @Override
            public boolean hasNext() {
                return (index < array.length);
            }

            /**
             * Returns {@code true} if the iteration has more elements. (In
             * other words, returns {@code true} if {@link #next} returns
             * an element, rather than throwing an exception.)
             *
             * @return {@code true} if the iteration has more elements
             */
            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return array[index++];
            }

            /**
             * This method does not need to be overwritten in JDK 8.
             */
            //@Override
            //public void remove() {
            //    throw UnsupportedOperationException(
            //            "Arrays don't support remove.")
            //}
        };
    }

    /**
     * Sample usage of the ArrayIterator
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        Iterator<String> it = ArrayIterator.iterator(
                new String[]{"one", "two", "three"});

        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }
}
