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

package java.lang.invoke;

import java.lang.reflect.*;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * These adapters apply arbitrary conversions to arguments
 * on the way to a ultimate target.
 * For simplicity, these are all generically typed.
 * @author jrose
 */
class FilterGeneric {
    // type for the incoming call (will be generic)
    private final MethodType entryType;
    // prototype adapters (clone and customize for each new target & conversion!)
    private final Adapter[] adapters;

    /** Compute and cache information common to all filtering adapters
     *  with the given generic type
     */
    FilterGeneric(MethodType entryType) {
        this.entryType = entryType;
        int tableSize = Kind.LIMIT.invokerIndex(1 + entryType.parameterCount());
        this.adapters = new Adapter[tableSize];
    }

    Adapter getAdapter(Kind kind, int pos) {
        int index = kind.invokerIndex(pos);
        Adapter ad = adapters[index];
        if (ad != null)  return ad;
        ad = findAdapter(entryType, kind, pos);
        if (ad == null)
            ad = buildAdapterFromBytecodes(entryType, kind, pos);
        adapters[index] = ad;
        return ad;
    }

    static {
        assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this class is deprecated
    }

    Adapter makeInstance(Kind kind, int pos, MethodHandle filter, MethodHandle target) {
        Adapter ad = getAdapter(kind, pos);
        return ad.makeInstance(ad.prototypeEntryPoint(), filter, target);
    }

    /** Build an adapter of the given generic type, which invokes filter
     *  on the selected incoming argument before passing it to the target.
     * @param pos the argument to filter
     * @param filter the function to call on the argument
     * @param target the target to call with the modified argument list
     * @return an adapter method handle
     */
    public static MethodHandle makeArgumentFilter(int pos, MethodHandle filter, MethodHandle target) {
        return make(Kind.value, pos, filter, target);
    }

    /** Build an adapter of the given generic type, which invokes a combiner
     *  on a selected group of leading arguments.
     *  The result of the combiner is prepended before all those arguments.
     * @param combiner the function to call on the selected leading arguments
     * @param target the target to call with the modified argument list
     * @return an adapter method handle
     */
    public static MethodHandle makeArgumentFolder(MethodHandle combiner, MethodHandle target) {
        int num = combiner.type().parameterCount();
        return make(Kind.fold, num, combiner, target);
    }

    /** Build an adapter of the given generic type, which invokes a filter
     *  on the incoming arguments, reified as a group.
     *  The argument may be modified (by side effects in the filter).
     *  The arguments, possibly modified, are passed on to the target.
     * @param filter the function to call on the arguments
     * @param target the target to call with the possibly-modified argument list
     * @return an adapter method handle
     */
    public static MethodHandle makeFlyby(MethodHandle filter, MethodHandle target) {
        return make(Kind.flyby, 0, filter, target);
    }

    /** Build an adapter of the given generic type, which invokes a collector
     *  on the selected incoming argument and all following arguments.
     *  The result of the collector replaces all those arguments.
     * @param collector the function to call on the selected trailing arguments
     * @param target the target to call with the modified argument list
     * @return an adapter method handle
     */
    public static MethodHandle makeArgumentCollector(MethodHandle collector, MethodHandle target) {
        int pos = target.type().parameterCount() - 1;
        return make(Kind.collect, pos, collector, target);
    }

    static MethodHandle make(Kind kind, int pos, MethodHandle filter, MethodHandle target) {
        FilterGeneric fgen = of(kind, pos, filter.type(), target.type());
        return fgen.makeInstance(kind, pos, filter, target);
    }

    /** Return the adapter information for this target and filter type. */
    static FilterGeneric of(Kind kind, int pos, MethodType filterType, MethodType targetType) {
        MethodType entryType = entryType(kind, pos, filterType, targetType);
        if (entryType.generic() != entryType)
            throw newIllegalArgumentException("must be generic: "+entryType);
        MethodTypeForm form = entryType.form();
        FilterGeneric filterGen = form.filterGeneric;
        if (filterGen == null)
            form.filterGeneric = filterGen = new FilterGeneric(entryType);
        return filterGen;
    }

    public String toString() {
        return "FilterGeneric/"+entryType;
    }

    static MethodType targetType(MethodType entryType, Kind kind, int pos, MethodType filterType) {
        MethodType type = entryType;
        switch (kind) {
            case value:
            case flyby:
                break;  // no change
            case fold:
                type = type.insertParameterTypes(0, filterType.returnType());
                break;
            case collect:
                type = type.dropParameterTypes(pos, type.parameterCount());
                type = type.insertParameterTypes(pos, filterType.returnType());
                break;
            default:
                throw new InternalError();
        }
        return type;
    }

    static MethodType entryType(Kind kind, int pos, MethodType filterType, MethodType targetType) {
        MethodType type = targetType;
        switch (kind) {
            case value:
            case flyby:
                break;  // no change
            case fold:
                type = type.dropParameterTypes(0, 1);
                break;
            case collect:
                type = type.dropParameterTypes(pos, pos+1);
                type = type.insertParameterTypes(pos, filterType.parameterList());
                break;
            default:
                throw new InternalError();
        }
        return type;
    }

    /* Create an adapter that handles spreading calls for the given type. */
    static Adapter findAdapter(MethodType entryType, Kind kind, int pos) {
        int argc = entryType.parameterCount();
        String cname0 = "F"+argc;
        String cname1 = "F"+argc+kind.key;
        String[] cnames = { cname0, cname1 };
        String iname = kind.invokerName(pos);
        // e.g., F5; invoke_C3
        for (String cname : cnames) {
            Class<? extends Adapter> acls = Adapter.findSubClass(cname);
            if (acls == null)  continue;
            // see if it has the required invoke method
            MethodHandle entryPoint = null;
            try {
                entryPoint = IMPL_LOOKUP.findSpecial(acls, iname, entryType, acls);
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

    static Adapter buildAdapterFromBytecodes(MethodType entryType, Kind kind, int pos) {
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
        protected final MethodHandle filter; // transforms one or more arguments
        protected final MethodHandle target; // ultimate target

        @Override
        String debugString() {
            return addTypeString(target, this);
        }

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

        static private final String CLASS_PREFIX; // "java.lang.invoke.FilterGeneric$"
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

    static enum Kind {
        value('V'),      // filter and replace Nth argument value
        fold('F'),       // fold first N arguments, prepend result
        collect('C'),    // collect last N arguments, replace with result
        flyby('Y'),      // reify entire argument list, filter, pass to target
        LIMIT('?');
        static final int COUNT = LIMIT.ordinal();

        final char key;
        Kind(char key) { this.key = key; }
        String invokerName(int pos) { return "invoke_"+key+""+pos; }
        int invokerIndex(int pos) { return pos * COUNT + ordinal(); }
    }

    /* generated classes follow this pattern:
    static class F1X extends Adapter {
        protected F1X(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F1X(MethodHandle e, MethodHandle f, MethodHandle t)
                        { super(e, f, t); }
        protected F1X makeInstance(MethodHandle e, MethodHandle f, MethodHandle t)
                        { return new F1X(e, f, t); }
        protected Object invoke_V0(Object a0) { return target.invokeExact(filter.invokeExact(a0)); }
        protected Object invoke_F0(Object a0) { return target.invokeExact(filter.invokeExact(), a0); }
        protected Object invoke_F1(Object a0) { return target.invokeExact(filter.invokeExact(a0), a0); }
        protected Object invoke_C0(Object a0) { return target.invokeExact(filter.invokeExact(a0)); }
        protected Object invoke_C1(Object a0) { return target.invokeExact(a0, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0) { Object[] av = { a0 };
                       filter.invokeExact(av); return target.invokeExact(av[0]); }
    }
    static class F2X extends Adapter {
        protected F2X(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F2X(MethodHandle e, MethodHandle f, MethodHandle t)
                        { super(e, f, t); }
        protected F2X makeInstance(MethodHandle e, MethodHandle f, MethodHandle t)
                        { return new F2X(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1) { return target.invokeExact(filter.invokeExact(a0), a1); }
        protected Object invoke_V1(Object a0, Object a1) { return target.invokeExact(a0, filter.invokeExact(a1)); }
        protected Object invoke_F0(Object a0, Object a1) { return target.invokeExact(filter.invokeExact(), a0, a1); }
        protected Object invoke_F1(Object a0, Object a1) { return target.invokeExact(filter.invokeExact(a0), a0, a1); }
        protected Object invoke_F2(Object a0, Object a1) { return target.invokeExact(filter.invokeExact(a0, a1), a0, a1); }
        protected Object invoke_C0(Object a0, Object a1) { return target.invokeExact(filter.invokeExact(a0, a1)); }
        protected Object invoke_C1(Object a0, Object a1) { return target.invokeExact(a0, filter.invokeExact(a1)); }
        protected Object invoke_C2(Object a0, Object a1) { return target.invokeExact(a0, a1, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1) { Object[] av = { a0, a1 };
                       filter.invokeExact(av); return target.invokeExact(av[0], av[1]); }
    }
    // */

    // This one is written by hand:
    static class F0 extends Adapter {
        protected F0(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F0(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F0 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F0(e, f, t); }
        protected Object invoke_F0() throws Throwable {
            return target.invokeExact(filter.invokeExact()); }
        protected Object invoke_C0() throws Throwable {
            return target.invokeExact(filter.invokeExact()); }
        static final Object[] NO_ARGS = { };
        protected Object invoke_Y0() throws Throwable {
            filter.invokeExact(NO_ARGS); // make the flyby
            return target.invokeExact(); }
    }

/*
  : SHELL; n=FilterGeneric; cp -p $n.java $n.java-; sed < $n.java- > $n.java+ -e '/{{*{{/,/}}*}}/w /tmp/genclasses.java' -e '/}}*}}/q'; (cd /tmp; javac -d . genclasses.java; java -ea -cp . genclasses | sed 's| *[/]/ *$||') >> $n.java+; echo '}' >> $n.java+; mv $n.java+ $n.java; mv $n.java- $n.java~
//{{{
import java.util.*;
class genclasses {
    static String[][] TEMPLATES = { {
        "@for@ N=1..20",
        "    //@each-cat@",
        "    static class @cat@ extends Adapter {",
        "        protected @cat@(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype",
        "        protected @cat@(MethodHandle e, MethodHandle f, MethodHandle t) {",
        "            super(e, f, t); }",
        "        protected @cat@ makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {",
        "            return new @cat@(e, f, t); }",
        "        //@each-P@",
        "        protected Object invoke_V@P@(@Tvav@) throws Throwable {",
        "            return target.invokeExact(@a0_@@Psp@filter.invokeExact(a@P@)@_aN@); }",
        "        //@end-P@",
        "        //@each-P@",
        "        protected Object invoke_F@P@(@Tvav@) throws Throwable {",
        "            return target.invokeExact(filter.invokeExact(@a0@),",
        "                                 @av@); }",
        "        //@end-P@",
        "        protected Object invoke_F@N@(@Tvav@) throws Throwable {",
        "            return target.invokeExact(filter.invokeExact(@av@),",
        "                                 @av@); }",
        "        //@each-P@",
        "        protected Object invoke_C@P@(@Tvav@) throws Throwable {",
        "            return target.invokeExact(@a0_@filter.invokeExact(a@P@@_aN@)); }",
        "        //@end-P@",
        "        protected Object invoke_C@N@(@Tvav@) throws Throwable {",
        "            return target.invokeExact(@av@, filter.invokeExact()); }",
        "        protected Object invoke_Y0(@Tvav@) throws Throwable {",
        "            Object[] av = { @av@ };",
        "            filter.invokeExact(av); // make the flyby",
        "            return target.invokeExact(@av[i]@); }",
        "    }",
    } };
    static final String NEWLINE_INDENT = " //\n                                 ";
    enum VAR {
        cat, N, P, Tvav, av, a0, a0_, _aN, Psp, av_i_;
        public final String pattern = "@"+toString().replace('_','.')+"@";
        public String binding = toString();
        static void makeBindings(boolean topLevel, int inargs, int pos) {
            assert(-1 <= pos && pos < inargs);
            VAR.cat.binding = "F"+inargs;
            VAR.N.binding = String.valueOf(inargs); // incoming arg count
            VAR.P.binding = String.valueOf(pos);  // selected arg position
            String[] av = new String[inargs];
            String[] Tvav = new String[inargs];
            String[] av_i_ = new String[inargs];
            for (int i = 0; i < inargs; i++) {
                av[i] = arg(i);
                av_i_[i] = "av["+i+"]";
                String spc = "";
                if (i > 0 && i % 4 == 0) spc = NEWLINE_INDENT+(pos>9?" ":"")+"  ";
                Tvav[i] = spc+param("Object", av[i]);
            }
            VAR.av.binding = comma(av);
            VAR.av_i_.binding = comma(av_i_);
            VAR.Tvav.binding = comma(Tvav);
            if (pos >= 0) {
                VAR.Psp.binding = (pos > 0 && pos % 10 == 0) ? NEWLINE_INDENT : "";
                String[] a0 = new String[pos];
                String[] aN = new String[inargs - (pos+1)];
                for (int i = 0; i < pos; i++) {
                    String spc = "";
                    if (i > 0 && i % 10 == 0) spc = NEWLINE_INDENT;
                    a0[i] = spc+av[i];
                }
                VAR.a0.binding = comma(a0);
                VAR.a0_.binding = comma(a0, ", ");
                for (int i = pos+1; i < inargs; i++) {
                    String spc = "";
                    if (i > 0 && i % 10 == 0) spc = NEWLINE_INDENT;
                    aN[i - (pos+1)] = spc+av[i];
                }
                VAR._aN.binding = comma(", ", aN);
            }
        }
        static String arg(int i) { return "a"+i; }
        static String param(String t, String a) { return t+" "+a; }
        static String comma(String[] v) { return comma(v, ""); }
        static String comma(String[] v, String sep) { return comma("", v, sep); }
        static String comma(String sep, String[] v) { return comma(sep, v, ""); }
        static String comma(String sep1, String[] v, String sep2) {
            if (v.length == 0)  return "";
            String res = v[0];
            for (int i = 1; i < v.length; i++)  res += ", "+v[i];
            return sep1 + res + sep2;
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
    static int MIN_ARITY, MAX_ARITY;
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
        if (pcur != params.length)  throw new RuntimeException("bad extra param: "+forLine);
        for (int inargs = MIN_ARITY; inargs <= MAX_ARITY; inargs++) {
            expandTemplate(template, true, inargs, -1);
        }
    }
    static void expandTemplate(String[] template, boolean topLevel, int inargs, int pos) {
        VAR.makeBindings(topLevel, inargs, pos);
        for (int i = 0; i < template.length; i++) {
            String line = template[i];
            if (line.endsWith("@each-cat@")) {
                // ignore
            } else if (line.endsWith("@each-P@")) {
                int blockEnd = indexAfter(template, i, "@end-P@");
                String[] block = stringsIn(template, i+1, blockEnd-1);
                for (int pos1 = Math.max(0,pos); pos1 < inargs; pos1++)
                    expandTemplate(block, false, inargs, pos1);
                VAR.makeBindings(topLevel, inargs, pos);
                i = blockEnd-1; continue;
            } else {
                System.out.println(VAR.transform(line));
            }
        }
    }
}
//}}} */
//params=[1, 20]
    static class F1 extends Adapter {
        protected F1(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F1(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F1 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F1(e, f, t); }
        protected Object invoke_V0(Object a0) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0)); }
        protected Object invoke_F0(Object a0) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0); }
        protected Object invoke_F1(Object a0) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0); }
        protected Object invoke_C0(Object a0) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0)); }
        protected Object invoke_C1(Object a0) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0) throws Throwable {
            Object[] av = { a0 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0]); }
    }
    static class F2 extends Adapter {
        protected F2(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F2(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F2 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F2(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1); }
        protected Object invoke_V1(Object a0, Object a1) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1)); }
        protected Object invoke_F0(Object a0, Object a1) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1); }
        protected Object invoke_F1(Object a0, Object a1) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1); }
        protected Object invoke_F2(Object a0, Object a1) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1); }
        protected Object invoke_C0(Object a0, Object a1) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1)); }
        protected Object invoke_C1(Object a0, Object a1) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1)); }
        protected Object invoke_C2(Object a0, Object a1) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1) throws Throwable {
            Object[] av = { a0, a1 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1]); }
    }
    static class F3 extends Adapter {
        protected F3(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F3(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F3 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F3(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2); }
        protected Object invoke_V1(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2); }
        protected Object invoke_V2(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2); }
        protected Object invoke_F1(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2); }
        protected Object invoke_F2(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2); }
        protected Object invoke_F3(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2); }
        protected Object invoke_C0(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2) throws Throwable {
            Object[] av = { a0, a1, a2 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2]); }
    }
    static class F4 extends Adapter {
        protected F4(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F4(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F4 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F4(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            Object[] av = { a0, a1, a2, a3 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3]); }
    }
    static class F5 extends Adapter {
        protected F5(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F5(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F5 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F5(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4]); }
    }
    static class F6 extends Adapter {
        protected F6(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F6(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F6 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F6(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5]); }
    }
    static class F7 extends Adapter {
        protected F7(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F7(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F7 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F7(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6]); }
    }
    static class F8 extends Adapter {
        protected F8(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F8(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F8 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F8(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7]); }
    }
    static class F9 extends Adapter {
        protected F9(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F9(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F9 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F9(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8]); }
    }
    static class F10 extends Adapter {
        protected F10(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F10(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F10 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F10(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9]); }
    }
    static class F11 extends Adapter {
        protected F11(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F11(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F11 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F11(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10]); }
    }
    static class F12 extends Adapter {
        protected F12(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F12(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F12 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F12(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11]); }
    }
    static class F13 extends Adapter {
        protected F13(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F13(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F13 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F13(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12]); }
    }
    static class F14 extends Adapter {
        protected F14(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F14(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F14 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F14(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12, a13); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12, a13); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12, a13); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12, a13); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12), a13); }
        protected Object invoke_V13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_F14(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12, a13)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12, a13)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12, a13)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12, a13)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13)); }
        protected Object invoke_C14(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12], av[13]); }
    }
    static class F15 extends Adapter {
        protected F15(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F15(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F15 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F15(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12, a13, a14); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12, a13, a14); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12, a13, a14); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12), a13, a14); }
        protected Object invoke_V13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13), a14); }
        protected Object invoke_V14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_F15(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12, a13, a14)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12, a13, a14)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12, a13, a14)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12, a13, a14)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13, a14)); }
        protected Object invoke_C14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14)); }
        protected Object invoke_C15(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12], av[13], av[14]); }
    }
    static class F16 extends Adapter {
        protected F16(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F16(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F16 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F16(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12, a13, a14, a15); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12, a13, a14, a15); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12, a13, a14, a15); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12), a13, a14, a15); }
        protected Object invoke_V13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13), a14, a15); }
        protected Object invoke_V14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14), a15); }
        protected Object invoke_V15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_F16(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12, a13, a14, a15)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12, a13, a14, a15)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12, a13, a14, a15)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13, a14, a15)); }
        protected Object invoke_C14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14, a15)); }
        protected Object invoke_C15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15)); }
        protected Object invoke_C16(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12], av[13], av[14], av[15]); }
    }
    static class F17 extends Adapter {
        protected F17(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F17(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F17 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F17(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12, a13, a14, a15, a16); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12, a13, a14, a15, a16); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12), a13, a14, a15, a16); }
        protected Object invoke_V13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13), a14, a15, a16); }
        protected Object invoke_V14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14), a15, a16); }
        protected Object invoke_V15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15), a16); }
        protected Object invoke_V16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_F17(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12, a13, a14, a15, a16)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12, a13, a14, a15, a16)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13, a14, a15, a16)); }
        protected Object invoke_C14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14, a15, a16)); }
        protected Object invoke_C15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15, a16)); }
        protected Object invoke_C16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16)); }
        protected Object invoke_C17(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12], av[13], av[14], av[15], av[16]); }
    }
    static class F18 extends Adapter {
        protected F18(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F18(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F18 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F18(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12, a13, a14, a15, a16, a17); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12), a13, a14, a15, a16, a17); }
        protected Object invoke_V13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13), a14, a15, a16, a17); }
        protected Object invoke_V14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14), a15, a16, a17); }
        protected Object invoke_V15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15), a16, a17); }
        protected Object invoke_V16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16), a17); }
        protected Object invoke_V17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, filter.invokeExact(a17)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_F18(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12, a13, a14, a15, a16, a17)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13, a14, a15, a16, a17)); }
        protected Object invoke_C14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14, a15, a16, a17)); }
        protected Object invoke_C15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15, a16, a17)); }
        protected Object invoke_C16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16, a17)); }
        protected Object invoke_C17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, filter.invokeExact(a17)); }
        protected Object invoke_C18(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12], av[13], av[14], av[15], av[16], av[17]); }
    }
    static class F19 extends Adapter {
        protected F19(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F19(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F19 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F19(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12), a13, a14, a15, a16, a17, a18); }
        protected Object invoke_V13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13), a14, a15, a16, a17, a18); }
        protected Object invoke_V14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14), a15, a16, a17, a18); }
        protected Object invoke_V15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15), a16, a17, a18); }
        protected Object invoke_V16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16), a17, a18); }
        protected Object invoke_V17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, filter.invokeExact(a17), a18); }
        protected Object invoke_V18(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, filter.invokeExact(a18)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F18(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_F19(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12, a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13, a14, a15, a16, a17, a18)); }
        protected Object invoke_C14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14, a15, a16, a17, a18)); }
        protected Object invoke_C15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15, a16, a17, a18)); }
        protected Object invoke_C16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16, a17, a18)); }
        protected Object invoke_C17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, filter.invokeExact(a17, a18)); }
        protected Object invoke_C18(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, filter.invokeExact(a18)); }
        protected Object invoke_C19(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12], av[13], av[14], av[15], av[16], av[17], av[18]); }
    }
    static class F20 extends Adapter {
        protected F20(MethodHandle entryPoint) { super(entryPoint); }  // to build prototype
        protected F20(MethodHandle e, MethodHandle f, MethodHandle t) {
            super(e, f, t); }
        protected F20 makeInstance(MethodHandle e, MethodHandle f, MethodHandle t) {
            return new F20(e, f, t); }
        protected Object invoke_V0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0), a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1), a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2), a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3), a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4), a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5), a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6), a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7), a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8), a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9),
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 filter.invokeExact(a10), a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11), a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12), a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13), a14, a15, a16, a17, a18, a19); }
        protected Object invoke_V14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14), a15, a16, a17, a18, a19); }
        protected Object invoke_V15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15), a16, a17, a18, a19); }
        protected Object invoke_V16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16), a17, a18, a19); }
        protected Object invoke_V17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, filter.invokeExact(a17), a18, a19); }
        protected Object invoke_V18(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, filter.invokeExact(a18), a19); }
        protected Object invoke_V19(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, filter.invokeExact(a19)); }
        protected Object invoke_F0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F18(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F19(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_F20(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19),
                                 a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19); }
        protected Object invoke_C0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(filter.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C1(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, filter.invokeExact(a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C2(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, filter.invokeExact(a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C3(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, filter.invokeExact(a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C4(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, filter.invokeExact(a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C5(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, filter.invokeExact(a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C6(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, filter.invokeExact(a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C7(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, filter.invokeExact(a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C8(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, filter.invokeExact(a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C9(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, filter.invokeExact(a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C10(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, filter.invokeExact(a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C11(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, filter.invokeExact(a11, a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C12(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, filter.invokeExact(a12, a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C13(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, filter.invokeExact(a13, a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C14(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, filter.invokeExact(a14, a15, a16, a17, a18, a19)); }
        protected Object invoke_C15(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, filter.invokeExact(a15, a16, a17, a18, a19)); }
        protected Object invoke_C16(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, filter.invokeExact(a16, a17, a18, a19)); }
        protected Object invoke_C17(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, filter.invokeExact(a17, a18, a19)); }
        protected Object invoke_C18(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, filter.invokeExact(a18, a19)); }
        protected Object invoke_C19(Object a0, Object a1, Object a2, Object a3,
                                    Object a4, Object a5, Object a6, Object a7,
                                    Object a8, Object a9, Object a10, Object a11,
                                    Object a12, Object a13, Object a14, Object a15,
                                    Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
                                 a10, a11, a12, a13, a14, a15, a16, a17, a18, filter.invokeExact(a19)); }
        protected Object invoke_C20(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, filter.invokeExact()); }
        protected Object invoke_Y0(Object a0, Object a1, Object a2, Object a3,
                                   Object a4, Object a5, Object a6, Object a7,
                                   Object a8, Object a9, Object a10, Object a11,
                                   Object a12, Object a13, Object a14, Object a15,
                                   Object a16, Object a17, Object a18, Object a19) throws Throwable {
            Object[] av = { a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19 };
            filter.invokeExact(av); // make the flyby
            return target.invokeExact(av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11], av[12], av[13], av[14], av[15], av[16], av[17], av[18], av[19]); }
    }
}
