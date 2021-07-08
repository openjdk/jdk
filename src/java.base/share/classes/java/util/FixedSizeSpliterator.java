package java.base.share.classes.java.util;
import java.util.Spliterator;
import java.util.function.Consumer;
public class FixedSizeSpliterator<T> implements Spliterator<T> {
    private final T[] values;
    private int start;
    private int end;
    private final int THRESHOLD;
    public FixedSizeSpliterator(T[] values, int threshold) {
        this(values, 0, values.length, threshold);
    }
    public FixedSizeSpliterator(T[] values, int start, int end, int threshold) {
        this.values = values;
        this.start = start;
        this.end = end;
        this.THRESHOLD = threshold;
    }

    @Override
    public boolean tryAdvance(Consumer action) {
        if(start< end){
            action.accept(values[start++]);
            return true;
        }
        return false;
    }

    @Override
    public Spliterator trySplit() {
        if(end - start < THRESHOLD){
            return null;
        }
        int mid = start + (end - start)/2;
        return new FixedSizeSpliterator(values, start, start= mid+1, THRESHOLD);
    }

    @Override
    public long estimateSize() {
        return end - start;
    }

    @Override
    public int characteristics() {
        return ORDERED | SIZED | SUBSIZED | NONNULL;
    }
}
