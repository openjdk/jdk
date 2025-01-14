package jdk.internal.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class MethodHandleMapper {

    private MethodHandleMapper() {}

    public static <T, R> R mapSystemCall(Class<T> type,
                                         Class<R> resultType,
                                         String stateName) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(resultType);
        Objects.requireNonNull(stateName);
/*        final Method abstractMethod = checkTypeAndMethod(type, handle);
        final List<Class<?>> parameters = checkParameters(abstractMethod, handle, 0);
        final List<Class<?>> throwables = checkThrowables(abstractMethod);
        return generate(lookup, type, abstractMethod, parameters, throwables, handle, stateName);*/
        return null;
    }

    public static <T> T mapSystemCall(MethodHandles.Lookup lookup,
                                      Class<T> type,
                                      MethodHandle handle,
                                      String stateName) {

        Objects.requireNonNull(lookup);
        Objects.requireNonNull(stateName);
        final Method abstractMethod = checkTypeAndMethod(type, handle);
        final List<Class<?>> parameters = checkParameters(abstractMethod, handle, 0);
        final List<Class<?>> throwables = checkThrowables(abstractMethod);
        return generate(lookup, type, abstractMethod, parameters, throwables, handle, stateName);
    }

    public static <T> T map(MethodHandles.Lookup lookup,
                            Class<T> type,
                            MethodHandle handle) {
        Objects.requireNonNull(lookup);
        final Method abstractMethod = checkTypeAndMethod(type, handle);
        final List<Class<?>> parameters = checkParameters(abstractMethod, handle, 0);
        final List<Class<?>> throwables = checkThrowables(abstractMethod);
        return MethodHandleProxies.asInterfaceInstance(type, handle);
    }

    private static <T> T generate(MethodHandles.Lookup lookup,
                                  Class<T> type,
                                  Method abstractMethod,
                                  List<Class<?>> parameters,
                                  List<Class<?>> throwables,
                                  MethodHandle handle,
                                  String stateName) {
        // generate
        // lookup.defineHiddenClass(...)
        return MethodHandleProxies.asInterfaceInstance(type, handle);
    }

    private static Method checkTypeAndMethod(Class<?> type,
                                             MethodHandle handle) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(handle);
        if (!type.isInterface()) {
            throw newIAE(type, "is not an interface");
        }
        if (type.isHidden()) {
            throw newIAE(type, "is hidden");
        }
        if (type.isSealed()) {
            throw newIAE(type, "is sealed");
        }
        if (type.getAnnotation(FunctionalInterface.class) == null) {
            throw newIAE(type, "is not a @" + FunctionalInterface.class.getSimpleName());
        }
        if (!(type.getTypeParameters().length == 0)) {
            throw newIAE(type, "has type parameters");
        }

        Method abstractMethod = null;
        for (Method m : type.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers())) {
                abstractMethod = m;
            }
        }
        if (abstractMethod == null) {
            throw new InternalError("Unable to find the abstract method in " + type);
        }

        final Class<?> returnType = abstractMethod.getReturnType();
        final Class<?> handleReturnType = handle.type().returnType();
        if (!returnType.equals(handleReturnType)) {
            throw newIAE(type, " has a return type of '" + returnType +
                    "' but the provided handle has a return type of '" + handleReturnType + "'");
        }
        return abstractMethod;
    }

    private static List<Class<?>> checkParameters(Method method,
                                                  MethodHandle handle,
                                                  int handleOffset) {
        final List<Class<?>> parameters = new ArrayList<>();
        final MethodType handleType = handle.type();
        final Parameter[] methodParameters = method.getParameters();
        for (int i = 0; i < methodParameters.length; i++) {
            final Parameter parameter = methodParameters[i];
            if (!(parameter.getType().equals(handleType.parameterType(i + handleOffset)))) {
                throw new IllegalArgumentException("The abstract method " + method + " has a parameter list of " +
                        Arrays.toString(methodParameters) + " but the method handle has " + handleType);
            }
            parameters.add(parameter.getType());
        }
        return List.copyOf(parameters);
    }

    private static List<Class<?>> checkThrowables(Method method) {
        final Class<?>[] exceptionTypes = method.getExceptionTypes();
        final List<Class<?>> exceptions = new ArrayList<>(Arrays.asList(exceptionTypes));
        return List.copyOf(exceptions);
    }

    private static IllegalArgumentException newIAE(Class<?> type, String msg) {
        return new IllegalArgumentException(type + " " + msg);
    }

}
