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
     * default constructor
     *
     * @param referent reference
     */
    public FuzzyReference(T referent) {
        super(referent);
    }

    /**
     * default constructor, with ReferenceQueue.  see SoftReference constructor for details
     *
     * @param referent reference
     * @param q        queue
     */
    public FuzzyReference(T referent, ReferenceQueue<T> q) {
        super(referent, q);
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
