/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import sun.invoke.util.Wrapper;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * The symbolic, non-executable form of a method handle's invocation semantics.
 * It consists of a series of names.
 * The first N (N=arity) names are parameters,
 * while any remaining names are temporary values.
 * Each temporary specifies the application of a function to some arguments.
 * The functions are method handles, while the arguments are mixes of
 * constant values and local names.
 * The result of the lambda is defined as one of the names, often the last one.
 * <p>
 * Here is an approximate grammar:
 * <pre>
 * LambdaForm = "(" ArgName* ")=>{" TempName* Result "}"
 * ArgName = "a" N ":" T
 * TempName = "t" N ":" T "=" Function "(" Argument* ");"
 * Function = ConstantValue
 * Argument = NameRef | ConstantValue
 * Result = NameRef | "void"
 * NameRef = "a" N | "t" N
 * N = (any whole number)
 * T = "L" | "I" | "J" | "F" | "D" | "V"
 * </pre>
 * Names are numbered consecutively from left to right starting at zero.
 * (The letters are merely a taste of syntax sugar.)
 * Thus, the first temporary (if any) is always numbered N (where N=arity).
 * Every occurrence of a name reference in an argument list must refer to
 * a name previously defined within the same lambda.
 * A lambda has a void result if and only if its result index is -1.
 * If a temporary has the type "V", it cannot be the subject of a NameRef,
 * even though possesses a number.
 * Note that all reference types are erased to "L", which stands for {@code Object).
 * All subword types (boolean, byte, short, char) are erased to "I" which is {@code int}.
 * The other types stand for the usual primitive types.
 * <p>
 * Function invocation closely follows the static rules of the Java verifier.
 * Arguments and return values must exactly match when their "Name" types are
 * considered.
 * Conversions are allowed only if they do not change the erased type.
 * <ul>
 * <li>L = Object: casts are used freely to convert into and out of reference types
 * <li>I = int: subword types are forcibly narrowed when passed as arguments (see {@code explicitCastArguments})
 * <li>J = long: no implicit conversions
 * <li>F = float: no implicit conversions
 * <li>D = double: no implicit conversions
 * <li>V = void: a function result may be void if and only if its Name is of type "V"
 * </ul>
 * Although implicit conversions are not allowed, explicit ones can easily be
 * encoded by using temporary expressions which call type-transformed identity functions.
 * <p>
 * Examples:
 * <pre>
 * (a0:J)=>{ a0 }
 *     == identity(long)
 * (a0:I)=>{ t1:V = System.out#println(a0); void }
 *     == System.out#println(int)
 * (a0:L)=>{ t1:V = System.out#println(a0); a0 }
 *     == identity, with printing side-effect
 * (a0:L, a1:L)=>{ t2:L = BoundMethodHandle#argument(a0);
 *                 t3:L = BoundMethodHandle#target(a0);
 *                 t4:L = MethodHandle#invoke(t3, t2, a1); t4 }
 *     == general invoker for unary insertArgument combination
 * (a0:L, a1:L)=>{ t2:L = FilterMethodHandle#filter(a0);
 *                 t3:L = MethodHandle#invoke(t2, a1);
 *                 t4:L = FilterMethodHandle#target(a0);
 *                 t5:L = MethodHandle#invoke(t4, t3); t5 }
 *     == general invoker for unary filterArgument combination
 * (a0:L, a1:L)=>{ ...(same as previous example)...
 *                 t5:L = MethodHandle#invoke(t4, t3, a1); t5 }
 *     == general invoker for unary/unary foldArgument combination
 * (a0:L, a1:I)=>{ t2:I = identity(long).asType((int)->long)(a1); t2 }
 *     == invoker for identity method handle which performs i2l
 * (a0:L, a1:L)=>{ t2:L = BoundMethodHandle#argument(a0);
 *                 t3:L = Class#cast(t2,a1); t3 }
 *     == invoker for identity method handle which performs cast
 * </pre>
 * <p>
 * @author John Rose, JSR 292 EG
 */
class LambdaForm {
    final int arity;
    final int result;
    @Stable final Name[] names;
    final String debugName;
    MemberName vmentry;   // low-level behavior, or null if not yet prepared
    private boolean isCompiled;

    // Caches for common structural transforms:
    LambdaForm[] bindCache;

    public static final int VOID_RESULT = -1, LAST_RESULT = -2;

    LambdaForm(String debugName,
               int arity, Name[] names, int result) {
        assert(namesOK(arity, names));
        this.arity = arity;
        this.result = fixResult(result, names);
        this.names = names.clone();
        this.debugName = debugName;
        normalize();
    }

    LambdaForm(String debugName,
               int arity, Name[] names) {
        this(debugName,
             arity, names, LAST_RESULT);
    }

    LambdaForm(String debugName,
               Name[] formals, Name[] temps, Name result) {
        this(debugName,
             formals.length, buildNames(formals, temps, result), LAST_RESULT);
    }

    private static Name[] buildNames(Name[] formals, Name[] temps, Name result) {
        int arity = formals.length;
        int length = arity + temps.length + (result == null ? 0 : 1);
        Name[] names = Arrays.copyOf(formals, length);
        System.arraycopy(temps, 0, names, arity, temps.length);
        if (result != null)
            names[length - 1] = result;
        return names;
    }

    private LambdaForm(String sig) {
        // Make a blank lambda form, which returns a constant zero or null.
        // It is used as a template for managing the invocation of similar forms that are non-empty.
        // Called only from getPreparedForm.
        assert(isValidSignature(sig));
        this.arity = signatureArity(sig);
        this.result = (signatureReturn(sig) == 'V' ? -1 : arity);
        this.names = buildEmptyNames(arity, sig);
        this.debugName = "LF.zero";
        assert(nameRefsAreLegal());
        assert(isEmpty());
        assert(sig.equals(basicTypeSignature()));
    }

    private static Name[] buildEmptyNames(int arity, String basicTypeSignature) {
        assert(isValidSignature(basicTypeSignature));
        int resultPos = arity + 1;  // skip '_'
        if (arity < 0 || basicTypeSignature.length() != resultPos+1)
            throw new IllegalArgumentException("bad arity for "+basicTypeSignature);
        int numRes = (basicTypeSignature.charAt(resultPos) == 'V' ? 0 : 1);
        Name[] names = arguments(numRes, basicTypeSignature.substring(0, arity));
        for (int i = 0; i < numRes; i++) {
            names[arity + i] = constantZero(arity + i, basicTypeSignature.charAt(resultPos + i));
        }
        return names;
    }

    private static int fixResult(int result, Name[] names) {
        if (result >= 0) {
            if (names[result].type == 'V')
                return -1;
        } else if (result == LAST_RESULT) {
            return names.length - 1;
        }
        return result;
    }

    private static boolean namesOK(int arity, Name[] names) {
        for (int i = 0; i < names.length; i++) {
            Name n = names[i];
            assert(n != null) : "n is null";
            if (i < arity)
                assert( n.isParam()) : n + " is not param at " + i;
            else
                assert(!n.isParam()) : n + " is param at " + i;
        }
        return true;
    }

    /** Renumber and/or replace params so that they are interned and canonically numbered. */
    private void normalize() {
        Name[] oldNames = null;
        int changesStart = 0;
        for (int i = 0; i < names.length; i++) {
            Name n = names[i];
            if (!n.initIndex(i)) {
                if (oldNames == null) {
                    oldNames = names.clone();
                    changesStart = i;
                }
                names[i] = n.cloneWithIndex(i);
            }
        }
        if (oldNames != null) {
            int startFixing = arity;
            if (startFixing <= changesStart)
                startFixing = changesStart+1;
            for (int i = startFixing; i < names.length; i++) {
                Name fixed = names[i].replaceNames(oldNames, names, changesStart, i);
                names[i] = fixed.newIndex(i);
            }
        }
        assert(nameRefsAreLegal());
        int maxInterned = Math.min(arity, INTERNED_ARGUMENT_LIMIT);
        boolean needIntern = false;
        for (int i = 0; i < maxInterned; i++) {
            Name n = names[i], n2 = internArgument(n);
            if (n != n2) {
                names[i] = n2;
                needIntern = true;
            }
        }
        if (needIntern) {
            for (int i = arity; i < names.length; i++) {
                names[i].internArguments();
            }
            assert(nameRefsAreLegal());
        }
    }

    /**
     * Check that all embedded Name references are localizable to this lambda,
     * and are properly ordered after their corresponding definitions.
     * <p>
     * Note that a Name can be local to multiple lambdas, as long as
     * it possesses the same index in each use site.
     * This allows Name references to be freely reused to construct
     * fresh lambdas, without confusion.
     */
    private boolean nameRefsAreLegal() {
        assert(arity >= 0 && arity <= names.length);
        assert(result >= -1 && result < names.length);
        // Do all names possess an index consistent with their local definition order?
        for (int i = 0; i < arity; i++) {
            Name n = names[i];
            assert(n.index() == i) : Arrays.asList(n.index(), i);
            assert(n.isParam());
        }
        // Also, do all local name references
        for (int i = arity; i < names.length; i++) {
            Name n = names[i];
            assert(n.index() == i);
            for (Object arg : n.arguments) {
                if (arg instanceof Name) {
                    Name n2 = (Name) arg;
                    int i2 = n2.index;
                    assert(0 <= i2 && i2 < names.length) : n.debugString() + ": 0 <= i2 && i2 < names.length: 0 <= " + i2 + " < " + names.length;
                    assert(names[i2] == n2) : Arrays.asList("-1-", i, "-2-", n.debugString(), "-3-", i2, "-4-", n2.debugString(), "-5-", names[i2].debugString(), "-6-", this);
                    assert(i2 < i);  // ref must come after def!
                }
            }
        }
        return true;
    }

    /** Invoke this form on the given arguments. */
    // final Object invoke(Object... args) throws Throwable {
    //     // NYI: fit this into the fast path?
    //     return interpretWithArguments(args);
    // }

    /** Report the return type. */
    char returnType() {
        if (result < 0)  return 'V';
        Name n = names[result];
        return n.type;
    }

    /** Report the N-th argument type. */
    char parameterType(int n) {
        assert(n < arity);
        return names[n].type;
    }

    /** Report the arity. */
    int arity() {
        return arity;
    }

    /** Return the method type corresponding to my basic type signature. */
    MethodType methodType() {
        return signatureType(basicTypeSignature());
    }
    /** Return ABC_Z, where the ABC are parameter type characters, and Z is the return type character. */
    final String basicTypeSignature() {
        StringBuilder buf = new StringBuilder(arity() + 3);
        for (int i = 0, a = arity(); i < a; i++)
            buf.append(parameterType(i));
        return buf.append('_').append(returnType()).toString();
    }
    static int signatureArity(String sig) {
        assert(isValidSignature(sig));
        return sig.indexOf('_');
    }
    static char signatureReturn(String sig) {
        return sig.charAt(signatureArity(sig)+1);
    }
    static boolean isValidSignature(String sig) {
        int arity = sig.indexOf('_');
        if (arity < 0)  return false;  // must be of the form *_*
        int siglen = sig.length();
        if (siglen != arity + 2)  return false;  // *_X
        for (int i = 0; i < siglen; i++) {
            if (i == arity)  continue;  // skip '_'
            char c = sig.charAt(i);
            if (c == 'V')
                return (i == siglen - 1 && arity == siglen - 2);
            if (ALL_TYPES.indexOf(c) < 0)  return false; // must be [LIJFD]
        }
        return true;  // [LIJFD]*_[LIJFDV]
    }
    static Class<?> typeClass(char t) {
        switch (t) {
        case 'I': return int.class;
        case 'J': return long.class;
        case 'F': return float.class;
        case 'D': return double.class;
        case 'L': return Object.class;
        case 'V': return void.class;
        default: assert false;
        }
        return null;
    }
    static MethodType signatureType(String sig) {
        Class<?>[] ptypes = new Class<?>[signatureArity(sig)];
        for (int i = 0; i < ptypes.length; i++)
            ptypes[i] = typeClass(sig.charAt(i));
        Class<?> rtype = typeClass(signatureReturn(sig));
        return MethodType.methodType(rtype, ptypes);
    }

    /*
     * Code generation issues:
     *
     * Compiled LFs should be reusable in general.
     * The biggest issue is how to decide when to pull a name into
     * the bytecode, versus loading a reified form from the MH data.
     *
     * For example, an asType wrapper may require execution of a cast
     * after a call to a MH.  The target type of the cast can be placed
     * as a constant in the LF itself.  This will force the cast type
     * to be compiled into the bytecodes and native code for the MH.
     * Or, the target type of the cast can be erased in the LF, and
     * loaded from the MH data.  (Later on, if the MH as a whole is
     * inlined, the data will flow into the inlined instance of the LF,
     * as a constant, and the end result will be an optimal cast.)
     *
     * This erasure of cast types can be done with any use of
     * reference types.  It can also be done with whole method
     * handles.  Erasing a method handle might leave behind
     * LF code that executes correctly for any MH of a given
     * type, and load the required MH from the enclosing MH's data.
     * Or, the erasure might even erase the expected MT.
     *
     * Also, for direct MHs, the MemberName of the target
     * could be erased, and loaded from the containing direct MH.
     * As a simple case, a LF for all int-valued non-static
     * field getters would perform a cast on its input argument
     * (to non-constant base type derived from the MemberName)
     * and load an integer value from the input object
     * (at a non-constant offset also derived from the MemberName).
     * Such MN-erased LFs would be inlinable back to optimized
     * code, whenever a constant enclosing DMH is available
     * to supply a constant MN from its data.
     *
     * The main problem here is to keep LFs reasonably generic,
     * while ensuring that hot spots will inline good instances.
     * "Reasonably generic" means that we don't end up with
     * repeated versions of bytecode or machine code that do
     * not differ in their optimized form.  Repeated versions
     * of machine would have the undesirable overheads of
     * (a) redundant compilation work and (b) extra I$ pressure.
     * To control repeated versions, we need to be ready to
     * erase details from LFs and move them into MH data,
     * whevener those details are not relevant to significant
     * optimization.  "Significant" means optimization of
     * code that is actually hot.
     *
     * Achieving this may require dynamic splitting of MHs, by replacing
     * a generic LF with a more specialized one, on the same MH,
     * if (a) the MH is frequently executed and (b) the MH cannot
     * be inlined into a containing caller, such as an invokedynamic.
     *
     * Compiled LFs that are no longer used should be GC-able.
     * If they contain non-BCP references, they should be properly
     * interlinked with the class loader(s) that their embedded types
     * depend on.  This probably means that reusable compiled LFs
     * will be tabulated (indexed) on relevant class loaders,
     * or else that the tables that cache them will have weak links.
     */

    /**
     * Make this LF directly executable, as part of a MethodHandle.
     * Invariant:  Every MH which is invoked must prepare its LF
     * before invocation.
     * (In principle, the JVM could do this very lazily,
     * as a sort of pre-invocation linkage step.)
     */
    public void prepare() {
        if (COMPILE_THRESHOLD == 0) {
            compileToBytecode();
        }
        if (this.vmentry != null) {
            // already prepared (e.g., a primitive DMH invoker form)
            return;
        }
        LambdaForm prep = getPreparedForm(basicTypeSignature());
        this.vmentry = prep.vmentry;
        // TO DO: Maybe add invokeGeneric, invokeWithArguments
    }

    /** Generate optimizable bytecode for this form. */
    MemberName compileToBytecode() {
        MethodType invokerType = methodType();
        assert(vmentry == null || vmentry.getMethodType().basicType().equals(invokerType));
        if (vmentry != null && isCompiled) {
            return vmentry;  // already compiled somehow
        }
        try {
            vmentry = InvokerBytecodeGenerator.generateCustomizedCode(this, invokerType);
            if (TRACE_INTERPRETER)
                traceInterpreter("compileToBytecode", this);
            isCompiled = true;
            return vmentry;
        } catch (Error | Exception ex) {
            throw newInternalError(this.toString(), ex);
        }
    }

    private static final ConcurrentHashMap<String,LambdaForm> PREPARED_FORMS;
    static {
        int   capacity   = 512;    // expect many distinct signatures over time
        float loadFactor = 0.75f;  // normal default
        int   writers    = 1;
        PREPARED_FORMS = new ConcurrentHashMap<>(capacity, loadFactor, writers);
    }

    private static Map<String,LambdaForm> computeInitialPreparedForms() {
        // Find all predefined invokers and associate them with canonical empty lambda forms.
        HashMap<String,LambdaForm> forms = new HashMap<>();
        for (MemberName m : MemberName.getFactory().getMethods(LambdaForm.class, false, null, null, null)) {
            if (!m.isStatic() || !m.isPackage())  continue;
            MethodType mt = m.getMethodType();
            if (mt.parameterCount() > 0 &&
                mt.parameterType(0) == MethodHandle.class &&
                m.getName().startsWith("interpret_")) {
                String sig = basicTypeSignature(mt);
                assert(m.getName().equals("interpret" + sig.substring(sig.indexOf('_'))));
                LambdaForm form = new LambdaForm(sig);
                form.vmentry = m;
                mt.form().setCachedLambdaForm(MethodTypeForm.LF_COUNTER, form);
                // FIXME: get rid of PREPARED_FORMS; use MethodTypeForm cache only
                forms.put(sig, form);
            }
        }
        //System.out.println("computeInitialPreparedForms => "+forms);
        return forms;
    }

    // Set this false to disable use of the interpret_L methods defined in this file.
    private static final boolean USE_PREDEFINED_INTERPRET_METHODS = true;

    // The following are predefined exact invokers.  The system must build
    // a separate invoker for each distinct signature.
    static Object interpret_L(MethodHandle mh) throws Throwable {
        Object[] av = {mh};
        String sig = null;
        assert(argumentTypesMatch(sig = "L_L", av));
        Object res = mh.form.interpretWithArguments(av);
        assert(returnTypesMatch(sig, av, res));
        return res;
    }
    static Object interpret_L(MethodHandle mh, Object x1) throws Throwable {
        Object[] av = {mh, x1};
        String sig = null;
        assert(argumentTypesMatch(sig = "LL_L", av));
        Object res = mh.form.interpretWithArguments(av);
        assert(returnTypesMatch(sig, av, res));
        return res;
    }
    static Object interpret_L(MethodHandle mh, Object x1, Object x2) throws Throwable {
        Object[] av = {mh, x1, x2};
        String sig = null;
        assert(argumentTypesMatch(sig = "LLL_L", av));
        Object res = mh.form.interpretWithArguments(av);
        assert(returnTypesMatch(sig, av, res));
        return res;
    }
    private static LambdaForm getPreparedForm(String sig) {
        MethodType mtype = signatureType(sig);
        //LambdaForm prep = PREPARED_FORMS.get(sig);
        LambdaForm prep =  mtype.form().cachedLambdaForm(MethodTypeForm.LF_INTERPRET);
        if (prep != null)  return prep;
        assert(isValidSignature(sig));
        prep = new LambdaForm(sig);
        prep.vmentry = InvokerBytecodeGenerator.generateLambdaFormInterpreterEntryPoint(sig);
        //LambdaForm prep2 = PREPARED_FORMS.putIfAbsent(sig.intern(), prep);
        return mtype.form().setCachedLambdaForm(MethodTypeForm.LF_INTERPRET, prep);
    }

    // The next few routines are called only from assert expressions
    // They verify that the built-in invokers process the correct raw data types.
    private static boolean argumentTypesMatch(String sig, Object[] av) {
        int arity = signatureArity(sig);
        assert(av.length == arity) : "av.length == arity: av.length=" + av.length + ", arity=" + arity;
        assert(av[0] instanceof MethodHandle) : "av[0] not instace of MethodHandle: " + av[0];
        MethodHandle mh = (MethodHandle) av[0];
        MethodType mt = mh.type();
        assert(mt.parameterCount() == arity-1);
        for (int i = 0; i < av.length; i++) {
            Class<?> pt = (i == 0 ? MethodHandle.class : mt.parameterType(i-1));
            assert(valueMatches(sig.charAt(i), pt, av[i]));
        }
        return true;
    }
    private static boolean valueMatches(char tc, Class<?> type, Object x) {
        // The following line is needed because (...)void method handles can use non-void invokers
        if (type == void.class)  tc = 'V';   // can drop any kind of value
        assert tc == basicType(type) : tc + " == basicType(" + type + ")=" + basicType(type);
        switch (tc) {
        case 'I': assert checkInt(type, x)   : "checkInt(" + type + "," + x +")";   break;
        case 'J': assert x instanceof Long   : "instanceof Long: " + x;             break;
        case 'F': assert x instanceof Float  : "instanceof Float: " + x;            break;
        case 'D': assert x instanceof Double : "instanceof Double: " + x;           break;
        case 'L': assert checkRef(type, x)   : "checkRef(" + type + "," + x + ")";  break;
        case 'V': break;  // allow anything here; will be dropped
        default:  assert(false);
        }
        return true;
    }
    private static boolean returnTypesMatch(String sig, Object[] av, Object res) {
        MethodHandle mh = (MethodHandle) av[0];
        return valueMatches(signatureReturn(sig), mh.type().returnType(), res);
    }
    private static boolean checkInt(Class<?> type, Object x) {
        assert(x instanceof Integer);
        if (type == int.class)  return true;
        Wrapper w = Wrapper.forBasicType(type);
        assert(w.isSubwordOrInt());
        Object x1 = Wrapper.INT.wrap(w.wrap(x));
        return x.equals(x1);
    }
    private static boolean checkRef(Class<?> type, Object x) {
        assert(!type.isPrimitive());
        if (x == null)  return true;
        if (type.isInterface())  return true;
        return type.isInstance(x);
    }

    /** If the invocation count hits the threshold we spin bytecodes and call that subsequently. */
    private static final int COMPILE_THRESHOLD;
    static {
        if (MethodHandleStatics.COMPILE_THRESHOLD != null)
            COMPILE_THRESHOLD = MethodHandleStatics.COMPILE_THRESHOLD;
        else
            COMPILE_THRESHOLD = 30;  // default value
    }
    private int invocationCounter = 0;

    @Hidden
    @DontInline
    /** Interpretively invoke this form on the given arguments. */
    Object interpretWithArguments(Object... argumentValues) throws Throwable {
        if (TRACE_INTERPRETER)
            return interpretWithArgumentsTracing(argumentValues);
        checkInvocationCounter();
        assert(arityCheck(argumentValues));
        Object[] values = Arrays.copyOf(argumentValues, names.length);
        for (int i = argumentValues.length; i < values.length; i++) {
            values[i] = interpretName(names[i], values);
        }
        return (result < 0) ? null : values[result];
    }

    @Hidden
    @DontInline
    /** Evaluate a single Name within this form, applying its function to its arguments. */
    Object interpretName(Name name, Object[] values) throws Throwable {
        if (TRACE_INTERPRETER)
            traceInterpreter("| interpretName", name.debugString(), (Object[]) null);
        Object[] arguments = Arrays.copyOf(name.arguments, name.arguments.length, Object[].class);
        for (int i = 0; i < arguments.length; i++) {
            Object a = arguments[i];
            if (a instanceof Name) {
                int i2 = ((Name)a).index();
                assert(names[i2] == a);
                a = values[i2];
                arguments[i] = a;
            }
        }
        return name.function.invokeWithArguments(arguments);
    }

    private void checkInvocationCounter() {
        if (COMPILE_THRESHOLD != 0 &&
            invocationCounter < COMPILE_THRESHOLD) {
            invocationCounter++;  // benign race
            if (invocationCounter >= COMPILE_THRESHOLD) {
                // Replace vmentry with a bytecode version of this LF.
                compileToBytecode();
            }
        }
    }
    Object interpretWithArgumentsTracing(Object... argumentValues) throws Throwable {
        traceInterpreter("[ interpretWithArguments", this, argumentValues);
        if (invocationCounter < COMPILE_THRESHOLD) {
            int ctr = invocationCounter++;  // benign race
            traceInterpreter("| invocationCounter", ctr);
            if (invocationCounter >= COMPILE_THRESHOLD) {
                compileToBytecode();
            }
        }
        Object rval;
        try {
            assert(arityCheck(argumentValues));
            Object[] values = Arrays.copyOf(argumentValues, names.length);
            for (int i = argumentValues.length; i < values.length; i++) {
                values[i] = interpretName(names[i], values);
            }
            rval = (result < 0) ? null : values[result];
        } catch (Throwable ex) {
            traceInterpreter("] throw =>", ex);
            throw ex;
        }
        traceInterpreter("] return =>", rval);
        return rval;
    }

    //** This transform is applied (statically) to every name.function. */
    /*
    private static MethodHandle eraseSubwordTypes(MethodHandle mh) {
        MethodType mt = mh.type();
        if (mt.hasPrimitives()) {
            mt = mt.changeReturnType(eraseSubwordType(mt.returnType()));
            for (int i = 0; i < mt.parameterCount(); i++) {
                mt = mt.changeParameterType(i, eraseSubwordType(mt.parameterType(i)));
            }
            mh = MethodHandles.explicitCastArguments(mh, mt);
        }
        return mh;
    }
    private static Class<?> eraseSubwordType(Class<?> type) {
        if (!type.isPrimitive())  return type;
        if (type == int.class)  return type;
        Wrapper w = Wrapper.forPrimitiveType(type);
        if (w.isSubwordOrInt())  return int.class;
        return type;
    }
    */

    static void traceInterpreter(String event, Object obj, Object... args) {
        if (!TRACE_INTERPRETER)  return;
        System.out.println("LFI: "+event+" "+(obj != null ? obj : "")+(args != null && args.length != 0 ? Arrays.asList(args) : ""));
    }
    static void traceInterpreter(String event, Object obj) {
        traceInterpreter(event, obj, (Object[])null);
    }
    private boolean arityCheck(Object[] argumentValues) {
        assert(argumentValues.length == arity) : arity+"!="+Arrays.asList(argumentValues)+".length";
        // also check that the leading (receiver) argument is somehow bound to this LF:
        assert(argumentValues[0] instanceof MethodHandle) : "not MH: " + argumentValues[0];
        assert(((MethodHandle)argumentValues[0]).internalForm() == this);
        // note:  argument #0 could also be an interface wrapper, in the future
        return true;
    }

    private boolean isEmpty() {
        if (result < 0)
            return (names.length == arity);
        else if (result == arity && names.length == arity + 1)
            return names[arity].isConstantZero();
        else
            return false;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(debugName+"=Lambda(");
        for (int i = 0; i < names.length; i++) {
            if (i == arity)  buf.append(")=>{");
            Name n = names[i];
            if (i >= arity)  buf.append("\n    ");
            buf.append(n);
            if (i < arity) {
                if (i+1 < arity)  buf.append(",");
                continue;
            }
            buf.append("=").append(n.exprString());
            buf.append(";");
        }
        buf.append(result < 0 ? "void" : names[result]).append("}");
        if (TRACE_INTERPRETER) {
            // Extra verbosity:
            buf.append(":").append(basicTypeSignature());
            buf.append("/").append(vmentry);
        }
        return buf.toString();
    }

    /**
     * Apply immediate binding for a Name in this form indicated by its position relative to the form.
     * The first parameter to a LambdaForm, a0:L, always represents the form's method handle, so 0 is not
     * accepted as valid.
     */
    LambdaForm bindImmediate(int pos, char basicType, Object value) {
        // must be an argument, and the types must match
        assert pos > 0 && pos < arity && names[pos].type == basicType && Name.typesMatch(basicType, value);

        int arity2 = arity - 1;
        Name[] names2 = new Name[names.length - 1];
        for (int r = 0, w = 0; r < names.length; ++r, ++w) { // (r)ead from names, (w)rite to names2
            Name n = names[r];
            if (n.isParam()) {
                if (n.index == pos) {
                    // do not copy over the argument that is to be replaced with a literal,
                    // but adjust the write index
                    --w;
                } else {
                    names2[w] = new Name(w, n.type);
                }
            } else {
                Object[] arguments2 = new Object[n.arguments.length];
                for (int i = 0; i < n.arguments.length; ++i) {
                    Object arg = n.arguments[i];
                    if (arg instanceof Name) {
                        int ni = ((Name) arg).index;
                        if (ni == pos) {
                            arguments2[i] = value;
                        } else if (ni < pos) {
                            // replacement position not yet passed
                            arguments2[i] = names2[ni];
                        } else {
                            // replacement position passed
                            arguments2[i] = names2[ni - 1];
                        }
                    } else {
                        arguments2[i] = arg;
                    }
                }
                names2[w] = new Name(n.function, arguments2);
                names2[w].initIndex(w);
            }
        }

        int result2 = result == -1 ? -1 : result - 1;
        return new LambdaForm(debugName, arity2, names2, result2);
    }

    LambdaForm bind(int namePos, BoundMethodHandle.SpeciesData oldData) {
        Name name = names[namePos];
        BoundMethodHandle.SpeciesData newData = oldData.extendWithType(name.type);
        return bind(name, newData.getterName(names[0], oldData.fieldCount()), oldData, newData);
    }
    LambdaForm bind(Name name, Name binding,
                    BoundMethodHandle.SpeciesData oldData,
                    BoundMethodHandle.SpeciesData newData) {
        int pos = name.index;
        assert(name.isParam());
        assert(!binding.isParam());
        assert(name.type == binding.type);
        assert(0 <= pos && pos < arity && names[pos] == name);
        assert(binding.function.memberDeclaringClassOrNull() == newData.clazz);
        assert(oldData.getters.length == newData.getters.length-1);
        if (bindCache != null) {
            LambdaForm form = bindCache[pos];
            if (form != null) {
                assert(form.contains(binding)) : "form << " + form + " >> does not contain binding << " + binding + " >>";
                return form;
            }
        } else {
            bindCache = new LambdaForm[arity];
        }
        assert(nameRefsAreLegal());
        int arity2 = arity-1;
        Name[] names2 = names.clone();
        names2[pos] = binding;  // we might move this in a moment

        // The newly created LF will run with a different BMH.
        // Switch over any pre-existing BMH field references to the new BMH class.
        int firstOldRef = -1;
        for (int i = 0; i < names2.length; i++) {
            Name n = names[i];
            if (n.function != null &&
                n.function.memberDeclaringClassOrNull() == oldData.clazz) {
                MethodHandle oldGetter = n.function.resolvedHandle;
                MethodHandle newGetter = null;
                for (int j = 0; j < oldData.getters.length; j++) {
                    if (oldGetter == oldData.getters[j])
                        newGetter =  newData.getters[j];
                }
                if (newGetter != null) {
                    if (firstOldRef < 0)  firstOldRef = i;
                    Name n2 = new Name(newGetter, n.arguments);
                    names2[i] = n2;
                }
            }
        }

        // Walk over the new list of names once, in forward order.
        // Replace references to 'name' with 'binding'.
        // Replace data structure references to the old BMH species with the new.
        // This might cause a ripple effect, but it will settle in one pass.
        assert(firstOldRef < 0 || firstOldRef > pos);
        for (int i = pos+1; i < names2.length; i++) {
            if (i <= arity2)  continue;
            names2[i] = names2[i].replaceNames(names, names2, pos, i);
        }

        //  (a0, a1, name=a2, a3, a4)  =>  (a0, a1, a3, a4, binding)
        int insPos = pos;
        for (; insPos+1 < names2.length; insPos++) {
            Name n = names2[insPos+1];
            if (n.isSiblingBindingBefore(binding)) {
                names2[insPos] = n;
            } else {
                break;
            }
        }
        names2[insPos] = binding;

        // Since we moved some stuff, maybe update the result reference:
        int result2 = result;
        if (result2 == pos)
            result2 = insPos;
        else if (result2 > pos && result2 <= insPos)
            result2 -= 1;

        return bindCache[pos] = new LambdaForm(debugName, arity2, names2, result2);
    }

    boolean contains(Name name) {
        int pos = name.index();
        if (pos >= 0) {
            return pos < names.length && name.equals(names[pos]);
        }
        for (int i = arity; i < names.length; i++) {
            if (name.equals(names[i]))
                return true;
        }
        return false;
    }

    LambdaForm addArguments(int pos, char... types) {
        assert(pos <= arity);
        int length = names.length;
        int inTypes = types.length;
        Name[] names2 = Arrays.copyOf(names, length + inTypes);
        int arity2 = arity + inTypes;
        int result2 = result;
        if (result2 >= arity)
            result2 += inTypes;
        // names array has MH in slot 0; skip it.
        int argpos = pos + 1;
        // Note:  The LF constructor will rename names2[argpos...].
        // Make space for new arguments (shift temporaries).
        System.arraycopy(names, argpos, names2, argpos + inTypes, length - argpos);
        for (int i = 0; i < inTypes; i++) {
            names2[argpos + i] = new Name(types[i]);
        }
        return new LambdaForm(debugName, arity2, names2, result2);
    }

    LambdaForm addArguments(int pos, List<Class<?>> types) {
        char[] basicTypes = new char[types.size()];
        for (int i = 0; i < basicTypes.length; i++)
            basicTypes[i] = basicType(types.get(i));
        return addArguments(pos, basicTypes);
    }

    LambdaForm permuteArguments(int skip, int[] reorder, char[] types) {
        // Note:  When inArg = reorder[outArg], outArg is fed by a copy of inArg.
        // The types are the types of the new (incoming) arguments.
        int length = names.length;
        int inTypes = types.length;
        int outArgs = reorder.length;
        assert(skip+outArgs == arity);
        assert(permutedTypesMatch(reorder, types, names, skip));
        int pos = 0;
        // skip trivial first part of reordering:
        while (pos < outArgs && reorder[pos] == pos)  pos += 1;
        Name[] names2 = new Name[length - outArgs + inTypes];
        System.arraycopy(names, 0, names2, 0, skip+pos);
        // copy the body:
        int bodyLength = length - arity;
        System.arraycopy(names, skip+outArgs, names2, skip+inTypes, bodyLength);
        int arity2 = names2.length - bodyLength;
        int result2 = result;
        if (result2 >= 0) {
            if (result2 < skip+outArgs) {
                // return the corresponding inArg
                result2 = reorder[result2-skip];
            } else {
                result2 = result2 - outArgs + inTypes;
            }
        }
        // rework names in the body:
        for (int j = pos; j < outArgs; j++) {
            Name n = names[skip+j];
            int i = reorder[j];
            // replace names[skip+j] by names2[skip+i]
            Name n2 = names2[skip+i];
            if (n2 == null)
                names2[skip+i] = n2 = new Name(types[i]);
            else
                assert(n2.type == types[i]);
            for (int k = arity2; k < names2.length; k++) {
                names2[k] = names2[k].replaceName(n, n2);
            }
        }
        // some names are unused, but must be filled in
        for (int i = skip+pos; i < arity2; i++) {
            if (names2[i] == null)
                names2[i] = argument(i, types[i - skip]);
        }
        for (int j = arity; j < names.length; j++) {
            int i = j - arity + arity2;
            // replace names2[i] by names[j]
            Name n = names[j];
            Name n2 = names2[i];
            if (n != n2) {
                for (int k = i+1; k < names2.length; k++) {
                    names2[k] = names2[k].replaceName(n, n2);
                }
            }
        }
        return new LambdaForm(debugName, arity2, names2, result2);
    }

    static boolean permutedTypesMatch(int[] reorder, char[] types, Name[] names, int skip) {
        int inTypes = types.length;
        int outArgs = reorder.length;
        for (int i = 0; i < outArgs; i++) {
            assert(names[skip+i].isParam());
            assert(names[skip+i].type == types[reorder[i]]);
        }
        return true;
    }

    static class NamedFunction {
        final MemberName member;
        @Stable MethodHandle resolvedHandle;
        @Stable MethodHandle invoker;

        NamedFunction(MethodHandle resolvedHandle) {
            this(resolvedHandle.internalMemberName(), resolvedHandle);
        }
        NamedFunction(MemberName member, MethodHandle resolvedHandle) {
            this.member = member;
            //resolvedHandle = eraseSubwordTypes(resolvedHandle);
            this.resolvedHandle = resolvedHandle;
        }

        // The next 3 constructors are used to break circular dependencies on MH.invokeStatic, etc.
        // Any LambdaForm containing such a member is not interpretable.
        // This is OK, since all such LFs are prepared with special primitive vmentry points.
        // And even without the resolvedHandle, the name can still be compiled and optimized.
        NamedFunction(Method method) {
            this(new MemberName(method));
        }
        NamedFunction(Field field) {
            this(new MemberName(field));
        }
        NamedFunction(MemberName member) {
            this.member = member;
            this.resolvedHandle = null;
        }

        MethodHandle resolvedHandle() {
            if (resolvedHandle == null)  resolve();
            return resolvedHandle;
        }

        void resolve() {
            resolvedHandle = DirectMethodHandle.make(member);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null) return false;
            if (!(other instanceof NamedFunction)) return false;
            NamedFunction that = (NamedFunction) other;
            return this.member != null && this.member.equals(that.member);
        }

        @Override
        public int hashCode() {
            if (member != null)
                return member.hashCode();
            return super.hashCode();
        }

        // Put the predefined NamedFunction invokers into the table.
        static void initializeInvokers() {
            for (MemberName m : MemberName.getFactory().getMethods(NamedFunction.class, false, null, null, null)) {
                if (!m.isStatic() || !m.isPackage())  continue;
                MethodType type = m.getMethodType();
                if (type.equals(INVOKER_METHOD_TYPE) &&
                    m.getName().startsWith("invoke_")) {
                    String sig = m.getName().substring("invoke_".length());
                    int arity = LambdaForm.signatureArity(sig);
                    MethodType srcType = MethodType.genericMethodType(arity);
                    if (LambdaForm.signatureReturn(sig) == 'V')
                        srcType = srcType.changeReturnType(void.class);
                    MethodTypeForm typeForm = srcType.form();
                    typeForm.namedFunctionInvoker = DirectMethodHandle.make(m);
                }
            }
        }

        // The following are predefined NamedFunction invokers.  The system must build
        // a separate invoker for each distinct signature.
        /** void return type invokers. */
        @Hidden
        static Object invoke__V(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 0);
            mh.invokeBasic();
            return null;
        }
        @Hidden
        static Object invoke_L_V(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 1);
            mh.invokeBasic(a[0]);
            return null;
        }
        @Hidden
        static Object invoke_LL_V(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 2);
            mh.invokeBasic(a[0], a[1]);
            return null;
        }
        @Hidden
        static Object invoke_LLL_V(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 3);
            mh.invokeBasic(a[0], a[1], a[2]);
            return null;
        }
        @Hidden
        static Object invoke_LLLL_V(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 4);
            mh.invokeBasic(a[0], a[1], a[2], a[3]);
            return null;
        }
        @Hidden
        static Object invoke_LLLLL_V(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 5);
            mh.invokeBasic(a[0], a[1], a[2], a[3], a[4]);
            return null;
        }
        /** Object return type invokers. */
        @Hidden
        static Object invoke__L(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 0);
            return mh.invokeBasic();
        }
        @Hidden
        static Object invoke_L_L(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 1);
            return mh.invokeBasic(a[0]);
        }
        @Hidden
        static Object invoke_LL_L(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 2);
            return mh.invokeBasic(a[0], a[1]);
        }
        @Hidden
        static Object invoke_LLL_L(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 3);
            return mh.invokeBasic(a[0], a[1], a[2]);
        }
        @Hidden
        static Object invoke_LLLL_L(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 4);
            return mh.invokeBasic(a[0], a[1], a[2], a[3]);
        }
        @Hidden
        static Object invoke_LLLLL_L(MethodHandle mh, Object[] a) throws Throwable {
            assert(a.length == 5);
            return mh.invokeBasic(a[0], a[1], a[2], a[3], a[4]);
        }

        static final MethodType INVOKER_METHOD_TYPE =
            MethodType.methodType(Object.class, MethodHandle.class, Object[].class);

        private static MethodHandle computeInvoker(MethodTypeForm typeForm) {
            MethodHandle mh = typeForm.namedFunctionInvoker;
            if (mh != null)  return mh;
            MemberName invoker = InvokerBytecodeGenerator.generateNamedFunctionInvoker(typeForm);  // this could take a while
            mh = DirectMethodHandle.make(invoker);
            MethodHandle mh2 = typeForm.namedFunctionInvoker;
            if (mh2 != null)  return mh2;  // benign race
            if (!mh.type().equals(INVOKER_METHOD_TYPE))
                throw new InternalError(mh.debugString());
            return typeForm.namedFunctionInvoker = mh;
        }

        @Hidden
        Object invokeWithArguments(Object... arguments) throws Throwable {
            // If we have a cached invoker, call it right away.
            // NOTE: The invoker always returns a reference value.
            if (TRACE_INTERPRETER)  return invokeWithArgumentsTracing(arguments);
            assert(checkArgumentTypes(arguments, methodType()));
            return invoker().invokeBasic(resolvedHandle(), arguments);
        }

        @Hidden
        Object invokeWithArgumentsTracing(Object[] arguments) throws Throwable {
            Object rval;
            try {
                traceInterpreter("[ call", this, arguments);
                if (invoker == null) {
                    traceInterpreter("| getInvoker", this);
                    invoker();
                }
                if (resolvedHandle == null) {
                    traceInterpreter("| resolve", this);
                    resolvedHandle();
                }
                assert(checkArgumentTypes(arguments, methodType()));
                rval = invoker().invokeBasic(resolvedHandle(), arguments);
            } catch (Throwable ex) {
                traceInterpreter("] throw =>", ex);
                throw ex;
            }
            traceInterpreter("] return =>", rval);
            return rval;
        }

        private MethodHandle invoker() {
            if (invoker != null)  return invoker;
            // Get an invoker and cache it.
            return invoker = computeInvoker(methodType().form());
        }

        private static boolean checkArgumentTypes(Object[] arguments, MethodType methodType) {
            if (true)  return true;  // FIXME
            MethodType dstType = methodType.form().erasedType();
            MethodType srcType = dstType.basicType().wrap();
            Class<?>[] ptypes = new Class<?>[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Object arg = arguments[i];
                Class<?> ptype = arg == null ? Object.class : arg.getClass();
                // If the dest. type is a primitive we keep the
                // argument type.
                ptypes[i] = dstType.parameterType(i).isPrimitive() ? ptype : Object.class;
            }
            MethodType argType = MethodType.methodType(srcType.returnType(), ptypes).wrap();
            assert(argType.isConvertibleTo(srcType)) : "wrong argument types: cannot convert " + argType + " to " + srcType;
            return true;
        }

        String basicTypeSignature() {
            //return LambdaForm.basicTypeSignature(resolvedHandle.type());
            return LambdaForm.basicTypeSignature(methodType());
        }

        MethodType methodType() {
            if (resolvedHandle != null)
                return resolvedHandle.type();
            else
                // only for certain internal LFs during bootstrapping
                return member.getInvocationType();
        }

        MemberName member() {
            assert(assertMemberIsConsistent());
            return member;
        }

        // Called only from assert.
        private boolean assertMemberIsConsistent() {
            if (resolvedHandle instanceof DirectMethodHandle) {
                MemberName m = resolvedHandle.internalMemberName();
                assert(m.equals(member));
            }
            return true;
        }

        Class<?> memberDeclaringClassOrNull() {
            return (member == null) ? null : member.getDeclaringClass();
        }

        char returnType() {
            return basicType(methodType().returnType());
        }

        char parameterType(int n) {
            return basicType(methodType().parameterType(n));
        }

        int arity() {
            //int siglen = member.getMethodType().parameterCount();
            //if (!member.isStatic())  siglen += 1;
            //return siglen;
            return methodType().parameterCount();
        }

        public String toString() {
            if (member == null)  return resolvedHandle.toString();
            return member.getDeclaringClass().getSimpleName()+"."+member.getName();
        }
    }

    void resolve() {
        for (Name n : names) n.resolve();
    }

    public static char basicType(Class<?> type) {
        char c = Wrapper.basicTypeChar(type);
        if ("ZBSC".indexOf(c) >= 0)  c = 'I';
        assert("LIJFDV".indexOf(c) >= 0);
        return c;
    }
    public static char[] basicTypes(List<Class<?>> types) {
        char[] btypes = new char[types.size()];
        for (int i = 0; i < btypes.length; i++) {
            btypes[i] = basicType(types.get(i));
        }
        return btypes;
    }
    public static String basicTypeSignature(MethodType type) {
        char[] sig = new char[type.parameterCount() + 2];
        int sigp = 0;
        for (Class<?> pt : type.parameterList()) {
            sig[sigp++] = basicType(pt);
        }
        sig[sigp++] = '_';
        sig[sigp++] = basicType(type.returnType());
        assert(sigp == sig.length);
        return String.valueOf(sig);
    }

    static final class Name {
        final char type;
        private short index;
        final NamedFunction function;
        @Stable final Object[] arguments;

        private Name(int index, char type, NamedFunction function, Object[] arguments) {
            this.index = (short)index;
            this.type = type;
            this.function = function;
            this.arguments = arguments;
            assert(this.index == index);
        }
        Name(MethodHandle function, Object... arguments) {
            this(new NamedFunction(function), arguments);
        }
        Name(MemberName function, Object... arguments) {
            this(new NamedFunction(function), arguments);
        }
        Name(NamedFunction function, Object... arguments) {
            this(-1, function.returnType(), function, arguments = arguments.clone());
            assert(arguments.length == function.arity()) : "arity mismatch: arguments.length=" + arguments.length + " == function.arity()=" + function.arity() + " in " + debugString();
            for (int i = 0; i < arguments.length; i++)
                assert(typesMatch(function.parameterType(i), arguments[i])) : "types don't match: function.parameterType(" + i + ")=" + function.parameterType(i) + ", arguments[" + i + "]=" + arguments[i] + " in " + debugString();
        }
        Name(int index, char type) {
            this(index, type, null, null);
        }
        Name(char type) {
            this(-1, type);
        }

        char type() { return type; }
        int index() { return index; }
        boolean initIndex(int i) {
            if (index != i) {
                if (index != -1)  return false;
                index = (short)i;
            }
            return true;
        }


        void resolve() {
            if (function != null)
                function.resolve();
        }

        Name newIndex(int i) {
            if (initIndex(i))  return this;
            return cloneWithIndex(i);
        }
        Name cloneWithIndex(int i) {
            Object[] newArguments = (arguments == null) ? null : arguments.clone();
            return new Name(i, type, function, newArguments);
        }
        Name replaceName(Name oldName, Name newName) {  // FIXME: use replaceNames uniformly
            if (oldName == newName)  return this;
            @SuppressWarnings("LocalVariableHidesMemberVariable")
            Object[] arguments = this.arguments;
            if (arguments == null)  return this;
            boolean replaced = false;
            for (int j = 0; j < arguments.length; j++) {
                if (arguments[j] == oldName) {
                    if (!replaced) {
                        replaced = true;
                        arguments = arguments.clone();
                    }
                    arguments[j] = newName;
                }
            }
            if (!replaced)  return this;
            return new Name(function, arguments);
        }
        Name replaceNames(Name[] oldNames, Name[] newNames, int start, int end) {
            @SuppressWarnings("LocalVariableHidesMemberVariable")
            Object[] arguments = this.arguments;
            boolean replaced = false;
        eachArg:
            for (int j = 0; j < arguments.length; j++) {
                if (arguments[j] instanceof Name) {
                    Name n = (Name) arguments[j];
                    int check = n.index;
                    // harmless check to see if the thing is already in newNames:
                    if (check >= 0 && check < newNames.length && n == newNames[check])
                        continue eachArg;
                    // n might not have the correct index: n != oldNames[n.index].
                    for (int i = start; i < end; i++) {
                        if (n == oldNames[i]) {
                            if (n == newNames[i])
                                continue eachArg;
                            if (!replaced) {
                                replaced = true;
                                arguments = arguments.clone();
                            }
                            arguments[j] = newNames[i];
                            continue eachArg;
                        }
                    }
                }
            }
            if (!replaced)  return this;
            return new Name(function, arguments);
        }
        void internArguments() {
            @SuppressWarnings("LocalVariableHidesMemberVariable")
            Object[] arguments = this.arguments;
            for (int j = 0; j < arguments.length; j++) {
                if (arguments[j] instanceof Name) {
                    Name n = (Name) arguments[j];
                    if (n.isParam() && n.index < INTERNED_ARGUMENT_LIMIT)
                        arguments[j] = internArgument(n);
                }
            }
        }
        boolean isParam() {
            return function == null;
        }
        boolean isConstantZero() {
            return !isParam() && arguments.length == 0 && function.equals(constantZero(0, type).function);
        }

        public String toString() {
            return (isParam()?"a":"t")+(index >= 0 ? index : System.identityHashCode(this))+":"+type;
        }
        public String debugString() {
            String s = toString();
            return (function == null) ? s : s + "=" + exprString();
        }
        public String exprString() {
            if (function == null)  return "null";
            StringBuilder buf = new StringBuilder(function.toString());
            buf.append("(");
            String cma = "";
            for (Object a : arguments) {
                buf.append(cma); cma = ",";
                if (a instanceof Name || a instanceof Integer)
                    buf.append(a);
                else
                    buf.append("(").append(a).append(")");
            }
            buf.append(")");
            return buf.toString();
        }

        private static boolean typesMatch(char parameterType, Object object) {
            if (object instanceof Name) {
                return ((Name)object).type == parameterType;
            }
            switch (parameterType) {
                case 'I':  return object instanceof Integer;
                case 'J':  return object instanceof Long;
                case 'F':  return object instanceof Float;
                case 'D':  return object instanceof Double;
            }
            assert(parameterType == 'L');
            return true;
        }

        /**
         * Does this Name precede the given binding node in some canonical order?
         * This predicate is used to order data bindings (via insertion sort)
         * with some stability.
         * @param binding
         * @return
         */
        boolean isSiblingBindingBefore(Name binding) {
            assert(!binding.isParam());
            if (isParam())  return true;
            if (function.equals(binding.function) &&
                arguments.length == binding.arguments.length) {
                boolean sawInt = false;
                for (int i = 0; i < arguments.length; i++) {
                    Object a1 = arguments[i];
                    Object a2 = binding.arguments[i];
                    if (!a1.equals(a2)) {
                        if (a1 instanceof Integer && a2 instanceof Integer) {
                            if (sawInt)  continue;
                            sawInt = true;
                            if ((int)a1 < (int)a2)  continue;  // still might be true
                        }
                        return false;
                    }
                }
                return sawInt;
            }
            return false;
        }

        public boolean equals(Name that) {
            if (this == that)  return true;
            if (isParam())
                // each parameter is a unique atom
                return false;  // this != that
            return
                //this.index == that.index &&
                this.type == that.type &&
                this.function.equals(that.function) &&
                Arrays.equals(this.arguments, that.arguments);
        }
        @Override
        public boolean equals(Object x) {
            return x instanceof Name && equals((Name)x);
        }
        @Override
        public int hashCode() {
            if (isParam())
                return index | (type << 8);
            return function.hashCode() ^ Arrays.hashCode(arguments);
        }
    }

    static Name argument(int which, char type) {
        int tn = ALL_TYPES.indexOf(type);
        if (tn < 0 || which >= INTERNED_ARGUMENT_LIMIT)
            return new Name(which, type);
        return INTERNED_ARGUMENTS[tn][which];
    }
    static Name internArgument(Name n) {
        assert(n.isParam()) : "not param: " + n;
        assert(n.index < INTERNED_ARGUMENT_LIMIT);
        return argument(n.index, n.type);
    }
    static Name[] arguments(int extra, String types) {
        int length = types.length();
        Name[] names = new Name[length + extra];
        for (int i = 0; i < length; i++)
            names[i] = argument(i, types.charAt(i));
        return names;
    }
    static Name[] arguments(int extra, char... types) {
        int length = types.length;
        Name[] names = new Name[length + extra];
        for (int i = 0; i < length; i++)
            names[i] = argument(i, types[i]);
        return names;
    }
    static Name[] arguments(int extra, List<Class<?>> types) {
        int length = types.size();
        Name[] names = new Name[length + extra];
        for (int i = 0; i < length; i++)
            names[i] = argument(i, basicType(types.get(i)));
        return names;
    }
    static Name[] arguments(int extra, Class<?>... types) {
        int length = types.length;
        Name[] names = new Name[length + extra];
        for (int i = 0; i < length; i++)
            names[i] = argument(i, basicType(types[i]));
        return names;
    }
    static Name[] arguments(int extra, MethodType types) {
        int length = types.parameterCount();
        Name[] names = new Name[length + extra];
        for (int i = 0; i < length; i++)
            names[i] = argument(i, basicType(types.parameterType(i)));
        return names;
    }
    static final String ALL_TYPES = "LIJFD";  // omit V, not an argument type
    static final int INTERNED_ARGUMENT_LIMIT = 10;
    private static final Name[][] INTERNED_ARGUMENTS
            = new Name[ALL_TYPES.length()][INTERNED_ARGUMENT_LIMIT];
    static {
        for (int tn = 0; tn < ALL_TYPES.length(); tn++) {
            for (int i = 0; i < INTERNED_ARGUMENTS[tn].length; i++) {
                char type = ALL_TYPES.charAt(tn);
                INTERNED_ARGUMENTS[tn][i] = new Name(i, type);
            }
        }
    }

    private static final MemberName.Factory IMPL_NAMES = MemberName.getFactory();

    static Name constantZero(int which, char type) {
        return CONSTANT_ZERO[ALL_TYPES.indexOf(type)].newIndex(which);
    }
    private static final Name[] CONSTANT_ZERO
            = new Name[ALL_TYPES.length()];
    static {
        for (int tn = 0; tn < ALL_TYPES.length(); tn++) {
            char bt = ALL_TYPES.charAt(tn);
            Wrapper wrap = Wrapper.forBasicType(bt);
            MemberName zmem = new MemberName(LambdaForm.class, "zero"+bt, MethodType.methodType(wrap.primitiveType()), REF_invokeStatic);
            try {
                zmem = IMPL_NAMES.resolveOrFail(REF_invokeStatic, zmem, null, NoSuchMethodException.class);
            } catch (IllegalAccessException|NoSuchMethodException ex) {
                throw newInternalError(ex);
            }
            NamedFunction zcon = new NamedFunction(zmem);
            Name n = new Name(zcon).newIndex(0);
            assert(n.type == ALL_TYPES.charAt(tn));
            CONSTANT_ZERO[tn] = n;
            assert(n.isConstantZero());
        }
    }

    // Avoid appealing to ValueConversions at bootstrap time:
    private static int zeroI() { return 0; }
    private static long zeroJ() { return 0; }
    private static float zeroF() { return 0; }
    private static double zeroD() { return 0; }
    private static Object zeroL() { return null; }

    // Put this last, so that previous static inits can run before.
    static {
        if (USE_PREDEFINED_INTERPRET_METHODS)
            PREPARED_FORMS.putAll(computeInitialPreparedForms());
    }

    /**
     * Internal marker for byte-compiled LambdaForms.
     */
    /*non-public*/
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Compiled {
    }

    /**
     * Internal marker for LambdaForm interpreter frames.
     */
    /*non-public*/
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Hidden {
    }


/*
    // Smoke-test for the invokers used in this file.
    static void testMethodHandleLinkers() throws Throwable {
        MemberName.Factory lookup = MemberName.getFactory();
        MemberName asList_MN = new MemberName(Arrays.class, "asList",
                                              MethodType.methodType(List.class, Object[].class),
                                              REF_invokeStatic);
        //MethodHandleNatives.resolve(asList_MN, null);
        asList_MN = lookup.resolveOrFail(asList_MN, REF_invokeStatic, null, NoSuchMethodException.class);
        System.out.println("about to call "+asList_MN);
        Object[] abc = { "a", "bc" };
        List<?> lst = (List<?>) MethodHandle.linkToStatic(abc, asList_MN);
        System.out.println("lst="+lst);
        MemberName toString_MN = new MemberName(Object.class.getMethod("toString"));
        String s1 = (String) MethodHandle.linkToVirtual(lst, toString_MN);
        toString_MN = new MemberName(Object.class.getMethod("toString"), true);
        String s2 = (String) MethodHandle.linkToSpecial(lst, toString_MN);
        System.out.println("[s1,s2,lst]="+Arrays.asList(s1, s2, lst.toString()));
        MemberName toArray_MN = new MemberName(List.class.getMethod("toArray"));
        Object[] arr = (Object[]) MethodHandle.linkToInterface(lst, toArray_MN);
        System.out.println("toArray="+Arrays.toString(arr));
    }
    static { try { testMethodHandleLinkers(); } catch (Throwable ex) { throw new RuntimeException(ex); } }
    // Requires these definitions in MethodHandle:
    static final native Object linkToStatic(Object x1, MemberName mn) throws Throwable;
    static final native Object linkToVirtual(Object x1, MemberName mn) throws Throwable;
    static final native Object linkToSpecial(Object x1, MemberName mn) throws Throwable;
    static final native Object linkToInterface(Object x1, MemberName mn) throws Throwable;
 */

    static { NamedFunction.initializeInvokers(); }
}
