/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.internal.org.objectweb.asm.Opcodes.*;
import static java.lang.invoke.LambdaForm.*;
import static java.lang.invoke.LambdaForm.BasicType.*;
import static java.lang.invoke.MethodHandleStatics.*;

import java.lang.invoke.LambdaForm.NamedFunction;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;

import sun.invoke.util.ValueConversions;
import sun.invoke.util.Wrapper;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;

/**
 * The flavor of method handle which emulates an invoke instruction
 * on a predetermined argument.  The JVM dispatches to the correct method
 * when the handle is created, not when it is invoked.
 *
 * All bound arguments are encapsulated in dedicated species.
 */
/*non-public*/ abstract class BoundMethodHandle extends MethodHandle {

    /*non-public*/ BoundMethodHandle(MethodType type, LambdaForm form) {
        super(type, form);
    }

    //
    // BMH API and internals
    //

    static BoundMethodHandle bindSingle(MethodType type, LambdaForm form, BasicType xtype, Object x) {
        // for some type signatures, there exist pre-defined concrete BMH classes
        try {
            switch (xtype) {
            case L_TYPE:
                return bindSingle(type, form, x);  // Use known fast path.
            case I_TYPE:
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWith(I_TYPE).constructor().invokeBasic(type, form, ValueConversions.widenSubword(x));
            case J_TYPE:
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWith(J_TYPE).constructor().invokeBasic(type, form, (long) x);
            case F_TYPE:
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWith(F_TYPE).constructor().invokeBasic(type, form, (float) x);
            case D_TYPE:
                return (BoundMethodHandle) SpeciesData.EMPTY.extendWith(D_TYPE).constructor().invokeBasic(type, form, (double) x);
            default : throw newInternalError("unexpected xtype: " + xtype);
            }
        } catch (Throwable t) {
            throw newInternalError(t);
        }
    }

    static BoundMethodHandle bindSingle(MethodType type, LambdaForm form, Object x) {
        return Species_L.make(type, form, x);
    }

    @Override // there is a default binder in the super class, for 'L' types only
    /*non-public*/
    BoundMethodHandle bindArgumentL(int pos, Object value) {
        MethodType type = type().dropParameterTypes(pos, pos+1);
        LambdaForm form = internalForm().bind(1+pos, speciesData());
        return copyWithExtendL(type, form, value);
    }
    /*non-public*/
    BoundMethodHandle bindArgumentI(int pos, int value) {
        MethodType type = type().dropParameterTypes(pos, pos+1);
        LambdaForm form = internalForm().bind(1+pos, speciesData());
        return copyWithExtendI(type, form, value);
    }
    /*non-public*/
    BoundMethodHandle bindArgumentJ(int pos, long value) {
        MethodType type = type().dropParameterTypes(pos, pos+1);
        LambdaForm form = internalForm().bind(1+pos, speciesData());
        return copyWithExtendJ(type, form, value);
    }
    /*non-public*/
    BoundMethodHandle bindArgumentF(int pos, float value) {
        MethodType type = type().dropParameterTypes(pos, pos+1);
        LambdaForm form = internalForm().bind(1+pos, speciesData());
        return copyWithExtendF(type, form, value);
    }
    /*non-public*/
    BoundMethodHandle bindArgumentD(int pos, double value) {
        MethodType type = type().dropParameterTypes(pos, pos + 1);
        LambdaForm form = internalForm().bind(1+pos, speciesData());
        return copyWithExtendD(type, form, value);
    }

    @Override
    BoundMethodHandle rebind() {
        if (!tooComplex()) {
            return this;
        }
        return makeReinvoker(this);
    }

    private boolean tooComplex() {
        return (fieldCount() > FIELD_COUNT_THRESHOLD ||
                form.expressionCount() > FORM_EXPRESSION_THRESHOLD);
    }
    private static final int FIELD_COUNT_THRESHOLD = 12;      // largest convenient BMH field count
    private static final int FORM_EXPRESSION_THRESHOLD = 24;  // largest convenient BMH expression count

    /**
     * A reinvoker MH has this form:
     * {@code lambda (bmh, arg*) { thismh = bmh[0]; invokeBasic(thismh, arg*) }}
     */
    static BoundMethodHandle makeReinvoker(MethodHandle target) {
        LambdaForm form = DelegatingMethodHandle.makeReinvokerForm(
                target, MethodTypeForm.LF_REBIND, Species_L.SPECIES_DATA.getterFunction(0) );
        return Species_L.make(target.type(), form, target);
    }

    /**
     * Return the {@link SpeciesData} instance representing this BMH species. All subclasses must provide a
     * static field containing this value, and they must accordingly implement this method.
     */
    /*non-public*/ abstract SpeciesData speciesData();

    /**
     * Return the number of fields in this BMH.  Equivalent to speciesData().fieldCount().
     */
    /*non-public*/ abstract int fieldCount();

    @Override
    Object internalProperties() {
        return "\n& BMH="+internalValues();
    }

    @Override
    final Object internalValues() {
        Object[] boundValues = new Object[speciesData().fieldCount()];
        for (int i = 0; i < boundValues.length; ++i) {
            boundValues[i] = arg(i);
        }
        return Arrays.asList(boundValues);
    }

    /*non-public*/ final Object arg(int i) {
        try {
            switch (speciesData().fieldType(i)) {
            case L_TYPE: return          speciesData().getters[i].invokeBasic(this);
            case I_TYPE: return (int)    speciesData().getters[i].invokeBasic(this);
            case J_TYPE: return (long)   speciesData().getters[i].invokeBasic(this);
            case F_TYPE: return (float)  speciesData().getters[i].invokeBasic(this);
            case D_TYPE: return (double) speciesData().getters[i].invokeBasic(this);
            }
        } catch (Throwable ex) {
            throw newInternalError(ex);
        }
        throw new InternalError("unexpected type: " + speciesData().typeChars+"."+i);
    }

    //
    // cloning API
    //

    /*non-public*/ abstract BoundMethodHandle copyWith(MethodType mt, LambdaForm lf);
    /*non-public*/ abstract BoundMethodHandle copyWithExtendL(MethodType mt, LambdaForm lf, Object narg);
    /*non-public*/ abstract BoundMethodHandle copyWithExtendI(MethodType mt, LambdaForm lf, int    narg);
    /*non-public*/ abstract BoundMethodHandle copyWithExtendJ(MethodType mt, LambdaForm lf, long   narg);
    /*non-public*/ abstract BoundMethodHandle copyWithExtendF(MethodType mt, LambdaForm lf, float  narg);
    /*non-public*/ abstract BoundMethodHandle copyWithExtendD(MethodType mt, LambdaForm lf, double narg);

    //
    // concrete BMH classes required to close bootstrap loops
    //

    private  // make it private to force users to access the enclosing class first
    static final class Species_L extends BoundMethodHandle {
        final Object argL0;
        private Species_L(MethodType mt, LambdaForm lf, Object argL0) {
            super(mt, lf);
            this.argL0 = argL0;
        }
        @Override
        /*non-public*/ SpeciesData speciesData() {
            return SPECIES_DATA;
        }
        @Override
        /*non-public*/ int fieldCount() {
            return 1;
        }
        /*non-public*/ static final SpeciesData SPECIES_DATA = SpeciesData.getForClass("L", Species_L.class);
        /*non-public*/ static BoundMethodHandle make(MethodType mt, LambdaForm lf, Object argL0) {
            return new Species_L(mt, lf, argL0);
        }
        @Override
        /*non-public*/ final BoundMethodHandle copyWith(MethodType mt, LambdaForm lf) {
            return new Species_L(mt, lf, argL0);
        }
        @Override
        /*non-public*/ final BoundMethodHandle copyWithExtendL(MethodType mt, LambdaForm lf, Object narg) {
            try {
                return (BoundMethodHandle) SPECIES_DATA.extendWith(L_TYPE).constructor().invokeBasic(mt, lf, argL0, narg);
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
        @Override
        /*non-public*/ final BoundMethodHandle copyWithExtendI(MethodType mt, LambdaForm lf, int narg) {
            try {
                return (BoundMethodHandle) SPECIES_DATA.extendWith(I_TYPE).constructor().invokeBasic(mt, lf, argL0, narg);
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
        @Override
        /*non-public*/ final BoundMethodHandle copyWithExtendJ(MethodType mt, LambdaForm lf, long narg) {
            try {
                return (BoundMethodHandle) SPECIES_DATA.extendWith(J_TYPE).constructor().invokeBasic(mt, lf, argL0, narg);
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
        @Override
        /*non-public*/ final BoundMethodHandle copyWithExtendF(MethodType mt, LambdaForm lf, float narg) {
            try {
                return (BoundMethodHandle) SPECIES_DATA.extendWith(F_TYPE).constructor().invokeBasic(mt, lf, argL0, narg);
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
        @Override
        /*non-public*/ final BoundMethodHandle copyWithExtendD(MethodType mt, LambdaForm lf, double narg) {
            try {
                return (BoundMethodHandle) SPECIES_DATA.extendWith(D_TYPE).constructor().invokeBasic(mt, lf, argL0, narg);
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
    }

    //
    // BMH species meta-data
    //

    /**
     * Meta-data wrapper for concrete BMH types.
     * Each BMH type corresponds to a given sequence of basic field types (LIJFD).
     * The fields are immutable; their values are fully specified at object construction.
     * Each BMH type supplies an array of getter functions which may be used in lambda forms.
     * A BMH is constructed by cloning a shorter BMH and adding one or more new field values.
     * The shortest possible BMH has zero fields; its class is SimpleMethodHandle.
     * BMH species are not interrelated by subtyping, even though it would appear that
     * a shorter BMH could serve as a supertype of a longer one which extends it.
     */
    static class SpeciesData {
        private final String                             typeChars;
        private final BasicType[]                        typeCodes;
        private final Class<? extends BoundMethodHandle> clazz;
        // Bootstrapping requires circular relations MH -> BMH -> SpeciesData -> MH
        // Therefore, we need a non-final link in the chain.  Use array elements.
        @Stable private final MethodHandle[]             constructor;
        @Stable private final MethodHandle[]             getters;
        @Stable private final NamedFunction[]            nominalGetters;
        @Stable private final SpeciesData[]              extensions;

        /*non-public*/ int fieldCount() {
            return typeCodes.length;
        }
        /*non-public*/ BasicType fieldType(int i) {
            return typeCodes[i];
        }
        /*non-public*/ char fieldTypeChar(int i) {
            return typeChars.charAt(i);
        }
        Object fieldSignature() {
            return typeChars;
        }
        public Class<? extends BoundMethodHandle> fieldHolder() {
            return clazz;
        }
        public String toString() {
            return "SpeciesData<"+fieldSignature()+">";
        }

        /**
         * Return a {@link LambdaForm.Name} containing a {@link LambdaForm.NamedFunction} that
         * represents a MH bound to a generic invoker, which in turn forwards to the corresponding
         * getter.
         */
        NamedFunction getterFunction(int i) {
            NamedFunction nf = nominalGetters[i];
            assert(nf.memberDeclaringClassOrNull() == fieldHolder());
            assert(nf.returnType() == fieldType(i));
            return nf;
        }

        NamedFunction[] getterFunctions() {
            return nominalGetters;
        }

        MethodHandle[] getterHandles() { return getters; }

        MethodHandle constructor() {
            return constructor[0];
        }

        static final SpeciesData EMPTY = new SpeciesData("", BoundMethodHandle.class);

        private SpeciesData(String types, Class<? extends BoundMethodHandle> clazz) {
            this.typeChars = types;
            this.typeCodes = basicTypes(types);
            this.clazz = clazz;
            if (!INIT_DONE) {
                this.constructor = new MethodHandle[1];  // only one ctor
                this.getters = new MethodHandle[types.length()];
                this.nominalGetters = new NamedFunction[types.length()];
            } else {
                this.constructor = Factory.makeCtors(clazz, types, null);
                this.getters = Factory.makeGetters(clazz, types, null);
                this.nominalGetters = Factory.makeNominalGetters(types, null, this.getters);
            }
            this.extensions = new SpeciesData[ARG_TYPE_LIMIT];
        }

        private void initForBootstrap() {
            assert(!INIT_DONE);
            if (constructor() == null) {
                String types = typeChars;
                Factory.makeCtors(clazz, types, this.constructor);
                Factory.makeGetters(clazz, types, this.getters);
                Factory.makeNominalGetters(types, this.nominalGetters, this.getters);
            }
        }

        private SpeciesData(String typeChars) {
            // Placeholder only.
            this.typeChars = typeChars;
            this.typeCodes = basicTypes(typeChars);
            this.clazz = null;
            this.constructor = null;
            this.getters = null;
            this.nominalGetters = null;
            this.extensions = null;
        }
        private boolean isPlaceholder() { return clazz == null; }

        private static final HashMap<String, SpeciesData> CACHE = new HashMap<>();
        static { CACHE.put("", EMPTY); }  // make bootstrap predictable
        private static final boolean INIT_DONE;  // set after <clinit> finishes...

        SpeciesData extendWith(byte type) {
            return extendWith(BasicType.basicType(type));
        }

        SpeciesData extendWith(BasicType type) {
            int ord = type.ordinal();
            SpeciesData d = extensions[ord];
            if (d != null)  return d;
            extensions[ord] = d = get(typeChars+type.basicTypeChar());
            return d;
        }

        private static SpeciesData get(String types) {
            // Acquire cache lock for query.
            SpeciesData d = lookupCache(types);
            if (!d.isPlaceholder())
                return d;
            synchronized (d) {
                // Use synch. on the placeholder to prevent multiple instantiation of one species.
                // Creating this class forces a recursive call to getForClass.
                if (lookupCache(types).isPlaceholder())
                    Factory.generateConcreteBMHClass(types);
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
            try {
                for (Class<?> c : rootCls.getDeclaredClasses()) {
                    if (rootCls.isAssignableFrom(c)) {
                        final Class<? extends BoundMethodHandle> cbmh = c.asSubclass(BoundMethodHandle.class);
                        SpeciesData d = Factory.speciesDataFromConcreteBMHClass(cbmh);
                        assert(d != null) : cbmh.getName();
                        assert(d.clazz == cbmh);
                        assert(d == lookupCache(d.typeChars));
                    }
                }
            } catch (Throwable e) {
                throw newInternalError(e);
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
        static final String INT_SIG    = "()I";

        static final String SIG_INCIPIT = "(Ljava/lang/invoke/MethodType;Ljava/lang/invoke/LambdaForm;";

        static final String[] E_THROWABLE = new String[] { "java/lang/Throwable" };

        /**
         * Generate a concrete subclass of BMH for a given combination of bound types.
         *
         * A concrete BMH species adheres to the following schema:
         *
         * <pre>
         * class Species_[[types]] extends BoundMethodHandle {
         *     [[fields]]
         *     final SpeciesData speciesData() { return SpeciesData.get("[[types]]"); }
         * }
         * </pre>
         *
         * The {@code [[types]]} signature is precisely the string that is passed to this
         * method.
         *
         * The {@code [[fields]]} section consists of one field definition per character in
         * the type signature, adhering to the naming schema described in the definition of
         * {@link #makeFieldName}.
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
         *     private Species_LLI(MethodType mt, LambdaForm lf, Object argL0, Object argL1, int argI2) {
         *         super(mt, lf);
         *         this.argL0 = argL0;
         *         this.argL1 = argL1;
         *         this.argI2 = argI2;
         *     }
         *     final SpeciesData speciesData() { return SPECIES_DATA; }
         *     final int fieldCount() { return 3; }
         *     static final SpeciesData SPECIES_DATA = SpeciesData.getForClass("LLI", Species_LLI.class);
         *     static BoundMethodHandle make(MethodType mt, LambdaForm lf, Object argL0, Object argL1, int argI2) {
         *         return new Species_LLI(mt, lf, argL0, argL1, argI2);
         *     }
         *     final BoundMethodHandle copyWith(MethodType mt, LambdaForm lf) {
         *         return new Species_LLI(mt, lf, argL0, argL1, argI2);
         *     }
         *     final BoundMethodHandle copyWithExtendL(MethodType mt, LambdaForm lf, Object narg) {
         *         return SPECIES_DATA.extendWith(L_TYPE).constructor().invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     final BoundMethodHandle copyWithExtendI(MethodType mt, LambdaForm lf, int narg) {
         *         return SPECIES_DATA.extendWith(I_TYPE).constructor().invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     final BoundMethodHandle copyWithExtendJ(MethodType mt, LambdaForm lf, long narg) {
         *         return SPECIES_DATA.extendWith(J_TYPE).constructor().invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     final BoundMethodHandle copyWithExtendF(MethodType mt, LambdaForm lf, float narg) {
         *         return SPECIES_DATA.extendWith(F_TYPE).constructor().invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         *     public final BoundMethodHandle copyWithExtendD(MethodType mt, LambdaForm lf, double narg) {
         *         return SPECIES_DATA.extendWith(D_TYPE).constructor().invokeBasic(mt, lf, argL0, argL1, argI2, narg);
         *     }
         * }
         * </pre>
         *
         * @param types the type signature, wherein reference types are erased to 'L'
         * @return the generated concrete BMH class
         */
        static Class<? extends BoundMethodHandle> generateConcreteBMHClass(String types) {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);

            String shortTypes = LambdaForm.shortenSignature(types);
            final String className  = SPECIES_PREFIX_PATH + shortTypes;
            final String sourceFile = SPECIES_PREFIX_NAME + shortTypes;
            final int NOT_ACC_PUBLIC = 0;  // not ACC_PUBLIC
            cw.visit(V1_6, NOT_ACC_PUBLIC + ACC_FINAL + ACC_SUPER, className, null, BMH, null);
            cw.visitSource(sourceFile, null);

            // emit static types and SPECIES_DATA fields
            cw.visitField(NOT_ACC_PUBLIC + ACC_STATIC, "SPECIES_DATA", SPECIES_DATA_SIG, null, null).visitEnd();

            // emit bound argument fields
            for (int i = 0; i < types.length(); ++i) {
                final char t = types.charAt(i);
                final String fieldName = makeFieldName(types, i);
                final String fieldDesc = t == 'L' ? JLO_SIG : String.valueOf(t);
                cw.visitField(ACC_FINAL, fieldName, fieldDesc, null, null).visitEnd();
            }

            MethodVisitor mv;

            // emit constructor
            mv = cw.visitMethod(ACC_PRIVATE, "<init>", makeSignature(types, true), null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0); // this
            mv.visitVarInsn(ALOAD, 1); // type
            mv.visitVarInsn(ALOAD, 2); // form

            mv.visitMethodInsn(INVOKESPECIAL, BMH, "<init>", makeSignature("", true), false);

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

            // emit implementation of speciesData()
            mv = cw.visitMethod(NOT_ACC_PUBLIC + ACC_FINAL, "speciesData", MYSPECIES_DATA_SIG, null, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, className, "SPECIES_DATA", SPECIES_DATA_SIG);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // emit implementation of fieldCount()
            mv = cw.visitMethod(NOT_ACC_PUBLIC + ACC_FINAL, "fieldCount", INT_SIG, null, null);
            mv.visitCode();
            int fc = types.length();
            if (fc <= (ICONST_5 - ICONST_0)) {
                mv.visitInsn(ICONST_0 + fc);
            } else {
                mv.visitIntInsn(SIPUSH, fc);
            }
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            // emit make()  ...factory method wrapping constructor
            mv = cw.visitMethod(NOT_ACC_PUBLIC + ACC_STATIC, "make", makeSignature(types, false), null, null);
            mv.visitCode();
            // make instance
            mv.visitTypeInsn(NEW, className);
            mv.visitInsn(DUP);
            // load mt, lf
            mv.visitVarInsn(ALOAD, 0);  // type
            mv.visitVarInsn(ALOAD, 1);  // form
            // load factory method arguments
            for (int i = 0, j = 0; i < types.length(); ++i, ++j) {
                // i counts the arguments, j counts corresponding argument slots
                char t = types.charAt(i);
                mv.visitVarInsn(typeLoadOp(t), j + 2); // parameters start at 3
                if (t == 'J' || t == 'D') {
                    ++j; // adjust argument register access
                }
            }

            // finally, invoke the constructor and return
            mv.visitMethodInsn(INVOKESPECIAL, className, "<init>", makeSignature(types, true), false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // emit copyWith()
            mv = cw.visitMethod(NOT_ACC_PUBLIC + ACC_FINAL, "copyWith", makeSignature("", false), null, null);
            mv.visitCode();
            // make instance
            mv.visitTypeInsn(NEW, className);
            mv.visitInsn(DUP);
            // load mt, lf
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            // put fields on the stack
            emitPushFields(types, className, mv);
            // finally, invoke the constructor and return
            mv.visitMethodInsn(INVOKESPECIAL, className, "<init>", makeSignature(types, true), false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // for each type, emit copyWithExtendT()
            for (BasicType type : BasicType.ARG_TYPES) {
                int ord = type.ordinal();
                char btChar = type.basicTypeChar();
                mv = cw.visitMethod(NOT_ACC_PUBLIC + ACC_FINAL, "copyWithExtend" + btChar, makeSignature(String.valueOf(btChar), false), null, E_THROWABLE);
                mv.visitCode();
                // return SPECIES_DATA.extendWith(t).constructor().invokeBasic(mt, lf, argL0, ..., narg)
                // obtain constructor
                mv.visitFieldInsn(GETSTATIC, className, "SPECIES_DATA", SPECIES_DATA_SIG);
                int iconstInsn = ICONST_0 + ord;
                assert(iconstInsn <= ICONST_5);
                mv.visitInsn(iconstInsn);
                mv.visitMethodInsn(INVOKEVIRTUAL, SPECIES_DATA, "extendWith", BMHSPECIES_DATA_EWI_SIG, false);
                mv.visitMethodInsn(INVOKEVIRTUAL, SPECIES_DATA, "constructor", "()" + MH_SIG, false);
                // load mt, lf
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                // put fields on the stack
                emitPushFields(types, className, mv);
                // put narg on stack
                mv.visitVarInsn(typeLoadOp(btChar), 3);
                // finally, invoke the constructor and return
                mv.visitMethodInsn(INVOKEVIRTUAL, MH, "invokeBasic", makeSignature(types + btChar, false), false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            // emit class initializer
            mv = cw.visitMethod(NOT_ACC_PUBLIC | ACC_STATIC, "<clinit>", VOID_SIG, null, null);
            mv.visitCode();
            mv.visitLdcInsn(types);
            mv.visitLdcInsn(Type.getObjectType(className));
            mv.visitMethodInsn(INVOKESTATIC, SPECIES_DATA, "getForClass", BMHSPECIES_DATA_GFC_SIG, false);
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
                UNSAFE.defineClass(className, classFile, 0, classFile.length,
                                   BoundMethodHandle.class.getClassLoader(), null)
                    .asSubclass(BoundMethodHandle.class);
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
            default : throw newInternalError("unrecognized type " + t);
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
                throw newInternalError(e);
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
            if (types.equals(""))  return mhs;  // hack for empty BMH species
            mhs[0] = makeCbmhCtor(cbmh, types);
            return mhs;
        }

        static NamedFunction[] makeNominalGetters(String types, NamedFunction[] nfs, MethodHandle[] getters) {
            if (nfs == null)  nfs = new NamedFunction[types.length()];
            for (int i = 0; i < nfs.length; ++i) {
                nfs[i] = new NamedFunction(getters[i]);
            }
            return nfs;
        }

        //
        // Auxiliary methods.
        //

        static SpeciesData speciesDataFromConcreteBMHClass(Class<? extends BoundMethodHandle> cbmh) {
            try {
                Field F_SPECIES_DATA = cbmh.getDeclaredField("SPECIES_DATA");
                return (SpeciesData) F_SPECIES_DATA.get(null);
            } catch (ReflectiveOperationException ex) {
                throw newInternalError(ex);
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
                return LOOKUP.findStatic(cbmh, "make", MethodType.fromMethodDescriptorString(makeSignature(types, false), null));
            } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | TypeNotPresentException e) {
                throw newInternalError(e);
            }
        }
    }

    private static final Lookup LOOKUP = Lookup.IMPL_LOOKUP;

    /**
     * All subclasses must provide such a value describing their type signature.
     */
    static final SpeciesData SPECIES_DATA = SpeciesData.EMPTY;

    private static final SpeciesData[] SPECIES_DATA_CACHE = new SpeciesData[5];
    private static SpeciesData checkCache(int size, String types) {
        int idx = size - 1;
        SpeciesData data = SPECIES_DATA_CACHE[idx];
        if (data != null)  return data;
        SPECIES_DATA_CACHE[idx] = data = getSpeciesData(types);
        return data;
    }
    static SpeciesData speciesData_L()     { return checkCache(1, "L"); }
    static SpeciesData speciesData_LL()    { return checkCache(2, "LL"); }
    static SpeciesData speciesData_LLL()   { return checkCache(3, "LLL"); }
    static SpeciesData speciesData_LLLL()  { return checkCache(4, "LLLL"); }
    static SpeciesData speciesData_LLLLL() { return checkCache(5, "LLLLL"); }
}
