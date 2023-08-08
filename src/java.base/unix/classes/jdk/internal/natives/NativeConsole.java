package jdk.internal.natives;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static jdk.internal.foreign.DefaultNativeLookupUtil.*;

public final class NativeConsole {

    // See: https://github.com/openjdk/jdk/pull/4390/files
    // See: https://en.cppreference.com/w/cpp/io/c/std_streams
    // See: https://linux.die.net/man/3/isatty

    private NativeConsole() {}

    private static final MethodHandle FILE_NO;
    private static final MethodHandle IS_A_TTY;
    private static final MethodHandle STDIN;
    private static final MethodHandle STDOUT;

    public static boolean istty() {
        try {
            return isatty(fileno(stdin())) &&
                   isatty(fileno(stdout()));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static String encoding() {
        return null;
    }

    static boolean isatty(int fd) throws Throwable {
        return (boolean) IS_A_TTY.invokeExact(fd);
    }

    static int fileno(MemorySegment stream) throws Throwable {
        return (int) FILE_NO.invokeExact(stream);
    }

    static MemorySegment stdin() throws Throwable {
        return (MemorySegment) STDIN.invokeExact();
    }

    static MemorySegment stdout() throws Throwable {
        return (MemorySegment) STDOUT.invokeExact();
    }

    static {
        FILE_NO = downcall("fileno", JAVA_INT, ADDRESS);
        IS_A_TTY = intIsOne(downcall("isatty", JAVA_INT, JAVA_INT));
        STDIN = downcallOfVoid("stdin",ADDRESS);
        STDOUT = downcallOfVoid("stdout",ADDRESS);
    }

}
