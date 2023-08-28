package jdk.internal.natives;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public final class StructUtil {

    private StructUtil() {}

    public static boolean equals(MemorySegment a, MemorySegment b) {
        return a.mismatch(b) == -1;
    }

    // Todo: improve when https://bugs.openjdk.org/browse/JDK-8294432 becomes available
    public static int hashCode(MemorySegment segment) {
        return Arrays.hashCode(segment.toArray(JAVA_BYTE));
    }

    public static String toString(String type, MemoryLayout layout, MemorySegment segment) {
        var formatter = HexFormat.ofDelimiter(" ");
        return type + "{"
                + layout.toString() + " = " + formatter.formatHex(segment.toArray(JAVA_BYTE))
                + "}";
    }

    record StructMapperImpl<T>(MemoryLayout layout,
                               Function<? super MemorySegment, ? extends T> constructor)
            implements StructMapper<T> {

        @Override
        public T of(MemorySegment segment) {
            return constructor.apply(segment);
        }

        @Override
        public T of(Arena arena) {
            return of(arena.allocate(layout));
        }

        public List<T> ofElements(int size, MemorySegment segment) {
            List<T> result = segment.elements(layout)
                    .limit(size)
                    .map(this::of)
                    .toList();
            if (result.size() < size) {
                throw new NoSuchElementException("Insufficient segment size: " + segment);
            }
            return result;
        }

        @Override
        public List<T> ofElements(MemorySegment segment) {
            return ofElements(Math.toIntExact(segment.byteSize()/layout.byteSize()), segment);
        }
    }

}
