/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.bcel.internal.generic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.Constant;
import com.sun.org.apache.bcel.internal.classfile.ConstantCP;
import com.sun.org.apache.bcel.internal.classfile.ConstantClass;
import com.sun.org.apache.bcel.internal.classfile.ConstantDouble;
import com.sun.org.apache.bcel.internal.classfile.ConstantDynamic;
import com.sun.org.apache.bcel.internal.classfile.ConstantFieldref;
import com.sun.org.apache.bcel.internal.classfile.ConstantFloat;
import com.sun.org.apache.bcel.internal.classfile.ConstantInteger;
import com.sun.org.apache.bcel.internal.classfile.ConstantInterfaceMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantInvokeDynamic;
import com.sun.org.apache.bcel.internal.classfile.ConstantLong;
import com.sun.org.apache.bcel.internal.classfile.ConstantMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantNameAndType;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.ConstantString;
import com.sun.org.apache.bcel.internal.classfile.ConstantUtf8;
import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * This class is used to build up a constant pool. The user adds constants via 'addXXX' methods, 'addString',
 * 'addClass', etc.. These methods return an index into the constant pool. Finally, 'getFinalConstantPool()' returns the
 * constant pool built up. Intermediate versions of the constant pool can be obtained with 'getConstantPool()'. A
 * constant pool has capacity for Constants.MAX_SHORT entries. Note that the first (0) is used by the JVM and that
 * Double and Long constants need two slots.
 *
 * @see Constant
 * @LastModified: Feb 2023
 */
public class ConstantPoolGen {

    private static final int DEFAULT_BUFFER_SIZE = 256;

    private static final String METHODREF_DELIM = ":";

    private static final String IMETHODREF_DELIM = "#";

    private static final String FIELDREF_DELIM = "&";

    private static final String NAT_DELIM = "%"; // Name and Type

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected int size;

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected Constant[] constants;

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getSize()
     */
    @Deprecated
    protected int index = 1; // First entry (0) used by JVM

    private final Map<String, Integer> stringTable = new HashMap<>();

    private final Map<String, Integer> classTable = new HashMap<>();

    private final Map<String, Integer> utf8Table = new HashMap<>();

    private final Map<String, Integer> natTable = new HashMap<>();

    private final Map<String, Integer> cpTable = new HashMap<>();

    /**
     * Constructs a new empty constant pool.
     */
    public ConstantPoolGen() {
        size = DEFAULT_BUFFER_SIZE;
        constants = new Constant[size];
    }

    /**
     * Constructs a new instance with the given array of constants.
     *
     * @param cs array of given constants, new ones will be appended
     */
    public ConstantPoolGen(final Constant[] cs) {
        /*
         * To be logically/programmatically correct, the size of the constant pool
         * shall not exceed the size limit as the code below does a copy and then
         * walk through the whole array.
         * This is however, not used by XSLT (or the java.xml implementation),
         * and only happens when BCELifier is called (see BCELifier).
        */
        if (cs.length > Const.MAX_CP_ENTRIES) {
            throw new IllegalStateException("The number of constants " + cs.length
                    + " is over the size limit of the constant pool: "
                    + Const.MAX_CP_ENTRIES);
        }

        final StringBuilder sb = new StringBuilder(DEFAULT_BUFFER_SIZE);

        size = Math.min(Math.max(DEFAULT_BUFFER_SIZE, cs.length + 64), Const.MAX_CP_ENTRIES);
        constants = new Constant[size];

        System.arraycopy(cs, 0, constants, 0, cs.length);
        if (cs.length > 0) {
            index = cs.length;
        }

        for (int i = 1; i < index; i++) {
            final Constant c = constants[i];
            if (c instanceof ConstantString) {
                final ConstantString s = (ConstantString) c;
                final ConstantUtf8 u8 = (ConstantUtf8) constants[s.getStringIndex()];
                final String key = u8.getBytes();
                if (!stringTable.containsKey(key)) {
                    stringTable.put(key, Integer.valueOf(i));
                }
            } else if (c instanceof ConstantClass) {
                final ConstantClass s = (ConstantClass) c;
                final ConstantUtf8 u8 = (ConstantUtf8) constants[s.getNameIndex()];
                final String key = u8.getBytes();
                if (!classTable.containsKey(key)) {
                    classTable.put(key, Integer.valueOf(i));
                }
            } else if (c instanceof ConstantNameAndType) {
                final ConstantNameAndType n = (ConstantNameAndType) c;
                final ConstantUtf8 u8NameIdx = (ConstantUtf8) constants[n.getNameIndex()];
                final ConstantUtf8 u8SigIdx = (ConstantUtf8) constants[n.getSignatureIndex()];

                sb.append(u8NameIdx.getBytes());
                sb.append(NAT_DELIM);
                sb.append(u8SigIdx.getBytes());
                final String key = sb.toString();
                sb.delete(0, sb.length());

                if (!natTable.containsKey(key)) {
                    natTable.put(key, Integer.valueOf(i));
                }
            } else if (c instanceof ConstantUtf8) {
                final ConstantUtf8 u = (ConstantUtf8) c;
                final String key = u.getBytes();
                if (!utf8Table.containsKey(key)) {
                    utf8Table.put(key, Integer.valueOf(i));
                }
            } else if (c instanceof ConstantCP) {
                final ConstantCP m = (ConstantCP) c;
                String className;
                ConstantUtf8 u8;

                if (c instanceof ConstantInvokeDynamic) {
                    className = Integer.toString(((ConstantInvokeDynamic) m).getBootstrapMethodAttrIndex());
                } else if (c instanceof ConstantDynamic) {
                    className = Integer.toString(((ConstantDynamic) m).getBootstrapMethodAttrIndex());
                } else {
                    final ConstantClass clazz = (ConstantClass) constants[m.getClassIndex()];
                    u8 = (ConstantUtf8) constants[clazz.getNameIndex()];
                    className = Utility.pathToPackage(u8.getBytes());
                }

                final ConstantNameAndType n = (ConstantNameAndType) constants[m.getNameAndTypeIndex()];
                u8 = (ConstantUtf8) constants[n.getNameIndex()];
                final String methodName = u8.getBytes();
                u8 = (ConstantUtf8) constants[n.getSignatureIndex()];
                final String signature = u8.getBytes();

                // Since name cannot begin with digit, we can use METHODREF_DELIM without fear of duplicates
                String delim = METHODREF_DELIM;
                if (c instanceof ConstantInterfaceMethodref) {
                    delim = IMETHODREF_DELIM;
                } else if (c instanceof ConstantFieldref) {
                    delim = FIELDREF_DELIM;
                }

                sb.append(className);
                sb.append(delim);
                sb.append(methodName);
                sb.append(delim);
                sb.append(signature);
                final String key = sb.toString();
                sb.delete(0, sb.length());

                if (!cpTable.containsKey(key)) {
                    cpTable.put(key, Integer.valueOf(i));
                }
            }
//            else if (c == null) { // entries may be null
//                // nothing to do
//            } else if (c instanceof ConstantInteger) {
//                // nothing to do
//            } else if (c instanceof ConstantLong) {
//                // nothing to do
//            } else if (c instanceof ConstantFloat) {
//                // nothing to do
//            } else if (c instanceof ConstantDouble) {
//                // nothing to do
//            } else if (c instanceof com.sun.org.apache.bcel.internal.classfile.ConstantMethodType) {
//                // TODO should this be handled somehow?
//            } else if (c instanceof com.sun.org.apache.bcel.internal.classfile.ConstantMethodHandle) {
//                // TODO should this be handled somehow?
//            } else if (c instanceof com.sun.org.apache.bcel.internal.classfile.ConstantModule) {
//                // TODO should this be handled somehow?
//            } else if (c instanceof com.sun.org.apache.bcel.internal.classfile.ConstantPackage) {
//                // TODO should this be handled somehow?
//            } else {
//                // Not helpful, should throw an exception.
//                assert false : "Unexpected constant type: " + c.getClass().getName();
//            }
        }
    }

    /**
     * Constructs a new instance with the given constant pool.
     *
     * @param cp the constant pool.
     */
    public ConstantPoolGen(final ConstantPool cp) {
        this(cp.getConstantPool());
    }

    /**
     * Add a reference to an array class (e.g. String[][]) as needed by MULTIANEWARRAY instruction, e.g. to the
     * ConstantPool.
     *
     * @param type type of array class
     * @return index of entry
     */
    public int addArrayClass(final ArrayType type) {
        return addClass_(type.getSignature());
    }

    /**
     * Add a new Class reference to the ConstantPool for a given type.
     *
     * @param type Class to add
     * @return index of entry
     */
    public int addClass(final ObjectType type) {
        return addClass(type.getClassName());
    }

    /**
     * Add a new Class reference to the ConstantPool, if it is not already in there.
     *
     * @param str Class to add
     * @return index of entry
     */
    public int addClass(final String str) {
        return addClass_(Utility.packageToPath(str));
    }

    private int addClass_(final String clazz) {
        final int cpRet;
        if ((cpRet = lookupClass(clazz)) != -1) {
            return cpRet; // Already in CP
        }
        adjustSize();
        final ConstantClass c = new ConstantClass(addUtf8(clazz));
        final int ret = index;
        constants[index++] = c;
        return computeIfAbsent(classTable, clazz, ret);
    }

    /**
     * Adds a constant from another ConstantPool and returns the new index.
     *
     * @param constant The constant to add.
     * @param cpGen Source pool.
     * @return index of entry
     */
    public int addConstant(final Constant constant, final ConstantPoolGen cpGen) {
        final Constant[] constants = cpGen.getConstantPool().getConstantPool();
        switch (constant.getTag()) {
        case Const.CONSTANT_String: {
            final ConstantString s = (ConstantString) constant;
            final ConstantUtf8 u8 = (ConstantUtf8) constants[s.getStringIndex()];
            return addString(u8.getBytes());
        }
        case Const.CONSTANT_Class: {
            final ConstantClass s = (ConstantClass) constant;
            final ConstantUtf8 u8 = (ConstantUtf8) constants[s.getNameIndex()];
            return addClass(u8.getBytes());
        }
        case Const.CONSTANT_NameAndType: {
            final ConstantNameAndType n = (ConstantNameAndType) constant;
            final ConstantUtf8 u8 = (ConstantUtf8) constants[n.getNameIndex()];
            final ConstantUtf8 u8_2 = (ConstantUtf8) constants[n.getSignatureIndex()];
            return addNameAndType(u8.getBytes(), u8_2.getBytes());
        }
        case Const.CONSTANT_Utf8:
            return addUtf8(((ConstantUtf8) constant).getBytes());
        case Const.CONSTANT_Double:
            return addDouble(((ConstantDouble) constant).getBytes());
        case Const.CONSTANT_Float:
            return addFloat(((ConstantFloat) constant).getBytes());
        case Const.CONSTANT_Long:
            return addLong(((ConstantLong) constant).getBytes());
        case Const.CONSTANT_Integer:
            return addInteger(((ConstantInteger) constant).getBytes());
        case Const.CONSTANT_InterfaceMethodref:
        case Const.CONSTANT_Methodref:
        case Const.CONSTANT_Fieldref: {
            final ConstantCP m = (ConstantCP) constant;
            final ConstantClass clazz = (ConstantClass) constants[m.getClassIndex()];
            final ConstantNameAndType n = (ConstantNameAndType) constants[m.getNameAndTypeIndex()];
            ConstantUtf8 u8 = (ConstantUtf8) constants[clazz.getNameIndex()];
            final String className = Utility.pathToPackage(u8.getBytes());
            u8 = (ConstantUtf8) constants[n.getNameIndex()];
            final String name = u8.getBytes();
            u8 = (ConstantUtf8) constants[n.getSignatureIndex()];
            final String signature = u8.getBytes();
            switch (constant.getTag()) {
            case Const.CONSTANT_InterfaceMethodref:
                return addInterfaceMethodref(className, name, signature);
            case Const.CONSTANT_Methodref:
                return addMethodref(className, name, signature);
            case Const.CONSTANT_Fieldref:
                return addFieldref(className, name, signature);
            default: // Never reached
                throw new IllegalArgumentException("Unknown constant type " + constant);
            }
        }
        default: // Never reached
            throw new IllegalArgumentException("Unknown constant type " + constant);
        }
    }

    /**
     * Add a new double constant to the ConstantPool, if it is not already in there.
     *
     * @param n Double number to add
     * @return index of entry
     */
    public int addDouble(final double n) {
        int ret;
        if ((ret = lookupDouble(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index] = new ConstantDouble(n);
        index += 2; // Wastes one entry according to spec
        return ret;
    }

    /**
     * Add a new Fieldref constant to the ConstantPool, if it is not already in there.
     *
     * @param className class name string to add
     * @param fieldName field name string to add
     * @param signature signature string to add
     * @return index of entry
     */
    public int addFieldref(final String className, final String fieldName, final String signature) {
        final int cpRet;
        if ((cpRet = lookupFieldref(className, fieldName, signature)) != -1) {
            return cpRet; // Already in CP
        }
        adjustSize();
        final int classIndex = addClass(className);
        final int nameAndTypeIndex = addNameAndType(fieldName, signature);
        final int ret = index;
        constants[index++] = new ConstantFieldref(classIndex, nameAndTypeIndex);
        return computeIfAbsent(cpTable, className + FIELDREF_DELIM + fieldName + FIELDREF_DELIM + signature, ret);
    }

    /**
     * Add a new Float constant to the ConstantPool, if it is not already in there.
     *
     * @param n Float number to add
     * @return index of entry
     */
    public int addFloat(final float n) {
        int ret;
        if ((ret = lookupFloat(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index++] = new ConstantFloat(n);
        return ret;
    }

    /**
     * Add a new Integer constant to the ConstantPool, if it is not already in there.
     *
     * @param n integer number to add
     * @return index of entry
     */
    public int addInteger(final int n) {
        int ret;
        if ((ret = lookupInteger(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index++] = new ConstantInteger(n);
        return ret;
    }

    public int addInterfaceMethodref(final MethodGen method) {
        return addInterfaceMethodref(method.getClassName(), method.getName(), method.getSignature());
    }

    /**
     * Add a new InterfaceMethodref constant to the ConstantPool, if it is not already in there.
     *
     * @param className class name string to add
     * @param methodName method name string to add
     * @param signature signature string to add
     * @return index of entry
     */
    public int addInterfaceMethodref(final String className, final String methodName, final String signature) {
        final int cpRet;
        if ((cpRet = lookupInterfaceMethodref(className, methodName, signature)) != -1) {
            return cpRet; // Already in CP
        }
        adjustSize();
        final int classIndex = addClass(className);
        final int nameAndTypeIndex = addNameAndType(methodName, signature);
        final int ret = index;
        constants[index++] = new ConstantInterfaceMethodref(classIndex, nameAndTypeIndex);
        return computeIfAbsent(cpTable, className + IMETHODREF_DELIM + methodName + IMETHODREF_DELIM + signature, ret);
    }

    /**
     * Add a new long constant to the ConstantPool, if it is not already in there.
     *
     * @param n Long number to add
     * @return index of entry
     */
    public int addLong(final long n) {
        int ret;
        if ((ret = lookupLong(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index] = new ConstantLong(n);
        index += 2; // Wastes one entry according to spec
        return ret;
    }
    public int addMethodref(final MethodGen method) {
        return addMethodref(method.getClassName(), method.getName(), method.getSignature());
    }

    /**
     * Add a new Methodref constant to the ConstantPool, if it is not already in there.
     *
     * @param className class name string to add
     * @param methodName method name string to add
     * @param signature method signature string to add
     * @return index of entry
     */
    public int addMethodref(final String className, final String methodName, final String signature) {
        final int cpRet;
        if ((cpRet = lookupMethodref(className, methodName, signature)) != -1) {
            return cpRet; // Already in CP
        }
        adjustSize();
        final int nameAndTypeIndex = addNameAndType(methodName, signature);
        final int classIndex = addClass(className);
        final int ret = index;
        constants[index++] = new ConstantMethodref(classIndex, nameAndTypeIndex);
        return computeIfAbsent(cpTable, className + METHODREF_DELIM + methodName + METHODREF_DELIM + signature, ret);
    }

    /**
     * Add a new NameAndType constant to the ConstantPool if it is not already in there.
     *
     * @param name Name string to add
     * @param signature signature string to add
     * @return index of entry
     */
    public int addNameAndType(final String name, final String signature) {
        int ret;
        if ((ret = lookupNameAndType(name, signature)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        final int nameIndex = addUtf8(name);
        final int signatureIndex = addUtf8(signature);
        ret = index;
        constants[index++] = new ConstantNameAndType(nameIndex, signatureIndex);
        return computeIfAbsent(natTable, name + NAT_DELIM + signature, ret);
    }

    /**
     * Add a new String constant to the ConstantPool, if it is not already in there.
     *
     * @param str String to add
     * @return index of entry
     */
    public int addString(final String str) {
        int ret;
        if ((ret = lookupString(str)) != -1) {
            return ret; // Already in CP
        }
        final int utf8 = addUtf8(str);
        adjustSize();
        final ConstantString s = new ConstantString(utf8);
        ret = index;
        constants[index++] = s;
        return computeIfAbsent(stringTable, str, ret);
    }

    /**
     * Add a new Utf8 constant to the ConstantPool, if it is not already in there.
     *
     * @param n Utf8 string to add
     * @return index of entry
     */
    public int addUtf8(final String n) {
        int ret;
        if ((ret = lookupUtf8(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index++] = new ConstantUtf8(n);
        return computeIfAbsent(utf8Table, n, ret);
    }

    /**
     * Resize internal array of constants.
     */
    protected void adjustSize() {
        // 3 extra spaces are needed as some entries may take 3 slots
        if (index + 3 >= Const.MAX_CP_ENTRIES) {
            throw new IllegalStateException("The number of constants " + (index + 3)
                    + " is over the size limit of the constant pool: "
                    + Const.MAX_CP_ENTRIES);
        }

        if (index + 3 >= size) {
            final Constant[] cs = constants;
            size *= 2;
            // the constant array shall not exceed the size of the constant pool
            size = Math.min(size, Const.MAX_CP_ENTRIES);
            constants = new Constant[size];
            System.arraycopy(cs, 0, constants, 0, index);
        }
    }

    private int computeIfAbsent(final Map<String, Integer> map, final String key, final int value) {
        return map.computeIfAbsent(key, k -> Integer.valueOf(value));
    }

    /**
     * @param i index in constant pool
     * @return constant pool entry at index i
     */
    public Constant getConstant(final int i) {
        return constants[i];
    }

    /**
     * @return intermediate constant pool
     */
    public ConstantPool getConstantPool() {
        return new ConstantPool(constants);
    }

    /**
     * @return constant pool with proper length
     */
    public ConstantPool getFinalConstantPool() {
        return new ConstantPool(Arrays.copyOf(constants, index));
    }

    private int getIndex(final Map<String, Integer> map, final String key) {
        return toIndex(map.get(key));
    }

    /**
     * @return current size of constant pool
     */
    public int getSize() {
        return index;
    }

    /**
     * Look for ConstantClass in ConstantPool named 'str'.
     *
     * @param str String to search for
     * @return index on success, -1 otherwise
     */
    public int lookupClass(final String str) {
        return getIndex(classTable, Utility.packageToPath(str));
    }

    /**
     * Look for ConstantDouble in ConstantPool.
     *
     * @param n Double number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupDouble(final double n) {
        final long bits = Double.doubleToLongBits(n);
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantDouble) {
                final ConstantDouble c = (ConstantDouble) constants[i];
                if (Double.doubleToLongBits(c.getBytes()) == bits) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Look for ConstantFieldref in ConstantPool.
     *
     * @param className Where to find method
     * @param fieldName Guess what
     * @param signature return and argument types
     * @return index on success, -1 otherwise
     */
    public int lookupFieldref(final String className, final String fieldName, final String signature) {
        return getIndex(cpTable, className + FIELDREF_DELIM + fieldName + FIELDREF_DELIM + signature);
    }

    /**
     * Look for ConstantFloat in ConstantPool.
     *
     * @param n Float number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupFloat(final float n) {
        final int bits = Float.floatToIntBits(n);
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantFloat) {
                final ConstantFloat c = (ConstantFloat) constants[i];
                if (Float.floatToIntBits(c.getBytes()) == bits) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Look for ConstantInteger in ConstantPool.
     *
     * @param n integer number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupInteger(final int n) {
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantInteger) {
                final ConstantInteger c = (ConstantInteger) constants[i];
                if (c.getBytes() == n) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int lookupInterfaceMethodref(final MethodGen method) {
        return lookupInterfaceMethodref(method.getClassName(), method.getName(), method.getSignature());
    }

    /**
     * Look for ConstantInterfaceMethodref in ConstantPool.
     *
     * @param className Where to find method
     * @param methodName Guess what
     * @param signature return and argument types
     * @return index on success, -1 otherwise
     */
    public int lookupInterfaceMethodref(final String className, final String methodName, final String signature) {
        return getIndex(cpTable, className + IMETHODREF_DELIM + methodName + IMETHODREF_DELIM + signature);
    }

    /**
     * Look for ConstantLong in ConstantPool.
     *
     * @param n Long number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupLong(final long n) {
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantLong) {
                final ConstantLong c = (ConstantLong) constants[i];
                if (c.getBytes() == n) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int lookupMethodref(final MethodGen method) {
        return lookupMethodref(method.getClassName(), method.getName(), method.getSignature());
    }

    /**
     * Look for ConstantMethodref in ConstantPool.
     *
     * @param className Where to find method
     * @param methodName Guess what
     * @param signature return and argument types
     * @return index on success, -1 otherwise
     */
    public int lookupMethodref(final String className, final String methodName, final String signature) {
        return getIndex(cpTable, className + METHODREF_DELIM + methodName + METHODREF_DELIM + signature);
    }

    /**
     * Look for ConstantNameAndType in ConstantPool.
     *
     * @param name of variable/method
     * @param signature of variable/method
     * @return index on success, -1 otherwise
     */
    public int lookupNameAndType(final String name, final String signature) {
        return getIndex(natTable, name + NAT_DELIM + signature);
    }

    /**
     * Look for ConstantString in ConstantPool containing String 'str'.
     *
     * @param str String to search for
     * @return index on success, -1 otherwise
     */
    public int lookupString(final String str) {
        return getIndex(stringTable, str);
    }

    /**
     * Look for ConstantUtf8 in ConstantPool.
     *
     * @param n Utf8 string to look for
     * @return index on success, -1 otherwise
     */
    public int lookupUtf8(final String n) {
        return getIndex(utf8Table, n);
    }

    /**
     * Use with care!
     *
     * @param i index in constant pool
     * @param c new constant pool entry at index i
     */
    public void setConstant(final int i, final Constant c) {
        constants[i] = c;
    }

    private int toIndex(final Integer index) {
        return index != null ? index.intValue() : -1;
    }

    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        for (int i = 1; i < index; i++) {
            buf.append(i).append(")").append(constants[i]).append("\n");
        }
        return buf.toString();
    }
}
