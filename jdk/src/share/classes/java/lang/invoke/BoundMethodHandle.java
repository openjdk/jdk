/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.xml.internal.ws.org.objectweb.asm.Opcodes.*;
import static java.lang.invoke.LambdaForm.basicTypes;
import static java.lang.invoke.MethodHandleNatives.Constants.REF_invokeStatic;
import static java.lang.invoke.MethodHandleStatics.*;

import java.lang.invoke.LambdaForm.Name;
import java.lang.invoke.LambdaForm.NamedFunction;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;

import sun.invoke.util.ValueConversions;
import sun.invoke.util.Wrapper;

import com.sun.xml.internal.ws.org.objectweb.asm.ClassWriter;
import com.sun.xml.internal.ws.org.objectweb.asm.MethodVisitor;
import com.sun.xml.internal.ws.org.objectweb.asm.Type;

/**
 * The flavor of method handle which emulates an invoke instruction
 * on a predetermined argument.  The JVM dispatches to the correct method
 * when the handle is created, not when it is invoked.
 *
 * All bound arguments are encapsulated in dedicated species.
 */
/* non-public */ abstract class BoundMethodHandle extends MethodHandle {

    /* non-public */ BoundMethodHandle(MethodType type, LambdaForm form) {
        super(type, form);
    }

    //
    // BMH API and internals
    //

    static MethodHandle bindSingle(MethodType type, LambdaForm form, char xtype, Object x) {
        // for some type signatures, there exist pre-defined concrete BMH classes
        try {
            switch (xtype) {
            case 'L':
                if (true)  return bindSingle(type, form, x);  // Use known fast path.
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWithType('L').constructor[0].invokeBasic(type, form, x);
            case 'I':
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWithType('I').constructor[0].invokeBasic(type, form, ValueConversions.widenSubword(x));
            case 'J':
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWithType('J').constructor[0].invokeBasic(type, form, (long) x);
            case 'F':
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWithType('F').constructor[0].invokeBasic(type, form, (float) x);
            case 'D':
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWithType('D').constructor[0].invokeBasic(type, form, (double) x);
            default : throw new InternalError("unexpected xtype: " + xtype);
            }
        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    static MethodHandle bindSingle(MethodType type, LambdaForm form, Object x) {
            return new Species_L(type, form, x);
    }

    MethodHandle cloneExtend(MethodType type, LambdaForm form, char xtype, Object x) {
        try {
            switch (xtype) {
            case 'L': return cloneExtendL(type, form, x);
            case 'I': return cloneExtendI(type, form, ValueConversions.widenSubword(x));
            case 'J': return cloneExtendJ(type, form, (long) x);
            case 'F': return cloneExtendF(type, form, (float) x);
            case 'D': return cloneExtendD(type, form, (double) x);
            }
        } catch (Throwable t) {
            throw new InternalError(t);
        }
        throw new InternalError("unexpected type: " + xtype);
    }

    @Override
    MethodHandle bindArgument(int pos, char basicType, Object value) {
        MethodType type = type().dropParameterTypes(pos, pos+1);
        LambdaForm form = internalForm().bind(1+pos, speciesData());
        return cloneExtend(type, form, basicType, value);
    }

    @Override
    MethodHandle dropArguments(MethodType srcType, int pos, int drops) {
        LambdaForm form = internalForm().addArguments(pos, srcType.parameterList().subList(pos, pos+drops));
        try {
             return clone(srcType, form);
         } catch (Throwable t) {
             throw new InternalError(t);
         }
    }

    @Override
    MethodHandle permuteArguments(MethodType newType, int[] reorder) {
        try {
             return clone(newType, form.permuteArguments(1, reorder, basicTypes(newType.parameterList())));
         } catch (Throwable t) {
             throw new InternalError(t);
         }
    }

    static final String EXTENSION_TYPES = "LIJFD";
    static final byte INDEX_L = 0, INDEX_I = 1, INDEX_J = 2, INDEX_F = 3, INDEX_D = 4;
    static byte extensionIndex(char type) {
        int i = EXTENSION_TYPES.indexOf(type);
        if (i < 0)  throw new InternalError();
        return (byte) i;
    }

    /**
     * Return the {@link SpeciesData} instance representing this BMH species. All subclasses must provide a
     * static field containing this value, and they must accordingly implement this method.
     */
    protected abstract SpeciesData speciesData();

    @Override
    final Object internalValues() {
        Object[] boundValues = new Object[speciesData().fieldCount()];
        for (int i = 0; i < boundValues.length; ++i) {
            boundValues[i] = arg(i);
        }
        return Arrays.asList(boundValues);
    }

    public final Object arg(int i) {
        try {
            switch (speciesData().fieldType(i)) {
            case 'L': return argL(i);
            case 'I': return argI(i);
            case 'F': return argF(i);
            case 'D': return argD(i);
            case 'J': return argJ(i);
            }
        } catch (Throwable ex) {
            throw new InternalError(ex);
        }
        throw new InternalError("unexpected type: " + speciesData().types+"."+i);
    }
    public final Object argL(int i) throws Throwable { return          speciesData().getters[i].invokeBasic(this); }
    public final int    argI(int i) throws Throwable { return (int)    speciesData().getters[i].invokeBasic(this); }
    public final float  argF(int i) throws Throwable { return (float)  speciesData().getters[i].invokeBasic(this); }
    public final double argD(int i) throws Throwable { return (double) speciesData().getters[i].invokeBasic(this); }
    public final long   argJ(int i) throws Throwable { return (long)   speciesData().getters[i].invokeBasic(this); }

    //
    // cloning API
    //

    public abstract BoundMethodHandle clone(MethodType mt, LambdaForm lf) throws Throwable;
    public abstract BoundMethodHandle cloneExtendL(MethodType mt, LambdaForm lf, Object narg) throws Throwable;
    public abstract BoundMethodHandle cloneExtendI(MethodType mt, LambdaForm lf, int    narg) throws Throwable;
    public abstract BoundMethodHandle cloneExtendJ(MethodType mt, LambdaForm lf, long   narg) throws Throwable;
    public abstract BoundMethodHandle cloneExtendF(MethodType mt, LambdaForm lf, float  narg) throws Throwable;
    public abstract BoundMethodHandle cloneExtendD(MethodType mt, LambdaForm lf, double narg) throws Throwable;

    // The following is a grossly irregular hack:
    @Override MethodHandle reinvokerTarget() {
        try {
            return (MethodHandle) argL(0);
        } catch (Throwable ex) {
            throw new InternalError(ex);
        }
    }

    //
    // concrete BMH classes required to close bootstrap loops
    //

    private  // make it private to force users to access the enclosing class first
    static final class Species_L extends BoundMethodHandle {
        final Object argL0;
        public Species_L(MethodType mt, LambdaForm lf, Object argL0) {
            super(mt, lf);
            this.argL0 = argL0;
        }
        // The following is a grossly irregular hack:
        @Override MethodHandle reinvokerTarget() { return (MethodHandle) argL0; }
        @Override
        public SpeciesData speciesData() {
            return SPECIES_DATA;
        }
        public static final SpeciesData SPECIES_DATA = SpeciesData.getForClass("L", Species_L.class);
        @Override
        public final BoundMethodHandle clone(MethodType mt, LambdaForm lf) throws Throwable {
            return new Species_L(mt, lf, argL0);
        }
        @Override
        public final BoundMethodHandle cloneExtendL(MethodType mt, LambdaForm lf, Object narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_L).constructor[0].invokeBasic(mt, lf, argL0, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendI(MethodType mt, LambdaForm lf, int narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_I).constructor[0].invokeBasic(mt, lf, argL0, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendJ(MethodType mt, LambdaForm lf, long narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_J).constructor[0].invokeBasic(mt, lf, argL0, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendF(MethodType mt, LambdaForm lf, float narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_F).constructor[0].invokeBasic(mt, lf, argL0, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendD(MethodType mt, LambdaForm lf, double narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_D).constructor[0].invokeBasic(mt, lf, argL0, narg);
        }
    }

/*
    static final class Species_LL extends BoundMethodHandle {
        final Object argL0;
        final Object argL1;
        public Species_LL(MethodType mt, LambdaForm lf, Object argL0, Object argL1) {
            super(mt, lf);
            this.argL0 = argL0;
            this.argL1 = argL1;
        }
        @Override
        public SpeciesData speciesData() {
            return SPECIES_DATA;
        }
        public static final SpeciesData SPECIES_DATA = SpeciesData.getForClass("LL", Species_LL.class);
        @Override
        public final BoundMethodHandle clone(MethodType mt, LambdaForm lf) throws Throwable {
            return new Species_LL(mt, lf, argL0, argL1);
        }
        @Override
        public final BoundMethodHandle cloneExtendL(MethodType mt, LambdaForm lf, Object narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_L).constructor[0].invokeBasic(mt, lf, argL0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendI(MethodType mt, LambdaForm lf, int narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_I).constructor[0].invokeBasic(mt, lf, argL0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendJ(MethodType mt, LambdaForm lf, long narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_J).constructor[0].invokeBasic(mt, lf, argL0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendF(MethodType mt, LambdaForm lf, float narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_F).constructor[0].invokeBasic(mt, lf, argL0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendD(MethodType mt, LambdaForm lf, double narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_D).constructor[0].invokeBasic(mt, lf, argL0, argL1, narg);
        }
    }

    static final class Species_JL extends BoundMethodHandle {
        final long argJ0;
        final Object argL1;
        public Species_JL(MethodType mt, LambdaForm lf, long argJ0, Object argL1) {
            super(mt, lf);
            this.argJ0 = argJ0;
            this.argL1 = argL1;
        }
        @Override
        public SpeciesData speciesData() {
            return SPECIES_DATA;
        }
        public static final SpeciesData SPECIES_DATA = SpeciesData.getForClass("JL", Species_JL.class);
        @Override public final long   argJ0() { return argJ0; }
        @Override public final Object argL1() { return argL1; }
        @Override
        public final BoundMethodHandle clone(MethodType mt, LambdaForm lf) throws Throwable {
            return new Species_JL(mt, lf, argJ0, argL1);
        }
        @Override
        public final BoundMethodHandle cloneExtendL(MethodType mt, LambdaForm lf, Object narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_L).constructor[0].invokeBasic(mt, lf, argJ0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendI(MethodType mt, LambdaForm lf, int narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_I).constructor[0].invokeBasic(mt, lf, argJ0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendJ(MethodType mt, LambdaForm lf, long narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_J).constructor[0].invokeBasic(mt, lf, argJ0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendF(MethodType mt, LambdaForm lf, float narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_F).constructor[0].invokeBasic(mt, lf, argJ0, argL1, narg);
        }
        @Override
        public final BoundMethodHandle cloneExtendD(MethodType mt, LambdaForm lf, double narg) throws Throwable {
            return (BoundMethodHandle) SPECIES_DATA.extendWithIndex(INDEX_D).constructor[0].invokeBasic(mt, lf, argJ0, argL1, narg);
        }
    }
*/

    //
    // BMH species meta-data
    //

    /**
     * Meta-data wrapper for concrete BMH classes.
     */
    static class SpeciesData {
        final String                             types;
        final Class<? extends BoundMethodHandle> clazz;
        // Bootstrapping requires circular relations MH -> BMH -> SpeciesData -> MH
        // Therefore, we need a non-final link in the chain.  Use array elements.
        final MethodHandle[]                     constructor;
        final MethodHandle[]                     getters;
        final SpeciesData[]                      extensions;

        public int fieldCount() {
            return types.length();
        }
        public char fieldType(int i) {
            return types.charAt(i);
        }

        public String toString() {
            return "SpeciesData["+(isPlaceholder() ? "<placeholder>" : clazz.getSimpleName())+":"+types+"]";
        }

        /**
         * Return a {@link LambdaForm.Name} containing a {@link LambdaForm.NamedFunction} that
         * represents a MH bound to a generic invoker, which in turn forwards to the corresponding
         * getter.
         */
        Name getterName(Name mhName, int i) {
            MethodHandle mh = getters[i];
            assert(mh != null) : this+"."+i;
            return new Name(mh, mhName);
        }

        static final SpeciesData EMPTY = new SpeciesData("", BoundMethodHandle.class);

        private SpeciesData(String types, Class<? extends BoundMethodHandle> clazz) {
            this.types = types;
            this.clazz = clazz;
            if (!INIT_DONE) {
                this.constructor = new MethodHandle[1];
                this.getters = new MethodHandle[types.length()];
            } else {
                this.constructor = Factory.makeCtors(clazz, types, null);
                this.getters = Factory.makeGetters(clazz, types, null);
            }
            this.extensions = new SpeciesData[EXTENSION_TYPES.length()];
        }

        private void initForBootstrap() {
            assert(!INIT_DONE);
            if (constructor[0] == null) {
                Factory.makeCtors(clazz, types, this.constructor);
                Factory.makeGetters(clazz, types, this.getters);
            }
        }

        private SpeciesData(String types) {
            // Placeholder only.
            this.types = types;
            this.clazz = null;
            this.constructor = null;
            this.getters = null;
            this.extensions = null;
        }
        private boolean isPlaceholder() { return clazz == null; }

        private static final HashMap<String, SpeciesData> CACHE = new HashMap<>();
        private static final boolean INIT_DONE;  // set after <clinit> finishes...

        SpeciesData extendWithType(char type) {
            int i = extensionIndex(type);
            SpeciesData d = extensions[i];
            if (d != null)  return d;
            extensions[i] = d = get(types+type);
            return d;
        }

        SpeciesData extendWithIndex(byte index) {
            SpeciesData d = extensions[index];
            if (d != null)  return d;
            extensions[index] = d = get(types+EXTENSION_TYPES.charAt(index));
            return d;
        }

        private static SpeciesData get(String types) {
            // Acquire cache lock for query.
            SpeciesData d = lookupCache(types);
            if (!d.isPlaceholder())
                return d;
            Class<? extends BoundMethodHandle> cbmh;
            synchronized (d) {
                // Use synch. on the placeholder to prevent multiple instantiation of one species.
                // Creating this class forces a recursive call to getForClass.
                cbmh = Factory.generateConcreteBMHClass(types);
            }
            // Reacquire cache lock.
            d = lookupCache(types);
            // Class loading must have upgraded the cache.
            assert(d != null && !d.isPlaceholder());
            return d;
        }
        static SpeciesData getForClass(String types, Class<? extends BoundMethodHandle> clazz) {
            // clazz is a new class which is initializing its SPECIES_DATA field
            return updateCache(types, new SpeciesData(types, clazz));
        }
        private static synchronized SpeciesData lookupCache(String types) {
            SpeciesData d = CACHE.get(types);
            if (d != null)  return d;
            d = new SpeciesData(types);
            assert(d.isPlaceholder());
            CACHE.put(types, d);
            return d;
        }
        private static synchronized SpeciesData updateCache(String types, SpeciesData d) {
            SpeciesData d2;
            assert((d2 = CACHE.get(types)) == null || d2.isPlaceholder());
            assert(!d.isPlaceholder());
            CACHE.put(types, d);
            return d;
        }

        static {
            // pre-fill the BMH speciesdata cache with BMH's inner classes
            final Class<BoundMethodHandle> rootCls = BoundMethodHandle.class;
            SpeciesData d0 = BoundMethodHandle.SPECIES_DATA;  // trigger class init
            assert(d0 == null || d0 == lookupCache("")) : d0;
            try {
                for (Class<?> c : rootCls.getDeclaredClasses()) {
                    if (rootCls.isAssignableFrom(c)) {
                        final Class<? extends BoundMethodHandle> cbmh = c.asSubclass(BoundMethodHandle.class);
                        SpeciesData d = Factory.speciesDataFromConcreteBMHClass(cbmh);
                        assert(d != null) : cbmh.getName();
                        assert(d.clazz == cbmh);
                        assert(d == lookupCache(d.types));
                    }
                }
            } catch (Throwable e) {
                throw new InternalError(e);
            }

            for (SpeciesData d : CACHE.values()) {
                d.initForBootstrap();
            }
            // Note:  Do not simplify this, because INIT_DONE must not be
            // a compile-time constant during bootstrapping.
            INIT_DONE = Boolean.TRUE;
        }
    }

    static SpeciesData getSpeciesData(String types) {
        return SpeciesData.get(types);
    }

    /**
     * Generation of concrete BMH classes.
     *
     * A concrete BMH species is fit for binding a number of values adhering to a
     * given type pattern. Reference types are erased.
     *
     * BMH species are cached by type pattern.
     *
     * A BMH species has a number of fields with the concrete (possibly erased) types of
     * bound values. Setters are provided as an API in BMH. Getters are exposed as MHs,
     * which can be included as names in lambda forms.
     */
    static class Factory {

        static final String JLO_SIG  = "Ljava/lang/Object;";
        static final String JLS_SIG  = "Ljava/lang/String;";
        static final String JLC_SIG  = "Ljava/lang/Class;";
        static final String MH       = "java/lang/invoke/MethodHandle";
        static final String MH_SIG   = "L"+MH+";";
        static final String BMH      = "java/lang/invoke/BoundMethodHandle";
        static final String BMH_SIG  = "L"+BMH+";";
        static final String SPECIES_DATA     = "java/lang/invoke/BoundMethodHandle$SpeciesData";
        static final String SPECIES_DATA_SIG = "L"+SPECIES_DATA+";";

        static final String SPECIES_PREFIX_NAME = "Species_";
        static final String SPECIES_PREFIX_PATH = BMH + "$" + SPECIES_PREFIX_NAME;

        static final String BMHSPECIES_DATA_EWI_SIG = "(B)" + SPECIES_DATA_SIG;
        static final String BMHSPECIES_DATA_GFC_SIG = "(" + JLS_SIG + JLC_SIG + ")" + SPECIES_DATA_SIG;
        static final String MYSPECIES_DATA_SIG = "()" + SPECIES_DATA_SIG;
        static final String VOID_SIG   = "()V";

        static final String SIG_INCIPIT = "(Ljava/lang/invoke/MethodType;Ljava/lang/invoke/LambdaForm;";

        static final Class<?>[] TYPES = new Class<?>[] { Object.class, int.class, long.class, float.class, double.class };

        static final String[] E_THROWABLE = new String[] { "java/lang/Throwable" };

        /**
         * Generate a concrete subclass of BMH for a given combination of bound types.
         *
         * A concrete BMH species adheres to the following schema:
         *
         * <pre>
         * class Species_<<types>> extends BoundMethodHandle {
         *     <<fields>>
         *     final SpeciesData speciesData() { return SpeciesData.get("<<types>>"); }
         * }
         * </pre>
         *
         * The {@code <<types>>} signature is precisely the string that is passed to this
         * method.
         *
         * The {@code <<fields>>} section consists of one field definition per character in
         * the type signature, adhering to the naming schema described in the definition of
         * {@link #makeFieldName()}.
         *
         * For example, a concrete BMH species for two reference and one integral bound values
         * would have the following shape:
         *
         * <pre>
         * class BoundMethodHandle { ... private static
         * final class Species_LLI extends BoundMethodHandle {
         *     final Object argL0;
         *     final Object argL1;
         *     final int argI2;
         *     public Species_LLI(MethodType mt, LambdaForm lf, Object argL0, Object argL1, int argI2) {
         *         super(mt, lf);
         *         this.argL0 = argL0;
         *         this.argL1 = argL1;
         *         this.argI2 = argI2;
         *     }
         *     public final SpeciesData speciesData() { return SPECIES_DATA; }
         *     public static final SpeciesData SPECIES_DATA = SpeciesData.getForClass("LLI", Species_LLI.class);
         *     public final BoundMethodHandle clone(MethodType mt, LambdaForm lf) {
         *         return SPECIES_DATA.constructor[0].invokeBasic(mt, lf, argL0, argL1, argI2);
         *     }
         *     public final BoundMethodHandle cloneExtendL(MethodType mt, LambdaForm lf, Object narg) {
         *         return SPECIES_DATA.extendWithIndex(INDEX_L).constructor[0].invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     public final BoundMethodHandle cloneExtendI(MethodType mt, LambdaForm lf, int narg) {
         *         return SPECIES_DATA.extendWithIndex(INDEX_I).constructor[0].invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     public final BoundMethodHandle cloneExtendJ(MethodType mt, LambdaForm lf, long narg) {
         *         return SPECIES_DATA.extendWithIndex(INDEX_J).constructor[0].invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     public final BoundMethodHandle cloneExtendF(MethodType mt, LambdaForm lf, float narg) {
         *         return SPECIES_DATA.extendWithIndex(INDEX_F).constructor[0].invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     public final BoundMethodHandle cloneExtendD(MethodType mt, LambdaForm lf, double narg) {
         *         return SPECIES_DATA.extendWithIndex(INDEX_D).constructor[0].invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         * }
         * </pre>
         *
         * @param types the type signature, wherein reference types are erased to 'L'
         * @return the generated concrete BMH class
         */
        static Class<? extends BoundMethodHandle> generateConcreteBMHClass(String types) {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);

            final String className  = SPECIES_PREFIX_PATH + types;
            final String sourceFile = SPECIES_PREFIX_NAME + types;
            cw.visit(V1_6, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, className, null, BMH, null);
            cw.visitSource(sourceFile, null);

            // emit static types and SPECIES_DATA fields
            cw.visitField(ACC_PUBLIC + ACC_STATIC, "SPECIES_DATA", SPECIES_DATA_SIG, null, null).visitEnd();

            // emit bound argument fields
            for (int i = 0; i < types.length(); ++i) {
                final char t = types.charAt(i);
                final String fieldName = makeFieldName(types, i);
                final String fieldDesc = t == 'L' ? JLO_SIG : String.valueOf(t);
                cw.visitField(ACC_FINAL, fieldName, fieldDesc, null, null).visitEnd();
            }

            MethodVisitor mv;

            // emit constructor
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", makeSignature(types, true), null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);

            mv.visitMethodInsn(INVOKESPECIAL, BMH, "<init>", makeSignature("", true));

            for (int i = 0, j = 0; i < types.length(); ++i, ++j) {
                // i counts the arguments, j counts corresponding argument slots
                char t = types.charAt(i);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(typeLoadOp(t), j + 3); // parameters start at 3
                mv.visitFieldInsn(PUTFIELD, className, makeFieldName(types, i), typeSig(t));
                if (t == 'J' || t == 'D') {
                    ++j; // adjust argument register access
                }
            }

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // emit implementation of reinvokerTarget()
            mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "reinvokerTarget", "()" + MH_SIG, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, "argL0", JLO_SIG);
            mv.visitTypeInsn(CHECKCAST, MH);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // emit implementation of speciesData()
            mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "speciesData", MYSPECIES_DATA_SIG, null, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, className, "SPECIES_DATA", SPECIES_DATA_SIG);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // emit clone()
            mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "clone", makeSignature("", false), null, E_THROWABLE);
            mv.visitCode();
            // return speciesData().constructor[0].invokeBasic(mt, lf, argL0, ...)
            // obtain constructor
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETSTATIC, className, "SPECIES_DATA", SPECIES_DATA_SIG);
            mv.visitFieldInsn(GETFIELD, SPECIES_DATA, "constructor", "[" + MH_SIG);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(AALOAD);
            // load mt, lf
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            // put fields on the stack
            emitPushFields(types, className, mv);
            // finally, invoke the constructor and return
            mv.visitMethodInsn(INVOKEVIRTUAL, MH, "invokeBasic", makeSignature(types, false));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // for each type, emit cloneExtendT()
            for (Class<?> c : TYPES) {
                char t = Wrapper.basicTypeChar(c);
                mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "cloneExtend" + t, makeSignature(String.valueOf(t), false), null, E_THROWABLE);
                mv.visitCode();
                // return SPECIES_DATA.extendWithIndex(extensionIndex(t)).constructor[0].invokeBasic(mt, lf, argL0, ..., narg)
                // obtain constructor
                mv.visitFieldInsn(GETSTATIC, className, "SPECIES_DATA", SPECIES_DATA_SIG);
                int iconstInsn = ICONST_0 + extensionIndex(t);
                assert(iconstInsn <= ICONST_5);
                mv.visitInsn(iconstInsn);
                mv.visitMethodInsn(INVOKEVIRTUAL, SPECIES_DATA, "extendWithIndex", BMHSPECIES_DATA_EWI_SIG);
                mv.visitFieldInsn(GETFIELD, SPECIES_DATA, "constructor", "[" + MH_SIG);
                mv.visitInsn(ICONST_0);
                mv.visitInsn(AALOAD);
                // load mt, lf
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                // put fields on the stack
                emitPushFields(types, className, mv);
                // put narg on stack
                mv.visitVarInsn(typeLoadOp(t), 3);
                // finally, invoke the constructor and return
                mv.visitMethodInsn(INVOKEVIRTUAL, MH, "invokeBasic", makeSignature(types + t, false));
                mv.visitInsn(ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            // emit class initializer
            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>", VOID_SIG, null, null);
            mv.visitCode();
            mv.visitLdcInsn(types);
            mv.visitLdcInsn(Type.getObjectType(className));
            mv.visitMethodInsn(INVOKESTATIC, SPECIES_DATA, "getForClass", BMHSPECIES_DATA_GFC_SIG);
            mv.visitFieldInsn(PUTSTATIC, className, "SPECIES_DATA", SPECIES_DATA_SIG);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            cw.visitEnd();

            // load class
            final byte[] classFile = cw.toByteArray();
            InvokerBytecodeGenerator.maybeDump(className, classFile);
            Class<? extends BoundMethodHandle> bmhClass =
                //UNSAFE.defineAnonymousClass(BoundMethodHandle.class, classFile, null).asSubclass(BoundMethodHandle.class);
                UNSAFE.defineClass(className, classFile, 0, classFile.length).asSubclass(BoundMethodHandle.class);
            UNSAFE.ensureClassInitialized(bmhClass);

            return bmhClass;
        }

        private static int typeLoadOp(char t) {
            switch (t) {
            case 'L': return ALOAD;
            case 'I': return ILOAD;
            case 'J': return LLOAD;
            case 'F': return FLOAD;
            case 'D': return DLOAD;
            default : throw new InternalError("unrecognized type " + t);
            }
        }

        private static void emitPushFields(String types, String className, MethodVisitor mv) {
            for (int i = 0; i < types.length(); ++i) {
                char tc = types.charAt(i);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, className, makeFieldName(types, i), typeSig(tc));
            }
        }

        static String typeSig(char t) {
            return t == 'L' ? JLO_SIG : String.valueOf(t);
        }

        //
        // Getter MH generation.
        //

        private static MethodHandle makeGetter(Class<?> cbmhClass, String types, int index) {
            String fieldName = makeFieldName(types, index);
            Class<?> fieldType = Wrapper.forBasicType(types.charAt(index)).primitiveType();
            try {
                return LOOKUP.findGetter(cbmhClass, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        }

        static MethodHandle[] makeGetters(Class<?> cbmhClass, String types, MethodHandle[] mhs) {
            if (mhs == null)  mhs = new MethodHandle[types.length()];
            for (int i = 0; i < mhs.length; ++i) {
                mhs[i] = makeGetter(cbmhClass, types, i);
                assert(mhs[i].internalMemberName().getDeclaringClass() == cbmhClass);
            }
            return mhs;
        }

        static MethodHandle[] makeCtors(Class<? extends BoundMethodHandle> cbmh, String types, MethodHandle mhs[]) {
            if (mhs == null)  mhs = new MethodHandle[1];
            mhs[0] = makeCbmhCtor(cbmh, types);
            return mhs;
        }

        //
        // Auxiliary methods.
        //

        static SpeciesData speciesDataFromConcreteBMHClass(Class<? extends BoundMethodHandle> cbmh) {
            try {
                Field F_SPECIES_DATA = cbmh.getDeclaredField("SPECIES_DATA");
                return (SpeciesData) F_SPECIES_DATA.get(null);
            } catch (ReflectiveOperationException ex) {
                throw new InternalError(ex);
            }
        }

        /**
         * Field names in concrete BMHs adhere to this pattern:
         * arg + type + index
         * where type is a single character (L, I, J, F, D).
         */
        private static String makeFieldName(String types, int index) {
            assert index >= 0 && index < types.length();
            return "arg" + types.charAt(index) + index;
        }

        private static String makeSignature(String types, boolean ctor) {
            StringBuilder buf = new StringBuilder(SIG_INCIPIT);
            for (char c : types.toCharArray()) {
                buf.append(typeSig(c));
            }
            return buf.append(')').append(ctor ? "V" : BMH_SIG).toString();
        }

        static MethodHandle makeCbmhCtor(Class<? extends BoundMethodHandle> cbmh, String types) {
            try {
                return linkConstructor(LOOKUP.findConstructor(cbmh, MethodType.fromMethodDescriptorString(makeSignature(types, true), null)));
            } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | TypeNotPresentException e) {
                throw new InternalError(e);
            }
        }

        /**
         * Wrap a constructor call in a {@link LambdaForm}.
         *
         * If constructors ({@code <init>} methods) are called in LFs, problems might arise if the LFs
         * are turned into bytecode, because the call to the allocator is routed through an MH, and the
         * verifier cannot find a {@code NEW} instruction preceding the {@code INVOKESPECIAL} to
         * {@code <init>}. To avoid this, we add an indirection by invoking {@code <init>} through
         * {@link MethodHandle#linkToSpecial}.
         *
         * The last {@link LambdaForm#Name Name} in the argument's form is expected to be the {@code void}
         * result of the {@code <init>} invocation. This entry is replaced.
         */
        private static MethodHandle linkConstructor(MethodHandle cmh) {
            final LambdaForm lf = cmh.form;
            final int initNameIndex = lf.names.length - 1;
            final Name initName = lf.names[initNameIndex];
            final MemberName ctorMN = initName.function.member;
            final MethodType ctorMT = ctorMN.getInvocationType();

            // obtain function member (call target)
            // linker method type replaces initial parameter (BMH species) with BMH to avoid naming a species (anonymous class!)
            final MethodType linkerMT = ctorMT.changeParameterType(0, BoundMethodHandle.class).appendParameterTypes(MemberName.class);
            MemberName linkerMN = new MemberName(MethodHandle.class, "linkToSpecial", linkerMT, REF_invokeStatic);
            try {
                linkerMN = MemberName.getFactory().resolveOrFail(REF_invokeStatic, linkerMN, null, NoSuchMethodException.class);
                assert(linkerMN.isStatic());
            } catch (ReflectiveOperationException ex) {
                throw new InternalError(ex);
            }
            // extend arguments array
            Object[] newArgs = Arrays.copyOf(initName.arguments, initName.arguments.length + 1);
            newArgs[newArgs.length - 1] = ctorMN;
            // replace function
            final NamedFunction nf = new NamedFunction(linkerMN);
            final Name linkedCtor = new Name(nf, newArgs);
            linkedCtor.initIndex(initNameIndex);
            lf.names[initNameIndex] = linkedCtor;
            return cmh;
        }

    }

    private static final Lookup LOOKUP = Lookup.IMPL_LOOKUP;

    /**
     * All subclasses must provide such a value describing their type signature.
     */
    static final SpeciesData SPECIES_DATA = SpeciesData.EMPTY;
}
