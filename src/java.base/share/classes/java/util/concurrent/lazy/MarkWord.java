package java.util.concurrent.lazy;

import jdk.internal.misc.Unsafe;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * To be removed!
 */
public class MarkWord {

    private MarkWord() {
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * A
     * @param args a
     */
    public static void main(String[] args) {

        Foo foo = new Foo();

        printMark(foo);
        synchronized (foo) {
            printMark(foo);
        }
        printMark(foo);

        int LOCK_OFFSET = UNSAFE.addressSize();

        System.out.println("UNSAFE.isLocked(foo) = " + UNSAFE.isLocked(foo));
        System.out.println("UNSAFE.getIntVolatile(foo, LOCK_OFFSET) = " + UNSAFE.getIntVolatile(foo, LOCK_OFFSET));
        synchronized (foo) {
            System.out.println("UNSAFE.isLocked(foo) = " + UNSAFE.isLocked(foo));
            System.out.println("UNSAFE.getIntVolatile(foo, LOCK_OFFSET) = " + UNSAFE.getIntVolatile(foo, LOCK_OFFSET));
        }


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

    static final class Foo {
    }

}
