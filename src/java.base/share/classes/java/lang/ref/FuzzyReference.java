/*    
    Copyright (C) 2020

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. 
*/
package java.lang.ref;

/**
 * Numerically re-prioritizable reference.
 * Repurposes SoftReference.timestamp field as a priority value.
 * <p>
 * Intended to re-use all SoftReference-related VM features
 * except its time-as-priority behavior.
 */
public class FuzzyReference<T> extends SoftReference<T> {

    /**
     * default constructor.  priority initialized to 0
     *
     * @param referent reference
     */
    public FuzzyReference(T referent) {
        this(referent, 0);
    }

    /**
     * default constructor
     *
     * @param referent reference
     * @param pri initial priority
     */
    public FuzzyReference(T referent, long pri) {
        super(referent);
        pri(pri);
    }

    /**
     * default constructor, with ReferenceQueue.  see SoftReference constructor for details
     *
     * @param referent reference
     * @param q        queue
     */
    public FuzzyReference(T referent, ReferenceQueue<T> q) {
        super(referent, q);
        pri(0);
    }

    /**
     * @return reference priority
     */
    public final long pri() {
        return timestamp;
    }

    /**
     * sets reference priority
     *
     * @param p new priority value
     */
    public final void pri(long p) {
        timestamp = p;
    }

    /**
     * @return reference, without triggering: SoftReference.timestamp=clock
     */
    @Override
    public T get() {
        return _get();
    }

}
