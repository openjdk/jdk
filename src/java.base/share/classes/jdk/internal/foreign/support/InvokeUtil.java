package jdk.internal.foreign.support;

import java.lang.invoke.MethodHandle;

public final class InvokeUtil {

    private InvokeUtil() {}

    public static InternalError newInternalError(MethodHandle handle,
                                                 Throwable throwable) {
        // Only show the argument types for security reasons
        return new InternalError("Unable to invoke " + handle, throwable);
    }

}
