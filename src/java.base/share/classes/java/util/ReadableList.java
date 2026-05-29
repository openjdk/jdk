import java.util.*;
import java.util.function.Consumer;
 
public interface ReadableList<E> extends Iterable<E> {
    int size();
    boolean isEmpty();
    boolean contains(Object o);
    boolean containsAll(Collection<?> c);
    E get(int index);
    int indexOf(Object o);
    int lastIndexOf(Object o);
    Object[] toArray();
    <T> T[] toArray(T[] a);
    List<E> subList(int fromIndex, int toIndex);
    ListIterator<E> listIterator();
    ListIterator<E> listIterator(int index);
}
