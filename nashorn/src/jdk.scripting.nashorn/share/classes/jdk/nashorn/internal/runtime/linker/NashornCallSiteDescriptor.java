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

package jdk.nashorn.internal.runtime.linker;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.CompositeOperation;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.nashorn.internal.ir.debug.NashornTextifier;
import jdk.nashorn.internal.runtime.AccessControlContextFactory;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Nashorn-specific implementation of Dynalink's {@link CallSiteDescriptor}.
 * The reason we have our own subclass is that we're storing flags in an
 * additional primitive field. The class also exposes some useful utilities in
 * form of static methods.
 */
public final class NashornCallSiteDescriptor extends CallSiteDescriptor {
    // Lowest three bits describe the operation
    /** Property getter operation {@code obj.prop} */
    public static final int GET_PROPERTY        = 0;
    /** Element getter operation {@code obj[index]} */
    public static final int GET_ELEMENT         = 1;
    /** Property getter operation, subsequently invoked {@code obj.prop()} */
    public static final int GET_METHOD_PROPERTY = 2;
    /** Element getter operation, subsequently invoked {@code obj[index]()} */
    public static final int GET_METHOD_ELEMENT  = 3;
    /** Property setter operation {@code obj.prop = value} */
    public static final int SET_PROPERTY        = 4;
    /** Element setter operation {@code obj[index] = value} */
    public static final int SET_ELEMENT         = 5;
    /** Call operation {@code fn(args...)} */
    public static final int CALL                = 6;
    /** New operation {@code new Constructor(args...)} */
    public static final int NEW                 = 7;

    private static final int OPERATION_MASK = 7;

    // Correspond to the operation indices above.
    private static final Operation[] OPERATIONS = new Operation[] {
        new CompositeOperation(StandardOperation.GET_PROPERTY, StandardOperation.GET_ELEMENT, StandardOperation.GET_METHOD),
        new CompositeOperation(StandardOperation.GET_ELEMENT, StandardOperation.GET_PROPERTY, StandardOperation.GET_METHOD),
        new CompositeOperation(StandardOperation.GET_METHOD, StandardOperation.GET_PROPERTY, StandardOperation.GET_ELEMENT),
        new CompositeOperation(StandardOperation.GET_METHOD, StandardOperation.GET_ELEMENT, StandardOperation.GET_PROPERTY),
        new CompositeOperation(StandardOperation.SET_PROPERTY, StandardOperation.SET_ELEMENT),
        new CompositeOperation(StandardOperation.SET_ELEMENT, StandardOperation.SET_PROPERTY),
        StandardOperation.CALL,
        StandardOperation.NEW
    };

    /** Flags that the call site references a scope variable (it's an identifier reference or a var declaration, not a
     * property access expression. */
    public static final int CALLSITE_SCOPE         = 1 << 3;
    /** Flags that the call site is in code that uses ECMAScript strict mode. */
    public static final int CALLSITE_STRICT        = 1 << 4;
    /** Flags that a property getter or setter call site references a scope variable that is located at a known distance
     * in the scope chain. Such getters and setters can often be linked more optimally using these assumptions. */
    public static final int CALLSITE_FAST_SCOPE    = 1 << 5;
    /** Flags that a callsite type is optimistic, i.e. we might get back a wider return value than encoded in the
     * descriptor, and in that case we have to throw an UnwarrantedOptimismException */
    public static final int CALLSITE_OPTIMISTIC    = 1 << 6;
    /** Is this really an apply that we try to call as a call? */
    public static final int CALLSITE_APPLY_TO_CALL = 1 << 7;
    /** Does this a callsite for a variable declaration? */
    public static final int CALLSITE_DECLARE       = 1 << 8;

    /** Flags that the call site is profiled; Contexts that have {@code "profile.callsites"} boolean property set emit
     * code where call sites have this flag set. */
    public static final int CALLSITE_PROFILE         = 1 << 9;
    /** Flags that the call site is traced; Contexts that have {@code "trace.callsites"} property set emit code where
     * call sites have this flag set. */
    public static final int CALLSITE_TRACE           = 1 << 10;
    /** Flags that the call site linkage miss (and thus, relinking) is traced; Contexts that have the keyword
     * {@code "miss"} in their {@code "trace.callsites"} property emit code where call sites have this flag set. */
    public static final int CALLSITE_TRACE_MISSES    = 1 << 11;
    /** Flags that entry/exit to/from the method linked at call site are traced; Contexts that have the keyword
     * {@code "enterexit"} in their {@code "trace.callsites"} property emit code where call sites have this flag set. */
    public static final int CALLSITE_TRACE_ENTEREXIT = 1 << 12;
    /** Flags that values passed as arguments to and returned from the method linked at call site are traced; Contexts
     * that have the keyword {@code "values"} in their {@code "trace.callsites"} property emit code where call sites
     * have this flag set. */
    public static final int CALLSITE_TRACE_VALUES    = 1 << 13;

    //we could have more tracing flags here, for example CALLSITE_TRACE_SCOPE, but bits are a bit precious
    //right now given the program points

    /**
     * Number of bits the program point is shifted to the left in the flags (lowest bit containing a program point).
     * Always one larger than the largest flag shift. Note that introducing a new flag halves the number of program
     * points we can have.
     * TODO: rethink if we need the various profile/trace flags or the linker can use the Context instead to query its
     * trace/profile settings.
     */
    public static final int CALLSITE_PROGRAM_POINT_SHIFT = 14;

    /**
     * Maximum program point value. We have 18 bits left over after flags, and
     * it should be plenty. Program points are local to a single function. Every
     * function maps to a single JVM bytecode method that can have at most 65535
     * bytes. (Large functions are synthetically split into smaller functions.)
     * A single invokedynamic is 5 bytes; even if a method consists of only
     * invokedynamic instructions that leaves us with at most 65535/5 = 13107
     * program points for the largest single method; those can be expressed on
     * 14 bits. It is true that numbering of program points is independent of
     * bytecode representation, but if a function would need more than ~14 bits
     * for the program points, then it is reasonable to presume splitter
     * would've split it into several smaller functions already.
     */
    public static final int MAX_PROGRAM_POINT_VALUE = (1 << 32 - CALLSITE_PROGRAM_POINT_SHIFT) - 1;

    /**
     * Flag mask to get the program point flags
     */
    public static final int FLAGS_MASK = (1 << CALLSITE_PROGRAM_POINT_SHIFT) - 1;

    private static final ClassValue<ConcurrentMap<NashornCallSiteDescriptor, NashornCallSiteDescriptor>> canonicals =
            new ClassValue<ConcurrentMap<NashornCallSiteDescriptor,NashornCallSiteDescriptor>>() {
        @Override
        protected ConcurrentMap<NashornCallSiteDescriptor, NashornCallSiteDescriptor> computeValue(final Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final AccessControlContext GET_LOOKUP_PERMISSION_CONTEXT =
            AccessControlContextFactory.createAccessControlContext(CallSiteDescriptor.GET_LOOKUP_PERMISSION_NAME);

    @SuppressWarnings("unchecked")
    private static final Map<String, Reference<NamedOperation>>[] NAMED_OPERATIONS =
            Stream.generate(() -> Collections.synchronizedMap(new WeakHashMap<>()))
            .limit(OPERATIONS.length).toArray(Map[]::new);

    private final int flags;

    /**
     * Function used by {@link NashornTextifier} to represent call site flags in
     * human readable form
     * @param flags call site flags
     * @return human readable form of this callsite descriptor
     */
    public static String toString(final int flags) {
        final StringBuilder sb = new StringBuilder();
        if ((flags & CALLSITE_SCOPE) != 0) {
            if ((flags & CALLSITE_FAST_SCOPE) != 0) {
                sb.append("fastscope ");
            } else {
                assert (flags & CALLSITE_FAST_SCOPE) == 0 : "can't be fastscope without scope";
                sb.append("scope ");
            }
            if ((flags & CALLSITE_DECLARE) != 0) {
                sb.append("declare ");
            }
        }
        if ((flags & CALLSITE_APPLY_TO_CALL) != 0) {
            sb.append("apply2call ");
        }
        if ((flags & CALLSITE_STRICT) != 0) {
            sb.append("strict ");
        }
        return sb.length() == 0 ? "" : " " + sb.toString().trim();
    }

    /**
     * Given call site flags, returns the operation name encoded in them.
     * @param flags flags
     * @return the operation name
     */
    public static String getOperationName(final int flags) {
        switch(flags & OPERATION_MASK) {
        case 0: return "GET_PROPERTY";
        case 1: return "GET_ELEMENT";
        case 2: return "GET_METHOD_PROPERTY";
        case 3: return "GET_METHOD_ELEMENT";
        case 4: return "SET_PROPERTY";
        case 5: return "SET_ELEMENT";
        case 6: return "CALL";
        case 7: return "NEW";
        default: throw new AssertionError();
        }
    }

    /**
     * Retrieves a Nashorn call site descriptor with the specified values. Since call site descriptors are immutable
     * this method is at liberty to retrieve canonicalized instances (although it is not guaranteed it will do so).
     * @param lookup the lookup describing the script
     * @param name the name at the call site. Can not be null, but it can be empty.
     * @param methodType the method type at the call site
     * @param flags Nashorn-specific call site flags
     * @return a call site descriptor with the specified values.
     */
    public static NashornCallSiteDescriptor get(final MethodHandles.Lookup lookup, final String name,
            final MethodType methodType, final int flags) {
        final int opIndex = flags & OPERATION_MASK;
        final Operation baseOp = OPERATIONS[opIndex];
        final String decodedName = NameCodec.decode(name);
        final Operation op = decodedName.isEmpty() ? baseOp : getNamedOperation(decodedName, opIndex, baseOp);
        return get(lookup, op, methodType, flags);
    }

    private static NamedOperation getNamedOperation(final String name, final int opIndex, final Operation baseOp) {
        final Map<String, Reference<NamedOperation>> namedOps = NAMED_OPERATIONS[opIndex];
        final Reference<NamedOperation> ref = namedOps.get(name);
        if (ref != null) {
            final NamedOperation existing = ref.get();
            if (existing != null) {
                return existing;
            }
        }
        final NamedOperation newOp = new NamedOperation(baseOp, name);
        namedOps.put(name, new WeakReference<>(newOp));
        return newOp;
    }

    private static NashornCallSiteDescriptor get(final MethodHandles.Lookup lookup, final Operation operation, final MethodType methodType, final int flags) {
        final NashornCallSiteDescriptor csd = new NashornCallSiteDescriptor(lookup, operation, methodType, flags);
        // Many of these call site descriptors are identical (e.g. every getter for a property color will be
        // "GET_PROPERTY:color(Object)Object", so it makes sense canonicalizing them. Make an exception for
        // optimistic call site descriptors, as they also carry a program point making them unique.
        if (csd.isOptimistic()) {
            return csd;
        }
        final NashornCallSiteDescriptor canonical = canonicals.get(lookup.lookupClass()).putIfAbsent(csd, csd);
        return canonical != null ? canonical : csd;
    }

    private NashornCallSiteDescriptor(final MethodHandles.Lookup lookup, final Operation operation, final MethodType methodType, final int flags) {
        super(lookup, operation, methodType);
        this.flags = flags;
    }

    static Lookup getLookupInternal(final CallSiteDescriptor csd) {
        if (csd instanceof NashornCallSiteDescriptor) {
            return ((NashornCallSiteDescriptor)csd).getLookupPrivileged();
        }
        return AccessController.doPrivileged((PrivilegedAction<Lookup>)()->csd.getLookup(), GET_LOOKUP_PERMISSION_CONTEXT);
    }

    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj) && flags == ((NashornCallSiteDescriptor)obj).flags;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ flags;
    }

    /**
     * Returns the named operand in this descriptor's operation. Equivalent to
     * {@code ((NamedOperation)getOperation()).getName().toString()} for call
     * sites with a named operand. For call sites without named operands returns null.
     * @return the named operand in this descriptor's operation.
     */
    public String getOperand() {
        return getOperand(this);
    }

    /**
     * Returns the named operand in the passed descriptor's operation.
     * Equivalent to
     * {@code ((NamedOperation)desc.getOperation()).getName().toString()} for
     * descriptors with a named operand. For descriptors without named operands
     * returns null.
     * @param desc the call site descriptors
     * @return the named operand in this descriptor's operation.
     */
    public static String getOperand(final CallSiteDescriptor desc) {
        final Operation operation = desc.getOperation();
        return operation instanceof NamedOperation ? ((NamedOperation)operation).getName().toString() : null;
    }

    /**
     * Returns the first operation in this call site descriptor's potentially
     * composite operation. E.g. if this call site descriptor has a composite
     * operation {@code GET_PROPERTY|GET_METHOD|GET_ELEM}, it will return
     * {@code GET_PROPERTY}. Nashorn - being a ECMAScript engine - does not
     * distinguish between property, element, and method namespace; ECMAScript
     * objects just have one single property namespace for all these, therefore
     * it is largely irrelevant what the composite operation is structured like;
     * if the first operation can't be satisfied, neither can the others. The
     * first operation is however sometimes used to slightly alter the
     * semantics; for example, a distinction between {@code GET_PROPERTY} and
     * {@code GET_METHOD} being the first operation can translate into whether
     * {@code "__noSuchProperty__"} or {@code "__noSuchMethod__"} will be
     * executed in case the property is not found. Note that if a call site
     * descriptor comes from outside of Nashorn, its class will be different,
     * and there is no guarantee about the way it composes its operations. For
     * that reason, for potentially foreign call site descriptors you should use
     * {@link #getFirstStandardOperation(CallSiteDescriptor)} instead.
     * @return the first operation in this call site descriptor. Note this will
     * always be a {@code StandardOperation} as Nashorn internally only uses
     * standard operations.
     */
    public StandardOperation getFirstOperation() {
        final Operation base = NamedOperation.getBaseOperation(getOperation());
        if (base instanceof CompositeOperation) {
            return (StandardOperation)((CompositeOperation)base).getOperation(0);
        }
        return (StandardOperation)base;
    }

    /**
     * Returns the first standard operation in the (potentially composite)
     * operation of the passed call site descriptor.
     * @param desc the call site descriptor.
     * @return Returns the first standard operation in the (potentially
     * composite) operation of the passed call site descriptor. Can return null
     * if the call site contains no standard operations.
     */
    public static StandardOperation getFirstStandardOperation(final CallSiteDescriptor desc) {
        final Operation base = NamedOperation.getBaseOperation(desc.getOperation());
        if (base instanceof StandardOperation) {
            return (StandardOperation)base;
        } else if (base instanceof CompositeOperation) {
            final CompositeOperation cop = (CompositeOperation)base;
            for(int i = 0; i < cop.getOperationCount(); ++i) {
                final Operation op = cop.getOperation(i);
                if (op instanceof StandardOperation) {
                    return (StandardOperation)op;
                }
            }
        }
        return null;
    }

    /**
     * Returns true if the passed call site descriptor's operation contains (or
     * is) the specified standard operation.
     * @param desc the call site descriptor.
     * @param operation the operation whose presence is tested.
     * @return Returns true if the call site descriptor's operation contains (or
     * is) the specified standard operation.
     */
    public static boolean contains(final CallSiteDescriptor desc, final StandardOperation operation) {
        return CompositeOperation.contains(NamedOperation.getBaseOperation(desc.getOperation()), operation);
    }

    /**
     * Returns the error message to be used when CALL or NEW is used on a non-function.
     *
     * @param obj object on which CALL or NEW is used
     * @return error message
     */
    public String getFunctionErrorMessage(final Object obj) {
        final String funcDesc = getOperand();
        return funcDesc != null? funcDesc : ScriptRuntime.safeToString(obj);
    }

    /**
     * Returns the error message to be used when CALL or NEW is used on a non-function.
     *
     * @param desc call site descriptor
     * @param obj object on which CALL or NEW is used
     * @return error message
     */
    public static String getFunctionErrorMessage(final CallSiteDescriptor desc, final Object obj) {
        return desc instanceof NashornCallSiteDescriptor ?
                ((NashornCallSiteDescriptor)desc).getFunctionErrorMessage(obj) :
                ScriptRuntime.safeToString(obj);
    }

    /**
     * Returns the Nashorn-specific flags for this call site descriptor.
     * @param desc the descriptor. It can be any kind of a call site descriptor, not necessarily a
     * {@code NashornCallSiteDescriptor}. This allows for graceful interoperability when linking Nashorn with code
     * generated outside of Nashorn.
     * @return the Nashorn-specific flags for the call site, or 0 if the passed descriptor is not a Nashorn call site
     * descriptor.
     */
    public static int getFlags(final CallSiteDescriptor desc) {
        return desc instanceof NashornCallSiteDescriptor ? ((NashornCallSiteDescriptor)desc).flags : 0;
    }

    /**
     * Returns true if this descriptor has the specified flag set, see {@code CALLSITE_*} constants in this class.
     * @param flag the tested flag
     * @return true if the flag is set, false otherwise
     */
    private boolean isFlag(final int flag) {
        return (flags & flag) != 0;
    }

    /**
     * Returns true if this descriptor has the specified flag set, see {@code CALLSITE_*} constants in this class.
     * @param desc the descriptor. It can be any kind of a call site descriptor, not necessarily a
     * {@code NashornCallSiteDescriptor}. This allows for graceful interoperability when linking Nashorn with code
     * generated outside of Nashorn.
     * @param flag the tested flag
     * @return true if the flag is set, false otherwise (it will be false if the descriptor is not a Nashorn call site
     * descriptor).
     */
    private static boolean isFlag(final CallSiteDescriptor desc, final int flag) {
        return (getFlags(desc) & flag) != 0;
    }

    /**
     * Returns true if this descriptor is a Nashorn call site descriptor and has the {@link  #CALLSITE_SCOPE} flag set.
     * @param desc the descriptor. It can be any kind of a call site descriptor, not necessarily a
     * {@code NashornCallSiteDescriptor}. This allows for graceful interoperability when linking Nashorn with code
     * generated outside of Nashorn.
     * @return true if the descriptor is a Nashorn call site descriptor, and the flag is set, false otherwise.
     */
    public static boolean isScope(final CallSiteDescriptor desc) {
        return isFlag(desc, CALLSITE_SCOPE);
    }

    /**
     * Returns true if this descriptor is a Nashorn call site descriptor and has the {@link  #CALLSITE_FAST_SCOPE} flag set.
     * @param desc the descriptor. It can be any kind of a call site descriptor, not necessarily a
     * {@code NashornCallSiteDescriptor}. This allows for graceful interoperability when linking Nashorn with code
     * generated outside of Nashorn.
     * @return true if the descriptor is a Nashorn call site descriptor, and the flag is set, false otherwise.
     */
    public static boolean isFastScope(final CallSiteDescriptor desc) {
        return isFlag(desc, CALLSITE_FAST_SCOPE);
    }

    /**
     * Returns true if this descriptor is a Nashorn call site descriptor and has the {@link  #CALLSITE_STRICT} flag set.
     * @param desc the descriptor. It can be any kind of a call site descriptor, not necessarily a
     * {@code NashornCallSiteDescriptor}. This allows for graceful interoperability when linking Nashorn with code
     * generated outside of Nashorn.
     * @return true if the descriptor is a Nashorn call site descriptor, and the flag is set, false otherwise.
     */
    public static boolean isStrict(final CallSiteDescriptor desc) {
        return isFlag(desc, CALLSITE_STRICT);
    }

    /**
     * Returns true if this is an apply call that we try to call as
     * a "call"
     * @param desc descriptor
     * @return true if apply to call
     */
    public static boolean isApplyToCall(final CallSiteDescriptor desc) {
        return isFlag(desc, CALLSITE_APPLY_TO_CALL);
    }

    /**
     * Is this an optimistic call site
     * @param desc descriptor
     * @return true if optimistic
     */
    public static boolean isOptimistic(final CallSiteDescriptor desc) {
        return isFlag(desc, CALLSITE_OPTIMISTIC);
    }

    /**
     * Does this callsite contain a declaration for its target?
     * @param desc descriptor
     * @return true if contains declaration
     */
    public static boolean isDeclaration(final CallSiteDescriptor desc) {
        return isFlag(desc, CALLSITE_DECLARE);
    }

    /**
     * Returns true if {@code flags} has the {@link  #CALLSITE_STRICT} bit set.
     * @param flags the flags
     * @return true if the flag is set, false otherwise.
     */
    public static boolean isStrictFlag(final int flags) {
        return (flags & CALLSITE_STRICT) != 0;
    }

    /**
     * Returns true if {@code flags} has the {@link  #CALLSITE_SCOPE} bit set.
     * @param flags the flags
     * @return true if the flag is set, false otherwise.
     */
    public static boolean isScopeFlag(final int flags) {
        return (flags & CALLSITE_SCOPE) != 0;
    }

    /**
     * Get a program point from a descriptor (must be optimistic)
     * @param desc descriptor
     * @return program point
     */
    public static int getProgramPoint(final CallSiteDescriptor desc) {
        assert isOptimistic(desc) : "program point requested from non-optimistic descriptor " + desc;
        return getFlags(desc) >> CALLSITE_PROGRAM_POINT_SHIFT;
    }

    boolean isProfile() {
        return isFlag(CALLSITE_PROFILE);
    }

    boolean isTrace() {
        return isFlag(CALLSITE_TRACE);
    }

    boolean isTraceMisses() {
        return isFlag(CALLSITE_TRACE_MISSES);
    }

    boolean isTraceEnterExit() {
        return isFlag(CALLSITE_TRACE_ENTEREXIT);
    }

    boolean isTraceObjects() {
        return isFlag(CALLSITE_TRACE_VALUES);
    }

    boolean isOptimistic() {
        return isFlag(CALLSITE_OPTIMISTIC);
    }

    @Override
    public CallSiteDescriptor changeMethodTypeInternal(final MethodType newMethodType) {
        return get(getLookupPrivileged(), getOperation(), newMethodType, flags);
    }
}
