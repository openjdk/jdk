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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.support.AbstractCallSiteDescriptor;
import jdk.nashorn.internal.ir.debug.NashornTextifier;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Nashorn-specific implementation of Dynalink's {@link CallSiteDescriptor}. The reason we have our own subclass is that
 * we can have a more compact representation, as we know that we're always only using {@code "dyn:*"} operations; also
 * we're storing flags in an additional primitive field.
 */
public final class NashornCallSiteDescriptor extends AbstractCallSiteDescriptor<NashornCallSiteDescriptor> {
    /** Flags that the call site references a scope variable (it's an identifier reference or a var declaration, not a
     * property access expression. */
    public static final int CALLSITE_SCOPE         = 1 << 0;
    /** Flags that the call site is in code that uses ECMAScript strict mode. */
    public static final int CALLSITE_STRICT        = 1 << 1;
    /** Flags that a property getter or setter call site references a scope variable that is located at a known distance
     * in the scope chain. Such getters and setters can often be linked more optimally using these assumptions. */
    public static final int CALLSITE_FAST_SCOPE    = 1 << 2;
    /** Flags that a callsite type is optimistic, i.e. we might get back a wider return value than encoded in the
     * descriptor, and in that case we have to throw an UnwarrantedOptimismException */
    public static final int CALLSITE_OPTIMISTIC    = 1 << 3;
    /** Is this really an apply that we try to call as a call? */
    public static final int CALLSITE_APPLY_TO_CALL = 1 << 4;
    /** Does this a callsite for a variable declaration? */
    public static final int CALLSITE_DECLARE       = 1 << 5;

    /** Flags that the call site is profiled; Contexts that have {@code "profile.callsites"} boolean property set emit
     * code where call sites have this flag set. */
    public static final int CALLSITE_PROFILE         = 1 << 6;
    /** Flags that the call site is traced; Contexts that have {@code "trace.callsites"} property set emit code where
     * call sites have this flag set. */
    public static final int CALLSITE_TRACE           = 1 << 7;
    /** Flags that the call site linkage miss (and thus, relinking) is traced; Contexts that have the keyword
     * {@code "miss"} in their {@code "trace.callsites"} property emit code where call sites have this flag set. */
    public static final int CALLSITE_TRACE_MISSES    = 1 << 8;
    /** Flags that entry/exit to/from the method linked at call site are traced; Contexts that have the keyword
     * {@code "enterexit"} in their {@code "trace.callsites"} property emit code where call sites have this flag set. */
    public static final int CALLSITE_TRACE_ENTEREXIT = 1 << 9;
    /** Flags that values passed as arguments to and returned from the method linked at call site are traced; Contexts
     * that have the keyword {@code "values"} in their {@code "trace.callsites"} property emit code where call sites
     * have this flag set. */
    public static final int CALLSITE_TRACE_VALUES    = 1 << 10;

    //we could have more tracing flags here, for example CALLSITE_TRACE_SCOPE, but bits are a bit precious
    //right now given the program points

    /**
     * Number of bits the program point is shifted to the left in the flags (lowest bit containing a program point).
     * Always one larger than the largest flag shift. Note that introducing a new flag halves the number of program
     * points we can have.
     * TODO: rethink if we need the various profile/trace flags or the linker can use the Context instead to query its
     * trace/profile settings.
     */
    public static final int CALLSITE_PROGRAM_POINT_SHIFT = 11;

    /**
     * Maximum program point value. 21 bits should be enough for anyone
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

    private final MethodHandles.Lookup lookup;
    private final String operator;
    private final String operand;
    private final MethodType methodType;
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
     * Retrieves a Nashorn call site descriptor with the specified values. Since call site descriptors are immutable
     * this method is at liberty to retrieve canonicalized instances (although it is not guaranteed it will do so).
     * @param lookup the lookup describing the script
     * @param name the name at the call site, e.g. {@code "dyn:getProp|getElem|getMethod:color"}.
     * @param methodType the method type at the call site
     * @param flags Nashorn-specific call site flags
     * @return a call site descriptor with the specified values.
     */
    public static NashornCallSiteDescriptor get(final MethodHandles.Lookup lookup, final String name,
            final MethodType methodType, final int flags) {
        final String[] tokenizedName = CallSiteDescriptor.tokenizeName(name);
        assert tokenizedName.length >= 2;
        assert "dyn".equals(tokenizedName[0]);
        assert tokenizedName[1] != null;
        // TODO: see if we can move mangling/unmangling into Dynalink
        return get(lookup, tokenizedName[1], tokenizedName.length == 3 ? tokenizedName[2].intern() : null,
                methodType, flags);
    }

    private static NashornCallSiteDescriptor get(final MethodHandles.Lookup lookup, final String operator, final String operand, final MethodType methodType, final int flags) {
        final NashornCallSiteDescriptor csd = new NashornCallSiteDescriptor(lookup, operator, operand, methodType, flags);
        // Many of these call site descriptors are identical (e.g. every getter for a property color will be
        // "dyn:getProp:color(Object)Object", so it makes sense canonicalizing them.
        final ConcurrentMap<NashornCallSiteDescriptor, NashornCallSiteDescriptor> classCanonicals = canonicals.get(lookup.lookupClass());
        final NashornCallSiteDescriptor canonical = classCanonicals.putIfAbsent(csd, csd);
        return canonical != null ? canonical : csd;
    }

    private NashornCallSiteDescriptor(final MethodHandles.Lookup lookup, final String operator, final String operand,
            final MethodType methodType, final int flags) {
        this.lookup = lookup;
        this.operator = operator;
        this.operand = operand;
        this.methodType = methodType;
        this.flags = flags;
    }

    @Override
    public int getNameTokenCount() {
        return operand == null ? 2 : 3;
    }

    @Override
    public String getNameToken(final int i) {
        switch(i) {
        case 0: return "dyn";
        case 1: return operator;
        case 2:
            if(operand != null) {
                return operand;
            }
            break;
        default:
            break;
        }
        throw new IndexOutOfBoundsException(String.valueOf(i));
    }

    @Override
    public Lookup getLookup() {
        return CallSiteDescriptor.checkLookup(lookup);
    }

    static Lookup getLookupPrivileged(final CallSiteDescriptor csd) {
        if (csd instanceof NashornCallSiteDescriptor) {
            return ((NashornCallSiteDescriptor)csd).lookup;
        }
        return AccessController.doPrivileged((PrivilegedAction<Lookup>)()->csd.getLookup(), null,
                CallSiteDescriptor.GET_LOOKUP_PERMISSION);
    }

    @Override
    protected boolean equalsInKind(final NashornCallSiteDescriptor csd) {
        return super.equalsInKind(csd) && flags == csd.flags;
    }

    @Override
    public MethodType getMethodType() {
        return methodType;
    }

    /**
     * Returns the operator (e.g. {@code "getProp"}) in this call site descriptor's name. Equivalent to
     * {@code getNameToken(CallSiteDescriptor.OPERATOR)}. The returned operator can be composite.
     * @return the operator in this call site descriptor's name.
     */
    public String getOperator() {
        return operator;
    }

    /**
     * Returns the first operator in this call site descriptor's name. E.g. if this call site descriptor has a composite
     * operation {@code "getProp|getMethod|getElem"}, it will return {@code "getProp"}. Nashorn - being a ECMAScript
     * engine - does not distinguish between property, element, and method namespace; ECMAScript objects just have one
     * single property namespace for all these, therefore it is largely irrelevant what the composite operation is
     * structured like; if the first operation can't be satisfied, neither can the others. The first operation is
     * however sometimes used to slightly alter the semantics; for example, a distinction between {@code "getProp"} and
     * {@code "getMethod"} being the first operation can translate into whether {@code "__noSuchProperty__"} or
     * {@code "__noSuchMethod__"} will be executed in case the property is not found.
     * @return the first operator in this call site descriptor's name.
     */
    public String getFirstOperator() {
        final int delim = operator.indexOf(CallSiteDescriptor.OPERATOR_DELIMITER);
        return delim == -1 ? operator : operator.substring(0, delim);
    }

    /**
     * Returns the named operand in this descriptor's name. Equivalent to
     * {@code getNameToken(CallSiteDescriptor.NAME_OPERAND)}. E.g. for operation {@code "dyn:getProp:color"}, returns
     * {@code "color"}. For call sites without named operands (e.g. {@code "dyn:new"}) returns null.
     * @return the named operand in this descriptor's name.
     */
    public String getOperand() {
        return operand;
    }

    /**
     * If this is a dyn:call or dyn:new, this returns function description from callsite.
     * Caller has to make sure this is a dyn:call or dyn:new call site.
     *
     * @return function description if available (or null)
     */
    public String getFunctionDescription() {
        assert getFirstOperator().equals("call") || getFirstOperator().equals("new");
        return getNameTokenCount() > 2? getNameToken(2) : null;
    }

    /**
     * If this is a dyn:call or dyn:new, this returns function description from callsite.
     * Caller has to make sure this is a dyn:call or dyn:new call site.
     *
     * @param desc call site descriptor
     * @return function description if available (or null)
     */
    public static String getFunctionDescription(final CallSiteDescriptor desc) {
        return desc instanceof NashornCallSiteDescriptor ?
                ((NashornCallSiteDescriptor)desc).getFunctionDescription() : null;
    }


    /**
     * Returns the error message to be used when dyn:call or dyn:new is used on a non-function.
     *
     * @param obj object on which dyn:call or dyn:new is used
     * @return error message
     */
    public String getFunctionErrorMessage(final Object obj) {
        final String funcDesc = getFunctionDescription();
        return funcDesc != null? funcDesc : ScriptRuntime.safeToString(obj);
    }

    /**
     * Returns the error message to be used when dyn:call or dyn:new is used on a non-function.
     *
     * @param desc call site descriptor
     * @param obj object on which dyn:call or dyn:new is used
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
    public CallSiteDescriptor changeMethodType(final MethodType newMethodType) {
        return get(lookup, operator, operand, newMethodType, flags);
    }


    @Override
    protected boolean lookupEquals(final NashornCallSiteDescriptor other) {
        return AbstractCallSiteDescriptor.lookupsEqual(lookup, other.lookup);
    }

    @Override
    protected int lookupHashCode() {
        return AbstractCallSiteDescriptor.lookupHashCode(lookup);
    }

    @Override
    protected String lookupToString() {
        return lookup.toString();
    }
}
