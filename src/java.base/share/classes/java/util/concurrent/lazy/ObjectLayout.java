package java.util.concurrent.lazy;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.concurrent.lazy.PreComputedEmptyLazyReference;
import jdk.internal.util.concurrent.lazy.PreComputedLazyReference;
import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReference;
import jdk.internal.util.concurrent.lazy.StandardLazyReference;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * To be removed! This is just a temoporarly debug class to inspect object layout
 */
public class ObjectLayout {

    private ObjectLayout() {
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * A
     *
     * @param args a
     */
    public static void main(String[] args) {

        analyze(PreComputedEmptyLazyReference.class);
        analyze(PreComputedLazyReference.class);

        analyze(StandardEmptyLazyReference.class);
        analyze(StandardLazyReference.class);

    }

    static void analyze(Class<?> c) {
        System.out.println("Fields of " + c.getName());

/*        intanceFields(c)
                .forEach(System.out::println);*/
/*
        System.out.println("details:");*/

        intanceFields(c)
                .map(cf -> new ClassFieldOffset(cf.clazz().getSimpleName(), cf.fieldName(), (int) UNSAFE.objectFieldOffset(cf.clazz(), cf.fieldName())))
                .sorted(Comparator.comparingInt(ClassFieldOffset::offset))
                .forEach(System.out::println);

        System.out.println();
    }

    @SuppressWarnings("unchecked")
    static <T> Stream<ClassFieldName> intanceFields(Class<T> clazz) {
        return Stream.iterate(clazz, c -> (Class<T>) c.getSuperclass())
                .takeWhile(c -> c != Object.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredFields())
                        .filter(f -> !Modifier.isStatic(f.getModifiers()))
                        .map(f -> new ClassFieldName(c, f.getName())));
    }

    record ClassFieldName(Class<?> clazz, String fieldName){
    }

    record ClassFieldOffset(String klass, String field, int offset) {
    }

    static void printMark(Object o) {
        int[] mark = new int[3];
        for (int i = 0; i < mark.length; i++) {
            mark[i] = UNSAFE.getInt(o, i * Integer.BYTES);
        }

        var hex = Arrays.stream(mark)
                .boxed()
                .map(i -> Integer.toHexString(i))
                .collect(Collectors.joining(", "));

        System.out.println(hex);
    }

}
