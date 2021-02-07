package jdk.internal.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

class MHConstructorAccessor extends ConstructorAccessorImpl {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @Stable
    private final MethodHandle target;
    @Stable
    private final Class<?> instantiatedType;

    public MHConstructorAccessor(MethodHandle target, Class<?> instantiatedType) {
        this.target = target;
        this.instantiatedType = instantiatedType;
    }

    @Override
    public Object newInstance(Object[] args) throws InstantiationException,
            IllegalArgumentException, InvocationTargetException {
        UNSAFE.ensureClassInitialized(instantiatedType); // May throw ExInInitError
        try {
            return target.invokeExact(args);
        } catch (Error | RuntimeException | InvocationTargetException e) {
            throw e;
        }  catch (Throwable t) {
            // This should not happen
            throw new InternalError(t);
        }
    }

}
