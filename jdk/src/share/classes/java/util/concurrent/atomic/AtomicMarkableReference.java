/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.atomic;

/**
 * An {@code AtomicMarkableReference} maintains an object reference
 * along with a mark bit, that can be updated atomically.
 * <p>
 * <p> Implementation note. This implementation maintains markable
 * references by creating internal objects representing "boxed"
 * [reference, boolean] pairs.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The type of object referred to by this reference
 */
public class AtomicMarkableReference<V>  {

    private static class ReferenceBooleanPair<T> {
        private final T reference;
        private final boolean bit;
        ReferenceBooleanPair(T r, boolean i) {
            reference = r; bit = i;
        }
    }

    private final AtomicReference<ReferenceBooleanPair<V>>  atomicRef;

    /**
     * Creates a new {@code AtomicMarkableReference} with the given
     * initial values.
     *
     * @param initialRef the initial reference
     * @param initialMark the initial mark
     */
    public AtomicMarkableReference(V initialRef, boolean initialMark) {
        atomicRef = new AtomicReference<ReferenceBooleanPair<V>> (new ReferenceBooleanPair<V>(initialRef, initialMark));
    }

    /**
     * Returns the current value of the reference.
     *
     * @return the current value of the reference
     */
    public V getReference() {
        return atomicRef.get().reference;
    }

    /**
     * Returns the current value of the mark.
     *
     * @return the current value of the mark
     */
    public boolean isMarked() {
        return atomicRef.get().bit;
    }

    /**
     * Returns the current values of both the reference and the mark.
     * Typical usage is {@code boolean[1] holder; ref = v.get(holder); }.
     *
     * @param markHolder an array of size of at least one. On return,
     * {@code markholder[0]} will hold the value of the mark.
     * @return the current value of the reference
     */
    public V get(boolean[] markHolder) {
        ReferenceBooleanPair<V> p = atomicRef.get();
        markHolder[0] = p.bit;
        return p.reference;
    }

    /**
     * Atomically sets the value of both the reference and mark
     * to the given update values if the
     * current reference is {@code ==} to the expected reference
     * and the current mark is equal to the expected mark.
     *
     * <p>May <a href="package-summary.html#Spurious">fail spuriously</a>
     * and does not provide ordering guarantees, so is only rarely an
     * appropriate alternative to {@code compareAndSet}.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference the new value for the reference
     * @param expectedMark the expected value of the mark
     * @param newMark the new value for the mark
     * @return true if successful
     */
    public boolean weakCompareAndSet(V       expectedReference,
                                     V       newReference,
                                     boolean expectedMark,
                                     boolean newMark) {
        ReferenceBooleanPair<V> current = atomicRef.get();
        return  expectedReference == current.reference &&
            expectedMark == current.bit &&
            ((newReference == current.reference && newMark == current.bit) ||
             atomicRef.weakCompareAndSet(current,
                                     new ReferenceBooleanPair<V>(newReference,
                                                              newMark)));
    }

    /**
     * Atomically sets the value of both the reference and mark
     * to the given update values if the
     * current reference is {@code ==} to the expected reference
     * and the current mark is equal to the expected mark.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference the new value for the reference
     * @param expectedMark the expected value of the mark
     * @param newMark the new value for the mark
     * @return true if successful
     */
    public boolean compareAndSet(V       expectedReference,
                                 V       newReference,
                                 boolean expectedMark,
                                 boolean newMark) {
        ReferenceBooleanPair<V> current = atomicRef.get();
        return  expectedReference == current.reference &&
            expectedMark == current.bit &&
            ((newReference == current.reference && newMark == current.bit) ||
             atomicRef.compareAndSet(current,
                                     new ReferenceBooleanPair<V>(newReference,
                                                              newMark)));
    }

    /**
     * Unconditionally sets the value of both the reference and mark.
     *
     * @param newReference the new value for the reference
     * @param newMark the new value for the mark
     */
    public void set(V newReference, boolean newMark) {
        ReferenceBooleanPair<V> current = atomicRef.get();
        if (newReference != current.reference || newMark != current.bit)
            atomicRef.set(new ReferenceBooleanPair<V>(newReference, newMark));
    }

    /**
     * Atomically sets the value of the mark to the given update value
     * if the current reference is {@code ==} to the expected
     * reference.  Any given invocation of this operation may fail
     * (return {@code false}) spuriously, but repeated invocation
     * when the current value holds the expected value and no other
     * thread is also attempting to set the value will eventually
     * succeed.
     *
     * @param expectedReference the expected value of the reference
     * @param newMark the new value for the mark
     * @return true if successful
     */
    public boolean attemptMark(V expectedReference, boolean newMark) {
        ReferenceBooleanPair<V> current = atomicRef.get();
        return  expectedReference == current.reference &&
            (newMark == current.bit ||
             atomicRef.compareAndSet
             (current, new ReferenceBooleanPair<V>(expectedReference,
                                                newMark)));
    }
}
