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
import java.lang.reflect.*;
import sun.dyn.util.*;
import static sun.dyn.MethodTypeImpl.invokers;

/**
 * Adapters which mediate between incoming calls which are generic
 * and outgoing calls which are not.  Any call can be represented generically
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
class FromGeneric {
    // type for the outgoing call (may have primitives, etc.)
    private final MethodType targetType;
    // type of the outgoing call internal to the adapter
    private final MethodType internalType;
    // prototype adapter (clone and customize for each new target!)
    private final Adapter adapter;
    // entry point for adapter (Adapter mh, a...) => ...
    private final MethodHandle entryPoint;
    // unboxing invoker of type (MH, Object**N) => raw return value
    // it makes up the difference of internalType => targetType
    private final MethodHandle unboxingInvoker;
    // conversion which boxes a the target's raw return value
    private final MethodHandle returnConversion;

    /** Compute and cache information common to all unboxing adapters
     *  that can call out to targets of the erasure-family of the given erased type.
     */
    private FromGeneric(MethodType targetType) {
        this.targetType = targetType;
        MethodType internalType0;
        // the target invoker will generally need casts on reference arguments
        Adapter ad = findAdapter(internalType0 = targetType.erase());
        if (ad != null) {
            // Immediate hit to exactly the adapter we want,
            // with no monkeying around with primitive types.
            this.internalType = internalType0;
            this.adapter = ad;
            this.entryPoint = ad.prototypeEntryPoint();
            this.returnConversion = computeReturnConversion(targetType, internalType0);
            this.unboxingInvoker = computeUnboxingInvoker(targetType, internalType0);
            return;
        }

        // outgoing primitive arguments will be wrapped; unwrap them
        MethodType primsAsObj = MethodTypeImpl.of(targetType).primArgsAsBoxes();
        MethodType objArgsRawRet = MethodTypeImpl.of(primsAsObj).primsAsInts();
        if (objArgsRawRet != targetType)
            ad = findAdapter(internalType0 = objArgsRawRet);
        if (ad == null) {
            ad = buildAdapterFromBytecodes(internalType0 = targetType);
        }
        this.internalType = internalType0;
        this.adapter = ad;
        MethodType tepType = targetType.insertParameterTypes(0, adapter.getClass());
        this.entryPoint = ad.prototypeEntryPoint();
        this.returnConversion = computeReturnConversion(targetType, internalType0);
        this.unboxingInvoker = computeUnboxingInvoker(targetType, internalType0);
    }

    /**
     * The typed target will be called according to targetType.
     * The adapter code will in fact see the raw result from internalType,
     * and must box it into an object.  Produce a converter for this.
     */
    private static MethodHandle computeReturnConversion(
            MethodType targetType, MethodType internalType) {
        Class<?> tret = targetType.returnType();
        Class<?> iret = internalType.returnType();
        Wrapper wrap = Wrapper.forBasicType(tret);
        if (!iret.isPrimitive()) {
            assert(iret == Object.class);
            return ValueConversions.identity();
        } else if (wrap.primitiveType() == iret) {
            return ValueConversions.box(wrap, false);
        } else {
            assert(tret == double.class ? iret == long.class : iret == int.class);
            return ValueConversions.boxRaw(wrap, false);
        }
    }

    /**
     * The typed target will need an exact invocation point; provide it here.
     * The adapter will possibly need to make a slightly different call,
     * so adapt the invoker.  This way, the logic for making up the
     * difference between what the adapter can call and what the target
     * needs can be cached once per type.
     */
    private static MethodHandle computeUnboxingInvoker(
            MethodType targetType, MethodType internalType) {
        // All the adapters we have here have reference-untyped internal calls.
        assert(internalType == internalType.erase());
        MethodHandle invoker = invokers(targetType).exactInvoker();
        // cast all narrow reference types, unbox all primitive arguments:
        MethodType fixArgsType = internalType.changeReturnType(targetType.returnType());
        MethodHandle fixArgs = AdapterMethodHandle.convertArguments(Access.TOKEN,
                                 invoker, Invokers.invokerType(fixArgsType),
                                 invoker.type(), null);
        if (fixArgs == null)
            throw new InternalError("bad fixArgs");
        // reinterpret the calling sequence as raw:
        MethodHandle retyper = AdapterMethodHandle.makeRetypeRaw(Access.TOKEN,
                                        Invokers.invokerType(internalType), fixArgs);
        if (retyper == null)
            throw new InternalError("bad retyper");
        return retyper;
    }

    Adapter makeInstance(MethodHandle typedTarget) {
        MethodType type = typedTarget.type();
        if (type == targetType) {
            return adapter.makeInstance(entryPoint, unboxingInvoker, returnConversion, typedTarget);
        }
        // my erased-type is not exactly the same as the desired type
        assert(type.erase() == targetType);  // else we are busted
        MethodHandle invoker = computeUnboxingInvoker(type, internalType);
        return adapter.makeInstance(entryPoint, invoker, returnConversion, typedTarget);
    }

    /** Build an adapter of the given generic type, which invokes typedTarget
     *  on the incoming arguments, after unboxing as necessary.
     *  The return value is boxed if necessary.
     * @param genericType  the required type of the result
     * @param typedTarget the target
     * @return an adapter method handle
     */
    public static MethodHandle make(MethodHandle typedTarget) {
        MethodType type = typedTarget.type();
        if (type == type.generic())  return typedTarget;
        return FromGeneric.of(type).makeInstance(typedTarget);
    }

    /** Return the adapter information for this type's erasure. */
    static FromGeneric of(MethodType type) {
        MethodTypeImpl form = MethodTypeImpl.of(type);
        FromGeneric fromGen = form.fromGeneric;
        if (fromGen == null)
            form.fromGeneric = fromGen = new FromGeneric(form.erasedType());
        return fromGen;
    }

    public String toString() {
        return "FromGeneric"+targetType;
    }

    /* Create an adapter that handles spreading calls for the given type. */
    static Adapter findAdapter(MethodType internalType) {
        MethodType entryType = internalType.generic();
        MethodTypeImpl form = MethodTypeImpl.of(internalType);
        Class<?> rtype = internalType.returnType();
        int argc = form.parameterCount();
        int lac = form.longPrimitiveParameterCount();
        int iac = form.primitiveParameterCount() - lac;
        String intsAndLongs = (iac > 0 ? "I"+iac : "")+(lac > 0 ? "J"+lac : "");
        String rawReturn = String.valueOf(Wrapper.forPrimitiveType(rtype).basicTypeChar());
        String cname0 = rawReturn + argc;
        String cname1 = "A"       + argc;
        String[] cnames = { cname0+intsAndLongs, cname0, cname1+intsAndLongs, cname1 };
        String iname = "invoke_"+cname0+intsAndLongs;
        // e.g., D5I2, D5, L5I2, L5; invoke_D5
        for (String cname : cnames) {
            Class<? extends Adapter> acls = Adapter.findSubClass(cname);
            if (acls == null)  continue;
            // see if it has the required invoke method
            MethodHandle entryPoint = null;
            try {
                entryPoint = MethodHandleImpl.IMPL_LOOKUP.findSpecial(acls, iname, entryType, acls);
            } catch (ReflectiveOperationException ex) {
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
            } catch (InvocationTargetException wex) {
                Throwable ex = wex.getTargetException();
                if (ex instanceof Error)  throw (Error)ex;
                if (ex instanceof RuntimeException)  throw (RuntimeException)ex;
            } catch (InstantiationException ex) {
            } catch (IllegalAccessException ex) {
            }
        }
        return null;
    }

    static Adapter buildAdapterFromBytecodes(MethodType internalType) {
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
    static abstract class Adapter extends BoundMethodHandle {
        /*
         * class X<<R,int N>> extends Adapter {
         *   (MH, Object**N)=>raw(R) invoker;
         *   (any**N)=>R target;
         *   raw(R)=>Object convert;
         *   Object invoke(Object**N a) = convert(invoker(target, a...))
         * }
         */
        protected final MethodHandle invoker;  // (MH, Object**N) => raw(R)
        protected final MethodHandle convert;  // raw(R) => Object
        protected final MethodHandle target;   // (any**N) => R

        @Override
        public String toString() {
            return MethodHandleImpl.addTypeString(target, this);
        }

        protected boolean isPrototype() { return target == null; }
        protected Adapter(MethodHandle entryPoint) {
            this(entryPoint, null, entryPoint, null);
            assert(isPrototype());
        }
        protected MethodHandle prototypeEntryPoint() {
            if (!isPrototype())  throw new InternalError();
            return convert;
        }

        protected Adapter(MethodHandle entryPoint,
                          MethodHandle invoker, MethodHandle convert, MethodHandle target) {
            super(Access.TOKEN, entryPoint);
            this.invoker = invoker;
            this.convert = convert;
            this.target  = target;
        }

        /** Make a copy of self, with new fields. */
        protected abstract Adapter makeInstance(MethodHandle entryPoint,
                MethodHandle invoker, MethodHandle convert, MethodHandle target);
        // { return new ThisType(entryPoint, convert, target); }

        /// Conversions on the value returned from the target.
        protected Object convert_L(Object result) throws Throwable { return convert.invokeExact(result); }
        protected Object convert_I(int    result) throws Throwable { return convert.invokeExact(result); }
        protected Object convert_J(long   result) throws Throwable { return convert.invokeExact(result); }
        protected Object convert_F(float  result) throws Throwable { return convert.invokeExact(result); }
        protected Object convert_D(double result) throws Throwable { return convert.invokeExact(result); }

        static private final String CLASS_PREFIX; // "sun.dyn.FromGeneric$"
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
    static class xA2 extends Adapter {
        protected xA2(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected xA2(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected xA2 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new xA2(e, i, c, t); }
        protected Object invoke_L2(Object a0, Object a1) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_I2(Object a0, Object a1) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_J2(Object a0, Object a1) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_F2(Object a0, Object a1) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_D2(Object a0, Object a1) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1)); }
    }
    // */

/*
: SHELL; n=FromGeneric; cp -p $n.java $n.java-; sed < $n.java- > $n.java+ -e '/{{*{{/,/}}*}}/w /tmp/genclasses.java' -e '/}}*}}/q'; (cd /tmp; javac -d . genclasses.java; java -cp . genclasses) >> $n.java+; echo '}' >> $n.java+; mv $n.java+ $n.java; mv $n.java- $n.java~
//{{{
import java.util.*;
class genclasses {
    static String[] TYPES = { "Object",    "int   ",    "long  ",    "float ",    "double" };
    static String[] WRAPS = { "         ", "(Integer)", "(Long)   ", "(Float)  ", "(Double) " };
    static String[] TCHARS = { "L",     "I",      "J",      "F",      "D",     "A" };
    static String[][] TEMPLATES = { {
        "@for@ arity=0..10  rcat<=4 nrefs<=99 nints=0   nlongs=0",
        "    //@each-cat@",
        "    static class @cat@ extends Adapter {",
        "        protected @cat@(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype",
        "        protected @cat@(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)",
        "                        { super(e, i, c, t); }",
        "        protected @cat@ makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)",
        "                        { return new @cat@(e, i, c, t); }",
        "        //@each-R@",
        "        protected Object invoke_@catN@(@Tvav@) throws Throwable { return convert_@Rc@((@R@)@W@invoker.invokeExact(target@av@)); }",
        "        //@end-R@",
        "    }",
    } };
    static final String NEWLINE_INDENT = "\n                ";
    enum VAR {
        cat, catN, R, Rc, W, av, Tvav, Ovav;
        public final String pattern = "@"+toString().replace('_','.')+"@";
        public String binding;
        static void makeBindings(boolean topLevel, int rcat, int nrefs, int nints, int nlongs) {
            int nargs = nrefs + nints + nlongs;
            if (topLevel)
                VAR.cat.binding = catstr(ALL_RETURN_TYPES ? TYPES.length : rcat, nrefs, nints, nlongs);
            VAR.catN.binding = catstr(rcat, nrefs, nints, nlongs);
            VAR.R.binding = TYPES[rcat];
            VAR.Rc.binding = TCHARS[rcat];
            VAR.W.binding = WRAPS[rcat];
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
            VAR.av.binding = comma(", ", av);
            VAR.Tvav.binding = comma(Tvav);
            VAR.Ovav.binding = comma(Ovav);
        }
        static String arg(int i) { return "a"+i; }
        static String param(String t, String a) { return t+" "+a; }
        static String comma(String[] v) { return comma("", v); }
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
//params=[0, 10, 4, 99, 0, 0]
    static class A0 extends Adapter {
        protected A0(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A0(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A0 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A0(e, i, c, t); }
        protected Object invoke_L0() throws Throwable { return convert_L((Object)invoker.invokeExact(target)); }
        protected Object invoke_I0() throws Throwable { return convert_I((int)   invoker.invokeExact(target)); }
        protected Object invoke_J0() throws Throwable { return convert_J((long)  invoker.invokeExact(target)); }
        protected Object invoke_F0() throws Throwable { return convert_F((float) invoker.invokeExact(target)); }
        protected Object invoke_D0() throws Throwable { return convert_D((double)invoker.invokeExact(target)); }
    }
    static class A1 extends Adapter {
        protected A1(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A1(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A1 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A1(e, i, c, t); }
        protected Object invoke_L1(Object a0) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0)); }
        protected Object invoke_I1(Object a0) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0)); }
        protected Object invoke_J1(Object a0) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0)); }
        protected Object invoke_F1(Object a0) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0)); }
        protected Object invoke_D1(Object a0) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0)); }
    }
    static class A2 extends Adapter {
        protected A2(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A2(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A2 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A2(e, i, c, t); }
        protected Object invoke_L2(Object a0, Object a1) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_I2(Object a0, Object a1) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_J2(Object a0, Object a1) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_F2(Object a0, Object a1) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1)); }
        protected Object invoke_D2(Object a0, Object a1) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1)); }
    }
    static class A3 extends Adapter {
        protected A3(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A3(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A3 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A3(e, i, c, t); }
        protected Object invoke_L3(Object a0, Object a1, Object a2) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2)); }
        protected Object invoke_I3(Object a0, Object a1, Object a2) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2)); }
        protected Object invoke_J3(Object a0, Object a1, Object a2) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2)); }
        protected Object invoke_F3(Object a0, Object a1, Object a2) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2)); }
        protected Object invoke_D3(Object a0, Object a1, Object a2) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2)); }
    }
    static class A4 extends Adapter {
        protected A4(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A4(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A4 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A4(e, i, c, t); }
        protected Object invoke_L4(Object a0, Object a1, Object a2, Object a3) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2, a3)); }
        protected Object invoke_I4(Object a0, Object a1, Object a2, Object a3) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2, a3)); }
        protected Object invoke_J4(Object a0, Object a1, Object a2, Object a3) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2, a3)); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2, a3)); }
        protected Object invoke_D4(Object a0, Object a1, Object a2, Object a3) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2, a3)); }
    }
    static class A5 extends Adapter {
        protected A5(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A5(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A5 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A5(e, i, c, t); }
        protected Object invoke_L5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2, a3, a4)); }
        protected Object invoke_I5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2, a3, a4)); }
        protected Object invoke_J5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2, a3, a4)); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2, a3, a4)); }
        protected Object invoke_D5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2, a3, a4)); }
    }
    static class A6 extends Adapter {
        protected A6(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A6(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A6 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A6(e, i, c, t); }
        protected Object invoke_L6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_I6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_J6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_D6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5)); }
    }
    static class A7 extends Adapter {
        protected A7(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A7(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A7 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A7(e, i, c, t); }
        protected Object invoke_L7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_I7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_J7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_D7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6)); }
    }
    static class A8 extends Adapter {
        protected A8(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A8(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A8 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A8(e, i, c, t); }
        protected Object invoke_L8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_I8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_J8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_D8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7)); }
    }
    static class A9 extends Adapter {
        protected A9(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A9(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A9 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A9(e, i, c, t); }
        protected Object invoke_L9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_I9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_J9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_D9(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
    }
    static class A10 extends Adapter {
        protected A10(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected A10(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { super(e, i, c, t); }
        protected A10 makeInstance(MethodHandle e, MethodHandle i, MethodHandle c, MethodHandle t)
                        { return new A10(e, i, c, t); }
        protected Object invoke_L10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return convert_L((Object)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_I10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return convert_I((int)   invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_J10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return convert_J((long)  invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return convert_F((float) invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_D10(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9) throws Throwable { return convert_D((double)invoker.invokeExact(target, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
    }
}
