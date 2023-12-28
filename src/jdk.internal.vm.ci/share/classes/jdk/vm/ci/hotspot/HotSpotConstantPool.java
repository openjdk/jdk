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

import static jdk.vm.ci.hotspot.CompilerToVM.compilerToVM;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Implementation of {@link ConstantPool} for HotSpot.
 *
 * The following convention is used in the jdk.vm.ci.hotspot package when accessing the ConstantPool with an index:
 * <ul>
 * <li>rawIndex - Index in the bytecode stream after the opcode (could be rewritten for some opcodes)</li>
 * <li>cpi -      The constant pool index (as specified in JVM Spec)</li>
 * <li>cpci -     The constant pool cache index, used only by the four bytecodes INVOKE{VIRTUAL,SPECIAL,STATIC,INTERFACE}.
 *                It's the same as {@code rawIndex}. </li>
 * <li>which -    May be either a {@code rawIndex} or a {@code cpci}.</li>
 * </ul>
 *
 * Note that {@code cpci} and {@code which} are used only in the HotSpot-specific implementation. They
 * are not used by the public interface in jdk.vm.ci.meta.*.
 * After JDK-8301993, all uses of {@code cpci} and {@code which} will be replaced with {@code rawIndex}.
 */
public final class HotSpotConstantPool implements ConstantPool, MetaspaceHandleObject {

    /**
     * Subset of JVM bytecode opcodes used by {@link HotSpotConstantPool}.
     */
    public static class Bytecodes {
        public static final int LDC = 18; // 0x12
        public static final int LDC_W = 19; // 0x13
        public static final int LDC2_W = 20; // 0x14
        public static final int GETSTATIC = 178; // 0xB2
        public static final int PUTSTATIC = 179; // 0xB3
        public static final int GETFIELD = 180; // 0xB4
        public static final int PUTFIELD = 181; // 0xB5
        public static final int INVOKEVIRTUAL = 182; // 0xB6
        public static final int INVOKESPECIAL = 183; // 0xB7
        public static final int INVOKESTATIC = 184; // 0xB8
        public static final int INVOKEINTERFACE = 185; // 0xB9
        public static final int INVOKEDYNAMIC = 186; // 0xBA
        public static final int NEW = 187; // 0xBB
        public static final int NEWARRAY = 188; // 0xBC
        public static final int ANEWARRAY = 189; // 0xBD
        public static final int CHECKCAST = 192; // 0xC0
        public static final int INSTANCEOF = 193; // 0xC1
        public static final int MULTIANEWARRAY = 197; // 0xC5

        static boolean isInvoke(int opcode) {
            switch (opcode) {
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKEDYNAMIC:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * See: {@code Rewriter::maybe_rewrite_invokehandle}.
         */
        static boolean isInvokeHandleAlias(int opcode) {
            switch (opcode) {
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                    return true;
                default:
                    return false;
            }
        }
    }

    static final class JvmConstant {
        private final int tag;
        private final String name;

        JvmConstant(int tag, String name) {
            this.tag = tag;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * {@code JVM_CONSTANT} constants used in the VM including both public and internal ones.
     */
    static final class JvmConstants {

        private final HotSpotVMConfig c = config();
        private final int externalMax = c.jvmConstantExternalMax;
        private final int internalMax = c.jvmConstantInternalMax;
        private final int internalMin = c.jvmConstantInternalMin;
        private final JvmConstant[] table = new JvmConstant[externalMax + 1 + (internalMax - internalMin) + 1];

        final JvmConstant jvmUtf8 = add(new JvmConstant(c.jvmConstantUtf8, "Utf8"));
        final JvmConstant jvmInteger = add(new JvmConstant(c.jvmConstantInteger, "Integer"));
        final JvmConstant jvmLong = add(new JvmConstant(c.jvmConstantLong, "Long"));
        final JvmConstant jvmFloat = add(new JvmConstant(c.jvmConstantFloat, "Float"));
        final JvmConstant jvmDouble = add(new JvmConstant(c.jvmConstantDouble, "Double"));
        final JvmConstant jvmClass = add(new JvmConstant(c.jvmConstantClass, "Class"));
        final JvmConstant jvmUnresolvedClass = add(new JvmConstant(c.jvmConstantUnresolvedClass, "UnresolvedClass"));
        final JvmConstant jvmUnresolvedClassInError = add(new JvmConstant(c.jvmConstantUnresolvedClassInError, "UnresolvedClassInError"));
        final JvmConstant jvmString = add(new JvmConstant(c.jvmConstantString, "String"));
        final JvmConstant jvmFieldref = add(new JvmConstant(c.jvmConstantFieldref, "Fieldref"));
        final JvmConstant jvmMethodref = add(new JvmConstant(c.jvmConstantMethodref, "Methodref"));
        final JvmConstant jvmInterfaceMethodref = add(new JvmConstant(c.jvmConstantInterfaceMethodref, "InterfaceMethodref"));
        final JvmConstant jvmNameAndType = add(new JvmConstant(c.jvmConstantNameAndType, "NameAndType"));
        final JvmConstant jvmMethodHandle = add(new JvmConstant(c.jvmConstantMethodHandle, "MethodHandle"));
        final JvmConstant jvmMethodHandleInError = add(new JvmConstant(c.jvmConstantMethodHandleInError, "MethodHandleInError"));
        final JvmConstant jvmMethodType = add(new JvmConstant(c.jvmConstantMethodType, "MethodType"));
        final JvmConstant jvmMethodTypeInError = add(new JvmConstant(c.jvmConstantMethodTypeInError, "MethodTypeInError"));
        final JvmConstant jvmInvokeDynamic = add(new JvmConstant(c.jvmConstantInvokeDynamic, "InvokeDynamic"));
        final JvmConstant jvmDynamic = add(new JvmConstant(c.jvmConstantDynamic, "Dynamic"));
        final JvmConstant jvmDynamicInError = add(new JvmConstant(c.jvmConstantDynamicInError, "DynamicInError"));

        private JvmConstant add(JvmConstant constant) {
            table[indexOf(constant.tag)] = constant;
            return constant;
        }

        private int indexOf(int tag) {
            if (tag >= internalMin) {
                return tag - internalMin + externalMax + 1;
            } else {
                assert tag <= externalMax;
            }
            return tag;
        }

        JvmConstant get(int tag) {
            JvmConstant res = table[indexOf(tag)];
            if (res != null) {
                return res;
            }
            throw new JVMCIError("Unknown JvmConstant tag %s", tag);
        }

        @NativeImageReinitialize private static volatile JvmConstants instance;

        static JvmConstants instance() {
            JvmConstants result = instance;
            if (result == null) {
                synchronized (JvmConstants.class) {
                    result = instance;
                    if (result == null) {
                        instance = result = new JvmConstants();
                    }
                }
            }
            return result;
        }
    }

    private static class LookupTypeCacheElement {
        int lastCpi = Integer.MIN_VALUE;
        JavaType javaType;

        LookupTypeCacheElement(int lastCpi, JavaType javaType) {
            super();
            this.lastCpi = lastCpi;
            this.javaType = javaType;
        }
    }

    /**
     * A {@code jmetadata} value that is a handle to {@code ConstantPool*} value.
     */
    private final long constantPoolHandle;

    private volatile LookupTypeCacheElement lastLookupType;
    private final JvmConstants constants;

    /**
     * Cache for {@link #getHolder()}.
     */
    private HotSpotResolvedObjectTypeImpl holder;

    /**
     * Gets the JVMCI mirror from a HotSpot constant pool. The VM is responsible for ensuring that
     * the ConstantPool is kept alive for the duration of this call and the
     * {@link HotSpotJVMCIRuntime} keeps it alive after that.
     *
     * Called from the VM.
     *
     * @param constantPoolHandle a {@code jmetaspace} handle to a raw {@code ConstantPool*} value
     * @return the {@link HotSpotConstantPool} corresponding to {@code constantPoolHandle}
     */
    @SuppressWarnings("unused")
    @VMEntryPoint
    private static HotSpotConstantPool fromMetaspace(long constantPoolHandle) {
        return new HotSpotConstantPool(constantPoolHandle);
    }

    private HotSpotConstantPool(long constantPoolHandle) {
        this.constantPoolHandle = constantPoolHandle;
        this.constants = JvmConstants.instance();
        HandleCleaner.create(this, constantPoolHandle);
    }

    /**
     * Gets the holder for this constant pool as {@link HotSpotResolvedObjectTypeImpl}.
     *
     * @return holder for this constant pool
     */
    private HotSpotResolvedObjectType getHolder() {
        if (holder == null) {
            holder = compilerToVM().getResolvedJavaType(this, config().constantPoolHolderOffset);
        }
        return holder;
    }

    /**
     * See {@code ConstantPool::is_invokedynamic_index}.
     */
    private static boolean isInvokedynamicIndex(int index) {
        return index < 0;
    }

    /**
     * Gets the raw {@code ConstantPool*} value for the this constant pool.
     */
    long getConstantPoolPointer() {
        return getMetaspacePointer();
    }

    @Override
    public long getMetadataHandle() {
        return constantPoolHandle;
    }

    /**
     * Gets the constant pool tag at index {@code index}.
     *
     * @param index constant pool index
     * @return constant pool tag
     */
    private JvmConstant getTagAt(int index) {
        checkBounds(index);
        HotSpotVMConfig config = config();
        final long metaspaceConstantPoolTags = UNSAFE.getAddress(getConstantPoolPointer() + config.constantPoolTagsOffset);
        final int tag = UNSAFE.getByteVolatile(null, metaspaceConstantPoolTags + config.arrayU1DataOffset + index);
        if (tag == 0) {
            return null;
        }
        return constants.get(tag);
    }

    /**
     * Gets the constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return constant pool entry
     */
    long getEntryAt(int index) {
        checkBounds(index);
        int offset = index * runtime().getHostJVMCIBackend().getTarget().wordSize;
        return UNSAFE.getAddress(getConstantPoolPointer() + config().constantPoolSize + offset);
    }

    /**
     * Gets the integer constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return integer constant pool entry at index
     */
    private int getIntAt(int index) {
        checkTag(index, constants.jvmInteger);
        int offset = index * runtime().getHostJVMCIBackend().getTarget().wordSize;
        return UNSAFE.getInt(getConstantPoolPointer() + config().constantPoolSize + offset);
    }

    /**
     * Gets the long constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return long constant pool entry
     */
    private long getLongAt(int index) {
        checkTag(index, constants.jvmLong);
        int offset = index * runtime().getHostJVMCIBackend().getTarget().wordSize;
        return UNSAFE.getLong(getConstantPoolPointer() + config().constantPoolSize + offset);
    }

    /**
     * Gets the float constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return float constant pool entry
     */
    private float getFloatAt(int index) {
        checkTag(index, constants.jvmFloat);
        int offset = index * runtime().getHostJVMCIBackend().getTarget().wordSize;
        return UNSAFE.getFloat(getConstantPoolPointer() + config().constantPoolSize + offset);
    }

    /**
     * Gets the double constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return float constant pool entry
     */
    private double getDoubleAt(int index) {
        checkTag(index, constants.jvmDouble);
        int offset = index * runtime().getHostJVMCIBackend().getTarget().wordSize;
        return UNSAFE.getDouble(getConstantPoolPointer() + config().constantPoolSize + offset);
    }

    /**
     * Gets the {@code JVM_CONSTANT_NameAndType} constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return {@code JVM_CONSTANT_NameAndType} constant pool entry
     */
    private int getNameAndTypeAt(int index) {
        checkTag(index, constants.jvmNameAndType);
        int offset = index * runtime().getHostJVMCIBackend().getTarget().wordSize;
        return UNSAFE.getInt(getConstantPoolPointer() + config().constantPoolSize + offset);
    }

    /**
     * Gets the {@code JVM_CONSTANT_NameAndType} reference index constant pool entry at index
     * {@code index}.
     *
     * @param rawIndex rewritten index in the bytecode stream
     * @param opcode the opcode of the instruction for which the lookup is being performed
     * @return {@code JVM_CONSTANT_NameAndType} reference constant pool entry
     */
    private int getNameAndTypeRefIndexAt(int rawIndex, int opcode) {
        return compilerToVM().lookupNameAndTypeRefIndexInPool(this, rawIndex, opcode);
    }

    /**
     * Gets the name reference index of a {@code JVM_CONSTANT_NameAndType} constant pool entry at
     * index {@code index}.
     *
     * @param index constant pool index
     * @return name reference index
     */
    private int getNameRefIndexAt(int index) {
        final int refIndex = getNameAndTypeAt(index);
        // name ref index is in the low 16-bits.
        return refIndex & 0xFFFF;
    }


    /**
     * Gets the signature reference index of a {@code JVM_CONSTANT_NameAndType} constant pool entry
     * at index {@code index}.
     *
     * @param index constant pool index
     * @return signature reference index
     */
    private int getSignatureRefIndexAt(int index) {
        final int refIndex = getNameAndTypeAt(index);
        // signature ref index is in the high 16-bits.
        return refIndex >>> 16;
    }

    /**
     * Gets the klass reference index constant pool entry at index {@code index}.
     *
     * @param which  for INVOKE{VIRTUAL,SPECIAL,STATIC,INTERFACE}, must be {@code cpci}. For all other bytecodes,
     *               must be {@code rawIndex}
     * @param opcode the opcode of the instruction for which the lookup is being performed
     * @return klass reference index
     */
    private int getKlassRefIndexAt(int which, int opcode) {
        return compilerToVM().lookupKlassRefIndexInPool(this, which, opcode);
    }

    /**
     * Gets the uncached klass reference index constant pool entry at index {@code index}. See:
     * {@code ConstantPool::uncached_klass_ref_index_at}.
     *
     * @param index constant pool index
     * @return klass reference index
     */
    private int getUncachedKlassRefIndexAt(int index) {
        checkTagIsFieldOrMethod(index);
        int offset = index * runtime().getHostJVMCIBackend().getTarget().wordSize;
        final int refIndex = UNSAFE.getInt(getConstantPoolPointer() + config().constantPoolSize + offset);
        // klass ref index is in the low 16-bits.
        return refIndex & 0xFFFF;
    }

    /**
     * Checks that the constant pool index {@code index} is in the bounds of the constant pool.
     *
     * @param index constant pool index
     * @throws IndexOutOfBoundsException if the check fails
     */
    private void checkBounds(int index) {
        if (index < 1 || index >= length()) {
            throw new IndexOutOfBoundsException("index " + index + " not between 1 and " + length());
        }
    }

    /**
     * Checks that the constant pool tag at index {@code index} is equal to {@code tag}.
     *
     * @param index constant pool index
     * @param tag expected tag
     * @throws IllegalArgumentException if the check fails
     */
    private void checkTag(int index, JvmConstant tag) {
        final JvmConstant tagAt = getTagAt(index);
        if (tagAt != tag) {
            throw new IllegalArgumentException("constant pool tag at index " + index + " is " + tagAt + " but expected " + tag);
        }
    }

    /**
     * Asserts that the constant pool tag at index {@code index} is a
     * {@link JvmConstants#jvmFieldref}, or a {@link JvmConstants#jvmMethodref}, or a
     * {@link JvmConstants#jvmInterfaceMethodref}.
     *
     * @param index constant pool index
     * @throws IllegalArgumentException if the check fails
     */
    private void checkTagIsFieldOrMethod(int index) {
        final JvmConstant tagAt = getTagAt(index);
        if (tagAt != constants.jvmFieldref && tagAt != constants.jvmMethodref && tagAt != constants.jvmInterfaceMethodref) {
            throw new IllegalArgumentException("constant pool tag at index " + index + " is " + tagAt);
        }
    }

    @Override
    public int length() {
        return UNSAFE.getInt(getConstantPoolPointer() + config().constantPoolLengthOffset);
    }

    public boolean hasDynamicConstant() {
        return (flags() & config().constantPoolHasDynamicConstant) != 0;
    }

    private int flags() {
        return UNSAFE.getInt(getConstantPoolPointer() + config().constantPoolFlagsOffset);
    }

    /**
     * Represents a list of static arguments from a {@link BootstrapMethodInvocation} of the form
     * {{@code arg_count}, {@code pool_index}}, meaning the arguments are not already resolved
     * and that the JDK has to lookup the arguments when they are needed. The {@code bssIndex}
     * corresponds to {@code pool_index} and the {@code size} corresponds to {@code arg_count}.
     */
    static class CachedBSMArgs extends AbstractList<JavaConstant> {
        private final JavaConstant[] cache;
        private final HotSpotConstantPool cp;
        private final int bssIndex;

        CachedBSMArgs(HotSpotConstantPool cp, int bssIndex, int size) {
            this.cp = cp;
            this.bssIndex = bssIndex;
            this.cache = new JavaConstant[size];
        }

        /**
         * Lazily resolves and caches the argument at the given index and returns it. The method
         * {@link CompilerToVM#bootstrapArgumentIndexAt} is used to obtain the constant pool
         * index of the entry and the method {@link ConstantPool#lookupConstant} is used to
         * resolve it. If the resolution failed, the index is returned as a
         * {@link PrimitiveConstant}.
         *
         * @param index index of the element to return
         * @return A {@link JavaConstant} corresponding to the static argument requested. A return
         * value of type {@link PrimitiveConstant} represents an unresolved constant pool entry
         */
        @Override
        public JavaConstant get(int index) {
            JavaConstant res = cache[index];
            if (res == null) {
                int argCpi = compilerToVM().bootstrapArgumentIndexAt(cp, bssIndex, index);
                Object object = cp.lookupConstant(argCpi, false);
                if (object instanceof PrimitiveConstant primitiveConstant) {
                    res = runtime().getReflection().boxPrimitive(primitiveConstant);
                } else if (object instanceof JavaConstant javaConstant) {
                    res = javaConstant;
                } else if (object instanceof JavaType type) {
                    res = runtime().getReflection().forObject(type);
                } else {
                    res = JavaConstant.forInt(argCpi);
                }
                cache[index] = res;
            }
            return res;
        }

        @Override
        public int size() {
            return cache.length;
        }
    }

    static class BootstrapMethodInvocationImpl implements BootstrapMethodInvocation {
        private final boolean indy;
        private final ResolvedJavaMethod method;
        private final String name;
        private final JavaConstant type;
        private final List<JavaConstant> staticArguments;

        BootstrapMethodInvocationImpl(boolean indy, ResolvedJavaMethod method, String name, JavaConstant type, List<JavaConstant> staticArguments) {
            this.indy = indy;
            this.method = method;
            this.name = name;
            this.type = type;
            this.staticArguments = staticArguments;
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            return method;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInvokeDynamic() {
            return indy;
        }

        @Override
        public JavaConstant getType() {
            return type;
        }

        @Override
        public List<JavaConstant> getStaticArguments() {
            return staticArguments;
        }

        @Override
        public String toString() {
            String static_args = staticArguments.stream().map(BootstrapMethodInvocationImpl::argumentAsString).collect(Collectors.joining(", ", "[", "]"));
            return "BootstrapMethod[" + (indy ? "indy" : "condy") +
                            ", method:" + method.format("%H.%n(%p)") +
                            ", name: " + name +
                            ", type: " + type.toValueString() +
                            ", static arguments:" + static_args;
        }

        private static String argumentAsString(JavaConstant arg) {
            String type = arg.getJavaKind().getJavaName();
            String value = arg.toValueString();
            return type + ":" + value;
        }
    }

    @Override
    public BootstrapMethodInvocation lookupBootstrapMethodInvocation(int index, int opcode) {
        int cpi = opcode == -1 ? index : indyIndexConstantPoolIndex(index, opcode);
        final JvmConstant tag = getTagAt(cpi);
        switch (tag.name) {
            case "InvokeDynamic":
            case "Dynamic":
                Object[] bsmi = compilerToVM().resolveBootstrapMethod(this, cpi);
                ResolvedJavaMethod method = (ResolvedJavaMethod) bsmi[0];
                String name = (String) bsmi[1];
                JavaConstant type = (JavaConstant) bsmi[2];
                Object staticArguments = bsmi[3];
                List<JavaConstant> staticArgumentsList;
                if (staticArguments == null) {
                    staticArgumentsList = List.of();
                } else if (staticArguments instanceof JavaConstant) {
                    staticArgumentsList = List.of((JavaConstant) staticArguments);
                } else if (staticArguments instanceof JavaConstant[]) {
                    staticArgumentsList = List.of((JavaConstant[]) staticArguments);
                } else {
                    int[] bsciArgs = (int[]) staticArguments;
                    int argCount = bsciArgs[0];
                    int bss_index = bsciArgs[1];
                    staticArgumentsList = new CachedBSMArgs(this, bss_index, argCount);
                }
                return new BootstrapMethodInvocationImpl(tag.name.equals("InvokeDynamic"), method, name, type, staticArgumentsList);
            default:
                return null;
        }
    }

    /**
     * Gets the {@link JavaConstant} for the {@code ConstantValue} attribute of a field.
     */
    JavaConstant getStaticFieldConstantValue(int cpi) {
        final JvmConstant tag = getTagAt(cpi);
        switch (tag.name) {
            case "Integer":
                return JavaConstant.forInt(getIntAt(cpi));
            case "Long":
                return JavaConstant.forLong(getLongAt(cpi));
            case "Float":
                return JavaConstant.forFloat(getFloatAt(cpi));
            case "Double":
                return JavaConstant.forDouble(getDoubleAt(cpi));
            case "String":
                return compilerToVM().getUncachedStringInPool(this, cpi);
            default:
                throw new IllegalArgumentException("Illegal entry for a ConstantValue attribute:" + tag);
        }
    }

    @Override
    public Object lookupConstant(int cpi) {
        return lookupConstant(cpi, true);
    }

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        final JvmConstant tag = getTagAt(cpi);
        switch (tag.name) {
            case "Integer":
                return JavaConstant.forInt(getIntAt(cpi));
            case "Long":
                return JavaConstant.forLong(getLongAt(cpi));
            case "Float":
                return JavaConstant.forFloat(getFloatAt(cpi));
            case "Double":
                return JavaConstant.forDouble(getDoubleAt(cpi));
            case "Class":
            case "UnresolvedClass":
            case "UnresolvedClassInError":
                final int opcode = -1;  // opcode is not used
                return lookupType(cpi, opcode);
            case "String":
                /*
                 * Normally, we would expect a String here, but unsafe anonymous classes can have
                 * "pseudo strings" (arbitrary live objects) patched into a String entry. Such
                 * entries do not have a symbol in the constant pool slot.
                 */
                return compilerToVM().lookupConstantInPool(this, cpi, true);
            case "MethodHandle":
            case "MethodHandleInError":
            case "MethodType":
            case "MethodTypeInError":
            case "Dynamic":
            case "DynamicInError":
                return compilerToVM().lookupConstantInPool(this, cpi, resolve);
            default:
                throw new JVMCIError("Unknown constant pool tag %s", tag);
        }

    }

    @Override
    public String lookupUtf8(int cpi) {
        checkTag(cpi, constants.jvmUtf8);
        return compilerToVM().getSymbol(getEntryAt(cpi));
    }

    @Override
    public Signature lookupSignature(int cpi) {
        return new HotSpotSignature(runtime(), lookupUtf8(cpi));
    }

    @Override
    public JavaConstant lookupAppendix(int rawIndex, int opcode) {
        if (!Bytecodes.isInvoke(opcode)) {
            throw new IllegalArgumentException("expected an invoke bytecode for " + rawIndex + ", got " + opcode);
        }

        if (opcode == Bytecodes.INVOKEDYNAMIC) {
          if (!isInvokedynamicIndex(rawIndex)) {
              throw new IllegalArgumentException("expected a raw index for INVOKEDYNAMIC but got " + rawIndex);
          }
          return compilerToVM().lookupAppendixInPool(this, rawIndex);
        } else {
          return compilerToVM().lookupAppendixInPool(this, rawIndex);
        }
    }

    /**
     * Gets a {@link JavaType} corresponding a given resolved or unresolved type.
     *
     * @param type either a ResolvedJavaType or a String naming a unresolved type.
     */
    private static JavaType getJavaType(final Object type) {
        if (type instanceof String) {
            String name = (String) type;
            return UnresolvedJavaType.create("L" + name + ";");
        } else {
            return (JavaType) type;
        }
    }

    @Override
    public JavaMethod lookupMethod(int rawIndex, int opcode, ResolvedJavaMethod caller) {
        int which; // interpretation depends on opcode
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            if (!isInvokedynamicIndex(rawIndex)) {
                throw new IllegalArgumentException("expected a raw index for INVOKEDYNAMIC but got " + rawIndex);
            }
            which = rawIndex;
        } else {
            which = rawIndex;
        }
        final HotSpotResolvedJavaMethod method = compilerToVM().lookupMethodInPool(this, which, (byte) opcode, (HotSpotResolvedJavaMethodImpl) caller);
        if (method != null) {
            return method;
        } else {
            // Get the method's name and signature.
            String name = compilerToVM().lookupNameInPool(this, which, opcode);
            HotSpotSignature signature = new HotSpotSignature(runtime(), compilerToVM().lookupSignatureInPool(this, which, opcode));
            if (opcode == Bytecodes.INVOKEDYNAMIC) {
                return new UnresolvedJavaMethod(name, signature, runtime().getMethodHandleClass());
            } else {
                final int klassIndex = getKlassRefIndexAt(which, opcode);
                final Object type = compilerToVM().lookupKlassInPool(this, klassIndex);
                return new UnresolvedJavaMethod(name, signature, getJavaType(type));
            }
        }
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        final LookupTypeCacheElement elem = this.lastLookupType;
        if (elem != null && elem.lastCpi == cpi) {
            return elem.javaType;
        } else {
            final Object type = compilerToVM().lookupKlassInPool(this, cpi);
            JavaType result = getJavaType(type);
            if (result instanceof ResolvedJavaType) {
                this.lastLookupType = new LookupTypeCacheElement(cpi, result);
            }
            return result;
        }
    }

    @Override
    public JavaType lookupReferencedType(int rawIndex, int opcode) {
        int cpi;
        switch (opcode) {
            case Bytecodes.CHECKCAST:
            case Bytecodes.INSTANCEOF:
            case Bytecodes.NEW:
            case Bytecodes.ANEWARRAY:
            case Bytecodes.MULTIANEWARRAY:
            case Bytecodes.LDC:
            case Bytecodes.LDC_W:
            case Bytecodes.LDC2_W:
                cpi = rawIndex;
                break;
            case Bytecodes.GETSTATIC:
            case Bytecodes.PUTSTATIC:
            case Bytecodes.GETFIELD:
            case Bytecodes.PUTFIELD:
                cpi = getKlassRefIndexAt(rawIndex, opcode);
                break;
            case Bytecodes.INVOKEVIRTUAL:
            case Bytecodes.INVOKESPECIAL:
            case Bytecodes.INVOKESTATIC:
            case Bytecodes.INVOKEINTERFACE: {
                cpi = getKlassRefIndexAt(rawIndex, opcode);
                break;
            }
            default:
                throw JVMCIError.shouldNotReachHere("Unexpected opcode " + opcode);
        }
        final Object type = compilerToVM().lookupKlassInPool(this, cpi);
        return getJavaType(type);
    }

    @Override
    public JavaField lookupField(int rawIndex, ResolvedJavaMethod method, int opcode) {
        final int nameAndTypeIndex = getNameAndTypeRefIndexAt(rawIndex, opcode);
        final int typeIndex = getSignatureRefIndexAt(nameAndTypeIndex);
        String typeName = lookupUtf8(typeIndex);
        JavaType type = runtime().lookupType(typeName, getHolder(), false);

        final int holderIndex = getKlassRefIndexAt(rawIndex, opcode);
        JavaType fieldHolder = lookupType(holderIndex, opcode);

        if (fieldHolder instanceof HotSpotResolvedObjectTypeImpl) {
            int[] info = new int[4];
            HotSpotResolvedObjectTypeImpl resolvedHolder;
            try {
                resolvedHolder = compilerToVM().resolveFieldInPool(this, rawIndex, (HotSpotResolvedJavaMethodImpl) method, (byte) opcode, info);
            } catch (Throwable t) {
                resolvedHolder = null;
            }
            if (resolvedHolder == null) {
                // There was an exception resolving the field or it returned null so return an unresolved field.
                return new UnresolvedJavaField(fieldHolder, lookupUtf8(getNameRefIndexAt(nameAndTypeIndex)), type);
            }
            final int flags = info[0];
            final int offset = info[1];
            final int fieldIndex = info[2];
            final int fieldFlags = info[3];
            HotSpotResolvedJavaField result = resolvedHolder.createField(type, offset, flags, fieldFlags, fieldIndex);
            return result;
        } else {
            return new UnresolvedJavaField(fieldHolder, lookupUtf8(getNameRefIndexAt(nameAndTypeIndex)), type);
        }
    }

    /**
     * Converts a raw index for the INVOKEDYNAMIC bytecode to a constant pool index.
     *
     * @param rawIndex index from the bytecode
     *
     * @param opcode bytecode to convert the index for. Must be INVOKEDYNAMIC.
     *
     * @return constant pool index
     */
    private int indyIndexConstantPoolIndex(int rawIndex, int opcode) {
        if (isInvokedynamicIndex(rawIndex)) {
            if (opcode != Bytecodes.INVOKEDYNAMIC) {
                throw new IllegalArgumentException("expected INVOKEDYNAMIC at " + rawIndex + ", got " + opcode);
            }
            return compilerToVM().decodeIndyIndexToCPIndex(this, rawIndex, false);
        } else {
          throw new IllegalArgumentException("expected a raw index for INVOKEDYNAMIC but got " + rawIndex);
        }
    }

    @Override
    public void loadReferencedType(int rawIndex, int opcode) {
        loadReferencedType(rawIndex, opcode, true /* initialize */);
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void loadReferencedType(int rawIndex, int opcode, boolean initialize) {
        int cpi;
        switch (opcode) {
            case Bytecodes.CHECKCAST:
            case Bytecodes.INSTANCEOF:
            case Bytecodes.NEW:
            case Bytecodes.ANEWARRAY:
            case Bytecodes.MULTIANEWARRAY:
            case Bytecodes.LDC:
            case Bytecodes.LDC_W:
            case Bytecodes.LDC2_W:
                cpi = rawIndex;
                break;
            case Bytecodes.INVOKEDYNAMIC: {
                // invokedynamic indices are different from constant pool cache indices
                if (!isInvokedynamicIndex(rawIndex)) {
                    throw new IllegalArgumentException("must use invokedynamic index but got " + rawIndex);
                }
                cpi = compilerToVM().decodeIndyIndexToCPIndex(this, rawIndex, true);
                break;
            }
            case Bytecodes.GETSTATIC:
            case Bytecodes.PUTSTATIC:
            case Bytecodes.GETFIELD:
            case Bytecodes.PUTFIELD:
                cpi = compilerToVM().decodeFieldIndexToCPIndex(this, rawIndex);
                break;
            case Bytecodes.INVOKEVIRTUAL:
            case Bytecodes.INVOKESPECIAL:
            case Bytecodes.INVOKESTATIC:
            case Bytecodes.INVOKEINTERFACE: {
                cpi = compilerToVM().decodeMethodIndexToCPIndex(this, rawIndex);
                break;
            }
            default:
                throw JVMCIError.shouldNotReachHere("Unexpected opcode " + opcode);
        }

        final JvmConstant tag = getTagAt(cpi);
        if (tag == null) {
            assert getTagAt(cpi - 1) == constants.jvmDouble || getTagAt(cpi - 1) == constants.jvmLong;
            return;
        }
        switch (tag.name) {
            case "Methodref":
            case "Fieldref":
            case "InterfaceMethodref":
                cpi = getUncachedKlassRefIndexAt(cpi);
                // Read the tag only once because it could change between multiple reads.
                final JvmConstant klassTag = getTagAt(cpi);
                assert klassTag == constants.jvmClass || klassTag == constants.jvmUnresolvedClass || klassTag == constants.jvmUnresolvedClassInError : klassTag;
                // fall through
            case "Class":
            case "UnresolvedClass":
            case "UnresolvedClassInError":
                final HotSpotResolvedObjectTypeImpl type = compilerToVM().resolveTypeInPool(this, cpi);
                if (initialize && !type.isPrimitive() && !type.isArray()) {
                    type.ensureInitialized();
                }
                if (tag == constants.jvmMethodref) {
                    if (Bytecodes.isInvokeHandleAlias(opcode) && isSignaturePolymorphicHolder(type)) {
                        checkTag(compilerToVM().decodeMethodIndexToCPIndex(this, rawIndex), constants.jvmMethodref);
                        compilerToVM().resolveInvokeHandleInPool(this, rawIndex);
                    }
                }

                break;
            case "InvokeDynamic":
                // nothing
                break;
            default:
                // nothing
                break;
        }

    }

    // Lazily initialized.
    private static String[] signaturePolymorphicHolders;

    /**
     * Determines if {@code type} contains signature polymorphic methods.
     */
    @SuppressFBWarnings(value = "LI_LAZY_INIT_STATIC", justification = "signaturePolymorphicHolders is a cache, not a singleton that must be constructed exactly once" +
                    "and compiler re-ordering is not an issue due to the VM call")
    static boolean isSignaturePolymorphicHolder(final ResolvedJavaType type) {
        String name = type.getName();
        if (signaturePolymorphicHolders == null) {
            signaturePolymorphicHolders = compilerToVM().getSignaturePolymorphicHolders();
        }
        for (String holder : signaturePolymorphicHolders) {
            if (name.equals(holder)) {
                return true;
            }
        }
        return false;
    }

    public String getSourceFileName() {
        final int sourceFileNameIndex = UNSAFE.getChar(getConstantPoolPointer() + config().constantPoolSourceFileNameIndexOffset);
        if (sourceFileNameIndex == 0) {
            return null;
        }
        return lookupUtf8(sourceFileNameIndex);
    }

    @Override
    public String toString() {
        return "HotSpotConstantPool<" + getHolder().toJavaName() + ">";
    }
}
