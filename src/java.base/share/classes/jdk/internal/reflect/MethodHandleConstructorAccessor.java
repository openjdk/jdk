package jdk.internal.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

import jdk.internal.vm.annotation.Stable;

class MethodHandleConstructorAccessor extends ConstructorAccessorImpl {

    @Stable
    private final MethodHandle target;

    MethodHandleConstructorAccessor(MethodHandle target) {
        this.target = target
                .asSpreader(Object[].class, target.type().parameterCount())
                .asType(MethodType.methodType(Object.class, Object[].class));
    }

    @Override
    public Object newInstance(Object[] args) throws InstantiationException,
            IllegalArgumentException, InvocationTargetException {
        try {
            return target.invokeExact(args);
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }

}
