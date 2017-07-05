/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Sf, tifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.dyn;

import java.dyn.JavaMethodHandle;
import java.dyn.MethodHandle;
import java.dyn.MethodType;
import java.dyn.NoAccessException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * "Flyby adapters" which apply arbitrary conversions to arguments
 * on the way to a ultimate target.
 * For simplicity, these are all generically typed.
 * @author jrose
 */
class FilterGeneric {
    // type for the outgoing call (will be generic)
    private final MethodType targetType;
    // position of (first) argument to participate in filtering
    private final short argumentPosition;
    // number of arguments to participate in filtering
    private final short argumentCount;
    // how the result interacts with the filtered arguments: Prepend, Append, Replace, Discard
    private final char replaceMode;
    // prototype adapter (clone and customize for each new target & conversion!)
    private final Adapter adapter;
    // entry point for adapter (Adapter mh, a...) => ...
    private final MethodHandle entryPoint;
    // more of them (loosely cached)
    private FilterGeneric variations;

    /** Compute and cache information common to all unboxing adapters
     *  that can call out to targets of the erasure-family of the given erased type.
     */
    // TO DO: Make this private.
    FilterGeneric(MethodType targetType, short argumentPosition, short argumentCount, char replaceMode) {
        if (argumentCount == 0) {
            if (replaceMode == 'P' || replaceMode == 'A')  replaceMode = 'R';
            if (replaceMode == 'I')  argumentPosition = 0;
        }
        this.targetType = targetType;
        this.argumentPosition = argumentPosition;
        this.argumentCount = argumentCount;
        this.replaceMode = replaceMode;
        validate(targetType, argumentPosition, argumentCount, replaceMode);
        Adapter ad = findAdapter(targetType, argumentPosition, argumentCount, replaceMode, filterType());
        if (ad == null)
            ad = buildAdapterFromBytecodes(targetType, argumentPosition, argumentCount, replaceMode, filterType());
        this.adapter = ad;
        this.entryPoint = ad.prototypeEntryPoint();
    }

    Adapter makeInstance(MethodHandle filter, MethodHandle target) {
        return adapter.makeInstance(entryPoint, filter, target);
    }

    /** Build an adapter of the given generic type, which invokes typedTarget
     *  on the incoming arguments, after unboxing as necessary.
     *  The return value is boxed if necessary.
     * @param genericType  the required type of the result
     * @param typedTarget the target
     * @return an adapter method handle
     */
    public static MethodHandle make(MethodHandle target, int pos, MethodHandle filter) {
        return FilterGeneric.of(target.type(), (short)pos, (short)1, 'R').makeInstance(filter, target);
    }

    /** Return the adapter information for this type's erasure. */
    static FilterGeneric of(MethodType type, short ap, short ac, char mode) {
        if (type.generic() != type)
            throw new IllegalArgumentException("must be generic: "+type);
        validate(type, ap, ac, mode);
        MethodTypeImpl form = MethodTypeImpl.of(type);
        FilterGeneric filterGen = form.filterGeneric;
        if (filterGen == null)
            form.filterGeneric = filterGen = new FilterGeneric(type, (short)0, (short)1, 'R');
        return find(filterGen, ap, ac, mode);
    }

    static FilterGeneric find(FilterGeneric gen, short ap, short ac, char mode) {
        for (;;) {
            if (gen.argumentPosition == ap &&
                gen.argumentCount == ac &&
                gen.replaceMode == mode) {
                return gen;
            }
            FilterGeneric gen2 = gen.variations;
            if (gen2 == null)  break;
            gen = gen2;
        }
        FilterGeneric gen2 = new FilterGeneric(gen.targetType, ap, ac, mode);
        gen.variations = gen2;  // OK if this smashes another cached chain
        return gen2;
    }

    private static void validate(MethodType type, short ap, short ac, char mode) {
        int endpos = ap + ac;
        switch (mode) {
            case 'P': case 'A': case 'R': case 'D':
                if (ap >= 0 && ac >= 0 &&
                        endpos >= 0 && endpos <= type.parameterCount())
                    return;
            default:
                throw new InternalError("configuration "+patternName(ap, ac, mode));
        }
    }

    public String toString() {
        return "FilterGeneric/"+patternName()+targetType;
    }

    String patternName() {
        return patternName(argumentPosition, argumentCount, replaceMode);
    }

    static String patternName(short ap, short ac, char mode) {
        return ""+mode+ap+(ac>1?"_"+ac:"");
    }

    Class<?> filterType() {
        return Object.class;  // result of filter operation; an uninteresting type
    }

    static MethodType targetType(MethodType entryType, short ap, short ac, char mode,
                                 Class<?> arg) {
        MethodType type = entryType;
        int pos = ap;
        switch (mode) {
            case 'A':
                pos += ac;
            case 'P':
                type = type.insertParameterType(pos, arg);
                break;
            case 'I':
                for (int i = 1; i < ac; i++)
                    type = type.dropParameterType(pos);
                assert(type.parameterType(pos) == arg);
                break;
            case 'D':
                break;
        }
        return type;
    }

    static MethodType entryType(MethodType targetType, short ap, short ac, char mode,
                                Class<?> arg) {
        MethodType type = targetType;
        int pos = ap;
        switch (mode) {
            case 'A':
                pos += ac;
            case 'P':
                type = type.dropParameterType(pos);
                break;
            case 'I':
                for (int i = 1; i < ac; i++)
                    type = type.insertParameterType(pos, arg);
                assert(type.parameterType(pos) == arg);
                break;
            case 'D':
                break;
        }
        return type;
    }

    /* Create an adapter that handles spreading calls for the given type. */
    static Adapter findAdapter(MethodType targetType, short ap, short ac, char mode, Class<?> arg) {
        MethodType entryType = entryType(targetType, ap, ac, mode, arg);
        int argc = targetType.parameterCount();
        String pname = patternName(ap, ac, mode);
        String cname0 = "F"+argc;
        String cname1 = "F"+argc+mode;
        String cname2 = "F"+argc+pname;
        String[] cnames = { cname0, cname1, cname1+"X", cname2 };
        String iname = "invoke_"+pname;
        // e.g., F5R; invoke_R3
        for (String cname : cnames) {
            Class<? extends Adapter> acls = Adapter.findSubClass(cname);
            if (acls == null)  continue;
            // see if it has the required invoke method
            MethodHandle entryPoint = null;
            try {
                entryPoint = MethodHandleImpl.IMPL_LOOKUP.findSpecial(acls, iname, entryType, acls);
            } catch (NoAccessException ex) {
            }
            if (entryPoint == null)  continue;
            Constructor<? extends Adapter> ctor = null;
            try {
                ctor = acls.getDeclaredConstructor(MethodHandle.class);
            } catch (NoSuchMethodException ex) {
            } catch (SecurityException ex) {
            }
            if (ctor == null)  continue;
            try {
                // Produce an instance configured as a prototype.
                return ctor.newInstance(entryPoint);
            } catch (IllegalArgumentException ex) {
            } catch (InvocationTargetException ex) {
            } catch (InstantiationException ex) {
            } catch (IllegalAccessException ex) {
            }
        }
        return null;
    }

    static Adapter buildAdapterFromBytecodes(MethodType targetType, short ap, short ac, char mode, Class<?> arg) {
        throw new UnsupportedOperationException("NYI");
    }

    /**
     * This adapter takes some untyped arguments, and returns an untyped result.
     * Internally, it applies the invoker to the target, which causes the
     * objects to be unboxed; the result is a raw type in L/I/J/F/D.
     * This result is passed to convert, which is responsible for
     * converting the raw result into a boxed object.
     * The invoker is kept separate from the target because it can be
     * generated once per type erasure family, and reused across adapters.
     */
    static abstract class Adapter extends JavaMethodHandle {
        protected final MethodHandle filter;
        protected final MethodHandle target;

        protected boolean isPrototype() { return target == null; }
        protected Adapter(MethodHandle entryPoint) {
            this(entryPoint, entryPoint, null);
            assert(isPrototype());
        }
        protected MethodHandle prototypeEntryPoint() {
            if (!isPrototype())  throw new InternalError();
            return filter;
        }

        protected Adapter(MethodHandle entryPoint,
                          MethodHandle filter, MethodHandle target) {
            super(entryPoint);
            this.filter = filter;
            this.target = target;
        }

        /** Make a copy of self, with new fields. */
        protected abstract Adapter makeInstance(MethodHandle entryPoint,
                MethodHandle filter, MethodHandle target);
        // { return new ThisType(entryPoint, filter, target); }

        static private final String CLASS_PREFIX; // "sun.dyn.FilterGeneric$"
        static {
            String aname = Adapter.class.getName();
            String sname = Adapter.class.getSimpleName();
            if (!aname.endsWith(sname))  throw new InternalError();
            CLASS_PREFIX = aname.substring(0, aname.length() - sname.length());
        }
        /** Find a sibing class of Adapter. */
        static Class<? extends Adapter> findSubClass(String name) {
            String cname = Adapter.CLASS_PREFIX + name;
            try {
                return Class.forName(cname).asSubclass(Adapter.class);
            } catch (ClassNotFoundException ex) {
                return null;
            } catch (ClassCastException ex) {
                return null;
            }
        }
    }

    //* generated classes follow this pattern:
    static class F1RX extends Adapter {
        protected F1RX(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F1RX(MethodHandle e, MethodHandle f, MethodHandle t)
                        { super(e, f, t); }
        protected F1RX makeInstance(MethodHandle e, MethodHandle f, MethodHandle t)
                        { return new F1RX(e, f, t); }
        protected Object filter(Object a0) { return filter.<Object>invoke(a0); }
        protected Object target(Object a0) { return target.<Object>invoke(a0); }
        protected Object invoke_R0(Object a0) { return target(filter(a0)); }
    }
    static class F2RX extends Adapter {
        protected F2RX(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F2RX(MethodHandle e, MethodHandle f, MethodHandle t)
                        { super(e, f, t); }
        protected F2RX makeInstance(MethodHandle e, MethodHandle f, MethodHandle t)
                        { return new F2RX(e, f, t); }
        protected Object filter(Object a0) { return filter.<Object>invoke(a0); }
        protected Object target(Object a0, Object a1) { return target.<Object>invoke(a0, a1); }
        protected Object invoke_R0(Object a0, Object a1) { return target(filter(a0), a1); }
        protected Object invoke_R1(Object a0, Object a1) { return target(a0, filter(a1)); }
    }
    static class F3RX extends Adapter {
        protected F3RX(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F3RX(MethodHandle e, MethodHandle f, MethodHandle t)
                        { super(e, f, t); }
        protected F3RX makeInstance(MethodHandle e, MethodHandle f, MethodHandle t)
                        { return new F3RX(e, f, t); }
        protected Object filter(Object a0) { return filter.<Object>invoke(a0); }
        protected Object target(Object a0, Object a1, Object a2) { return target.<Object>invoke(a0, a1, a2); }
        protected Object invoke_R0(Object a0, Object a1, Object a2) { return target(filter(a0), a1, a2); }
        protected Object invoke_R1(Object a0, Object a1, Object a2) { return target(a0, filter(a1), a2); }
        protected Object invoke_R2(Object a0, Object a1, Object a2) { return target(a0, a1, filter(a2)); }
    }
    static class F4RX extends Adapter {
        protected F4RX(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F4RX(MethodHandle e, MethodHandle f, MethodHandle t)
                        { super(e, f, t); }
        protected F4RX makeInstance(MethodHandle e, MethodHandle f, MethodHandle t)
                        { return new F4RX(e, f, t); }
        protected Object filter(Object a0) { return filter.<Object>invoke(a0); }
        protected Object target(Object a0, Object a1, Object a2, Object a3) { return target.<Object>invoke(a0, a1, a2, a3); }
        protected Object invoke_R0(Object a0, Object a1, Object a2, Object a3) { return target(filter(a0), a1, a2, a3); }
        protected Object invoke_R1(Object a0, Object a1, Object a2, Object a3) { return target(a0, filter(a1), a2, a3); }
        protected Object invoke_R2(Object a0, Object a1, Object a2, Object a3) { return target(a0, a1, filter(a2), a3); }
        protected Object invoke_R3(Object a0, Object a1, Object a2, Object a3) { return target(a0, a1, a2, filter(a3)); }
    }
    // */
}
