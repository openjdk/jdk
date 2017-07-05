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
 * An {@code AtomicStampedReference} maintains an object reference
 * along with an integer "stamp", that can be updated atomically.
 *
 * <p> Implementation note. This implementation maintains stamped
 * references by creating internal objects representing "boxed"
 * [reference, integer] pairs.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The type of object referred to by this reference
 */
public class AtomicStampedReference<V>  {

    private static class ReferenceIntegerPair<T> {
        private final T reference;
        private final int integer;
        ReferenceIntegerPair(T r, int i) {
            reference = r; integer = i;
        }
    }

    private final AtomicReference<ReferenceIntegerPair<V>>  atomicRef;

    /**
     * Creates a new {@code AtomicStampedReference} with the given
     * initial values.
     *
     * @param initialRef the initial reference
     * @param initialStamp the initial stamp
     */
    public AtomicStampedReference(V initialRef, int initialStamp) {
        atomicRef = new AtomicReference<ReferenceIntegerPair<V>>
            (new ReferenceIntegerPair<V>(initialRef, initialStamp));
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
     * Returns the current value of the stamp.
     *
     * @return the current value of the stamp
     */
    public int getStamp() {
        return atomicRef.get().integer;
    }

    /**
     * Returns the current values of both the reference and the stamp.
     * Typical usage is {@code int[1] holder; ref = v.get(holder); }.
     *
     * @param stampHolder an array of size of at least one.  On return,
     * {@code stampholder[0]} will hold the value of the stamp.
     * @return the current value of the reference
     */
    public V get(int[] stampHolder) {
        ReferenceIntegerPair<V> p = atomicRef.get();
        stampHolder[0] = p.integer;
        return p.reference;
    }

    /**
     * Atomically sets the value of both the reference and stamp
     * to the given update values if the
     * current reference is {@code ==} to the expected reference
     * and the current stamp is equal to the expected stamp.
     *
     * <p>May <a href="package-summary.html#Spurious">fail spuriously</a>
     * and does not provide ordering guarantees, so is only rarely an
     * appropriate alternative to {@code compareAndSet}.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference the new value for the reference
     * @param expectedStamp the expected value of the stamp
     * @param newStamp the new value for the stamp
     * @return true if successful
     */
    public boolean weakCompareAndSet(V      expectedReference,
                                     V      newReference,
                                     int    expectedStamp,
                                     int    newStamp) {
        ReferenceIntegerPair<V> current = atomicRef.get();
        return  expectedReference == current.reference &&
            expectedStamp == current.integer &&
            ((newReference == current.reference &&
              newStamp == current.integer) ||
             atomicRef.weakCompareAndSet(current,
                                     new ReferenceIntegerPair<V>(newReference,
                                                              newStamp)));
    }

    /**
     * Atomically sets the value of both the reference and stamp
     * to the given update values if the
     * current reference is {@code ==} to the expected reference
     * and the current stamp is equal to the expected stamp.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference the new value for the reference
     * @param expectedStamp the expected value of the stamp
     * @param newStamp the new value for the stamp
     * @return true if successful
     */
    public boolean compareAndSet(V      expectedReference,
                                 V      newReference,
                                 int    expectedStamp,
                                 int    newStamp) {
        ReferenceIntegerPair<V> current = atomicRef.get();
        return  expectedReference == current.reference &&
            expectedStamp == current.integer &&
            ((newReference == current.reference &&
              newStamp == current.integer) ||
             atomicRef.compareAndSet(current,
                                     new ReferenceIntegerPair<V>(newReference,
                                                              newStamp)));
    }


    /**
     * Unconditionally sets the value of both the reference and stamp.
     *
     * @param newReference the new value for the reference
     * @param newStamp the new value for the stamp
     */
    public void set(V newReference, int newStamp) {
        ReferenceIntegerPair<V> current = atomicRef.get();
        if (newReference != current.reference || newStamp != current.integer)
            atomicRef.set(new ReferenceIntegerPair<V>(newReference, newStamp));
    }

    /**
     * Atomically sets the value of the stamp to the given update value
     * if the current reference is {@code ==} to the expected
     * reference.  Any given invocation of this operation may fail
     * (return {@code false}) spuriously, but repeated invocation
     * when the current value holds the expected value and no other
     * thread is also attempting to set the value will eventually
     * succeed.
     *
     * @param expectedReference the expected value of the reference
     * @param newStamp the new value for the stamp
     * @return true if successful
     */
    public boolean attemptStamp(V expectedReference, int newStamp) {
        ReferenceIntegerPair<V> current = atomicRef.get();
        return  expectedReference == current.reference &&
            (newStamp == current.integer ||
             atomicRef.compareAndSet(current,
                                     new ReferenceIntegerPair<V>(expectedReference,
                                                              newStamp)));
    }
}
