/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.internal.lookup;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * This class is abstraction for all method handle, switchpoint and method type
 * operations. This enables the functionality interface to be subclassed and
 * intrumensted, as it has been proven vital to keep the number of method
 * handles in the system down.
 *
 * All operations of the above type should go through this class, and not
 * directly into java.lang.invoke
 *
 */
public final class MethodHandleFactory {

    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();
    private static final MethodHandles.Lookup LOOKUP        = MethodHandles.lookup();

    private static final Level TRACE_LEVEL = Level.INFO;

    private MethodHandleFactory() {
    }

    /**
     * Runtime exception that collects every reason that a method handle lookup operation can go wrong
     */
    @SuppressWarnings("serial")
    public static class LookupException extends RuntimeException {
        /**
         * Constructor
         * @param e causing exception
         */
        public LookupException(final Exception e) {
            super(e);
        }
    }

    /**
     * Helper function that takes a class or an object with a toString override
     * and shortens it to notation after last dot. This is used to facilitiate
     * pretty printouts in various debug loggers - internal only
     *
     * @param obj class or object
     *
     * @return pretty version of object as string
     */
    public static String stripName(final Object obj) {
        if (obj == null) {
            return "null";
        }

        if (obj instanceof Class) {
            return ((Class<?>)obj).getSimpleName();
        }
        return obj.toString();
    }

    private static final MethodHandleFunctionality STANDARD = new StandardMethodHandleFunctionality();
    private static final MethodHandleFunctionality FUNC;

    private static final String DEBUG_PROPERTY = "nashorn.methodhandles.debug";
    private static final DebugLogger LOG = new DebugLogger("methodhandles", DEBUG_PROPERTY);

    static {
        if (LOG.isEnabled() || Options.getBooleanProperty(DEBUG_PROPERTY)) {
            if (Options.getStringProperty(DEBUG_PROPERTY, "").equals("create")) {
                FUNC = new TraceCreateMethodHandleFunctionality();
            } else {
                FUNC = new TraceMethodHandleFunctionality();
            }
        } else {
            FUNC  = STANDARD;
        }
    }

    private static final boolean PRINT_STACKTRACE = Options.getBooleanProperty("nashorn.methodhandles.debug.stacktrace");


    /**
     * Return the method handle functionality used for all method handle operations
     * @return a method handle functionality implementation
     */
    public static MethodHandleFunctionality getFunctionality() {
        return FUNC;
    }

    private static final MethodHandle TRACE        = STANDARD.findStatic(LOOKUP, MethodHandleFactory.class, "traceArgs",   MethodType.methodType(void.class, DebugLogger.class, String.class, int.class, Object[].class));
    private static final MethodHandle TRACE_RETURN = STANDARD.findStatic(LOOKUP, MethodHandleFactory.class, "traceReturn", MethodType.methodType(Object.class, DebugLogger.class, Object.class));

    /**
     * Tracer that is applied before a value is returned from the traced function. It will output the return
     * value and its class
     *
     * @param value return value for filter
     * @return return value unmodified
     */
    static Object traceReturn(final DebugLogger logger, final Object value) {
        final String str = "\treturn: " + stripName(value) + " [type=" + (value == null ? "null" : stripName(value.getClass()) + ']');
        logger.log(TRACE_LEVEL, str);
        return value;
    }

    /**
     * Tracer that is applied before a function is called, printing the arguments
     *
     * @param tag  tag to start the debug printout string
     * @param paramStart param index to start outputting from
     * @param args arguments to the function
     */
    static void traceArgs(final DebugLogger logger, final String tag, final int paramStart, final Object... args) {
        final StringBuilder sb = new StringBuilder();

        sb.append(tag);

        for (int i = paramStart; i < args.length; i++) {
            if (i == paramStart) {
                sb.append(" => args: ");
            }

            sb.append('\'').
                append(stripName(argString(args[i]))).
                append('\'').
                append(' ').
                append('[').
                append("type=").
                append(args[i] == null ? "null" : stripName(args[i].getClass())).
                append(']');

            if (i + 1 < args.length) {
                sb.append(", ");
            }
        }

        assert logger != null;
        logger.log(TRACE_LEVEL, sb);
        stacktrace(logger);
    }

    private static void stacktrace(final DebugLogger logger) {
        if (!PRINT_STACKTRACE) {
            return;
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos);
        new Throwable().printStackTrace(ps);
        logger.log(TRACE_LEVEL, baos.toString());
    }

    private static String argString(final Object arg) {
        if (arg == null) {
            return "null";
        }

        if (arg.getClass().isArray()) {
            final List<Object> list = new ArrayList<>();
            for (final Object elem : (Object[])arg) {
                list.add('\'' + argString(elem) + '\'');
            }

            return list.toString();
        }

        if (arg instanceof ScriptObject) {
            return arg.toString() +
                " (map=" + Debug.id((((ScriptObject)arg).getMap())) +
                ")";
        }

        return arg.toString();
    }

    /**
     * Add a debug printout to a method handle, tracing parameters and return values
     *
     * @param logger a specific logger to which to write the output
     * @param mh  method handle to trace
     * @param tag start of trace message
     * @return traced method handle
     */
    public static MethodHandle addDebugPrintout(final DebugLogger logger, final MethodHandle mh, final Object tag) {
        return addDebugPrintout(logger, mh, 0, true, tag);
    }


    /**
     * Add a debug printout to a method handle, tracing parameters and return values
     *
     * @param logger a specific logger to which to write the output
     * @param mh  method handle to trace
     * @param paramStart first param to print/trace
     * @param printReturnValue should we print/trace return value if available?
     * @param tag start of trace message
     * @return  traced method handle
     */
    public static MethodHandle addDebugPrintout(final DebugLogger logger, final MethodHandle mh, final int paramStart, final boolean printReturnValue, final Object tag) {
        final MethodType type = mh.type();

        if (logger != null && logger.levelAbove(TRACE_LEVEL)) {
            return mh;
        }

        assert logger != null;
        assert TRACE != null;

        MethodHandle trace = MethodHandles.insertArguments(TRACE, 0, logger, tag, paramStart);

        trace = MethodHandles.foldArguments(
                mh,
                trace.asCollector(
                    Object[].class,
                    type.parameterCount()).
                asType(type.changeReturnType(void.class)));

        final Class<?> retType = type.returnType();
        if (retType != void.class && printReturnValue) {
            final MethodHandle traceReturn = MethodHandles.insertArguments(TRACE_RETURN, 0, logger);
            trace = MethodHandles.filterReturnValue(trace,
                    traceReturn.asType(
                        traceReturn.type().changeParameterType(0, retType).changeReturnType(retType)));
        }

        return trace;
    }

    /**
     * The standard class that marshalls all method handle operations to the java.lang.invoke
     * package. This exists only so that it can be subclassed and method handles created from
     * Nashorn made possible to instrument.
     *
     * All Nashorn classes should use the MethodHandleFactory for their method handle operations
     */
    private static class StandardMethodHandleFunctionality implements MethodHandleFunctionality {

        @Override
        public MethodHandle filterArguments(final MethodHandle target, final int pos, final MethodHandle... filters) {
            return MethodHandles.filterArguments(target, pos, filters);
        }

        @Override
        public MethodHandle filterReturnValue(final MethodHandle target, final MethodHandle filter) {
            return MethodHandles.filterReturnValue(target, filter);
        }

        @Override
        public MethodHandle guardWithTest(final MethodHandle test, final MethodHandle target, final MethodHandle fallback) {
            return MethodHandles.guardWithTest(test, target, fallback);
        }

        @Override
        public MethodHandle insertArguments(final MethodHandle target, final int pos, final Object... values) {
            return MethodHandles.insertArguments(target, pos, values);
        }

        @Override
        public MethodHandle dropArguments(final MethodHandle target, final int pos, final Class<?>... valueTypes) {
            return MethodHandles.dropArguments(target, pos, valueTypes);
        }

        @Override
        public MethodHandle dropArguments(final MethodHandle target, final int pos, final List<Class<?>> valueTypes) {
            return MethodHandles.dropArguments(target, pos, valueTypes);
        }

        @Override
        public MethodHandle asType(final MethodHandle handle, final MethodType type) {
            return handle.asType(type);
        }

        @Override
        public MethodHandle bindTo(final MethodHandle handle, final Object x) {
            return handle.bindTo(x);
        }

        @Override
        public MethodHandle foldArguments(final MethodHandle target, final MethodHandle combiner) {
            return MethodHandles.foldArguments(target, combiner);
        }

        @Override
        public MethodHandle explicitCastArguments(final MethodHandle target, final MethodType type) {
            return MethodHandles.explicitCastArguments(target, type);
        }

        @Override
        public MethodHandle arrayElementGetter(final Class<?> type) {
            return MethodHandles.arrayElementGetter(type);
        }

        @Override
        public MethodHandle arrayElementSetter(final Class<?> type) {
            return MethodHandles.arrayElementSetter(type);
        }

        @Override
        public MethodHandle throwException(final Class<?> returnType, final Class<? extends Throwable> exType) {
            return MethodHandles.throwException(returnType, exType);
        }

        @Override
        public MethodHandle constant(final Class<?> type, final Object value) {
            return MethodHandles.constant(type, value);
        }

        @Override
        public MethodHandle asCollector(final MethodHandle handle, final Class<?> arrayType, final int arrayLength) {
            return handle.asCollector(arrayType, arrayLength);
        }

        @Override
        public MethodHandle asSpreader(final MethodHandle handle, final Class<?> arrayType, final int arrayLength) {
            return handle.asSpreader(arrayType, arrayLength);
        }

        @Override
        public MethodHandle getter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            try {
                return explicitLookup.findGetter(clazz, name, type);
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                throw new LookupException(e);
            }
        }

        @Override
        public MethodHandle staticGetter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            try {
                return explicitLookup.findStaticGetter(clazz, name, type);
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                throw new LookupException(e);
            }
        }


        @Override
        public MethodHandle setter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            try {
                return explicitLookup.findSetter(clazz, name, type);
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                throw new LookupException(e);
            }
        }

        @Override
        public MethodHandle staticSetter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            try {
                return explicitLookup.findStaticSetter(clazz, name, type);
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                throw new LookupException(e);
            }
        }

        @Override
        public MethodHandle find(final Method method) {
            try {
                return PUBLIC_LOOKUP.unreflect(method);
            } catch (final IllegalAccessException e) {
                throw new LookupException(e);
            }
        }

        @Override
        public MethodHandle findStatic(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final MethodType type) {
            try {
                return explicitLookup.findStatic(clazz, name, type);
            } catch (final NoSuchMethodException | IllegalAccessException e) {
                throw new LookupException(e);
            }
        }

        @Override
        public MethodHandle findVirtual(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final MethodType type) {
            try {
                return explicitLookup.findVirtual(clazz, name, type);
            } catch (final NoSuchMethodException | IllegalAccessException e) {
                throw new LookupException(e);
            }
        }

        @Override
        public SwitchPoint createSwitchPoint() {
            return new SwitchPoint();
        }

        @Override
        public MethodHandle guardWithTest(final SwitchPoint sp, final MethodHandle before, final MethodHandle after) {
            return sp.guardWithTest(before, after);
        }

        @Override
        public MethodType type(final Class<?> returnType, final Class<?>... paramTypes) {
            return MethodType.methodType(returnType, paramTypes);
        }

    }

    /**
     * Class used for instrumenting and debugging Nashorn generated method handles
     */
    private static class TraceMethodHandleFunctionality extends StandardMethodHandleFunctionality {

        protected static String describe(final Object... data) {
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < data.length; i++) {
                final Object d = data[i];
                if (d == null) {
                    sb.append("<null> ");
                } else if (d instanceof String || d instanceof ConsString) {
                    sb.append(d.toString());
                    sb.append(' ');
                } else if (d.getClass().isArray()) {
                    sb.append("[ ");
                    for (final Object da : (Object[])d) {
                        sb.append(describe(new Object[]{ da })).append(' ');
                    }
                    sb.append("] ");
                } else {
                    sb.append(d)
                        .append('{')
                        .append(Integer.toHexString(System.identityHashCode(d)))
                        .append('}');
                }

                if (i + 1 < data.length) {
                    sb.append(", ");
                }
            }

            return sb.toString();
        }

        public MethodHandle debug(final MethodHandle master, final String str, final Object... args) {
            return addDebugPrintout(LOG, master, Integer.MAX_VALUE, false, str + ' ' + describe(args));
        }

        @Override
        public MethodHandle filterArguments(final MethodHandle target, final int pos, final MethodHandle... filters) {
            final MethodHandle mh = super.filterArguments(target, pos, filters);
            return debug(mh, "filterArguments", target, pos, filters);
        }

        @Override
        public MethodHandle filterReturnValue(final MethodHandle target, final MethodHandle filter) {
            final MethodHandle mh = super.filterReturnValue(target, filter);
            return debug(mh, "filterReturnValue", target, filter);
        }

        @Override
        public MethodHandle guardWithTest(final MethodHandle test, final MethodHandle target, final MethodHandle fallback) {
            final MethodHandle mh = super.guardWithTest(test, target, fallback);
            return debug(mh, "guardWithTest", test, target, fallback);
        }

        @Override
        public MethodHandle insertArguments(final MethodHandle target, final int pos, final Object... values) {
            final MethodHandle mh = super.insertArguments(target, pos, values);
            return debug(mh, "insertArguments", target, pos, values);
        }

        @Override
        public MethodHandle dropArguments(final MethodHandle target, final int pos, final Class<?>... values) {
            final MethodHandle mh = super.dropArguments(target, pos, values);
            return debug(mh, "dropArguments", target, pos, values);
        }

        @Override
        public MethodHandle dropArguments(final MethodHandle target, final int pos, final List<Class<?>> values) {
            final MethodHandle mh = super.dropArguments(target, pos, values);
            return debug(mh, "dropArguments", target, pos, values);
        }

        @Override
        public MethodHandle asType(final MethodHandle handle, final MethodType type) {
            final MethodHandle mh = super.asType(handle, type);
            return debug(mh, "asType", handle, type);
        }

        @Override
        public MethodHandle bindTo(final MethodHandle handle, final Object x) {
            final MethodHandle mh = super.bindTo(handle, x);
            return debug(mh, "bindTo", handle, x);
        }

        @Override
        public MethodHandle foldArguments(final MethodHandle target, final MethodHandle combiner) {
            final MethodHandle mh = super.foldArguments(target, combiner);
            return debug(mh, "foldArguments", target, combiner);
        }

        @Override
        public MethodHandle explicitCastArguments(final MethodHandle target, final MethodType type) {
            final MethodHandle mh = super.explicitCastArguments(target, type);
            return debug(mh, "explicitCastArguments", target, type);
        }

        @Override
        public MethodHandle arrayElementGetter(final Class<?> type) {
            final MethodHandle mh = super.arrayElementGetter(type);
            return debug(mh, "arrayElementGetter", type);
        }

        @Override
        public MethodHandle arrayElementSetter(final Class<?> type) {
            final MethodHandle mh = super.arrayElementSetter(type);
            return debug(mh, "arrayElementSetter", type);
        }

        @Override
        public MethodHandle throwException(final Class<?> returnType, final Class<? extends Throwable> exType) {
            final MethodHandle mh = super.throwException(returnType, exType);
            return debug(mh, "throwException", returnType, exType);
        }

        @Override
        public MethodHandle constant(final Class<?> type, final Object value) {
            final MethodHandle mh = super.constant(type, value);
            return debug(mh, "constant", type, value);
        }

        @Override
        public MethodHandle asCollector(final MethodHandle handle, final Class<?> arrayType, final int arrayLength) {
            final MethodHandle mh = super.asCollector(handle, arrayType, arrayLength);
            return debug(mh, "asCollector", handle, arrayType, arrayLength);
        }

        @Override
        public MethodHandle asSpreader(final MethodHandle handle, final Class<?> arrayType, final int arrayLength) {
            final MethodHandle mh = super.asSpreader(handle, arrayType, arrayLength);
            return debug(mh, "asSpreader", handle, arrayType, arrayLength);
        }

        @Override
        public MethodHandle getter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            final MethodHandle mh = super.getter(explicitLookup, clazz, name, type);
            return debug(mh, "getter", explicitLookup, clazz, name, type);
        }

        @Override
        public MethodHandle staticGetter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            final MethodHandle mh = super.staticGetter(explicitLookup, clazz, name, type);
            return debug(mh, "static getter", explicitLookup, clazz, name, type);
        }

        @Override
        public MethodHandle setter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            final MethodHandle mh = super.setter(explicitLookup, clazz, name, type);
            return debug(mh, "setter", explicitLookup, clazz, name, type);
        }

        @Override
        public MethodHandle staticSetter(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final Class<?> type) {
            final MethodHandle mh = super.staticSetter(explicitLookup, clazz, name, type);
            return debug(mh, "static setter", explicitLookup, clazz, name, type);
        }

        @Override
        public MethodHandle find(final Method method) {
            final MethodHandle mh = super.find(method);
            return debug(mh, "find", method);
        }

        @Override
        public MethodHandle findStatic(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final MethodType type) {
            final MethodHandle mh = super.findStatic(explicitLookup, clazz, name, type);
            return debug(mh, "findStatic", explicitLookup, clazz, name, type);
        }

        @Override
        public MethodHandle findVirtual(final MethodHandles.Lookup explicitLookup, final Class<?> clazz, final String name, final MethodType type) {
            final MethodHandle mh = super.findVirtual(explicitLookup, clazz, name, type);
            return debug(mh, "findVirtual", explicitLookup, clazz, name, type);
        }

        @Override
        public SwitchPoint createSwitchPoint() {
            final SwitchPoint sp = super.createSwitchPoint();
            LOG.log(TRACE_LEVEL, "createSwitchPoint ", sp);
            return sp;
        }

        @Override
        public MethodHandle guardWithTest(final SwitchPoint sp, final MethodHandle before, final MethodHandle after) {
            final MethodHandle mh = super.guardWithTest(sp, before, after);
            return debug(mh, "guardWithTest", sp, before, after);
        }

        @Override
        public MethodType type(final Class<?> returnType, final Class<?>... paramTypes) {
            final MethodType mt = super.type(returnType, paramTypes);
            LOG.log(TRACE_LEVEL, "methodType ", returnType, " ", Arrays.toString(paramTypes), " ", mt);
            return mt;
        }
    }

    /**
     * Class used for debugging Nashorn generated method handles
     */
    private static class TraceCreateMethodHandleFunctionality extends TraceMethodHandleFunctionality {
        @Override
        public MethodHandle debug(final MethodHandle master, final String str, final Object... args) {
            LOG.log(TRACE_LEVEL, str, " ", describe(args));
            stacktrace(LOG);
            return master;
        }
    }
}
