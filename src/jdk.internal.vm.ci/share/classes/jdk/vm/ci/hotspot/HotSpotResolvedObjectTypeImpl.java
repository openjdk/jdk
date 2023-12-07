/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.hotspot;

import static java.util.Objects.requireNonNull;
import static jdk.vm.ci.hotspot.CompilerToVM.compilerToVM;
import static jdk.vm.ci.hotspot.HotSpotConstantPool.isSignaturePolymorphicHolder;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.HotSpotModifiers.jvmClassModifiers;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import jdk.internal.vm.VMSupport;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AnnotationData;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.Assumptions.ConcreteMethod;
import jdk.vm.ci.meta.Assumptions.ConcreteSubtype;
import jdk.vm.ci.meta.Assumptions.LeafType;
import jdk.vm.ci.meta.Assumptions.NoFinalizableSubclass;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Implementation of {@link JavaType} for resolved non-primitive HotSpot classes. This class is not
 * an {@link MetaspaceHandleObject} because it doesn't have to be scanned for GC. It's liveness is
 * maintained by a reference to the {@link Class} instance.
 */
final class HotSpotResolvedObjectTypeImpl extends HotSpotResolvedJavaType implements HotSpotResolvedObjectType, MetaspaceObject {

    private static final HotSpotResolvedJavaField[] NO_FIELDS = new HotSpotResolvedJavaField[0];
    private static final int METHOD_CACHE_ARRAY_CAPACITY = 8;
    private static final SortByOffset fieldSortingMethod = new SortByOffset();

    /**
     * The {@code Klass*} of this type.
     */
    private final long klassPointer;

    private HotSpotResolvedJavaMethodImpl[] methodCacheArray;
    private HashMap<Long, HotSpotResolvedJavaMethodImpl> methodCacheHashMap;
    private volatile HotSpotResolvedJavaField[] instanceFields;
    private volatile HotSpotResolvedObjectTypeImpl[] interfaces;
    private HotSpotConstantPool constantPool;
    private final JavaConstant mirror;
    private HotSpotResolvedObjectTypeImpl superClass;

    /**
     * Lazily initialized cache for {@link #getComponentType()}. Set to {@code this}, if this has no
     * component type (i.e., this is an non-array type).
     */
    private HotSpotResolvedJavaType componentType;

    /**
     * Lazily initialized cache for FieldInfo.
     */
    private FieldInfo[] fieldInfo;

    /**
     * Managed exclusively by {@link HotSpotJDKReflection#getField}.
     */
    HashMap<HotSpotResolvedJavaFieldImpl, Field> reflectionFieldCache;

    static HotSpotResolvedObjectTypeImpl getJavaLangObject() {
        return runtime().getJavaLangObject();
    }

    /**
     * Gets the JVMCI mirror from a HotSpot type.
     *
     * Called from the VM.
     *
     * @param klassPointer a native pointer to the Klass*
     * @return the {@link ResolvedJavaType} corresponding to {@code javaClass}
     */
    @SuppressWarnings("unused")
    @VMEntryPoint
    private static HotSpotResolvedObjectTypeImpl fromMetaspace(long klassPointer) {
        return runtime().fromMetaspace(klassPointer);
    }

    /**
     * Creates the JVMCI mirror for a {@link Class} object.
     *
     * <b>NOTE</b>: Creating an instance of this class does not install the mirror for the
     * {@link Class} type.
     * </p>
     *
     * @param klass the {@code Klass*} for the type
     */
    @SuppressWarnings("try")
    HotSpotResolvedObjectTypeImpl(long klass, String name) {
        super(name);
        assert klass != 0;
        this.klassPointer = klass;

        // The mirror object must be in the global scope since
        // this object will be cached in HotSpotJVMCIRuntime.resolvedJavaTypes
        // and live across more than one compilation.
        try (HotSpotObjectConstantScope global = HotSpotObjectConstantScope.enterGlobalScope()) {
            this.mirror = runtime().compilerToVm.getJavaMirror(this);
            assert getName().charAt(0) != '[' || isArray() : getName();
        }
    }

    /**
     * Gets the {@code Klass*} for this type.
     */
    long getKlassPointer() {
        long metaspacePointer = getMetaspacePointer();
        if (metaspacePointer == 0) {
            throw new NullPointerException("Klass* is null");
        }
        return metaspacePointer;
    }

    @Override
    public long getMetaspacePointer() {
        return klassPointer;
    }

    @Override
    public int getModifiers() {
        if (isArray()) {
            return (getElementalType().getModifiers() & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.FINAL | Modifier.ABSTRACT;
        } else {
            return getAccessFlags() & jvmClassModifiers();
        }
    }

    public int getAccessFlags() {
        HotSpotVMConfig config = config();
        return UNSAFE.getInt(getKlassPointer() + config.klassAccessFlagsOffset);
    }

    @Override
    public ResolvedJavaType getComponentType() {
        if (componentType == null) {
            if (isArray()) {
                componentType = runtime().compilerToVm.getComponentType(this);
            } else {
                componentType = this;
            }
        }
        return this.equals(componentType) ? null : componentType;
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        if (isLeaf()) {
            // No assumptions are required.
            return new AssumptionResult<>(this);
        }
        HotSpotVMConfig config = config();
        if (isArray()) {
            ResolvedJavaType elementalType = getElementalType();
            AssumptionResult<ResolvedJavaType> elementType = elementalType.findLeafConcreteSubtype();
            if (elementType != null && elementType.getResult().equals(elementalType)) {
                /*
                 * If the elementType is leaf then the array is leaf under the same assumptions but
                 * only if the element type is exactly the leaf type. The element type can be
                 * abstract even if there is only one implementor of the abstract type.
                 */
                AssumptionResult<ResolvedJavaType> result = new AssumptionResult<>(this);
                result.add(elementType);
                return result;
            }
            return null;
        } else if (isInterface()) {
            HotSpotResolvedObjectTypeImpl implementor = getSingleImplementor();
            /*
             * If the implementor field contains itself that indicates that the interface has more
             * than one implementors (see: InstanceKlass::add_implementor).
             */
            if (implementor == null || implementor.equals(this)) {
                return null;
            }

            assert !implementor.isInterface();
            if (implementor.isAbstract() || !implementor.isLeafClass()) {
                AssumptionResult<ResolvedJavaType> leafConcreteSubtype = implementor.findLeafConcreteSubtype();
                if (leafConcreteSubtype != null) {
                    assert !leafConcreteSubtype.getResult().equals(implementor);
                    AssumptionResult<ResolvedJavaType> newResult = new AssumptionResult<>(leafConcreteSubtype.getResult(), new ConcreteSubtype(this, implementor));
                    // Accumulate leaf assumptions and return the combined result.
                    newResult.add(leafConcreteSubtype);
                    return newResult;
                }
                return null;
            }
            return concreteSubtype(implementor);
        } else {
            HotSpotResolvedObjectTypeImpl type = this;
            while (type.isAbstract()) {
                HotSpotResolvedObjectTypeImpl subklass = type.getSubklass();
                if (subklass == null) {
                    return null;
                }
                if (compilerToVM().getResolvedJavaType(subklass, config.nextSiblingOffset, false) != null) {
                    return null;
                }
                type = subklass;
            }
            if (type.isAbstract() || type.isInterface() || !type.isLeafClass()) {
                return null;
            }
            if (this.isAbstract()) {
                return concreteSubtype(type);
            } else {
                assert this.equals(type);
                return new AssumptionResult<>(type, new LeafType(type));
            }
        }
    }

    private AssumptionResult<ResolvedJavaType> concreteSubtype(HotSpotResolvedObjectTypeImpl type) {
        if (type.isLeaf()) {
            return new AssumptionResult<>(type, new ConcreteSubtype(this, type));
        } else {
            return new AssumptionResult<>(type, new LeafType(type), new ConcreteSubtype(this, type));
        }
    }

    /**
     * Returns if type {@code type} is a leaf class. This is the case if the
     * {@code Klass::_subklass} field of the underlying class is zero.
     *
     * @return true if the type is a leaf class
     */
    private boolean isLeafClass() {
        // In general, compilerToVM().getResolvedJavaType should always be used to read a Klass*
        // from HotSpot data structures but that has the side effect of creating a strong reference
        // to the Class which we do not want since it can cause class unloading problems.  Since
        // this code is only checking for null vs non-null so it should be safe to perform this
        // check directly.
        return UNSAFE.getLong(this.getKlassPointer() + config().subklassOffset) == 0;
    }

    /**
     * Returns the {@code Klass::_subklass} field of the underlying metaspace klass for the given
     * type {@code type}.
     *
     * @return value of the subklass field as metaspace klass pointer
     */
    private HotSpotResolvedObjectTypeImpl getSubklass() {
        return compilerToVM().getResolvedJavaType(this, config().subklassOffset, false);
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getSuperclass() {
        if (isInterface()) {
            return null;
        }
        HotSpotResolvedObjectTypeImpl javaLangObject = runtime().getJavaLangObject();
        if (this.equals(javaLangObject)) {
            return null;
        }
        if (isArray()) {
            return javaLangObject;
        }

        // Cache result of native call
        if (superClass == null) {
            superClass = compilerToVM().getResolvedJavaType(this, config().superOffset, false);
        }
        return superClass;
    }

    @Override
    public HotSpotResolvedObjectTypeImpl[] getInterfaces() {
        if (interfaces == null) {
            if (isArray()) {
                HotSpotResolvedObjectTypeImpl[] types = new HotSpotResolvedObjectTypeImpl[2];
                types[0] = runtime().getJavaLangCloneable();
                types[1] = runtime().getJavaLangSerializable();
                this.interfaces = types;
            } else {
                interfaces = runtime().compilerToVm.getInterfaces(this);
            }
        }
        return interfaces;
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getSingleImplementor() {
        if (!isInterface()) {
            throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", this);
        }
        return compilerToVM().getImplementor(this);
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getSupertype() {
        ResolvedJavaType component = getComponentType();
        if (component != null) {
            if (component.equals(getJavaLangObject()) || component.isPrimitive()) {
                return getJavaLangObject();
            }
            HotSpotResolvedObjectTypeImpl supertype = ((HotSpotResolvedObjectTypeImpl) component).getSupertype();
            return (HotSpotResolvedObjectTypeImpl) supertype.getArrayClass();
        }
        if (isInterface()) {
            return getJavaLangObject();
        }
        return getSuperclass();
    }

    @Override
    public HotSpotResolvedObjectType findLeastCommonAncestor(ResolvedJavaType otherType) {
        if (otherType.isPrimitive()) {
            return null;
        } else {
            HotSpotResolvedObjectTypeImpl t1 = this;
            HotSpotResolvedObjectTypeImpl t2 = (HotSpotResolvedObjectTypeImpl) otherType;
            while (true) {
                if (t1.isAssignableFrom(t2)) {
                    return t1;
                }
                if (t2.isAssignableFrom(t1)) {
                    return t2;
                }
                t1 = t1.getSupertype();
                t2 = t2.getSupertype();
            }
        }
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        assert !isArray();
        if (!compilerToVM().hasFinalizableSubclass(this)) {
            return new AssumptionResult<>(false, new NoFinalizableSubclass(this));
        }
        return new AssumptionResult<>(true);
    }

    @Override
    public boolean hasFinalizer() {
        return (getAccessFlags() & config().jvmAccHasFinalizer) != 0;
    }

    @Override
    public boolean isArray() {
        return layoutHelper() < config().klassLayoutHelperNeutralValue;
    }

    @Override
    public boolean isEnum() {
        HotSpotResolvedObjectTypeImpl superclass = getSuperclass();
        return superclass != null && superclass.equals(runtime().getJavaLangEnum());
    }

    @Override
    public boolean isInitialized() {
        return isArray() ? true : getInitState() == config().instanceKlassStateFullyInitialized;
    }

    @Override
    public boolean isBeingInitialized() {
        return isArray() ? false : getInitState() == config().instanceKlassStateBeingInitialized;
    }

    @Override
    public boolean isLinked() {
        return isArray() ? true : getInitState() >= config().instanceKlassStateLinked;
    }

    @Override
    public void link() {
        if (!isLinked()) {
            runtime().compilerToVm.ensureLinked(this);
        }
    }

    @Override
    public boolean hasDefaultMethods() {
        HotSpotVMConfig config = config();
        int miscFlags = UNSAFE.getChar(getKlassPointer() + config.instanceKlassMiscFlagsOffset);
        return (miscFlags & config.jvmMiscFlagsHasDefaultMethods) != 0;
    }

    @Override
    public boolean declaresDefaultMethods() {
        HotSpotVMConfig config = config();
        int miscFlags = UNSAFE.getChar(getKlassPointer() + config.instanceKlassMiscFlagsOffset);
        return (miscFlags & config.jvmMiscFlagsDeclaresDefaultMethods) != 0;
    }

    /**
     * Returns the value of the state field {@code InstanceKlass::_init_state} of the metaspace
     * klass.
     *
     * @return state field value of this type
     */
    private int getInitState() {
        assert !isArray() : "_init_state only exists in InstanceKlass";
        return UNSAFE.getByte(getKlassPointer() + config().instanceKlassInitStateOffset) & 0xFF;
    }

    @Override
    public void initialize() {
        if (!isInitialized()) {
            runtime().compilerToVm.ensureInitialized(this);
            assert isInitialized() || isBeingInitialized();
        }
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        if (obj.getJavaKind() == JavaKind.Object && !obj.isNull()) {
            return runtime().reflection.isInstance(this, (HotSpotObjectConstantImpl) obj);
        }
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return !isArray() && !isInterface();
    }

    @Override
    public boolean isInterface() {
        return (getAccessFlags() & config().jvmAccInterface) != 0;
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        assert other != null;
        if (other instanceof HotSpotResolvedObjectTypeImpl) {
            HotSpotResolvedObjectTypeImpl otherType = (HotSpotResolvedObjectTypeImpl) other;
            return runtime().reflection.isAssignableFrom(this, otherType);
        }
        return false;
    }

    @Override
    public boolean isJavaLangObject() {
        return getName().equals("Ljava/lang/Object;");
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        assert !callerType.isArray();
        if (isInterface()) {
            // Methods can only be resolved against concrete types
            return null;
        }
        if (method.isConcrete() && method.getDeclaringClass().equals(this) && method.isPublic() && !isSignaturePolymorphicHolder(method.getDeclaringClass())) {
            return method;
        }
        if (!method.getDeclaringClass().isAssignableFrom(this)) {
            return null;
        }
        if (method.isConstructor()) {
            // Constructor calls should have been checked in the verifier and method's
            // declaring class is assignable from this (see above) so treat it as resolved.
            return method;
        }
        HotSpotResolvedJavaMethodImpl hotSpotMethod = (HotSpotResolvedJavaMethodImpl) method;
        HotSpotResolvedObjectTypeImpl hotSpotCallerType = (HotSpotResolvedObjectTypeImpl) callerType;
        return compilerToVM().resolveMethod(this, hotSpotMethod, hotSpotCallerType);
    }

    @Override
    public HotSpotConstantPool getConstantPool() {
        if (constantPool == null || !isArray() && UNSAFE.getAddress(getKlassPointer() + config().instanceKlassConstantsOffset) != constantPool.getConstantPoolPointer()) {
            /*
             * If the pointer to the ConstantPool has changed since this was last read refresh the
             * HotSpotConstantPool wrapper object. This ensures that uses of the constant pool are
             * operating on the latest one and that HotSpotResolvedJavaMethodImpls will be able to
             * use the shared copy instead of creating their own instance.
             */
            constantPool = compilerToVM().getConstantPool(this);
        }
        return constantPool;
    }

    /**
     * Gets the instance size of this type. If an instance of this type cannot be fast path
     * allocated, then the returned value is negative (its absolute value gives the size). Must not
     * be called if this is an array or interface type.
     */
    @Override
    public int instanceSize() {
        assert !isArray();
        assert !isInterface();

        HotSpotVMConfig config = config();
        final int layoutHelper = layoutHelper();
        assert layoutHelper > config.klassLayoutHelperNeutralValue : "must be instance";

        // See: Klass::layout_helper_size_in_bytes
        int size = layoutHelper & ~config.klassLayoutHelperInstanceSlowPathBit;

        // See: Klass::layout_helper_needs_slow_path
        boolean needsSlowPath = (layoutHelper & config.klassLayoutHelperInstanceSlowPathBit) != 0;

        return needsSlowPath ? -size : size;
    }

    @Override
    public int layoutHelper() {
        HotSpotVMConfig config = config();
        assert getKlassPointer() != 0 : getName();
        return UNSAFE.getInt(getKlassPointer() + config.klassLayoutHelperOffset);
    }

    synchronized HotSpotResolvedJavaMethod createMethod(long metaspaceHandle) {
        long metaspaceMethod = UNSAFE.getLong(metaspaceHandle);
        // Maintain cache as array.
        if (methodCacheArray == null) {
            methodCacheArray = new HotSpotResolvedJavaMethodImpl[METHOD_CACHE_ARRAY_CAPACITY];
        }

        int i = 0;
        for (; i < methodCacheArray.length; ++i) {
            HotSpotResolvedJavaMethodImpl curMethod = methodCacheArray[i];
            if (curMethod == null) {
                HotSpotResolvedJavaMethodImpl newMethod = new HotSpotResolvedJavaMethodImpl(this, metaspaceHandle);
                methodCacheArray[i] = newMethod;
                return newMethod;
            } else if (curMethod.getMethodPointer() == metaspaceMethod) {
                return curMethod;
            }
        }

        // Fall-back to hash table.
        if (methodCacheHashMap == null) {
            methodCacheHashMap = new HashMap<>();
        }

        HotSpotResolvedJavaMethodImpl lookupResult = methodCacheHashMap.get(metaspaceMethod);
        if (lookupResult == null) {
            HotSpotResolvedJavaMethodImpl newMethod = new HotSpotResolvedJavaMethodImpl(this, metaspaceHandle);
            methodCacheHashMap.put(metaspaceMethod, newMethod);
            return newMethod;
        } else {
            return lookupResult;
        }
    }

    @Override
    public int getVtableLength() {
        HotSpotVMConfig config = config();
        if (isInterface() || isArray()) {
            /* Everything has the core vtable of java.lang.Object */
            return config.baseVtableLength();
        }
        int result = UNSAFE.getInt(getKlassPointer() + config.klassVtableLengthOffset) / (config.vtableEntrySize / config.heapWordSize);
        assert result >= config.baseVtableLength() : UNSAFE.getInt(getKlassPointer() + config.klassVtableLengthOffset) + " " + config.vtableEntrySize;
        return result;
    }

    HotSpotResolvedJavaField createField(JavaType type, int offset, int classfileFlags, int internalFlags, int index) {
        return new HotSpotResolvedJavaFieldImpl(this, type, offset, classfileFlags, internalFlags, index);
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        HotSpotResolvedJavaMethod hmethod = (HotSpotResolvedJavaMethod) method;
        HotSpotResolvedObjectType declaredHolder = hmethod.getDeclaringClass();
        /*
         * Sometimes the receiver type in the graph hasn't stabilized to a subtype of declared
         * holder, usually because of phis, so make sure that the type is related to the declared
         * type before using it for lookup. Unlinked types should also be ignored because we can't
         * resolve the proper method to invoke. Generally unlinked types in invokes should result in
         * a deopt instead since they can't really be used if they aren't linked yet.
         */
        if (!declaredHolder.isAssignableFrom(this) || this.isArray() || this.equals(declaredHolder) || !isLinked() || isInterface()) {
            if (hmethod.canBeStaticallyBound()) {
                // No assumptions are required.
                return new AssumptionResult<>(hmethod);
            }
            ResolvedJavaMethod result = hmethod.uniqueConcreteMethod(declaredHolder);
            if (result != null) {
                return new AssumptionResult<>(result, new ConcreteMethod(method, declaredHolder, result));
            }
            return null;
        }
        /*
         * The holder may be a subtype of the declaredHolder so make sure to resolve the method to
         * the correct method for the subtype.
         */
        HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) resolveMethod(hmethod, this);
        if (resolvedMethod == null) {
            // The type isn't known to implement the method.
            return null;
        }
        if (resolvedMethod.canBeStaticallyBound()) {
            // No assumptions are required.
            return new AssumptionResult<>(resolvedMethod);
        }

        ResolvedJavaMethod result = resolvedMethod.uniqueConcreteMethod(this);
        if (result != null) {
            return new AssumptionResult<>(result, new ConcreteMethod(method, this, result));
        }
        return null;
    }

    private FieldInfo[] getFieldInfo() {
        if (fieldInfo == null) {
            fieldInfo = runtime().compilerToVm.getDeclaredFieldsInfo(this);
        }
        return fieldInfo;
    }

    FieldInfo getFieldInfo(int index) {
        return getFieldInfo()[index];
    }

    public void ensureInitialized() {
        runtime().compilerToVm.ensureInitialized(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof HotSpotResolvedObjectTypeImpl)) {
            return false;
        }
        HotSpotResolvedObjectTypeImpl that = (HotSpotResolvedObjectTypeImpl) obj;
        return getKlassPointer() == that.getKlassPointer();
    }

    @Override
    JavaConstant getJavaMirror() {
        return mirror;
    }

    @Override
    HotSpotResolvedObjectTypeImpl getArrayType() {
        return runtime().compilerToVm.getArrayType((char) 0, this);
    }

    /**
     * This class represents the field information for one field contained in the fields array of an
     * {@code InstanceKlass}. The implementation is similar to the native {@code FieldInfo} class.
     */
    static class FieldInfo {

        private final int nameIndex;
        private final int signatureIndex;
        private final int offset;
        private final int classfileFlags;
        private final int internalFlags;
        private final int initializerIndex;

        /**
         * Creates a field info with the provided indices.
         *
         * @param nameIndex index of field's name in the constant pool
         * @param signatureIndex index of field's signature in the constant pool
         * @param offset field's offset
         * @param classfileFlags field's access flags (from the class file)
         * @param internalFlags field's internal flags (from the VM)
         * @param initializerIndex field's initial value index in the constant pool
         */
        FieldInfo(int nameIndex, int signatureIndex, int offset, int classfileFlags, int internalFlags, int initializerIndex) {
            this.nameIndex = nameIndex;
            this.signatureIndex = signatureIndex;
            this.offset = offset;
            this.classfileFlags = classfileFlags;
            this.internalFlags = internalFlags;
            this.initializerIndex = initializerIndex;
        }

        private int getClassfileFlags() {
            return classfileFlags;
        }

        private int getInternalFlags() {
            return internalFlags;
        }

        private int getNameIndex() {
            return nameIndex;
        }

        private int getSignatureIndex() {
            return signatureIndex;
        }

        private int getConstantValueIndex() {
            return initializerIndex;
        }

        public int getOffset() {
            return offset;
        }

        /**
         * Returns the name of this field as a {@link String}. If the field is an internal field the
         * name index is pointing into the vmSymbols table.
         * @param klass field's holder class
         */
        public String getName(HotSpotResolvedObjectTypeImpl klass) {
            return isInternal() ? config().symbolAt(nameIndex) : klass.getConstantPool().lookupUtf8(nameIndex);
        }

        /**
         * Returns the signature of this field as {@link String}. If the field is an internal field
         * the signature index is pointing into the vmSymbols table.
         * @param klass field's holder class
         */
        public String getSignature(HotSpotResolvedObjectTypeImpl klass) {
            return isInternal() ? config().symbolAt(signatureIndex) : klass.getConstantPool().lookupUtf8(signatureIndex);
        }

        /**
         * Gets the {@link JavaConstant} for the {@code ConstantValue} attribute of this field.
         *
         * @return {@code null} if this field has no {@code ConstantValue} attribute
         */
        public JavaConstant getConstantValue(HotSpotResolvedObjectTypeImpl klass) {
            if (initializerIndex == 0) {
                return null;
            }
            return klass.constantPool.getStaticFieldConstantValue(initializerIndex);
        }

        public JavaType getType(HotSpotResolvedObjectTypeImpl klass) {
            String signature = getSignature(klass);
            return runtime().lookupType(signature, klass, false);
        }

        private boolean isInternal() {
            return (getInternalFlags() & (1 << config().jvmFieldFlagInternalShift)) != 0;
        }

        public boolean isStatic() {
            return Modifier.isStatic(getClassfileFlags());
        }
    }

    static class SortByOffset implements Comparator<ResolvedJavaField> {
        public int compare(ResolvedJavaField a, ResolvedJavaField b) {
            return a.getOffset() - b.getOffset();
        }
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        if (instanceFields == null) {
            if (isArray() || isInterface()) {
                instanceFields = NO_FIELDS;
            } else {
                HotSpotResolvedJavaField[] prepend = NO_FIELDS;
                if (getSuperclass() != null) {
                    prepend = (HotSpotResolvedJavaField[]) getSuperclass().getInstanceFields(true);
                }
                instanceFields = getFields(false, prepend);
            }
        }
        if (!includeSuperclasses && getSuperclass() != null) {
            int superClassFieldCount = getSuperclass().getInstanceFields(true).length;
            if (superClassFieldCount == instanceFields.length) {
                // This class does not have any instance fields of its own.
                return NO_FIELDS;
            } else if (superClassFieldCount != 0) {
                // Fields of the current class can be interleaved with fields of its super-classes
                // but the array of fields to be returned must be sorted by increasing offset
                // This code populates the array, then applies the sorting function
                HotSpotResolvedJavaField[] result = new HotSpotResolvedJavaField[instanceFields.length - superClassFieldCount];
                int i = 0;
                for (HotSpotResolvedJavaField f : instanceFields) {
                    if (f.getDeclaringClass() == this) {
                        result[i++] = f;
                    }
                }
                Arrays.sort(result, fieldSortingMethod);
                return result;
            } else {
                // The super classes of this class do not have any instance fields.
            }
        }
        return instanceFields;
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        if (isArray()) {
            return new HotSpotResolvedJavaField[0];
        } else {
            return getFields(true, NO_FIELDS);
        }
    }

    /**
     * Gets the instance or static fields of this class.
     *
     * @param retrieveStaticFields specifies whether to return instance or static fields
     * @param prepend an array to be prepended to the returned result
     */
    private HotSpotResolvedJavaField[] getFields(boolean retrieveStaticFields, HotSpotResolvedJavaField[] prepend) {
        HotSpotVMConfig config = config();
        int resultCount = 0;
        int index = 0;

        for (index = 0; index < getFieldInfo().length; index++) {
            if (getFieldInfo(index).isStatic() == retrieveStaticFields) {
                resultCount++;
            }
        }

        if (resultCount == 0) {
            return prepend;
        }

        int prependLength = prepend.length;
        resultCount += prependLength;

        HotSpotResolvedJavaField[] result = new HotSpotResolvedJavaField[resultCount];
        if (prependLength != 0) {
            System.arraycopy(prepend, 0, result, 0, prependLength);
        }

        // Fields of the current class can be interleaved with fields of its super-classes
        // but the array of fields to be returned must be sorted by increasing offset
        // This code populates the array, then applies the sorting function
        int resultIndex = prependLength;
        for (int i = 0; i < getFieldInfo().length; ++i) {
            FieldInfo field = getFieldInfo(i);
            if (field.isStatic() == retrieveStaticFields) {
                int offset = field.getOffset();
                HotSpotResolvedJavaField resolvedJavaField = createField(field.getType(this), offset, field.getClassfileFlags(), field.getInternalFlags(), i);
                result[resultIndex++] = resolvedJavaField;
            }
        }
        Arrays.sort(result, fieldSortingMethod);
        return result;
    }

    @Override
    public String getSourceFileName() {
        if (isArray()) {
            return null;
        }
        return getConstantPool().getSourceFileName();
    }

    /**
     * Determines if this type may have annotations. A positive result does not mean this type has
     * annotations but a negative result guarantees this type has no annotations.
     *
     * @param includingInherited if true, expand this query to include superclasses of this type
     */
    private boolean mayHaveAnnotations(boolean includingInherited) {
        if (isArray()) {
            return false;
        }
        HotSpotVMConfig config = config();
        final long metaspaceAnnotations = UNSAFE.getAddress(getKlassPointer() + config.instanceKlassAnnotationsOffset);
        if (metaspaceAnnotations != 0) {
            long classAnnotations = UNSAFE.getAddress(metaspaceAnnotations + config.annotationsClassAnnotationsOffset);
            if (classAnnotations != 0) {
                return true;
            }
        }
        if (includingInherited) {
            HotSpotResolvedObjectTypeImpl superClass = getSuperclass();
            if (superClass != null) {
                return superClass.mayHaveAnnotations(true);
            }
        }
        return false;
    }

    private static final Annotation[] NO_ANNOTATIONS = {};

    @Override
    public Annotation[] getAnnotations() {
        if (!mayHaveAnnotations(true)) {
            return NO_ANNOTATIONS;
        }
        return runtime().reflection.getAnnotations(this);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        if (!mayHaveAnnotations(false)) {
            return NO_ANNOTATIONS;
        }
        return runtime().reflection.getDeclaredAnnotations(this);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (!mayHaveAnnotations(true)) {
            return null;
        }
        return runtime().reflection.getAnnotation(this, annotationClass);
    }

    /**
     * Performs a fast-path check that this type is resolved in the context of a given accessing
     * class. A negative result does not mean this type is not resolved with respect to
     * {@code accessingClass}. That can only be determined by
     * {@linkplain HotSpotJVMCIRuntime#lookupType(String, HotSpotResolvedObjectType, boolean)
     * re-resolving} the type.
     */
    @Override
    public boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
        assert accessingClass != null;
        ResolvedJavaType elementType = getElementalType();
        if (elementType.isPrimitive()) {
            // Primitive type resolution is context free.
            return true;
        }
        if (elementType.getName().startsWith("Ljava/") && hasSameClassLoader(runtime().getJavaLangObject())) {
            // Classes in a java.* package defined by the boot class loader are always resolved.
            return true;
        }
        HotSpotResolvedObjectTypeImpl otherMirror = ((HotSpotResolvedObjectTypeImpl) accessingClass);
        return hasSameClassLoader(otherMirror);
    }

    private boolean hasSameClassLoader(HotSpotResolvedObjectTypeImpl otherMirror) {
        return UnsafeAccess.UNSAFE.getAddress(getKlassPointer() + config().classLoaderDataOffset) == UnsafeAccess.UNSAFE.getAddress(
                        otherMirror.getKlassPointer() + config().classLoaderDataOffset);
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        if (isDefinitelyResolvedWithRespectTo(requireNonNull(accessingClass))) {
            return this;
        }
        HotSpotResolvedObjectTypeImpl accessingType = (HotSpotResolvedObjectTypeImpl) accessingClass;
        return (ResolvedJavaType) runtime().lookupType(getName(), accessingType, true);
    }

    /**
     * Gets the metaspace Klass boxed in a {@link JavaConstant}.
     */
    @Override
    public Constant klass() {
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(this, false);
    }

    @Override
    public boolean isPrimaryType() {
        return config().secondarySuperCacheOffset != superCheckOffset();
    }

    @Override
    public int superCheckOffset() {
        HotSpotVMConfig config = config();
        return UNSAFE.getInt(getKlassPointer() + config.superCheckOffsetOffset);
    }

    @Override
    public long prototypeMarkWord() {
        HotSpotVMConfig config = config();
        return config.prototypeMarkWord();
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedEntryKind) {
        ResolvedJavaField[] declaredFields = getInstanceFields(true);
        return findFieldWithOffset(offset, expectedEntryKind, declaredFields);
    }

    public ResolvedJavaField findStaticFieldWithOffset(long offset, JavaKind expectedEntryKind) {
        ResolvedJavaField[] declaredFields = getStaticFields();
        return findFieldWithOffset(offset, expectedEntryKind, declaredFields);
    }

    private static ResolvedJavaField findFieldWithOffset(long offset, JavaKind expectedEntryKind, ResolvedJavaField[] declaredFields) {
        for (ResolvedJavaField field : declaredFields) {
            long resolvedFieldOffset = field.getOffset();
            // @formatter:off
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN &&
                    expectedEntryKind.isPrimitive() &&
                    !expectedEntryKind.equals(JavaKind.Void) &&
                    field.getJavaKind().isPrimitive()) {
                resolvedFieldOffset +=
                        field.getJavaKind().getByteCount() -
                                Math.min(field.getJavaKind().getByteCount(), 4 + expectedEntryKind.getByteCount());
            }
            if (resolvedFieldOffset == offset) {
                return field;
            }
            // @formatter:on
        }
        return null;
    }

    @Override
    public boolean isLocal() {
        return runtime().reflection.isLocalClass(this);
    }

    @Override
    public boolean isMember() {
        return runtime().reflection.isMemberClass(this);
    }

    @Override
    public HotSpotResolvedObjectType getEnclosingType() {
        return runtime().reflection.getEnclosingClass(this);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        link();
        return runtime().compilerToVm.getDeclaredConstructors(this);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return runtime().compilerToVm.getDeclaredConstructors(this);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return runtime().compilerToVm.getDeclaredMethods(this);
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        if (!isArray()) {
            return compilerToVM().getClassInitializer(this);
        }
        return null;
    }

    @Override
    public String toString() {
        return "HotSpotType<" + getName() + ", resolved>";
    }

    @Override
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        JavaType javaType = HotSpotJVMCIRuntime.runtime().lookupType(unresolvedJavaType.getName(), this, resolve);
        if (javaType instanceof ResolvedJavaType) {
            return (ResolvedJavaType) javaType;
        }
        return null;
    }

    @Override
    public ResolvedJavaField resolveField(UnresolvedJavaField unresolvedJavaField, ResolvedJavaType accessingClass) {
        for (ResolvedJavaField field : getInstanceFields(false)) {
            if (field.getName().equals(unresolvedJavaField.getName())) {
                return field;
            }
        }
        for (ResolvedJavaField field : getStaticFields()) {
            if (field.getName().equals(unresolvedJavaField.getName())) {
                return field;
            }
        }
        throw new InternalError(unresolvedJavaField.toString());
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return (getAccessFlags() & config().jvmAccIsCloneableFast) != 0;
    }

    @Override
    public AnnotationData getAnnotationData(ResolvedJavaType annotationType) {
        if (!mayHaveAnnotations(true)) {
            return null;
        }
        return getAnnotationData0(annotationType).get(0);
    }

    @Override
    public List<AnnotationData> getAnnotationData(ResolvedJavaType type1, ResolvedJavaType type2, ResolvedJavaType... types) {
        if (!mayHaveAnnotations(true)) {
            return Collections.emptyList();
        }
        return getAnnotationData0(AnnotationDataDecoder.asArray(type1, type2, types));
    }

    private List<AnnotationData> getAnnotationData0(ResolvedJavaType... filter) {
        byte[] encoded = compilerToVM().getEncodedClassAnnotationData(this, filter);
        return VMSupport.decodeAnnotations(encoded, AnnotationDataDecoder.INSTANCE);
    }
}
