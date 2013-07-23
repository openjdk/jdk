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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.isRepresentableAsInt;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.ir.debug.JSONWriter;
import jdk.nashorn.internal.parser.Lexer;
import jdk.nashorn.internal.runtime.linker.Bootstrap;


/**
 * Utilities to be called by JavaScript runtime API and generated classes.
 */

public final class ScriptRuntime {
    private ScriptRuntime() {
    }

    /** Singleton representing the empty array object '[]' */
    public static final Object[] EMPTY_ARRAY = new Object[0];

    /** Unique instance of undefined. */
    public static final Undefined UNDEFINED = Undefined.getUndefined();

    /**
     * Unique instance of undefined used to mark empty array slots.
     * Can't escape the array.
     */
    public static final Undefined EMPTY = Undefined.getEmpty();

    /** Method handle to generic + operator, operating on objects */
    public static final Call ADD = staticCallNoLookup(ScriptRuntime.class, "ADD", Object.class, Object.class, Object.class);

    /** Method handle to generic === operator, operating on objects */
    public static final Call EQ_STRICT = staticCallNoLookup(ScriptRuntime.class, "EQ_STRICT", boolean.class, Object.class, Object.class);

    /** Method handle used to enter a {@code with} scope at runtime. */
    public static final Call OPEN_WITH = staticCallNoLookup(ScriptRuntime.class, "openWith", ScriptObject.class, ScriptObject.class, Object.class);

    /** Method handle used to exit a {@code with} scope at runtime. */
    public static final Call CLOSE_WITH = staticCallNoLookup(ScriptRuntime.class, "closeWith", ScriptObject.class, ScriptObject.class);

    /**
     * Method used to place a scope's variable into the Global scope, which has to be done for the
     * properties declared at outermost script level.
     */
    public static final Call MERGE_SCOPE = staticCallNoLookup(ScriptRuntime.class, "mergeScope", ScriptObject.class, ScriptObject.class);

    /**
     * Return an appropriate iterator for the elements in a for-in construct
     */
    public static final Call TO_PROPERTY_ITERATOR = staticCallNoLookup(ScriptRuntime.class, "toPropertyIterator", Iterator.class, Object.class);

    /**
     * Return an appropriate iterator for the elements in a for-each construct
     */
    public static final Call TO_VALUE_ITERATOR = staticCallNoLookup(ScriptRuntime.class, "toValueIterator", Iterator.class, Object.class);

    /**
      * Method handle for apply. Used from {@link ScriptFunction} for looking up calls to
      * call sites that are known to be megamorphic. Using an invoke dynamic here would
      * lead to the JVM deoptimizing itself to death
      */
    public static final Call APPLY = staticCall(ScriptRuntime.class, "apply", Object.class, ScriptFunction.class, Object.class, Object[].class);

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final Object tag, final int deflt) {
        if (tag instanceof Number) {
            final double d = ((Number)tag).doubleValue();
            if (isRepresentableAsInt(d)) {
                return (int)d;
            }
        }

        return deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final boolean tag, final int deflt) {
        return deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final long tag, final int deflt) {
        return isRepresentableAsInt(tag) ? (int)tag : deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final double tag, final int deflt) {
        return isRepresentableAsInt(tag) ? (int)tag : deflt;
    }

    /**
     * This is the builtin implementation of {@code Object.prototype.toString}
     * @param self reference
     * @return string representation as object
     */
    public static String builtinObjectToString(final Object self) {
        String className;
        // Spec tells us to convert primitives by ToObject..
        // But we don't need to -- all we need is the right class name
        // of the corresponding primitive wrapper type.

        final JSType type = JSType.of(self);

        switch (type) {
        case BOOLEAN:
            className = "Boolean";
            break;
        case NUMBER:
            className = "Number";
            break;
        case STRING:
            className = "String";
            break;
        // special case of null and undefined
        case NULL:
            className = "Null";
            break;
        case UNDEFINED:
            className = "Undefined";
            break;
        case OBJECT:
        case FUNCTION:
            if (self instanceof ScriptObject) {
                className = ((ScriptObject)self).getClassName();
            } else {
                className = self.getClass().getName();
            }
            break;
        default:
            // Nashorn extension: use Java class name
            className = self.getClass().getName();
            break;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("[object ");
        sb.append(className);
        sb.append(']');

        return sb.toString();
    }

    /**
     * This is called whenever runtime wants to throw an error and wants to provide
     * meaningful information about an object. We don't want to call toString which
     * ends up calling "toString" from script world which may itself throw error.
     * When we want to throw an error, we don't additional error from script land
     * -- which may sometimes lead to infinite recursion.
     *
     * @param obj Object to converted to String safely (without calling user script)
     * @return safe String representation of the given object
     */
    public static String safeToString(final Object obj) {
        return JSType.toStringImpl(obj, true);
    }

    /**
     * Used to determine property iterator used in for in.
     * @param obj Object to iterate on.
     * @return Iterator.
     */
    public static Iterator<String> toPropertyIterator(final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).propertyIterator();
        }

        if (obj != null && obj.getClass().isArray()) {
            final int length = Array.getLength(obj);

            return new Iterator<String>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < length;
                }

                @Override
                public String next() {
                    return "" + index++; //TODO numeric property iterator?
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).keySet().iterator();
        }

        return Collections.emptyIterator();
    }

    /**
     * Used to determine property value iterator used in for each in.
     * @param obj Object to iterate on.
     * @return Iterator.
     */
    public static Iterator<?> toValueIterator(final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).valueIterator();
        }

        if (obj != null && obj.getClass().isArray()) {
            final Object array  = obj;
            final int    length = Array.getLength(obj);

            return new Iterator<Object>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < length;
                }

                @Override
                public Object next() {
                    if (index >= length) {
                        throw new NoSuchElementException();
                    }
                    return Array.get(array, index++);
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).values().iterator();
        }

        if (obj instanceof Iterable) {
            return ((Iterable<?>)obj).iterator();
        }

        return Collections.emptyIterator();
    }

    /**
     * Merge a scope into its prototype's map.
     * Merge a scope into its prototype.
     *
     * @param scope Scope to merge.
     * @return prototype object after merge
     */
    public static ScriptObject mergeScope(final ScriptObject scope) {
        final ScriptObject global = scope.getProto();
        global.addBoundProperties(scope);
        return global;
    }

    /**
     * Check that the target function is associated with current Context. And also make sure that 'self', if
     * ScriptObject, is from current context.
     *
     * Call a function given self and args. If the number of the arguments is known in advance, you can likely achieve
     * better performance by {@link Bootstrap#createDynamicInvoker(String, Class, Class...) creating a dynamic invoker}
     * for operation {@code "dyn:call"}, then using its {@link MethodHandle#invokeExact(Object...)} method instead.
     *
     * @param target ScriptFunction object.
     * @param self   Receiver in call.
     * @param args   Call arguments.
     * @return Call result.
     */
    public static Object checkAndApply(final ScriptFunction target, final Object self, final Object... args) {
        final ScriptObject global = Context.getGlobalTrusted();
        assert (global instanceof GlobalObject): "No current global set";

        if (target.getContext() != global.getContext()) {
            throw new IllegalArgumentException("'target' function is not from current Context");
        }

        if (self instanceof ScriptObject && ((ScriptObject)self).getContext() != global.getContext()) {
            throw new IllegalArgumentException("'self' object is not from current Context");
        }

        // all in order - call real 'apply'
        return apply(target, self, args);
    }

    /**
     * Call a function given self and args. If the number of the arguments is known in advance, you can likely achieve
     * better performance by {@link Bootstrap#createDynamicInvoker(String, Class, Class...) creating a dynamic invoker}
     * for operation {@code "dyn:call"}, then using its {@link MethodHandle#invokeExact(Object...)} method instead.
     *
     * @param target ScriptFunction object.
     * @param self   Receiver in call.
     * @param args   Call arguments.
     * @return Call result.
     */
    public static Object apply(final ScriptFunction target, final Object self, final Object... args) {
        try {
            return target.invoke(self, args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Check that the target function is associated with current Context.
     * And also make sure that 'self', if ScriptObject, is from current context.
     *
     * Call a function as a constructor given args.
     *
     * @param target ScriptFunction object.
     * @param args   Call arguments.
     * @return Constructor call result.
     */
    public static Object checkAndConstruct(final ScriptFunction target, final Object... args) {
        final ScriptObject global = Context.getGlobalTrusted();
        assert (global instanceof GlobalObject): "No current global set";

        if (target.getContext() != global.getContext()) {
            throw new IllegalArgumentException("'target' function is not from current Context");
        }

        // all in order - call real 'construct'
        return construct(target, args);
    }

    /*
     * Call a script function as a constructor with given args.
     *
     * @param target ScriptFunction object.
     * @param args   Call arguments.
     * @return Constructor call result.
     */
    public static Object construct(final ScriptFunction target, final Object... args) {
        try {
            return target.construct(args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Generic implementation of ECMA 9.12 - SameValue algorithm
     *
     * @param x first value to compare
     * @param y second value to compare
     *
     * @return true if both objects have the same value
     */
    public static boolean sameValue(final Object x, final Object y) {
        final JSType xType = JSType.of(x);
        final JSType yType = JSType.of(y);

        if (xType != yType) {
            return false;
        }

        if (xType == JSType.UNDEFINED || xType == JSType.NULL) {
            return true;
        }

        if (xType == JSType.NUMBER) {
            final double xVal = ((Number)x).doubleValue();
            final double yVal = ((Number)y).doubleValue();

            if (Double.isNaN(xVal) && Double.isNaN(yVal)) {
                return true;
            }

            // checking for xVal == -0.0 and yVal == +0.0 or vice versa
            if (xVal == 0.0 && (Double.doubleToLongBits(xVal) != Double.doubleToLongBits(yVal))) {
                return false;
            }

            return xVal == yVal;
        }

        if (xType == JSType.STRING || yType == JSType.BOOLEAN) {
            return x.equals(y);
        }

        return (x == y);
    }

    /**
     * Returns AST as JSON compatible string. This is used to
     * implement "parse" function in resources/parse.js script.
     *
     * @param code code to be parsed
     * @param name name of the code source (used for location)
     * @param includeLoc tells whether to include location information for nodes or not
     * @return JSON string representation of AST of the supplied code
     */
    public static String parse(final String code, final String name, final boolean includeLoc) {
        return JSONWriter.parse(Context.getContextTrusted().getEnv(), code, name, includeLoc);
    }

    /**
     * Test whether a char is valid JavaScript whitespace
     * @param ch a char
     * @return true if valid JavaScript whitespace
     */
    public static boolean isJSWhitespace(final char ch) {
        return Lexer.isJSWhitespace(ch);
    }

    /**
     * Entering a {@code with} node requires new scope. This is the implementation
     *
     * @param scope      existing scope
     * @param expression expression in with
     *
     * @return {@link WithObject} that is the new scope
     */
    public static ScriptObject openWith(final ScriptObject scope, final Object expression) {
        final ScriptObject global = Context.getGlobalTrusted();
        if (expression == UNDEFINED) {
            throw typeError(global, "cant.apply.with.to.undefined");
        } else if (expression == null) {
            throw typeError(global, "cant.apply.with.to.null");
        }

        final ScriptObject withObject = new WithObject(scope, JSType.toScriptObject(global, expression));

        return withObject;
    }

    /**
     * Exiting a {@code with} node requires restoring scope. This is the implementation
     *
     * @param scope existing scope
     *
     * @return restored scope
     */
    public static ScriptObject closeWith(final ScriptObject scope) {
        if (scope instanceof WithObject) {
            return scope.getProto();
        }
        return scope;
    }

    /**
     * ECMA 11.6.1 - The addition operator (+) - generic implementation
     * Compiler specializes using {@link jdk.nashorn.internal.codegen.RuntimeCallSite}
     * if any type information is available for any of the operands
     *
     * @param x  first term
     * @param y  second term
     *
     * @return result of addition
     */
    public static Object ADD(final Object x, final Object y) {
        // This prefix code to handle Number special is for optimization.
        final boolean xIsNumber = x instanceof Number;
        final boolean yIsNumber = y instanceof Number;

        if (xIsNumber && yIsNumber) {
             return ((Number)x).doubleValue() + ((Number)y).doubleValue();
        }

        final boolean xIsUndefined = x == UNDEFINED;
        final boolean yIsUndefined = y == UNDEFINED;

        if ((xIsNumber && yIsUndefined) || (xIsUndefined && yIsNumber) || (xIsUndefined && yIsUndefined)) {
            return Double.NaN;
        }

        // code below is as per the spec.
        final Object xPrim = JSType.toPrimitive(x);
        final Object yPrim = JSType.toPrimitive(y);

        if (xPrim instanceof String || yPrim instanceof String
                || xPrim instanceof ConsString || yPrim instanceof ConsString) {
            return new ConsString(JSType.toCharSequence(xPrim), JSType.toCharSequence(yPrim));
        }

        return JSType.toNumber(xPrim) + JSType.toNumber(yPrim);
    }

    /**
     * Debugger hook.
     * TODO: currently unimplemented
     *
     * @return undefined
     */
    public static Object DEBUGGER() {
        return UNDEFINED;
    }

    /**
     * New hook
     *
     * @param clazz type for the clss
     * @param args  constructor arguments
     *
     * @return undefined
     */
    public static Object NEW(final Object clazz, final Object... args) {
        return UNDEFINED;
    }

    /**
     * ECMA 11.4.3 The typeof Operator - generic implementation
     *
     * @param object   the object from which to retrieve property to type check
     * @param property property in object to check
     *
     * @return type name
     */
    public static Object TYPEOF(final Object object, final Object property) {
        Object obj = object;

        if (property != null) {
            if (obj instanceof ScriptObject) {
                obj = ((ScriptObject)obj).get(property);
            } else if (object instanceof Undefined) {
                obj = ((Undefined)obj).get(property);
            } else if (object == null) {
                throw typeError("cant.get.property", safeToString(property), "null");
            } else if (JSType.isPrimitive(obj)) {
                obj = ((ScriptObject)JSType.toScriptObject(obj)).get(property);
            } else if (obj instanceof ScriptObjectMirror) {
                obj = ((ScriptObjectMirror)obj).getMember(property.toString());
            } else {
                obj = UNDEFINED;
            }
        }

        return JSType.of(obj).typeName();
    }

    /**
     * Throw ReferenceError when LHS of assignment or increment/decrement
     * operator is not an assignable node (say a literal)
     *
     * @param lhs Evaluated LHS
     * @param rhs Evaluated RHS
     * @param msg Additional LHS info for error message
     * @return undefined
     */
    public static Object REFERENCE_ERROR(final Object lhs, final Object rhs, final Object msg) {
        throw referenceError("cant.be.used.as.lhs", Objects.toString(msg));
    }

    /**
     * ECMA 11.4.1 - delete operation, generic implementation
     *
     * @param obj       object with property to delete
     * @param property  property to delete
     * @param strict    are we in strict mode
     *
     * @return true if property was successfully found and deleted
     */
    public static boolean DELETE(final Object obj, final Object property, final Object strict) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).delete(property, Boolean.TRUE.equals(strict));
        }

        if (obj instanceof Undefined) {
            return ((Undefined)obj).delete(property, false);
        }

        if (obj == null) {
            throw typeError("cant.delete.property", safeToString(property), "null");
        }

        if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).delete(property);
        }

        if (JSType.isPrimitive(obj)) {
            return ((ScriptObject) JSType.toScriptObject(obj)).delete(property, Boolean.TRUE.equals(strict));
        }

        // if object is not reference type, vacuously delete is successful.
        return true;
    }

    /**
     * ECMA 11.4.1 - delete operator, special case
     *
     * This is 'delete' that always fails. We have to check strict mode and throw error.
     * That is why this is a runtime function. Or else we could have inlined 'false'.
     *
     * @param property  property to delete
     * @param strict    are we in strict mode
     *
     * @return false always
     */
    public static boolean FAIL_DELETE(final Object property, final Object strict) {
        if (Boolean.TRUE.equals(strict)) {
            throw syntaxError("strict.cant.delete", safeToString(property));
        }
        return false;
    }

    /**
     * ECMA 11.9.1 - The equals operator (==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if type coerced versions of objects are equal
     */
    public static boolean EQ(final Object x, final Object y) {
        return equals(x, y);
    }

    /**
     * ECMA 11.9.2 - The does-not-equal operator (==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if type coerced versions of objects are not equal
     */
    public static boolean NE(final Object x, final Object y) {
        return !EQ(x, y);
    }

    /** ECMA 11.9.3 The Abstract Equality Comparison Algorithm */
    private static boolean equals(final Object x, final Object y) {
        final JSType xType = JSType.of(x);
        final JSType yType = JSType.of(y);

        if (xType == yType) {

            if (xType == JSType.UNDEFINED || xType == JSType.NULL) {
                return true;
            }

            if (xType == JSType.NUMBER) {
                final double xVal = ((Number)x).doubleValue();
                final double yVal = ((Number)y).doubleValue();
                if (Double.isNaN(xVal) || Double.isNaN(yVal)) {
                    return false;
                }

                return xVal == yVal;
            }

            if (xType == JSType.STRING) {
                // String may be represented by ConsString
                return x.toString().equals(y.toString());
            }

            if (xType == JSType.BOOLEAN) {
                // Boolean comparison
                return x.equals(y);
            }

            return x == y;
        }

        if ((xType == JSType.UNDEFINED && yType == JSType.NULL) ||
            (xType == JSType.NULL && yType == JSType.UNDEFINED)) {
            return true;
        }

        if (xType == JSType.NUMBER && yType == JSType.STRING) {
            return EQ(x, JSType.toNumber(y));
        }

        if (xType == JSType.STRING && yType == JSType.NUMBER) {
            return EQ(JSType.toNumber(x), y);
        }

        if (xType == JSType.BOOLEAN) {
            return EQ(JSType.toNumber(x), y);
        }

        if (yType == JSType.BOOLEAN) {
            return EQ(x, JSType.toNumber(y));
        }

        if ((xType == JSType.STRING || xType == JSType.NUMBER) &&
             (y instanceof ScriptObject))  {
            return EQ(x, JSType.toPrimitive(y));
        }

        if ((x instanceof ScriptObject) &&
            (yType == JSType.STRING || yType == JSType.NUMBER)) {
            return EQ(JSType.toPrimitive(x), y);
        }

        return false;
    }

    /**
     * ECMA 11.9.4 - The strict equal operator (===) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if objects are equal
     */
    public static boolean EQ_STRICT(final Object x, final Object y) {
        return strictEquals(x, y);
    }

    /**
     * ECMA 11.9.5 - The strict non equal operator (!==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if objects are not equal
     */
    public static boolean NE_STRICT(final Object x, final Object y) {
        return !EQ_STRICT(x, y);
    }

    /** ECMA 11.9.6 The Strict Equality Comparison Algorithm */
    private static boolean strictEquals(final Object x, final Object y) {
        final JSType xType = JSType.of(x);
        final JSType yType = JSType.of(y);

        if (xType != yType) {
            return false;
        }

        if (xType == JSType.UNDEFINED || xType == JSType.NULL) {
            return true;
        }

        if (xType == JSType.NUMBER) {
            final double xVal = ((Number)x).doubleValue();
            final double yVal = ((Number)y).doubleValue();

            if (Double.isNaN(xVal) || Double.isNaN(yVal)) {
                return false;
            }

            return xVal == yVal;
        }

        if (xType == JSType.STRING) {
            // String may be represented by ConsString
            return x.toString().equals(y.toString());
        }

        if (xType == JSType.BOOLEAN) {
            return x.equals(y);
        }

        // finally, the object identity comparison
        return x == y;
    }

    /**
     * ECMA 11.8.6 - The in operator - generic implementation
     *
     * @param property property to check for
     * @param obj object in which to check for property
     *
     * @return true if objects are equal
     */
    public static boolean IN(final Object property, final Object obj) {
        final JSType rvalType = JSType.of(obj);

        if (rvalType == JSType.OBJECT || rvalType == JSType.FUNCTION) {
            if (obj instanceof ScriptObject) {
                return ((ScriptObject)obj).has(property);
            }

            return false;
        }

        throw typeError("in.with.non.object", rvalType.toString().toLowerCase(Locale.ENGLISH));
    }

    /**
     * ECMA 11.8.6 - The strict instanceof operator - generic implementation
     *
     * @param obj first object to compare
     * @param clazz type to check against
     *
     * @return true if {@code obj} is an instanceof {@code clazz}
     */
    public static boolean INSTANCEOF(final Object obj, final Object clazz) {
        if (clazz instanceof ScriptFunction) {
            if (obj instanceof ScriptObject) {
                return ((ScriptObject)clazz).isInstance((ScriptObject)obj);
            }
            return false;
        }

        if (clazz instanceof StaticClass) {
            return ((StaticClass)clazz).getRepresentedClass().isInstance(obj);
        }

        if (clazz instanceof ScriptObjectMirror) {
            if (obj instanceof ScriptObjectMirror) {
                return ((ScriptObjectMirror)clazz).isInstance((ScriptObjectMirror)obj);
            }
            return false;
        }

        throw typeError("instanceof.on.non.object");
    }

    /**
     * ECMA 11.8.1 - The less than operator ({@literal <}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is less than y
     */
    public static boolean LT(final Object x, final Object y) {
        final Object value = lessThan(x, y, true);
        return (value == UNDEFINED) ? false : (Boolean)value;
    }

    /**
     * ECMA 11.8.2 - The greater than operator ({@literal >}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is greater than y
     */
    public static boolean GT(final Object x, final Object y) {
        final Object value = lessThan(y, x, false);
        return (value == UNDEFINED) ? false : (Boolean)value;
    }

    /**
     * ECMA 11.8.3 - The less than or equal operator ({@literal <=}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is less than or equal to y
     */
    public static boolean LE(final Object x, final Object y) {
        final Object value = lessThan(y, x, false);
        return (!(Boolean.TRUE.equals(value) || value == UNDEFINED));
    }

    /**
     * ECMA 11.8.4 - The greater than or equal operator ({@literal >=}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is greater than or equal to y
     */
    public static boolean GE(final Object x, final Object y) {
        final Object value = lessThan(x, y, true);
        return (!(Boolean.TRUE.equals(value) || value == UNDEFINED));
    }

    /** ECMA 11.8.5 The Abstract Relational Comparison Algorithm */
    private static Object lessThan(final Object x, final Object y, final boolean leftFirst) {
        Object px, py;

        //support e.g. x < y should throw exception correctly if x or y are not numeric
        if (leftFirst) {
            px = JSType.toPrimitive(x, Number.class);
            py = JSType.toPrimitive(y, Number.class);
        } else {
            py = JSType.toPrimitive(y, Number.class);
            px = JSType.toPrimitive(x, Number.class);
        }

        if (JSType.of(px) == JSType.STRING && JSType.of(py) == JSType.STRING) {
            // May be String or ConsString
            return (px.toString()).compareTo(py.toString()) < 0;
        }

        final double nx = JSType.toNumber(px);
        final double ny = JSType.toNumber(py);

        if (Double.isNaN(nx) || Double.isNaN(ny)) {
            return UNDEFINED;
        }

        if (nx == ny) {
            return false;
        }

        if (nx > 0 && ny > 0 && Double.isInfinite(nx) && Double.isInfinite(ny)) {
            return false;
        }

        if (nx < 0 && ny < 0 && Double.isInfinite(nx) && Double.isInfinite(ny)) {
            return false;
        }

        return nx < ny;
    }

}
