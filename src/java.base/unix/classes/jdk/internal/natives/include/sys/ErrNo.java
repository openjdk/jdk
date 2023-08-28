package jdk.internal.natives.include.sys;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;

// Generated partly via: jextract --source -t jdk.internal.natives.include.sys -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/errno.h /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/errno.h
public final class ErrNo {

    public ErrNo() {}

    public static final StructLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();

    public static final String CSS_ERROR_NAME = "errno";
    private static final VarHandle ERROR_HANDLE = CAPTURE_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(CSS_ERROR_NAME));

    public static int error(MemorySegment segment) {
        return (int)ERROR_HANDLE.get(segment);
    }

    public sealed interface HasErrno {
        int errno();
    }

    public sealed interface HasIsError {
        boolean isError();
    }

    public record Fd(int fd, int errno) implements HasErrno, HasIsError {

        @Override
        public boolean isError() {
            return fd < 0;
        }

        static Fd of(int fd, MemorySegment errNo) {
            if (fd < 0) {
                return new Fd(fd, error(errNo));
            }
            return new Fd(fd, 0);
        }

    }

    public record Result(int result, int errno) implements HasErrno, HasIsError {

        @Override
        public boolean isError() {
            return result < 0;
        }

        static Result of(int result, MemorySegment errNo) {
            if (result < 0) {
                return new Result(result, error(errNo));
            }
            return new Result(result, 0);
        }

    }

    /**
     * {@snippet :
     * #define EPROTONOSUPPORT 43
     * }
     */
    public static int EPROTONOSUPPORT = 43;

    /**
     * {@snippet :
     * #define EAFNOSUPPORT 47
     * }
     */
    public static int EAFNOSUPPORT = 47;

}
