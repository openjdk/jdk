/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.LambdaForm.BasicType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import jdk.internal.constant.ClassOrInterfaceDescImpl;
import jdk.internal.constant.ConstantUtils;
import jdk.internal.constant.MethodTypeDescImpl;
import jdk.internal.loader.BootLoader;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.BytecodeName;
import sun.invoke.util.Wrapper;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleNatives.Constants.REF_getStatic;
import static java.lang.invoke.MethodHandleNatives.Constants.REF_putStatic;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * Class specialization code.
 * @param <T> top class under which species classes are created.
 * @param <K> key which identifies individual specializations.
 * @param <S> species data type.
 */
/*non-public*/
abstract class ClassSpecializer<T,K,S extends ClassSpecializer<T,K,S>.SpeciesData> {

    private static final ClassDesc CD_LambdaForm = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/LambdaForm;");
    private static final ClassDesc CD_BoundMethodHandle = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/BoundMethodHandle;");
    private static final RuntimeVisibleAnnotationsAttribute STABLE_ANNOTATION = RuntimeVisibleAnnotationsAttribute.of(
            Annotation.of(ConstantUtils.referenceClassDesc(Stable.class))
    );

    private final Class<T> topClass;
    private final Class<K> keyType;
    private final Class<S> metaType;
    private final MemberName sdAccessor;
    private final String sdFieldName;
    private final List<MemberName> transformMethods;
    private final MethodType baseConstructorType;
    private final S topSpecies;
    private final ConcurrentHashMap<K, Object> cache = new ConcurrentHashMap<>();
    private final Factory factory;
    private @Stable boolean topClassIsSuper;

    /** Return the top type mirror, for type {@code T} */
    public final Class<T> topClass() { return topClass; }

    /** Return the key type mirror, for type {@code K} */
    public final Class<K> keyType() { return keyType; }

    /** Return the species metadata type mirror, for type {@code S} */
    public final Class<S> metaType() { return metaType; }

    /** Report the leading arguments (if any) required by every species factory.
     * Every species factory adds its own field types as additional arguments,
     * but these arguments always come first, in every factory method.
     */
    protected MethodType baseConstructorType() { return baseConstructorType; }

    /** Return the trivial species for the null sequence of arguments. */
    protected final S topSpecies() { return topSpecies; }

    /** Return the list of transform methods originally given at creation of this specializer. */
    protected final List<MemberName> transformMethods() { return transformMethods; }

    /** Return the factory object used to build and load concrete species code. */
    protected final Factory factory() { return factory; }

    /**
     * Constructor for this class specializer.
     * @param topClass type mirror for T
     * @param keyType type mirror for K
     * @param metaType type mirror for S
     * @param baseConstructorType principal constructor type
     * @param sdAccessor the method used to get the speciesData
     * @param sdFieldName the name of the species data field, inject the speciesData object
     * @param transformMethods optional list of transformMethods
     */
    protected ClassSpecializer(Class<T> topClass,
                               Class<K> keyType,
                               Class<S> metaType,
                               MethodType baseConstructorType,
                               MemberName sdAccessor,
                               String sdFieldName,
                               List<MemberName> transformMethods) {
        this.topClass = topClass;
        this.keyType = keyType;
        this.metaType = metaType;
        this.sdAccessor = sdAccessor;
        this.transformMethods = List.copyOf(transformMethods);
        this.sdFieldName = sdFieldName;
        this.baseConstructorType = baseConstructorType.changeReturnType(void.class);
        this.factory = makeFactory();
        K tsk = topSpeciesKey();
        S topSpecies = null;
        if (tsk != null && topSpecies == null) {
            // if there is a key, build the top species if needed:
            topSpecies = findSpecies(tsk);
        }
        this.topSpecies = topSpecies;
    }

    // Utilities for subclass constructors:
    protected static <T> Constructor<T> reflectConstructor(Class<T> defc, Class<?>... ptypes) {
        try {
            return defc.getDeclaredConstructor(ptypes);
        } catch (NoSuchMethodException ex) {
            throw newIAE(defc.getName()+"("+MethodType.methodType(void.class, ptypes)+")", ex);
        }
    }

    protected static Field reflectField(Class<?> defc, String name) {
        try {
            return defc.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            throw newIAE(defc.getName()+"."+name, ex);
        }
    }

    private static RuntimeException newIAE(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private static final Function<Object, Object> CREATE_RESERVATION = new Function<>() {
        @Override
        public Object apply(Object key) {
            return new Object();
        }
    };

    public final S findSpecies(K key) {
        // Note:  Species instantiation may throw VirtualMachineError because of
        // code cache overflow.  If this happens the species bytecode may be
        // loaded but not linked to its species metadata (with MH's etc).
        // That will cause a throw out of Factory.loadSpecies.
        //
        // In a later attempt to get the same species, the already-loaded
        // class will be present in the system dictionary, causing an
        // error when the species generator tries to reload it.
        // We try to detect this case and link the pre-existing code.
        //
        // Although it would be better to start fresh by loading a new
        // copy, we have to salvage the previously loaded but broken code.
        // (As an alternative, we might spin a new class with a new name,
        // or use the anonymous class mechanism.)
        //
        // In the end, as long as everybody goes through this findSpecies method,
        // it will ensure only one SpeciesData will be set successfully on a
        // concrete class if ever.
        // The concrete class is published via SpeciesData instance
        // returned here only after the class and species data are linked together.
        Object speciesDataOrReservation = cache.computeIfAbsent(key, CREATE_RESERVATION);
        // Separating the creation of a placeholder SpeciesData instance above
        // from the loading and linking a real one below ensures we can never
        // accidentally call computeIfAbsent recursively.
        S speciesData;
        if (speciesDataOrReservation.getClass() == Object.class) {
            synchronized (speciesDataOrReservation) {
                Object existingSpeciesData = cache.get(key);
                if (existingSpeciesData == speciesDataOrReservation) { // won the race
                    // create a new SpeciesData...
                    speciesData = newSpeciesData(key);
                    // load and link it...
                    speciesData = factory.loadSpecies(speciesData);
                    if (!cache.replace(key, existingSpeciesData, speciesData)) {
                        throw newInternalError("Concurrent loadSpecies");
                    }
                } else { // lost the race; the retrieved existingSpeciesData is the final
                    speciesData = metaType.cast(existingSpeciesData);
                }
            }
        } else {
            speciesData = metaType.cast(speciesDataOrReservation);
        }
        assert(speciesData != null && speciesData.isResolved());
        return speciesData;
    }

    /**
     * Meta-data wrapper for concrete subtypes of the top class.
     * Each concrete subtype corresponds to a given sequence of basic field types (LIJFD).
     * The fields are immutable; their values are fully specified at object construction.
     * Each species supplies an array of getter functions which may be used in lambda forms.
     * A concrete value is always constructed from the full tuple of its field values,
     * accompanied by the required constructor parameters.
     * There *may* also be transforms which cloning a species instance and
     * either replace a constructor parameter or add one or more new field values.
     * The shortest possible species has zero fields.
     * Subtypes are not interrelated among themselves by subtyping, even though
     * it would appear that a shorter species could serve as a supertype of a
     * longer one which extends it.
     */
    abstract class SpeciesData {
        // Bootstrapping requires circular relations Class -> SpeciesData -> Class
        // Therefore, we need non-final links in the chain.  Use @Stable fields.
        private final K key;
        private final List<Class<?>> fieldTypes;
        @Stable private Class<? extends T> speciesCode;
        @Stable private List<MethodHandle> factories;
        @Stable private List<MethodHandle> getters;
        @Stable private List<LambdaForm.NamedFunction> nominalGetters;
        @Stable private final MethodHandle[] transformHelpers = new MethodHandle[transformMethods.size()];

        protected SpeciesData(K key) {
            this.key = keyType.cast(Objects.requireNonNull(key));
            List<Class<?>> types = deriveFieldTypes(key);
            this.fieldTypes = List.copyOf(types);
        }

        public final K key() {
            return key;
        }

        protected final List<Class<?>> fieldTypes() {
            return fieldTypes;
        }

        protected final int fieldCount() {
            return fieldTypes.size();
        }

        protected ClassSpecializer<T,K,S> outer() {
            return ClassSpecializer.this;
        }

        protected final boolean isResolved() {
            return speciesCode != null && factories != null && !factories.isEmpty();
        }

        @Override public String toString() {
            return metaType.getSimpleName() + "[" + key.toString() + " => " + (isResolved() ? speciesCode.getSimpleName() : "UNRESOLVED") + "]";
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ClassSpecializer<?, ?, ?>.SpeciesData that)) {
                return false;
            }

            return this.outer() == that.outer() && this.key.equals(that.key);
        }

        /** Throws NPE if this species is not yet resolved. */
        protected final Class<? extends T> speciesCode() {
            return Objects.requireNonNull(speciesCode);
        }

        /**
         * Return a {@link MethodHandle} which can get the indexed field of this species.
         * The return type is the type of the species field it accesses.
         * The argument type is the {@code fieldHolder} class of this species.
         */
        protected MethodHandle getter(int i) {
            return getters.get(i);
        }

        /**
         * Return a {@link LambdaForm.Name} containing a {@link LambdaForm.NamedFunction} that
         * represents a MH bound to a generic invoker, which in turn forwards to the corresponding
         * getter.
         */
        protected LambdaForm.NamedFunction getterFunction(int i) {
            LambdaForm.NamedFunction nf = nominalGetters.get(i);
            assert(nf.memberDeclaringClassOrNull() == speciesCode());
            assert(nf.returnType() == BasicType.basicType(fieldTypes.get(i)));
            return nf;
        }

        protected List<LambdaForm.NamedFunction> getterFunctions() {
            return nominalGetters;
        }

        protected List<MethodHandle> getters() {
            return getters;
        }

        protected MethodHandle factory() {
            return factories.get(0);
        }

        protected MethodHandle transformHelper(int whichtm) {
            MethodHandle mh = transformHelpers[whichtm];
            if (mh != null)  return mh;
            mh = deriveTransformHelper(transformMethods().get(whichtm), whichtm);
            // Do a little type checking before we start using the MH.
            // (It will be called with invokeBasic, so this is our only chance.)
            final MethodType mt = transformHelperType(whichtm);
            mh = mh.asType(mt);
            return transformHelpers[whichtm] = mh;
        }

        private final MethodType transformHelperType(int whichtm) {
            MemberName tm = transformMethods().get(whichtm);
            MethodType tmt = tm.getMethodType();
            ArrayList<Class<?>> args = new ArrayList<>();
            ArrayList<Class<?>> fields = new ArrayList<>();
            Collections.addAll(args, tmt.ptypes());
            fields.addAll(fieldTypes());
            List<Class<?>> helperArgs = deriveTransformHelperArguments(tm, whichtm, args, fields);
            return MethodType.methodType(tmt.returnType(), helperArgs);
        }

        // Hooks for subclasses:

        /**
         * Given a key, derive the list of field types, which all instances of this
         * species must store.
         */
        protected abstract List<Class<?>> deriveFieldTypes(K key);

        /**
         * Given the index of a method in the transforms list, supply a factory
         * method that takes the arguments of the transform, plus the local fields,
         * and produce a value of the required type.
         * You can override this to return null or throw if there are no transforms.
         * This method exists so that the transforms can be "grown" lazily.
         * This is necessary if the transform *adds* a field to an instance,
         * which sometimes requires the creation, on the fly, of an extended species.
         * This method is only called once for any particular parameter.
         * The species caches the result in a private array.
         *
         * @param transform the transform being implemented
         * @param whichtm the index of that transform in the original list of transforms
         * @return the method handle which creates a new result from a mix of transform
         * arguments and field values
         */
        protected abstract MethodHandle deriveTransformHelper(MemberName transform, int whichtm);

        /**
         * During code generation, this method is called once per transform to determine
         * what is the mix of arguments to hand to the transform-helper.  The bytecode
         * which marshals these arguments is open-coded in the species-specific transform.
         * The two lists are of opaque objects, which you shouldn't do anything with besides
         * reordering them into the output list.  (They are both mutable, to make editing
         * easier.)  The imputed types of the args correspond to the transform's parameter
         * list, while the imputed types of the fields correspond to the species field types.
         * After code generation, this method may be called occasionally by error-checking code.
         *
         * @param transform the transform being implemented
         * @param whichtm the index of that transform in the original list of transforms
         * @param args a list of opaque objects representing the incoming transform arguments
         * @param fields a list of opaque objects representing the field values of the receiver
         * @param <X> the common element type of the various lists
         * @return a new list
         */
        protected abstract <X> List<X> deriveTransformHelperArguments(MemberName transform, int whichtm,
                                                                      List<X> args, List<X> fields);

        /** Given a key, generate the name of the class which implements the species for that key.
         * This algorithm must be stable.
         *
         * @return class name, which by default is {@code outer().topClass().getName() + "$Species_" + deriveTypeString(key)}
         */
        protected String deriveClassName() {
            return outer().topClass().getName() + "$Species_" + deriveTypeString();
        }

        /**
         * Default implementation collects basic type characters,
         * plus possibly type names, if some types don't correspond
         * to basic types.
         *
         * @return a string suitable for use in a class name
         */
        protected String deriveTypeString() {
            List<Class<?>> types = fieldTypes();
            StringBuilder buf = new StringBuilder();
            StringBuilder end = new StringBuilder();
            for (Class<?> type : types) {
                BasicType basicType = BasicType.basicType(type);
                if (basicType.basicTypeClass() == type) {
                    buf.append(basicType.basicTypeChar());
                } else {
                    buf.append('V');
                    end.append(type.descriptorString());
                }
            }
            String typeString;
            if (end.length() > 0) {
                typeString = BytecodeName.toBytecodeName(buf.append("_").append(end).toString());
            } else {
                typeString = buf.toString();
            }
            return LambdaForm.shortenSignature(typeString);
        }

        /**
         * Report what immediate super-class to use for the concrete class of this species.
         * Normally this is {@code topClass}, but if that is an interface, the factory must override.
         * The super-class must provide a constructor which takes the {@code baseConstructorType} arguments, if any.
         * This hook also allows the code generator to use more than one canned supertype for species.
         *
         * @return the super-class of the class to be generated
         */
        protected Class<? extends T> deriveSuperClass() {
            final Class<T> topc = topClass();
            if (!topClassIsSuper) {
                try {
                    final Constructor<T> con = reflectConstructor(topc, baseConstructorType().ptypes());
                    if (!topc.isInterface() && !Modifier.isPrivate(con.getModifiers())) {
                        topClassIsSuper = true;
                    }
                } catch (Exception|InternalError ex) {
                    // fall through...
                }
                if (!topClassIsSuper) {
                    throw newInternalError("must override if the top class cannot serve as a super class");
                }
            }
            return topc;
        }
    }

    protected abstract S newSpeciesData(K key);

    protected K topSpeciesKey() {
        return null;  // null means don't report a top species
    }

    /**
     * Code generation support for instances.
     * Subclasses can modify the behavior.
     */
    class Factory {
        /**
         * Constructs a factory.
         */
        Factory() {}

        /**
         * Get a concrete subclass of the top class for a given combination of bound types.
         *
         * @param speciesData the species requiring the class, not yet linked
         * @return a linked version of the same species
         */
        S loadSpecies(S speciesData) {
            String className = speciesData.deriveClassName();
            assert(className.indexOf('/') < 0) : className;
            Class<?> salvage = null;
            try {
                salvage = BootLoader.loadClassOrNull(className);
            } catch (Error ex) {
                // ignore
            } finally {
                traceSpeciesType(className, salvage);
            }
            final Class<? extends T> speciesCode;
            if (salvage != null) {
                speciesCode = salvage.asSubclass(topClass());
                linkSpeciesDataToCode(speciesData, speciesCode);
                linkCodeToSpeciesData(speciesCode, speciesData, true);
            } else {
                // Not pregenerated, generate the class
                try {
                    speciesCode = generateConcreteSpeciesCode(className, speciesData);
                    // This operation causes a lot of churn:
                    linkSpeciesDataToCode(speciesData, speciesCode);
                    // This operation commits the relation, but causes little churn:
                    linkCodeToSpeciesData(speciesCode, speciesData, false);
                } catch (Error ex) {
                    // We can get here if there is a race condition loading a class.
                    // Or maybe we are out of resources.  Back out of the CHM.get and retry.
                    throw ex;
                }
            }

            if (!speciesData.isResolved()) {
                throw newInternalError("bad species class linkage for " + className + ": " + speciesData);
            }
            assert(speciesData == loadSpeciesDataFromCode(speciesCode));
            return speciesData;
        }

        /**
         * Generate a concrete subclass of the top class for a given combination of bound types.
         *
         * A concrete species subclass roughly matches the following schema:
         *
         * <pre>
         * class Species_[[types]] extends [[T]] {
         *     final [[S]] speciesData() { return ... }
         *     static [[T]] make([[fields]]) { return ... }
         *     [[fields]]
         *     final [[T]] transform([[args]]) { return ... }
         * }
         * </pre>
         *
         * The {@code [[types]]} signature is precisely the key for the species.
         *
         * The {@code [[fields]]} section consists of one field definition per character in
         * the type signature, adhering to the naming schema described in the definition of
         * {@link #chooseFieldName}.
         *
         * For example, a concrete species for two references and one integral bound value
         * has a shape like the following:
         *
         * <pre>
         * class TopClass {
         *     ...
         *     private static final class Species_LLI extends TopClass {
         *         final Object argL0;
         *         final Object argL1;
         *         final int argI2;
         *         private Species_LLI(CT ctarg, ..., Object argL0, Object argL1, int argI2) {
         *             super(ctarg, ...);
         *             this.argL0 = argL0;
         *             this.argL1 = argL1;
         *             this.argI2 = argI2;
         *         }
         *         final SpeciesData speciesData() { return BMH_SPECIES; }
         *         &#64;Stable static SpeciesData BMH_SPECIES; // injected afterwards
         *         static TopClass make(CT ctarg, ..., Object argL0, Object argL1, int argI2) {
         *             return new Species_LLI(ctarg, ..., argL0, argL1, argI2);
         *         }
         *         final TopClass copyWith(CT ctarg, ...) {
         *             return new Species_LLI(ctarg, ..., argL0, argL1, argI2);
         *         }
         *         // two transforms, for the sake of illustration:
         *         final TopClass copyWithExtendL(CT ctarg, ..., Object narg) {
         *             return BMH_SPECIES.transform(L_TYPE).invokeBasic(ctarg, ..., argL0, argL1, argI2, narg);
         *         }
         *         final TopClass copyWithExtendI(CT ctarg, ..., int narg) {
         *             return BMH_SPECIES.transform(I_TYPE).invokeBasic(ctarg, ..., argL0, argL1, argI2, narg);
         *         }
         *     }
         * }
         * </pre>
         *
         * @param className of the species
         * @param speciesData what species we are generating
         * @return the generated concrete TopClass class
         */
        Class<? extends T> generateConcreteSpeciesCode(String className, ClassSpecializer<T,K,S>.SpeciesData speciesData) {
            byte[] classFile = generateConcreteSpeciesCodeFile(className, speciesData);
            var lookup = new MethodHandles.Lookup(topClass);
            Class<?> speciesCode = lookup.makeClassDefiner(classBCName(className), classFile, dumper())
                                         .defineClass(false);
            return speciesCode.asSubclass(topClass());
        }

        // These are named like constants because there is only one per specialization scheme:

        private final ClassDesc CD_SPECIES_DATA = classDesc(metaType);
        private final MethodTypeDesc MTD_SPECIES_DATA = MethodTypeDescImpl.ofValidated(CD_SPECIES_DATA);
        private final String SPECIES_DATA_NAME = sdAccessor.getName();
        private final int SPECIES_DATA_MODS = sdAccessor.getModifiers();
        private final List<String> TRANSFORM_NAMES;  // derived from transformMethods
        private final List<MethodType> TRANSFORM_TYPES;
        private final List<Integer> TRANSFORM_MODS;
        {
            // Tear apart transformMethods to get the names, types, and modifiers.
            List<String> tns = new ArrayList<>();
            List<MethodType> tts = new ArrayList<>();
            List<Integer> tms = new ArrayList<>();
            for (int i = 0; i < transformMethods.size(); i++) {
                MemberName tm = transformMethods.get(i);
                tns.add(tm.getName());
                final MethodType tt = tm.getMethodType();
                tts.add(tt);
                tms.add(tm.getModifiers());
            }
            TRANSFORM_NAMES = List.of(tns.toArray(new String[0]));
            TRANSFORM_TYPES = List.of(tts.toArray(new MethodType[0]));
            TRANSFORM_MODS = List.of(tms.toArray(new Integer[0]));
        }
        private static final MethodTypeDesc MTD_TRANFORM_HELPER = MethodTypeDescImpl.ofValidated(CD_MethodHandle, CD_int);
        private static final int ACC_PPP = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED;

        /*non-public*/
        byte[] generateConcreteSpeciesCodeFile(String className0, ClassSpecializer<T,K,S>.SpeciesData speciesData) {
            final ClassDesc classDesc = ClassDesc.of(className0);
            final ClassDesc superClassDesc = classDesc(speciesData.deriveSuperClass());
            return ClassFile.of().build(classDesc, new Consumer<>() {
                @Override
                public void accept(ClassBuilder clb) {
                    clb.withFlags(ACC_FINAL | ACC_SUPER)
                       .withSuperclass(superClassDesc)
                       .with(SourceFileAttribute.of(classDesc.displayName()))

                    // emit static types and BMH_SPECIES fields
                       .withField(sdFieldName, CD_SPECIES_DATA, new Consumer<>() {
                           @Override
                           public void accept(FieldBuilder fb) {
                               fb.withFlags(ACC_STATIC)
                                 .with(STABLE_ANNOTATION);
                           }
                       });

                    // handy holder for dealing with groups of typed values (ctor arguments and fields)
                    class Var {
                        final int index;
                        final String name;
                        final Class<?> type;
                        final ClassDesc desc;
                        final BasicType basicType;
                        final int slotIndex;
                        Var(int index, int slotIndex) {
                            this.index = index;
                            this.slotIndex = slotIndex;
                            name = null; type = null; desc = null;
                            basicType = BasicType.V_TYPE;
                        }
                        Var(String name, Class<?> type, Var prev) {
                            int slotIndex = prev.nextSlotIndex();
                            int index = prev.nextIndex();
                            if (name == null)  name = "x";
                            if (name.endsWith("#"))
                                name = name.substring(0, name.length()-1) + index;
                            assert(!type.equals(void.class));
                            this.index = index;
                            this.name = name;
                            this.type = type;
                            this.desc = classDesc(type);
                            this.basicType = BasicType.basicType(type);
                            this.slotIndex = slotIndex;
                        }
                        Var lastOf(List<Var> vars) {
                            int n = vars.size();
                            return (n == 0 ? this : vars.get(n-1));
                        }
                        <X> List<Var> fromTypes(List<X> types) {
                            Var prev = this;
                            ArrayList<Var> result = new ArrayList<>(types.size());
                            int i = 0;
                            for (X x : types) {
                                String vn = name;
                                Class<?> vt;
                                if (x instanceof Class<?> cl) {
                                    vt = cl;
                                    // make the names friendlier if debugging
                                    assert((vn = vn + "_" + (i++)) != null);
                                } else {
                                    @SuppressWarnings("unchecked")
                                    Var v = (Var) x;
                                    vn = v.name;
                                    vt = v.type;
                                }
                                prev = new Var(vn, vt, prev);
                                result.add(prev);
                            }
                            return result;
                        }

                        int slotSize() { return basicType.basicTypeSlots(); }
                        int nextIndex() { return index + (slotSize() == 0 ? 0 : 1); }
                        int nextSlotIndex() { return slotIndex >= 0 ? slotIndex + slotSize() : slotIndex; }
                        boolean isInHeap() { return slotIndex < 0; }
                        void emitLoadInstruction(CodeBuilder cob) {
                            cob.loadLocal(basicType.btKind, slotIndex);
                        }
                    }

                    final Var NO_THIS = new Var(0, 0),
                            AFTER_THIS = new Var(0, 1),
                            IN_HEAP = new Var(0, -1);

                    // figure out the field types
                    final List<Class<?>> fieldTypes = speciesData.fieldTypes();
                    final List<Var> fields = new ArrayList<>(fieldTypes.size());
                    {
                        Var nextF = IN_HEAP;
                        for (Class<?> ft : fieldTypes) {
                            String fn = chooseFieldName(ft, nextF.nextIndex());
                            nextF = new Var(fn, ft, nextF);
                            fields.add(nextF);
                        }
                    }

                    // emit bound argument fields
                    for (Var field : fields) {
                        clb.withField(field.name, field.desc, ACC_FINAL);
                    }

                    // emit implementation of speciesData()
                    clb.withMethodBody(SPECIES_DATA_NAME, MTD_SPECIES_DATA, (SPECIES_DATA_MODS & ACC_PPP) | ACC_FINAL,
                            new Consumer<>() {
                                @Override
                                public void accept(CodeBuilder cob) {
                                    cob.getstatic(classDesc, sdFieldName, CD_SPECIES_DATA)
                                            .areturn();
                                }
                            });

                    // figure out the constructor arguments
                    MethodType superCtorType = ClassSpecializer.this.baseConstructorType();
                    MethodType thisCtorType = superCtorType.appendParameterTypes(fieldTypes);

                    // emit constructor
                    clb.withMethodBody(INIT_NAME, methodDesc(thisCtorType), ACC_PRIVATE,
                            new Consumer<>() {
                                @Override
                                public void accept(CodeBuilder cob) {
                                    cob.aload(0); // this

                                    final List<Var> ctorArgs = AFTER_THIS.fromTypes(superCtorType.parameterList());
                                    for (Var ca : ctorArgs) {
                                        ca.emitLoadInstruction(cob);
                                    }

                                    // super(ca...)
                                    cob.invokespecial(superClassDesc, INIT_NAME, methodDesc(superCtorType));

                                    // store down fields
                                    Var lastFV = AFTER_THIS.lastOf(ctorArgs);
                                    for (Var f : fields) {
                                        // this.argL1 = argL1
                                        cob.aload(0);  // this
                                        lastFV = new Var(f.name, f.type, lastFV);
                                        lastFV.emitLoadInstruction(cob);
                                        cob.putfield(classDesc, f.name, f.desc);
                                    }

                                    cob.return_();
                                }
                            });

                    // emit make()  ...factory method wrapping constructor
                    MethodType ftryType = thisCtorType.changeReturnType(topClass());
                    clb.withMethodBody("make", methodDesc(ftryType), ACC_STATIC,
                            new Consumer<>() {
                                @Override
                                public void accept(CodeBuilder cob) {
                                    // make instance
                                    cob.new_(classDesc)
                                            .dup();
                                    // load factory method arguments:  ctarg... and arg...
                                    for (Var v : NO_THIS.fromTypes(ftryType.parameterList())) {
                                        v.emitLoadInstruction(cob);
                                    }

                                    // finally, invoke the constructor and return
                                    cob.invokespecial(classDesc, INIT_NAME, methodDesc(thisCtorType))
                                            .areturn();
                                }
                            });

                    // For each transform, emit the customized override of the transform method.
                    // This method mixes together some incoming arguments (from the transform's
                    // static type signature) with the field types themselves, and passes
                    // the resulting mish-mosh of values to a method handle produced by
                    // the species itself.  (Typically this method handle is the factory
                    // method of this species or a related one.)
                    for (int i = 0; i < TRANSFORM_NAMES.size(); i++) {
                        final int whichtm = i;
                        final String     TNAME = TRANSFORM_NAMES.get(whichtm);
                        final MethodType TTYPE = TRANSFORM_TYPES.get(whichtm);
                        final int        TMODS = TRANSFORM_MODS.get(whichtm);
                        clb.withMethod(TNAME, methodDesc(TTYPE), (TMODS & ACC_PPP) | ACC_FINAL, new Consumer<MethodBuilder>() {
                            @Override
                            public void accept(MethodBuilder mb) {
                                mb.with(ExceptionsAttribute.ofSymbols(CD_Throwable))
                                  .withCode(new Consumer<CodeBuilder>() {
                                    @Override
                                    public void accept(CodeBuilder cob) {
                                        // return a call to the corresponding "transform helper", something like this:
                                        //   MY_SPECIES.transformHelper(whichtm).invokeBasic(ctarg, ..., argL0, ..., xarg)
                                        cob.getstatic(classDesc, sdFieldName, CD_SPECIES_DATA)
                                                .loadConstant(whichtm)
                                                .invokevirtual(CD_SPECIES_DATA, "transformHelper", MTD_TRANFORM_HELPER);

                                        List<Var> targs = AFTER_THIS.fromTypes(TTYPE.parameterList());
                                        List<Var> tfields = new ArrayList<>(fields);
                                        // mix them up and load them for the transform helper:
                                        List<Var> helperArgs = speciesData.deriveTransformHelperArguments(transformMethods.get(whichtm), whichtm, targs, tfields);
                                        ClassDesc[] helperTypes = new ClassDesc[helperArgs.size()];
                                        for (int hi = 0; hi < helperTypes.length; hi++) {
                                            Var ha = helperArgs.get(hi);
                                            helperTypes[hi] = ha.basicType.basicTypeWrapper().basicClassDescriptor();
                                            if (ha.isInHeap()) {
                                                assert(tfields.contains(ha));
                                                cob.aload(0);
                                                cob.getfield(classDesc, ha.name, ha.desc);
                                            } else {
                                                assert(targs.contains(ha));
                                                ha.emitLoadInstruction(cob);
                                            }
                                        }

                                        // jump into the helper (which is probably a factory method)
                                        final Class<?> rtype = TTYPE.returnType();
                                        if (!rtype.isPrimitive()) {
                                            cob.invokevirtual(CD_MethodHandle, "invokeBasic", MethodTypeDescImpl.ofValidated(CD_Object, helperTypes))
                                                    .checkcast(classDesc(rtype))
                                                    .areturn();
                                        } else {
                                            throw newInternalError("NYI: transform of type "+rtype);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }
            });
        }

        //
        // Getter MH generation.
        //

        private MethodHandle findGetter(Class<?> speciesCode, List<Class<?>> types, int index) {
            Class<?> fieldType = types.get(index);
            String fieldName = chooseFieldName(fieldType, index);
            try {
                return IMPL_LOOKUP.findGetter(speciesCode, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw newInternalError(e);
            }
        }

        private List<MethodHandle> findGetters(Class<?> speciesCode, List<Class<?>> types) {
            MethodHandle[] mhs = new MethodHandle[types.size()];
            for (int i = 0; i < mhs.length; ++i) {
                mhs[i] = findGetter(speciesCode, types, i);
                assert(mhs[i].internalMemberName().getDeclaringClass() == speciesCode);
            }
            return List.of(mhs);
        }

        private List<MethodHandle> findFactories(Class<? extends T> speciesCode, List<Class<?>> types) {
            MethodHandle[] mhs = new MethodHandle[1];
            mhs[0] = findFactory(speciesCode, types);
            return List.of(mhs);
        }

        List<LambdaForm.NamedFunction> makeNominalGetters(List<Class<?>> types, List<MethodHandle> getters) {
            LambdaForm.NamedFunction[] nfs = new LambdaForm.NamedFunction[types.size()];
            for (int i = 0; i < nfs.length; ++i) {
                nfs[i] = new LambdaForm.NamedFunction(getters.get(i));
            }
            return List.of(nfs);
        }

        //
        // Auxiliary methods.
        //

        protected void linkSpeciesDataToCode(ClassSpecializer<T,K,S>.SpeciesData speciesData, Class<? extends T> speciesCode) {
            speciesData.speciesCode = speciesCode.asSubclass(topClass);
            final List<Class<?>> types = speciesData.fieldTypes;
            speciesData.factories = this.findFactories(speciesCode, types);
            speciesData.getters = this.findGetters(speciesCode, types);
            speciesData.nominalGetters = this.makeNominalGetters(types, speciesData.getters);
        }

        private Field reflectSDField(Class<? extends T> speciesCode) {
            final Field field = reflectField(speciesCode, sdFieldName);
            assert(field.getType() == metaType);
            assert(Modifier.isStatic(field.getModifiers()));
            return field;
        }

        private S readSpeciesDataFromCode(Class<? extends T> speciesCode) {
            try {
                MemberName sdField = IMPL_LOOKUP.resolveOrFail(REF_getStatic, speciesCode, sdFieldName, metaType);
                Object base = MethodHandleNatives.staticFieldBase(sdField);
                long offset = MethodHandleNatives.staticFieldOffset(sdField);
                UNSAFE.loadFence();
                return metaType.cast(UNSAFE.getReference(base, offset));
            } catch (Error err) {
                throw err;
            } catch (Exception ex) {
                throw newInternalError("Failed to load speciesData from speciesCode: " + speciesCode.getName(), ex);
            } catch (Throwable t) {
                throw uncaughtException(t);
            }
        }

        protected S loadSpeciesDataFromCode(Class<? extends T> speciesCode) {
            if (speciesCode == topClass()) {
                return topSpecies;
            }
            S result = readSpeciesDataFromCode(speciesCode);
            if (result.outer() != ClassSpecializer.this) {
                throw newInternalError("wrong class");
            }
            return result;
        }

        protected void linkCodeToSpeciesData(Class<? extends T> speciesCode, ClassSpecializer<T,K,S>.SpeciesData speciesData, boolean salvage) {
            try {
                assert(readSpeciesDataFromCode(speciesCode) == null ||
                    (salvage && readSpeciesDataFromCode(speciesCode).equals(speciesData)));

                MemberName sdField = IMPL_LOOKUP.resolveOrFail(REF_putStatic, speciesCode, sdFieldName, metaType);
                Object base = MethodHandleNatives.staticFieldBase(sdField);
                long offset = MethodHandleNatives.staticFieldOffset(sdField);
                UNSAFE.storeFence();
                UNSAFE.putReference(base, offset, speciesData);
                UNSAFE.storeFence();
            } catch (Error err) {
                throw err;
            } catch (Exception ex) {
                throw newInternalError("Failed to link speciesData to speciesCode: " + speciesCode.getName(), ex);
            } catch (Throwable t) {
                throw uncaughtException(t);
            }
        }

        /**
         * Field names in concrete species classes adhere to this pattern:
         * type + index, where type is a single character (L, I, J, F, D).
         * The factory subclass can customize this.
         * The name is purely cosmetic, since it applies to a private field.
         */
        protected String chooseFieldName(Class<?> type, int index) {
            BasicType bt = BasicType.basicType(type);
            return "" + bt.basicTypeChar() + index;
        }

        MethodHandle findFactory(Class<? extends T> speciesCode, List<Class<?>> types) {
            final MethodType type = baseConstructorType().changeReturnType(topClass()).appendParameterTypes(types);
            try {
                return IMPL_LOOKUP.findStatic(speciesCode, "make", type);
            } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | TypeNotPresentException e) {
                throw newInternalError(e);
            }
        }
    }

    /** Hook that virtualizes the Factory class, allowing subclasses to extend it. */
    protected Factory makeFactory() {
        return new Factory();
    }


    // Other misc helpers:
    static String classBCName(String str) {
        assert(str.indexOf('/') < 0) : str;
        return str.replace('.', '/');
    }

    static ClassDesc classDesc(Class<?> cls) {
        return cls.isPrimitive() ? Wrapper.forPrimitiveType(cls).basicClassDescriptor()
             : cls == Object.class ? CD_Object
             : cls == MethodType.class ? CD_MethodType
             : cls == LambdaForm.class ? CD_LambdaForm
             : cls == BoundMethodHandle.class ? CD_BoundMethodHandle
             : ConstantUtils.referenceClassDesc(cls.descriptorString());
    }

    static MethodTypeDesc methodDesc(MethodType mt) {
        var params = new ClassDesc[mt.parameterCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = classDesc(mt.parameterType(i));
        }
        return MethodTypeDescImpl.ofValidated(classDesc(mt.returnType()), params);
    }
}
