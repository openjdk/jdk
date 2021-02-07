package jdk.internal.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Objects;

import jdk.internal.vm.annotation.Stable;

class MHMethodAccessor extends MethodAccessorImpl {

    @Stable
    private final MethodHandle target;
    @Stable
    private final Class<?> owner;
    @Stable
    private final int modifiers;

    public MHMethodAccessor(Class<?> owner, MethodHandle target, int modifiers) {
        this.target = target;
        this.owner = owner;
        this.modifiers = modifiers;
    }

    @Override
    public Object invoke(Object obj, Object[] args)
            throws IllegalArgumentException, InvocationTargetException {
        if (!Modifier.isStatic(modifiers)) {
            Objects.requireNonNull(obj);
            owner.cast(obj);
        }
        try {
            return target.invokeExact(obj, args);
        } catch (Error | RuntimeException | InvocationTargetException e) {
            throw e;
        } catch (Throwable t) {
            // This should not happen
            throw new InternalError(t);
        }
    }

}
