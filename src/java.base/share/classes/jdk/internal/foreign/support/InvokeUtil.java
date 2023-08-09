package jdk.internal.foreign.support;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class InvokeUtil {
    private InvokeUtil() {
    }

    public static boolean invokeAsBoolean(MethodHandle handle, Object... args) {
        assert args.getClass() == Object[].class;
        try {
            return switch (args.length) {
                case 0 -> (boolean) handle.invokeExact();
                case 1 -> (boolean) handle.invokeExact(args[0]);
                case 2 -> (boolean) handle.invokeExact(args[0], args[1]);
                case 3 -> (boolean) handle.invokeExact(args[0], args[1], args[2]);
                default -> throw new UnsupportedOperationException(args.length + " arguments");
            };
        } catch (Throwable throwable) {
            throw newInternalError(boolean.class, throwable, handle, args);
        }
    }

    public static int invokeAsInt(MethodHandle handle, Object... args) {
        assert args.getClass() == Object[].class;
        try {
            return switch (args.length) {
                case 0 -> (int) handle.invokeExact();
                case 1 -> (int) handle.invokeExact(args[0]);
                case 2 -> (int) handle.invokeExact(args[0], args[1]);
                case 3 -> (int) handle.invokeExact(args[0], args[1], args[2]);
                default -> throw new UnsupportedOperationException(args.length + " arguments");
            };
        } catch (Throwable throwable) {
            throw newInternalError(int.class, throwable, handle, args);
        }
    }

    public static MemorySegment invokeAsSegment(MethodHandle handle, Object... args) {
        assert args.getClass() == Object[].class;
        try {
            return switch (args.length) {
                case 0 -> (MemorySegment) handle.invokeExact();
                case 1 -> (MemorySegment) handle.invokeExact(args[0]);
                case 2 -> (MemorySegment) handle.invokeExact(args[0], args[1]);
                case 3 -> (MemorySegment) handle.invokeExact(args[0], args[1], args[2]);
                default -> throw new UnsupportedOperationException(args.length + " arguments");
            };
        } catch (Throwable throwable) {
            throw newInternalError(MemorySegment.class, throwable, handle, args);
        }
    }

    public static InternalError newInternalError(Class<?> rType,
                                                 Throwable throwable,
                                                 MethodHandle handle,
                                                 Object... args) {
        // Only show the argument types for security reasons
        return new InternalError("Unable to invoke " + handle +
                " using " + argTypes(args) +
                " expecting a return type of " + rType, throwable);
    }

    private static String argTypes(Object... args) {
        return "[" + Arrays.stream(args)
                .map(Object::getClass)
                .map(Class::getName)
                .collect(Collectors.joining(", ")) + "]";
    }

}
