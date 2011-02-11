/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.dyn;

import java.dyn.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import sun.dyn.util.ValueConversions;
import sun.dyn.util.Wrapper;
import static sun.dyn.MemberName.newIllegalArgumentException;
import static sun.dyn.MethodTypeImpl.invokers;

/**
 * Adapters which mediate between incoming calls which are not generic
 * and outgoing calls which are.  Any call can be represented generically
 * boxing up its arguments, and (on return) unboxing the return value.
 * <p>
 * A call is "generic" (in MethodHandle terms) if its MethodType features
 * only Object arguments.  A non-generic call therefore features
 * primitives and/or reference types other than Object.
 * An adapter has types for its incoming and outgoing calls.
 * The incoming call type is simply determined by the adapter's type
 * (the MethodType it presents to callers).  The outgoing call type
 * is determined by the adapter's target (a MethodHandle that the adapter
 * either binds internally or else takes as a leading argument).
 * (To stretch the term, adapter-like method handles may have multiple
 * targets or be polymorphic across multiple call types.)
 * @author jrose
 */
class ToGeneric {
    // type for the incoming call (may be erased)
    private final MethodType entryType;
    // incoming type with primitives moved to the end and turned to int/long
    private final MethodType rawEntryType;
    // adapter for the erased type
    private final Adapter adapter;
    // entry point for adapter (Adapter mh, a...) => ...
    private final MethodHandle entryPoint;
    // permutation of arguments for primsAtEndType
    private final int[] primsAtEndOrder;
    // optional final argument list conversions (at least, invokes the target)
    private final MethodHandle invoker;
    // conversion which unboxes a primitive return value
    private final MethodHandle returnConversion;

    /** Compute and cache information common to all generifying (boxing) adapters
     *  that implement members of the erasure-family of the given erased type.
     */
    private ToGeneric(MethodType entryType) {
        assert(entryType.erase() == entryType); // for now
        // incoming call will first "forget" all reference types except Object
        this.entryType = entryType;
        MethodHandle invoker0 = invokers(entryType.generic()).exactInvoker();
        MethodType rawEntryTypeInit;
        Adapter ad = findAdapter(rawEntryTypeInit = entryType);
        if (ad != null) {
            // Immediate hit to exactly the adapter we want,
            // with no monkeying around with primitive types.
            this.returnConversion = computeReturnConversion(entryType, rawEntryTypeInit, false);
            this.rawEntryType = rawEntryTypeInit;
            this.adapter = ad;
            this.entryPoint = ad.prototypeEntryPoint();
            this.primsAtEndOrder = null;
            this.invoker = invoker0;
            return;
        }

        // next, it will reorder primitives after references
        MethodType primsAtEnd = MethodTypeImpl.of(entryType).primsAtEnd();
        // at the same time, it will "forget" all primitive types except int/long
        this.primsAtEndOrder = MethodTypeImpl.primsAtEndOrder(entryType);
        if (primsAtEndOrder != null) {
            // reordering is required; build on top of a simpler ToGeneric
            ToGeneric va2 = ToGeneric.of(primsAtEnd);
            this.adapter = va2.adapter;
            if (true) throw new UnsupportedOperationException("NYI: primitive parameters must follow references; entryType = "+entryType);
            this.entryPoint = MethodHandleImpl.convertArguments(Access.TOKEN,
                    va2.entryPoint, primsAtEnd, entryType, primsAtEndOrder);
            // example: for entryType of (int,Object,Object), the reordered
            // type is (Object,Object,int) and the order is {1,2,0},
            // and putPAE is (mh,int0,obj1,obj2) => mh.invokeExact(obj1,obj2,int0)
            return;
        }

        // after any needed argument reordering, it will reinterpret
        // primitive arguments according to their "raw" types int/long
        MethodType intsAtEnd = MethodTypeImpl.of(primsAtEnd).primsAsInts();
        ad = findAdapter(rawEntryTypeInit = intsAtEnd);
        MethodHandle rawEntryPoint;
        if (ad != null) {
            rawEntryPoint = ad.prototypeEntryPoint();
        } else {
            // Perhaps the adapter is available only for longs.
            // If so, we can use it, but there will have to be a little
            // more stack motion on each call.
            MethodType longsAtEnd = MethodTypeImpl.of(primsAtEnd).primsAsLongs();
            ad = findAdapter(rawEntryTypeInit = longsAtEnd);
            if (ad != null) {
                MethodType eptWithLongs = longsAtEnd.insertParameterTypes(0, ad.getClass());
                MethodType eptWithInts  =  intsAtEnd.insertParameterTypes(0, ad.getClass());
                rawEntryPoint = ad.prototypeEntryPoint();
                MethodType midType = eptWithLongs;  // will change longs to ints
                for (int i = 0, nargs = midType.parameterCount(); i < nargs; i++) {
                    if (midType.parameterType(i) != eptWithInts.parameterType(i)) {
                        assert(midType.parameterType(i) == long.class);
                        assert(eptWithInts.parameterType(i) == int.class);
                        MethodType nextType = midType.changeParameterType(i, int.class);
                        rawEntryPoint = MethodHandle.convertArguments(Access.TOKEN,
                                rawEntryPoint, nextType, midType, null);
                        midType = nextType;
                    }
                }
                assert(midType == eptWithInts);
            } else {
                // If there is no statically compiled adapter,
                // build one by means of dynamic bytecode generation.
                ad = buildAdapterFromBytecodes(rawEntryTypeInit = intsAtEnd);
                rawEntryPoint = ad.prototypeEntryPoint();
            }
        }
        MethodType tepType = entryType.insertParameterTypes(0, ad.getClass());
        this.entryPoint =
            AdapterMethodHandle.makeRetypeRaw(Access.TOKEN, tepType, rawEntryPoint);
        if (this.entryPoint == null)
            throw new UnsupportedOperationException("cannot retype to "+entryType
                    +" from "+rawEntryPoint.type().dropParameterTypes(0, 1));
        this.returnConversion = computeReturnConversion(entryType, rawEntryTypeInit, false);
        this.rawEntryType = rawEntryTypeInit;
        this.adapter = ad;
        this.invoker = makeRawArgumentFilter(invoker0, rawEntryTypeInit, entryType);
    }

    /** A generic argument list will be created by a call of type 'raw'.
     *  The values need to be reboxed for to match 'cooked'.
     *  Do this on the fly.
     */
    // TO DO: Use a generic argument converter in a different file
    static MethodHandle makeRawArgumentFilter(MethodHandle invoker,
            MethodType raw, MethodType cooked) {
        MethodHandle filteredInvoker = null;
        for (int i = 0, nargs = raw.parameterCount(); i < nargs; i++) {
            Class<?> src = raw.parameterType(i);
            Class<?> dst = cooked.parameterType(i);
            if (src == dst)  continue;
            assert(src.isPrimitive() && dst.isPrimitive());
            if (filteredInvoker == null) {
                filteredInvoker =
                        AdapterMethodHandle.makeCheckCast(Access.TOKEN,
                            invoker.type().generic(), invoker, 0, MethodHandle.class);
                if (filteredInvoker == null)  throw new UnsupportedOperationException("NYI");
            }
            MethodHandle reboxer = ValueConversions.rebox(dst, false);
            filteredInvoker = FilterGeneric.makeArgumentFilter(1+i, reboxer, filteredInvoker);
            if (filteredInvoker == null)  throw new InternalError();
        }
        if (filteredInvoker == null)  return invoker;
        return AdapterMethodHandle.makeRetypeOnly(Access.TOKEN, invoker.type(), filteredInvoker);
    }

    /**
     * Caller will be expecting a result from a call to {@code type},
     * while the internal adapter entry point is rawEntryType.
     * Also, the internal target method will be returning a boxed value,
     * as an untyped object.
     * <p>
     * Produce a value converter which will be typed to convert from
     * {@code Object} to the return value of {@code rawEntryType}, and will
     * in fact ensure that the value is compatible with the return type of
     * {@code type}.
     */
    private static MethodHandle computeReturnConversion(
            MethodType type, MethodType rawEntryType, boolean mustCast) {
        Class<?> tret = type.returnType();
        Class<?> rret = rawEntryType.returnType();
        if (mustCast || !tret.isPrimitive()) {
            assert(!tret.isPrimitive());
            assert(!rret.isPrimitive());
            if (rret == Object.class && !mustCast)
                return null;
            return ValueConversions.cast(tret, false);
        } else if (tret == rret) {
            return ValueConversions.unbox(tret, false);
        } else {
            assert(rret.isPrimitive());
            assert(tret == double.class ? rret == long.class : rret == int.class);
            return ValueConversions.unboxRaw(tret, false);
        }
    }

    Adapter makeInstance(MethodType type, MethodHandle genericTarget) {
        genericTarget.getClass();  // check for NPE
        MethodHandle convert = returnConversion;
        if (primsAtEndOrder != null)
            // reorder arguments passed to genericTarget, if primsAtEndOrder
            throw new UnsupportedOperationException("NYI");
        if (type == entryType) {
            if (convert == null)  convert = ValueConversions.identity();
            return adapter.makeInstance(entryPoint, invoker, convert, genericTarget);
        }
        // my erased-type is not exactly the same as the desired type
        assert(type.erase() == entryType);  // else we are busted
        if (convert == null)
            convert = computeReturnConversion(type, rawEntryType, true);
        // retype erased reference arguments (the cast makes it safe to do this)
        MethodType tepType = type.insertParameterTypes(0, adapter.getClass());
        MethodHandle typedEntryPoint =
            AdapterMethodHandle.makeRetypeRaw(Access.TOKEN, tepType, entryPoint);
        return adapter.makeInstance(typedEntryPoint, invoker, convert, genericTarget);
    }

    /** Build an adapter of the given type, which invokes genericTarget
     *  on the incoming arguments, after boxing as necessary.
     *  The return value is unboxed if necessary.
     * @param type  the required type of the
     * @param genericTarget the target, which must accept and return only Object values
     * @return an adapter method handle
     */
    public static MethodHandle make(MethodType type, MethodHandle genericTarget) {
        MethodType gtype = genericTarget.type();
        if (type.generic() != gtype)
            throw newIllegalArgumentException("type must be generic");
        if (type == gtype)  return genericTarget;
        return ToGeneric.of(type).makeInstance(type, genericTarget);
    }

    /** Return the adapter information for this type's erasure. */
    static ToGeneric of(MethodType type) {
        MethodTypeImpl form = MethodTypeImpl.of(type);
        ToGeneric toGen = form.toGeneric;
        if (toGen == null)
            form.toGeneric = toGen = new ToGeneric(form.erasedType());
        return toGen;
    }

    public String toString() {
        return "ToGeneric"+entryType
                +(primsAtEndOrder!=null?"[reorder]":"");
    }

    /* Create an adapter for the given incoming call type. */
    static Adapter findAdapter(MethodType entryPointType) {
        MethodTypeImpl form = MethodTypeImpl.of(entryPointType);
        Class<?> rtype = entryPointType.returnType();
        int argc = form.parameterCount();
        int lac = form.longPrimitiveParameterCount();
        int iac = form.primitiveParameterCount() - lac;
        String intsAndLongs = (iac > 0 ? "I"+iac : "")+(lac > 0 ? "J"+lac : "");
        String rawReturn = String.valueOf(Wrapper.forPrimitiveType(rtype).basicTypeChar());
        String iname0 = "invoke_"+rawReturn;
        String iname1 = "invoke";
        String[] inames = { iname0, iname1 };
        String cname0 = rawReturn + argc;
        String cname1 = "A"       + argc;
        String[] cnames = { cname1, cname1+intsAndLongs, cname0, cname0+intsAndLongs };
        // e.g., D5I2, D5, L5I2, L5
        for (String cname : cnames) {
            Class<? extends Adapter> acls = Adapter.findSubClass(cname);
            if (acls == null)  continue;
            // see if it has the required invoke method
            for (String iname : inames) {
                MethodHandle entryPoint = null;
                try {
                    entryPoint = MethodHandleImpl.IMPL_LOOKUP.
                                    findSpecial(acls, iname, entryPointType, acls);
                } catch (ReflectiveOperationException ex) {
                }
                if (entryPoint == null)  continue;
                Constructor<? extends Adapter> ctor = null;
                try {
                    // Prototype builder:
                    ctor = acls.getDeclaredConstructor(MethodHandle.class);
                } catch (NoSuchMethodException ex) {
                } catch (SecurityException ex) {
                }
                if (ctor == null)  continue;
                try {
                    return ctor.newInstance(entryPoint);
                } catch (IllegalArgumentException ex) {
                } catch (InvocationTargetException wex) {
                    Throwable ex = wex.getTargetException();
                    if (ex instanceof Error)  throw (Error)ex;
                    if (ex instanceof RuntimeException)  throw (RuntimeException)ex;
                } catch (InstantiationException ex) {
                } catch (IllegalAccessException ex) {
                }
            }
        }
        return null;
    }

    static Adapter buildAdapterFromBytecodes(MethodType entryPointType) {
        throw new UnsupportedOperationException("NYI");
    }

    /**
     * The invoke method takes some particular but unconstrained spread
     * of raw argument types, and returns a raw return type (in L/I/J/F/D).
     * Internally, it converts the incoming arguments uniformly into objects.
     * This series of objects is then passed to the {@code target} method,
     * which returns a result object.  This result is finally converted,
     * via another method handle {@code convert}, which is responsible for
     * converting the object result into the raw return value.
     */
    static abstract class Adapter extends BoundMethodHandle {
        /*
         * class X<<R,A...>> extends Adapter {
         *   Object...=>Object target;
         *   Object=>R convert;
         *   R invoke(A... a...) = convert(invoker(target, a...)))
         * }
         */
        protected final MethodHandle invoker;  // (MH, Object...) -> Object
        protected final MethodHandle target;   // Object... -> Object
        protected final MethodHandle convert;  // Object -> R

        @Override
        public String toString() {
            return target == null ? "prototype:"+convert : MethodHandleImpl.addTypeString(target, this);
        }

        protected boolean isPrototype() { return target == null; }
        /* Prototype constructor. */
        protected Adapter(MethodHandle entryPoint) {
            super(Access.TOKEN, entryPoint);
            this.invoker = null;
            this.convert = entryPoint;
            this.target = null;
            assert(isPrototype());
        }
        protected MethodHandle prototypeEntryPoint() {
            if (!isPrototype())  throw new InternalError();
            return convert;
        }

        protected Adapter(MethodHandle entryPoint, MethodHandle invoker, MethodHandle convert, MethodHandle target) {
            super(Access.TOKEN, entryPoint);
            this.invoker = invoker;
            this.convert = convert;
            this.target = target;
        }

        /** Make a copy of self, with new fields. */
        protected abstract Adapter makeInstance(MethodHandle entryPoint,
                MethodHandle invoker, MethodHandle convert, MethodHandle target);
        // { return new ThisType(entryPoint, convert, target); }

        // Code to run when the arguments (<= 4) have all been boxed.
        protected Object target()               throws Throwable { return invoker.invokeExact(target); }
        protected Object target(Object a0)      throws Throwable { return invoker.invokeExact(target, a0); }
        protected Object target(Object a0, Object a1)
                                                throws Throwable { return invoker.invokeExact(target, a0, a1); }
        protected Object target(Object a0, Object a1, Object a2)
                                                throws Throwable { return invoker.invokeExact(target, a0, a1, a2); }
        protected Object target(Object a0, Object a1, Object a2, Object a3)
                                                throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3); }
        /*
        protected Object target_0(Object... av) throws Throwable { return invoker.invokeExact(target, av); }
        protected Object target_1(Object a0, Object... av)
                                                throws Throwable { return invoker.invokeExact(target, a0, (Object)av); }
        protected Object target_2(Object a0, Object a1, Object... av)
                                                throws Throwable { return invoker.invokeExact(target, a0, a1, (Object)av); }
        protected Object target_3(Object a0, Object a1, Object a2, Object... av)
                                                throws Throwable { return invoker.invokeExact(target, a0, a1, a2, (Object)av); }
        protected Object target_4(Object a0, Object a1, Object a2, Object a3, Object... av)
                                                throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3, (Object)av); }
        // */
        // (For more than 4 arguments, generate the code in the adapter itself.)

        // Code to run when the generic target has finished and produced a value.
        protected Object return_L(Object res) throws Throwable { return (Object)convert.invokeExact(res); }
        protected int    return_I(Object res) throws Throwable { return (int)   convert.invokeExact(res); }
        protected long   return_J(Object res) throws Throwable { return (long)  convert.invokeExact(res); }
        protected float  return_F(Object res) throws Throwable { return (float) convert.invokeExact(res); }
        protected double return_D(Object res) throws Throwable { return (double)convert.invokeExact(res); }

        static private final String CLASS_PREFIX; // "sun.dyn.ToGeneric$"
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

    /* generated classes follow this pattern:
    static class A1 extends Adapter {
        protected A1(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A1(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A1 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A1(e, i, c, t); }
        protected Object target(Object a0)   throws Throwable { return invoker.invokeExact(target, a0); }
        protected Object targetA1(Object a0) throws Throwable { return target(a0); }
        protected Object targetA1(int    a0) throws Throwable { return target(a0); }
        protected Object targetA1(long   a0) throws Throwable { return target(a0); }
        protected Object invoke_L(Object a0) throws Throwable { return return_L(targetA1(a0)); }
        protected int    invoke_I(Object a0) throws Throwable { return return_I(targetA1(a0)); }
        protected long   invoke_J(Object a0) throws Throwable { return return_J(targetA1(a0)); }
        protected float  invoke_F(Object a0) throws Throwable { return return_F(targetA1(a0)); }
        protected double invoke_D(Object a0) throws Throwable { return return_D(targetA1(a0)); }
        protected Object invoke_L(int    a0) throws Throwable { return return_L(targetA1(a0)); }
        protected int    invoke_I(int    a0) throws Throwable { return return_I(targetA1(a0)); }
        protected long   invoke_J(int    a0) throws Throwable { return return_J(targetA1(a0)); }
        protected float  invoke_F(int    a0) throws Throwable { return return_F(targetA1(a0)); }
        protected double invoke_D(int    a0) throws Throwable { return return_D(targetA1(a0)); }
        protected Object invoke_L(long   a0) throws Throwable { return return_L(targetA1(a0)); }
        protected int    invoke_I(long   a0) throws Throwable { return return_I(targetA1(a0)); }
        protected long   invoke_J(long   a0) throws Throwable { return return_J(targetA1(a0)); }
        protected float  invoke_F(long   a0) throws Throwable { return return_F(targetA1(a0)); }
        protected double invoke_D(long   a0) throws Throwable { return return_D(targetA1(a0)); }
    }
    // */

/*
: SHELL; n=ToGeneric; cp -p $n.java $n.java-; sed < $n.java- > $n.java+ -e '/{{*{{/,/}}*}}/w /tmp/genclasses.java' -e '/}}*}}/q'; (cd /tmp; javac -d . genclasses.java; java -cp . genclasses) >> $n.java+; echo '}' >> $n.java+; mv $n.java+ $n.java; mv $n.java- $n.java~
//{{{
import java.util.*;
class genclasses {
    static String[] TYPES = { "Object", "int   ", "long  ", "float ", "double" };
    static String[] TCHARS = { "L",     "I",      "J",      "F",      "D",     "A" };
    static String[][] TEMPLATES = { {
        "@for@ arity=0..3   rcat<=4 nrefs<=99 nints<=99 nlongs<=99",
        "@for@ arity=4..5   rcat<=2 nrefs<=99 nints<=99 nlongs<=99",
        "@for@ arity=6..10  rcat<=2 nrefs<=99 nints=0   nlongs<=99",
        "    //@each-cat@",
        "    static class @cat@ extends Adapter {",
        "        protected @cat@(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype",
        "        protected @cat@(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }",
        "        protected @cat@ makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new @cat@(e, i, c, t); }",
        "        protected Object target(@Ovav@)   throws Throwable { return invoker.invokeExact(target, @av@); }",
        "        //@each-Tv@",
        "        protected Object target@cat@(@Tvav@) throws Throwable { return target(@av@); }",
        "        //@end-Tv@",
        "        //@each-Tv@",
        "        //@each-R@",
        "        protected @R@ invoke_@Rc@(@Tvav@) throws Throwable { return return_@Rc@(target@cat@(@av@)); }",
        "        //@end-R@",
        "        //@end-Tv@",
        "    }",
    } };
    enum VAR {
        cat, R, Rc, Tv, av, Tvav, Ovav;
        public final String pattern = "@"+toString().replace('_','.')+"@";
        public String binding;
        static void makeBindings(boolean topLevel, int rcat, int nrefs, int nints, int nlongs) {
            int nargs = nrefs + nints + nlongs;
            if (topLevel)
                VAR.cat.binding = catstr(ALL_RETURN_TYPES ? TYPES.length : rcat, nrefs, nints, nlongs);
            VAR.R.binding = TYPES[rcat];
            VAR.Rc.binding = TCHARS[rcat];
            String[] Tv = new String[nargs];
            String[] av = new String[nargs];
            String[] Tvav = new String[nargs];
            String[] Ovav = new String[nargs];
            for (int i = 0; i < nargs; i++) {
                int tcat = (i < nrefs) ? 0 : (i < nrefs + nints) ? 1 : 2;
                Tv[i] = TYPES[tcat];
                av[i] = arg(i);
                Tvav[i] = param(Tv[i], av[i]);
                Ovav[i] = param("Object", av[i]);
            }
            VAR.Tv.binding = comma(Tv);
            VAR.av.binding = comma(av);
            VAR.Tvav.binding = comma(Tvav);
            VAR.Ovav.binding = comma(Ovav);
        }
        static String arg(int i) { return "a"+i; }
        static String param(String t, String a) { return t+" "+a; }
        static String comma(String[] v) { return comma(v, ""); }
        static String comma(String sep, String[] v) {
            if (v.length == 0)  return "";
            String res = sep+v[0];
            for (int i = 1; i < v.length; i++)  res += ", "+v[i];
            return res;
        }
        static String transform(String string) {
            for (VAR var : values())
                string = string.replaceAll(var.pattern, var.binding);
            return string;
        }
    }
    static String[] stringsIn(String[] strings, int beg, int end) {
        return Arrays.copyOfRange(strings, beg, Math.min(end, strings.length));
    }
    static String[] stringsBefore(String[] strings, int pos) {
        return stringsIn(strings, 0, pos);
    }
    static String[] stringsAfter(String[] strings, int pos) {
        return stringsIn(strings, pos, strings.length);
    }
    static int indexAfter(String[] strings, int pos, String tag) {
        return Math.min(indexBefore(strings, pos, tag) + 1, strings.length);
    }
    static int indexBefore(String[] strings, int pos, String tag) {
        for (int i = pos, end = strings.length; ; i++) {
            if (i == end || strings[i].endsWith(tag))  return i;
        }
    }
    static int MIN_ARITY, MAX_ARITY, MAX_RCAT, MAX_REFS, MAX_INTS, MAX_LONGS;
    static boolean ALL_ARG_TYPES, ALL_RETURN_TYPES;
    static HashSet<String> done = new HashSet<String>();
    public static void main(String... av) {
        for (String[] template : TEMPLATES) {
            int forLinesLimit = indexBefore(template, 0, "@each-cat@");
            String[] forLines = stringsBefore(template, forLinesLimit);
            template = stringsAfter(template, forLinesLimit);
            for (String forLine : forLines)
                expandTemplate(forLine, template);
        }
    }
    static void expandTemplate(String forLine, String[] template) {
        String[] params = forLine.split("[^0-9]+");
        if (params[0].length() == 0)  params = stringsAfter(params, 1);
        System.out.println("//params="+Arrays.asList(params));
        int pcur = 0;
        MIN_ARITY = Integer.valueOf(params[pcur++]);
        MAX_ARITY = Integer.valueOf(params[pcur++]);
        MAX_RCAT  = Integer.valueOf(params[pcur++]);
        MAX_REFS  = Integer.valueOf(params[pcur++]);
        MAX_INTS  = Integer.valueOf(params[pcur++]);
        MAX_LONGS = Integer.valueOf(params[pcur++]);
        if (pcur != params.length)  throw new RuntimeException("bad extra param: "+forLine);
        if (MAX_RCAT >= TYPES.length)  MAX_RCAT = TYPES.length - 1;
        ALL_ARG_TYPES = (indexBefore(template, 0, "@each-Tv@") < template.length);
        ALL_RETURN_TYPES = (indexBefore(template, 0, "@each-R@") < template.length);
        for (int nargs = MIN_ARITY; nargs <= MAX_ARITY; nargs++) {
            for (int rcat = 0; rcat <= MAX_RCAT; rcat++) {
                expandTemplate(template, true, rcat, nargs, 0, 0);
                if (ALL_ARG_TYPES)  break;
                expandTemplateForPrims(template, true, rcat, nargs, 1, 1);
                if (ALL_RETURN_TYPES)  break;
            }
        }
    }
    static String catstr(int rcat, int nrefs, int nints, int nlongs) {
        int nargs = nrefs + nints + nlongs;
        String cat = TCHARS[rcat] + nargs;
        if (!ALL_ARG_TYPES)  cat += (nints==0?"":"I"+nints)+(nlongs==0?"":"J"+nlongs);
        return cat;
    }
    static void expandTemplateForPrims(String[] template, boolean topLevel, int rcat, int nargs, int minints, int minlongs) {
        for (int isLong = 0; isLong <= 1; isLong++) {
            for (int nprims = 1; nprims <= nargs; nprims++) {
                int nrefs = nargs - nprims;
                int nints = ((1-isLong) * nprims);
                int nlongs = (isLong * nprims);
                expandTemplate(template, topLevel, rcat, nrefs, nints, nlongs);
            }
        }
    }
    static void expandTemplate(String[] template, boolean topLevel,
                               int rcat, int nrefs, int nints, int nlongs) {
        int nargs = nrefs + nints + nlongs;
        if (nrefs > MAX_REFS || nints > MAX_INTS || nlongs > MAX_LONGS)  return;
        VAR.makeBindings(topLevel, rcat, nrefs, nints, nlongs);
        if (topLevel && !done.add(VAR.cat.binding)) {
            System.out.println("    //repeat "+VAR.cat.binding);
            return;
        }
        for (int i = 0; i < template.length; i++) {
            String line = template[i];
            if (line.endsWith("@each-cat@")) {
                // ignore
            } else if (line.endsWith("@each-R@")) {
                int blockEnd = indexAfter(template, i, "@end-R@");
                String[] block = stringsIn(template, i+1, blockEnd-1);
                for (int rcat1 = rcat; rcat1 <= MAX_RCAT; rcat1++)
                    expandTemplate(block, false, rcat1, nrefs, nints, nlongs);
                VAR.makeBindings(topLevel, rcat, nrefs, nints, nlongs);
                i = blockEnd-1; continue;
            } else if (line.endsWith("@each-Tv@")) {
                int blockEnd = indexAfter(template, i, "@end-Tv@");
                String[] block = stringsIn(template, i+1, blockEnd-1);
                expandTemplate(block, false, rcat, nrefs, nints, nlongs);
                expandTemplateForPrims(block, false, rcat, nargs, nints+1, nlongs+1);
                VAR.makeBindings(topLevel, rcat, nrefs, nints, nlongs);
                i = blockEnd-1; continue;
            } else {
                System.out.println(VAR.transform(line));
            }
        }
    }
}
//}}} */
//params=[0, 3, 4, 99, 99, 99]
    static class A0 extends Adapter {
        protected A0(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A0(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A0 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A0(e, i, c, t); }
        protected Object target()   throws Throwable { return invoker.invokeExact(target); }
        protected Object targetA0() throws Throwable { return target(); }
        protected Object invoke_L() throws Throwable { return return_L(targetA0()); }
        protected int    invoke_I() throws Throwable { return return_I(targetA0()); }
        protected long   invoke_J() throws Throwable { return return_J(targetA0()); }
        protected float  invoke_F() throws Throwable { return return_F(targetA0()); }
        protected double invoke_D() throws Throwable { return return_D(targetA0()); }
    }
    static class A1 extends Adapter {
        protected A1(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A1(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A1 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A1(e, i, c, t); }
        protected Object target(Object a0)   throws Throwable { return invoker.invokeExact(target, a0); }
        protected Object targetA1(Object a0) throws Throwable { return target(a0); }
        protected Object targetA1(int    a0) throws Throwable { return target(a0); }
        protected Object targetA1(long   a0) throws Throwable { return target(a0); }
        protected Object invoke_L(Object a0) throws Throwable { return return_L(targetA1(a0)); }
        protected int    invoke_I(Object a0) throws Throwable { return return_I(targetA1(a0)); }
        protected long   invoke_J(Object a0) throws Throwable { return return_J(targetA1(a0)); }
        protected float  invoke_F(Object a0) throws Throwable { return return_F(targetA1(a0)); }
        protected double invoke_D(Object a0) throws Throwable { return return_D(targetA1(a0)); }
        protected Object invoke_L(int    a0) throws Throwable { return return_L(targetA1(a0)); }
        protected int    invoke_I(int    a0) throws Throwable { return return_I(targetA1(a0)); }
        protected long   invoke_J(int    a0) throws Throwable { return return_J(targetA1(a0)); }
        protected float  invoke_F(int    a0) throws Throwable { return return_F(targetA1(a0)); }
        protected double invoke_D(int    a0) throws Throwable { return return_D(targetA1(a0)); }
        protected Object invoke_L(long   a0) throws Throwable { return return_L(targetA1(a0)); }
        protected int    invoke_I(long   a0) throws Throwable { return return_I(targetA1(a0)); }
        protected long   invoke_J(long   a0) throws Throwable { return return_J(targetA1(a0)); }
        protected float  invoke_F(long   a0) throws Throwable { return return_F(targetA1(a0)); }
        protected double invoke_D(long   a0) throws Throwable { return return_D(targetA1(a0)); }
    }
    static class A2 extends Adapter {
        protected A2(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A2(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A2 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A2(e, i, c, t); }
        protected Object target(Object a0, Object a1)   throws Throwable { return invoker.invokeExact(target, a0, a1); }
        protected Object targetA2(Object a0, Object a1) throws Throwable { return target(a0, a1); }
        protected Object targetA2(Object a0, int    a1) throws Throwable { return target(a0, a1); }
        protected Object targetA2(int    a0, int    a1) throws Throwable { return target(a0, a1); }
        protected Object targetA2(Object a0, long   a1) throws Throwable { return target(a0, a1); }
        protected Object targetA2(long   a0, long   a1) throws Throwable { return target(a0, a1); }
        protected Object invoke_L(Object a0, Object a1) throws Throwable { return return_L(targetA2(a0, a1)); }
        protected int    invoke_I(Object a0, Object a1) throws Throwable { return return_I(targetA2(a0, a1)); }
        protected long   invoke_J(Object a0, Object a1) throws Throwable { return return_J(targetA2(a0, a1)); }
        protected float  invoke_F(Object a0, Object a1) throws Throwable { return return_F(targetA2(a0, a1)); }
        protected double invoke_D(Object a0, Object a1) throws Throwable { return return_D(targetA2(a0, a1)); }
        protected Object invoke_L(Object a0, int    a1) throws Throwable { return return_L(targetA2(a0, a1)); }
        protected int    invoke_I(Object a0, int    a1) throws Throwable { return return_I(targetA2(a0, a1)); }
        protected long   invoke_J(Object a0, int    a1) throws Throwable { return return_J(targetA2(a0, a1)); }
        protected float  invoke_F(Object a0, int    a1) throws Throwable { return return_F(targetA2(a0, a1)); }
        protected double invoke_D(Object a0, int    a1) throws Throwable { return return_D(targetA2(a0, a1)); }
        protected Object invoke_L(int    a0, int    a1) throws Throwable { return return_L(targetA2(a0, a1)); }
        protected int    invoke_I(int    a0, int    a1) throws Throwable { return return_I(targetA2(a0, a1)); }
        protected long   invoke_J(int    a0, int    a1) throws Throwable { return return_J(targetA2(a0, a1)); }
        protected float  invoke_F(int    a0, int    a1) throws Throwable { return return_F(targetA2(a0, a1)); }
        protected double invoke_D(int    a0, int    a1) throws Throwable { return return_D(targetA2(a0, a1)); }
        protected Object invoke_L(Object a0, long   a1) throws Throwable { return return_L(targetA2(a0, a1)); }
        protected int    invoke_I(Object a0, long   a1) throws Throwable { return return_I(targetA2(a0, a1)); }
        protected long   invoke_J(Object a0, long   a1) throws Throwable { return return_J(targetA2(a0, a1)); }
        protected float  invoke_F(Object a0, long   a1) throws Throwable { return return_F(targetA2(a0, a1)); }
        protected double invoke_D(Object a0, long   a1) throws Throwable { return return_D(targetA2(a0, a1)); }
        protected Object invoke_L(long   a0, long   a1) throws Throwable { return return_L(targetA2(a0, a1)); }
        protected int    invoke_I(long   a0, long   a1) throws Throwable { return return_I(targetA2(a0, a1)); }
        protected long   invoke_J(long   a0, long   a1) throws Throwable { return return_J(targetA2(a0, a1)); }
        protected float  invoke_F(long   a0, long   a1) throws Throwable { return return_F(targetA2(a0, a1)); }
        protected double invoke_D(long   a0, long   a1) throws Throwable { return return_D(targetA2(a0, a1)); }
    }
    static class A3 extends Adapter {
        protected A3(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A3(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A3 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A3(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2); }
        protected Object targetA3(Object a0, Object a1, Object a2) throws Throwable { return target(a0, a1, a2); }
        protected Object targetA3(Object a0, Object a1, int    a2) throws Throwable { return target(a0, a1, a2); }
        protected Object targetA3(Object a0, int    a1, int    a2) throws Throwable { return target(a0, a1, a2); }
        protected Object targetA3(int    a0, int    a1, int    a2) throws Throwable { return target(a0, a1, a2); }
        protected Object targetA3(Object a0, Object a1, long   a2) throws Throwable { return target(a0, a1, a2); }
        protected Object targetA3(Object a0, long   a1, long   a2) throws Throwable { return target(a0, a1, a2); }
        protected Object targetA3(long   a0, long   a1, long   a2) throws Throwable { return target(a0, a1, a2); }
        protected Object invoke_L(Object a0, Object a1, Object a2) throws Throwable { return return_L(targetA3(a0, a1, a2)); }
        protected int    invoke_I(Object a0, Object a1, Object a2) throws Throwable { return return_I(targetA3(a0, a1, a2)); }
        protected long   invoke_J(Object a0, Object a1, Object a2) throws Throwable { return return_J(targetA3(a0, a1, a2)); }
        protected float  invoke_F(Object a0, Object a1, Object a2) throws Throwable { return return_F(targetA3(a0, a1, a2)); }
        protected double invoke_D(Object a0, Object a1, Object a2) throws Throwable { return return_D(targetA3(a0, a1, a2)); }
        protected Object invoke_L(Object a0, Object a1, int    a2) throws Throwable { return return_L(targetA3(a0, a1, a2)); }
        protected int    invoke_I(Object a0, Object a1, int    a2) throws Throwable { return return_I(targetA3(a0, a1, a2)); }
        protected long   invoke_J(Object a0, Object a1, int    a2) throws Throwable { return return_J(targetA3(a0, a1, a2)); }
        protected float  invoke_F(Object a0, Object a1, int    a2) throws Throwable { return return_F(targetA3(a0, a1, a2)); }
        protected double invoke_D(Object a0, Object a1, int    a2) throws Throwable { return return_D(targetA3(a0, a1, a2)); }
        protected Object invoke_L(Object a0, int    a1, int    a2) throws Throwable { return return_L(targetA3(a0, a1, a2)); }
        protected int    invoke_I(Object a0, int    a1, int    a2) throws Throwable { return return_I(targetA3(a0, a1, a2)); }
        protected long   invoke_J(Object a0, int    a1, int    a2) throws Throwable { return return_J(targetA3(a0, a1, a2)); }
        protected float  invoke_F(Object a0, int    a1, int    a2) throws Throwable { return return_F(targetA3(a0, a1, a2)); }
        protected double invoke_D(Object a0, int    a1, int    a2) throws Throwable { return return_D(targetA3(a0, a1, a2)); }
        protected Object invoke_L(int    a0, int    a1, int    a2) throws Throwable { return return_L(targetA3(a0, a1, a2)); }
        protected int    invoke_I(int    a0, int    a1, int    a2) throws Throwable { return return_I(targetA3(a0, a1, a2)); }
        protected long   invoke_J(int    a0, int    a1, int    a2) throws Throwable { return return_J(targetA3(a0, a1, a2)); }
        protected float  invoke_F(int    a0, int    a1, int    a2) throws Throwable { return return_F(targetA3(a0, a1, a2)); }
        protected double invoke_D(int    a0, int    a1, int    a2) throws Throwable { return return_D(targetA3(a0, a1, a2)); }
        protected Object invoke_L(Object a0, Object a1, long   a2) throws Throwable { return return_L(targetA3(a0, a1, a2)); }
        protected int    invoke_I(Object a0, Object a1, long   a2) throws Throwable { return return_I(targetA3(a0, a1, a2)); }
        protected long   invoke_J(Object a0, Object a1, long   a2) throws Throwable { return return_J(targetA3(a0, a1, a2)); }
        protected float  invoke_F(Object a0, Object a1, long   a2) throws Throwable { return return_F(targetA3(a0, a1, a2)); }
        protected double invoke_D(Object a0, Object a1, long   a2) throws Throwable { return return_D(targetA3(a0, a1, a2)); }
        protected Object invoke_L(Object a0, long   a1, long   a2) throws Throwable { return return_L(targetA3(a0, a1, a2)); }
        protected int    invoke_I(Object a0, long   a1, long   a2) throws Throwable { return return_I(targetA3(a0, a1, a2)); }
        protected long   invoke_J(Object a0, long   a1, long   a2) throws Throwable { return return_J(targetA3(a0, a1, a2)); }
        protected float  invoke_F(Object a0, long   a1, long   a2) throws Throwable { return return_F(targetA3(a0, a1, a2)); }
        protected double invoke_D(Object a0, long   a1, long   a2) throws Throwable { return return_D(targetA3(a0, a1, a2)); }
        protected Object invoke_L(long   a0, long   a1, long   a2) throws Throwable { return return_L(targetA3(a0, a1, a2)); }
        protected int    invoke_I(long   a0, long   a1, long   a2) throws Throwable { return return_I(targetA3(a0, a1, a2)); }
        protected long   invoke_J(long   a0, long   a1, long   a2) throws Throwable { return return_J(targetA3(a0, a1, a2)); }
        protected float  invoke_F(long   a0, long   a1, long   a2) throws Throwable { return return_F(targetA3(a0, a1, a2)); }
        protected double invoke_D(long   a0, long   a1, long   a2) throws Throwable { return return_D(targetA3(a0, a1, a2)); }
    }
//params=[4, 5, 2, 99, 99, 99]
    static class A4 extends Adapter {
        protected A4(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A4(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A4 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A4(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2, Object a3)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3); }
        protected Object targetA4(Object a0, Object a1, Object a2, Object a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(Object a0, Object a1, Object a2, int    a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(Object a0, Object a1, int    a2, int    a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(Object a0, int    a1, int    a2, int    a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(int    a0, int    a1, int    a2, int    a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(Object a0, Object a1, Object a2, long   a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(Object a0, Object a1, long   a2, long   a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(Object a0, long   a1, long   a2, long   a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object targetA4(long   a0, long   a1, long   a2, long   a3) throws Throwable { return target(a0, a1, a2, a3); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, int    a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, int    a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, int    a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(Object a0, Object a1, int    a2, int    a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(Object a0, Object a1, int    a2, int    a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(Object a0, Object a1, int    a2, int    a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(Object a0, int    a1, int    a2, int    a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(Object a0, int    a1, int    a2, int    a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(Object a0, int    a1, int    a2, int    a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(int    a0, int    a1, int    a2, int    a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(int    a0, int    a1, int    a2, int    a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(int    a0, int    a1, int    a2, int    a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, long   a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, long   a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, long   a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(Object a0, Object a1, long   a2, long   a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(Object a0, Object a1, long   a2, long   a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(Object a0, Object a1, long   a2, long   a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(Object a0, long   a1, long   a2, long   a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(Object a0, long   a1, long   a2, long   a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(Object a0, long   a1, long   a2, long   a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
        protected Object invoke_L(long   a0, long   a1, long   a2, long   a3) throws Throwable { return return_L(targetA4(a0, a1, a2, a3)); }
        protected int    invoke_I(long   a0, long   a1, long   a2, long   a3) throws Throwable { return return_I(targetA4(a0, a1, a2, a3)); }
        protected long   invoke_J(long   a0, long   a1, long   a2, long   a3) throws Throwable { return return_J(targetA4(a0, a1, a2, a3)); }
    }
    static class A5 extends Adapter {
        protected A5(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A5(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A5 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A5(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2, Object a3, Object a4)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, Object a1, Object a2, Object a3, int    a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, Object a1, Object a2, int    a3, int    a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, Object a1, int    a2, int    a3, int    a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(int    a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, Object a1, Object a2, Object a3, long   a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, Object a1, Object a2, long   a3, long   a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, Object a1, long   a2, long   a3, long   a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(Object a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object targetA5(long   a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return target(a0, a1, a2, a3, a4); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, int    a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, int    a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, int    a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, int    a3, int    a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, int    a3, int    a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, int    a3, int    a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, Object a1, int    a2, int    a3, int    a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, Object a1, int    a2, int    a3, int    a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, Object a1, int    a2, int    a3, int    a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(int    a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(int    a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(int    a0, int    a1, int    a2, int    a3, int    a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, long   a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, long   a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, long   a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, long   a3, long   a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, long   a3, long   a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, long   a3, long   a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, Object a1, long   a2, long   a3, long   a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, Object a1, long   a2, long   a3, long   a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, Object a1, long   a2, long   a3, long   a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(Object a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(Object a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(Object a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
        protected Object invoke_L(long   a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return return_L(targetA5(a0, a1, a2, a3, a4)); }
        protected int    invoke_I(long   a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return return_I(targetA5(a0, a1, a2, a3, a4)); }
        protected long   invoke_J(long   a0, long   a1, long   a2, long   a3, long   a4) throws Throwable { return return_J(targetA5(a0, a1, a2, a3, a4)); }
    }
//params=[6, 10, 2, 99, 0, 99]
    static class A6 extends Adapter {
        protected A6(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A6(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A6 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A6(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3, a4, a5); }
        protected Object targetA6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return target(a0, a1, a2, a3, a4, a5); }
        protected Object targetA6(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5) throws Throwable { return target(a0, a1, a2, a3, a4, a5); }
        protected Object targetA6(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5) throws Throwable { return target(a0, a1, a2, a3, a4, a5); }
        protected Object targetA6(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5) throws Throwable { return target(a0, a1, a2, a3, a4, a5); }
        protected Object targetA6(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return target(a0, a1, a2, a3, a4, a5); }
        protected Object targetA6(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return target(a0, a1, a2, a3, a4, a5); }
        protected Object targetA6(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return target(a0, a1, a2, a3, a4, a5); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return return_L(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return return_I(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return return_J(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5) throws Throwable { return return_L(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5) throws Throwable { return return_I(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5) throws Throwable { return return_J(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5) throws Throwable { return return_L(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5) throws Throwable { return return_I(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5) throws Throwable { return return_J(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5) throws Throwable { return return_L(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5) throws Throwable { return return_I(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5) throws Throwable { return return_J(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_L(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_L(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected int    invoke_I(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_I(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected long   invoke_J(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_J(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_L(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_L(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected int    invoke_I(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_I(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected long   invoke_J(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_J(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_L(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_L(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected int    invoke_I(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_I(targetA6(a0, a1, a2, a3, a4, a5)); }
        protected long   invoke_J(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5) throws Throwable { return return_J(targetA6(a0, a1, a2, a3, a4, a5)); }
    }
    static class A7 extends Adapter {
        protected A7(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A7(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A7 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A7(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object targetA7(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_L(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_L(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_L(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_L(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected int    invoke_I(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_I(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
        protected long   invoke_J(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6) throws Throwable { return return_J(targetA7(a0, a1, a2, a3, a4, a5, a6)); }
    }
    static class A8 extends Adapter {
        protected A8(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A8(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A8 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A8(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object targetA8(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_L(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_L(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected int    invoke_I(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_I(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected long   invoke_J(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7) throws Throwable { return return_J(targetA8(a0, a1, a2, a3, a4, a5, a6, a7)); }
    }
    static class A9 extends Adapter {
        protected A9(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A9(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A9 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A9(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object targetA9(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_L(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_L(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected int    invoke_I(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_I(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected long   invoke_J(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8) throws Throwable { return return_J(targetA9(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
    }
    static class A10 extends Adapter {
        protected A10(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A10(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { super(e, i, c, t); }
        protected A10 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t) { return new A10(e, i, c, t); }
        protected Object target(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9)   throws Throwable { return invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object targetA10(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return target(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, Object a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, Object a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, Object a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, Object a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(Object a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_L(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_L(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected int    invoke_I(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_I(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected long   invoke_J(long   a0, long   a1, long   a2, long   a3, long   a4, long   a5, long   a6, long   a7, long   a8, long   a9) throws Throwable { return return_J(targetA10(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
    }
}
