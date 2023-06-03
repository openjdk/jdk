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
package com.sun.org.apache.bcel.internal.classfile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.generic.Type;
import com.sun.org.apache.bcel.internal.util.BCELComparator;
import com.sun.org.apache.bcel.internal.util.ClassQueue;
import com.sun.org.apache.bcel.internal.util.SyntheticRepository;

/**
 * Represents a Java class, i.e., the data structures, constant pool, fields, methods and commands contained in a Java
 * .class file. See <a href="https://docs.oracle.com/javase/specs/">JVM specification</a> for details. The intent of
 * this class is to represent a parsed or otherwise existing class file. Those interested in programmatically generating
 * classes should see the <a href="../generic/ClassGen.html">ClassGen</a> class.
 *
 * @see com.sun.org.apache.bcel.internal.generic.ClassGen
 * @LastModified: Feb 2023
 */
public class JavaClass extends AccessFlags implements Cloneable, Node, Comparable<JavaClass> {

    /**
     * The standard class file extension.
     *
     * @since 6.7.0
     */
    public static final String EXTENSION = ".class";

    /**
     * Empty array.
     *
     * @since 6.6.0
     */
    public static final JavaClass[] EMPTY_ARRAY = {};

    public static final byte HEAP = 1;
    public static final byte FILE = 2;
    public static final byte ZIP = 3;
    private static BCELComparator bcelComparator = new BCELComparator() {

        @Override
        public boolean equals(final Object o1, final Object o2) {
            final JavaClass THIS = (JavaClass) o1;
            final JavaClass THAT = (JavaClass) o2;
            return Objects.equals(THIS.getClassName(), THAT.getClassName());
        }

        @Override
        public int hashCode(final Object o) {
            final JavaClass THIS = (JavaClass) o;
            return THIS.getClassName().hashCode();
        }
    };

    /**
     * @return Comparison strategy object
     */
    public static BCELComparator getComparator() {
        return bcelComparator;
    }

    private static String indent(final Object obj) {
        final StringTokenizer tokenizer = new StringTokenizer(obj.toString(), "\n");
        final StringBuilder buf = new StringBuilder();
        while (tokenizer.hasMoreTokens()) {
            buf.append("\t").append(tokenizer.nextToken()).append("\n");
        }
        return buf.toString();
    }

    /**
     * @param comparator Comparison strategy object
     */
    public static void setComparator(final BCELComparator comparator) {
        bcelComparator = comparator;
    }

    private String fileName;
    private final String packageName;
    private String sourceFileName = "<Unknown>";
    private int classNameIndex;
    private int superclassNameIndex;
    private String className;
    private String superclassName;
    private int major;
    private int minor; // Compiler version
    private ConstantPool constantPool; // Constant pool
    private int[] interfaces; // implemented interfaces
    private String[] interfaceNames;
    private Field[] fields; // Fields, i.e., variables of class
    private Method[] methods; // methods defined in the class
    private Attribute[] attributes; // attributes defined in the class

    private AnnotationEntry[] annotations; // annotations defined on the class
    private byte source = HEAP; // Generated in memory

    private boolean isAnonymous;

    private boolean isNested;

    private boolean computedNestedTypeStatus;

    /**
     * In cases where we go ahead and create something, use the default SyntheticRepository, because we don't know any
     * better.
     */
    private transient com.sun.org.apache.bcel.internal.util.Repository repository = SyntheticRepository.getInstance();

    /**
     * Constructor gets all contents as arguments.
     *
     * @param classNameIndex Class name
     * @param superclassNameIndex Superclass name
     * @param fileName File name
     * @param major Major compiler version
     * @param minor Minor compiler version
     * @param accessFlags Access rights defined by bit flags
     * @param constantPool Array of constants
     * @param interfaces Implemented interfaces
     * @param fields Class fields
     * @param methods Class methods
     * @param attributes Class attributes
     */
    public JavaClass(final int classNameIndex, final int superclassNameIndex, final String fileName, final int major, final int minor, final int accessFlags,
        final ConstantPool constantPool, final int[] interfaces, final Field[] fields, final Method[] methods, final Attribute[] attributes) {
        this(classNameIndex, superclassNameIndex, fileName, major, minor, accessFlags, constantPool, interfaces, fields, methods, attributes, HEAP);
    }

    /**
     * Constructor gets all contents as arguments.
     *
     * @param classNameIndex Index into constant pool referencing a ConstantClass that represents this class.
     * @param superclassNameIndex Index into constant pool referencing a ConstantClass that represents this class's
     *        superclass.
     * @param fileName File name
     * @param major Major compiler version
     * @param minor Minor compiler version
     * @param accessFlags Access rights defined by bit flags
     * @param constantPool Array of constants
     * @param interfaces Implemented interfaces
     * @param fields Class fields
     * @param methods Class methods
     * @param attributes Class attributes
     * @param source Read from file or generated in memory?
     */
    public JavaClass(final int classNameIndex, final int superclassNameIndex, final String fileName, final int major, final int minor, final int accessFlags,
        final ConstantPool constantPool, int[] interfaces, Field[] fields, Method[] methods, Attribute[] attributes, final byte source) {
        super(accessFlags);
        if (interfaces == null) {
            interfaces = Const.EMPTY_INT_ARRAY;
        }
        if (attributes == null) {
            attributes = Attribute.EMPTY_ARRAY;
        }
        if (fields == null) {
            fields = Field.EMPTY_FIELD_ARRAY;
        }
        if (methods == null) {
            methods = Method.EMPTY_METHOD_ARRAY;
        }
        this.classNameIndex = classNameIndex;
        this.superclassNameIndex = superclassNameIndex;
        this.fileName = fileName;
        this.major = major;
        this.minor = minor;
        this.constantPool = constantPool;
        this.interfaces = interfaces;
        this.fields = fields;
        this.methods = methods;
        this.attributes = attributes;
        this.source = source;
        // Get source file name if available
        for (final Attribute attribute : attributes) {
            if (attribute instanceof SourceFile) {
                sourceFileName = ((SourceFile) attribute).getSourceFileName();
                break;
            }
        }
        /*
         * According to the specification the following entries must be of type 'ConstantClass' but we check that anyway via the
         * 'ConstPool.getConstant' method.
         */
        className = constantPool.getConstantString(classNameIndex, Const.CONSTANT_Class);
        className = Utility.compactClassName(className, false);
        final int index = className.lastIndexOf('.');
        if (index < 0) {
            packageName = "";
        } else {
            packageName = className.substring(0, index);
        }
        if (superclassNameIndex > 0) {
            // May be zero -> class is java.lang.Object
            superclassName = constantPool.getConstantString(superclassNameIndex, Const.CONSTANT_Class);
            superclassName = Utility.compactClassName(superclassName, false);
        } else {
            superclassName = "java.lang.Object";
        }
        interfaceNames = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            final String str = constantPool.getConstantString(interfaces[i], Const.CONSTANT_Class);
            interfaceNames[i] = Utility.compactClassName(str, false);
        }
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitJavaClass(this);
    }

    /**
     * Return the natural ordering of two JavaClasses. This ordering is based on the class name
     *
     * @since 6.0
     */
    @Override
    public int compareTo(final JavaClass obj) {
        return getClassName().compareTo(obj.getClassName());
    }

    private void computeNestedTypeStatus() {
        if (computedNestedTypeStatus) {
            return;
        }
        for (final Attribute attribute : this.attributes) {
            if (attribute instanceof InnerClasses) {
                ((InnerClasses) attribute).forEach(innerClass ->  {
                    boolean innerClassAttributeRefersToMe = false;
                    String innerClassName = constantPool.getConstantString(innerClass.getInnerClassIndex(), Const.CONSTANT_Class);
                    innerClassName = Utility.compactClassName(innerClassName, false);
                    if (innerClassName.equals(getClassName())) {
                        innerClassAttributeRefersToMe = true;
                    }
                    if (innerClassAttributeRefersToMe) {
                        this.isNested = true;
                        if (innerClass.getInnerNameIndex() == 0) {
                            this.isAnonymous = true;
                        }
                    }
                });
            }
        }
        this.computedNestedTypeStatus = true;
    }

    /**
     * @return deep copy of this class
     */
    public JavaClass copy() {
        try {
            final JavaClass c = (JavaClass) clone();
            c.constantPool = constantPool.copy();
            c.interfaces = interfaces.clone();
            c.interfaceNames = interfaceNames.clone();
            c.fields = new Field[fields.length];
            Arrays.setAll(c.fields, i -> fields[i].copy(c.constantPool));
            c.methods = new Method[methods.length];
            Arrays.setAll(c.methods, i -> methods[i].copy(c.constantPool));
            c.attributes = new Attribute[attributes.length];
            Arrays.setAll(c.attributes, i -> attributes[i].copy(c.constantPool));
            return c;
        } catch (final CloneNotSupportedException e) {
            return null;
        }
    }

    /**
     * Dump Java class to output stream in binary format.
     *
     * @param file Output stream
     * @throws IOException if an I/O error occurs.
     */
    public void dump(final DataOutputStream file) throws IOException {
        file.writeInt(Const.JVM_CLASSFILE_MAGIC);
        file.writeShort(minor);
        file.writeShort(major);
        constantPool.dump(file);
        file.writeShort(super.getAccessFlags());
        file.writeShort(classNameIndex);
        file.writeShort(superclassNameIndex);
        file.writeShort(interfaces.length);
        for (final int interface1 : interfaces) {
            file.writeShort(interface1);
        }
        file.writeShort(fields.length);
        for (final Field field : fields) {
            field.dump(file);
        }
        file.writeShort(methods.length);
        for (final Method method : methods) {
            method.dump(file);
        }
        if (attributes != null) {
            file.writeShort(attributes.length);
            for (final Attribute attribute : attributes) {
                attribute.dump(file);
            }
        } else {
            file.writeShort(0);
        }
        file.flush();
    }

    /**
     * Dump class to a file.
     *
     * @param file Output file
     * @throws IOException if an I/O error occurs.
     */
    public void dump(final File file) throws IOException {
        final String parent = file.getParent();
        if (parent != null) {
            final File dir = new File(parent);
            if (!dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Could not create the directory " + dir);
            }
        }
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file))) {
            dump(dos);
        }
    }

    /**
     * Dump Java class to output stream in binary format.
     *
     * @param file Output stream
     * @throws IOException if an I/O error occurs.
     */
    public void dump(final OutputStream file) throws IOException {
        dump(new DataOutputStream(file));
    }

    /**
     * Dump class to a file named fileName.
     *
     * @param fileName Output file name
     * @throws IOException if an I/O error occurs.
     */
    public void dump(final String fileName) throws IOException {
        dump(new File(fileName));
    }

    /**
     * Return value as defined by given BCELComparator strategy. By default two JavaClass objects are said to be equal when
     * their class names are equal.
     *
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(final Object obj) {
        return bcelComparator.equals(this, obj);
    }

    /**
     * Get all interfaces implemented by this JavaClass (transitively).
     *
     * @throws ClassNotFoundException if any of the class's superclasses or interfaces can't be found.
     */
    public JavaClass[] getAllInterfaces() throws ClassNotFoundException {
        final ClassQueue queue = new ClassQueue();
        final Set<JavaClass> allInterfaces = new TreeSet<>();
        queue.enqueue(this);
        while (!queue.empty()) {
            final JavaClass clazz = queue.dequeue();
            final JavaClass souper = clazz.getSuperClass();
            final JavaClass[] interfaces = clazz.getInterfaces();
            if (clazz.isInterface()) {
                allInterfaces.add(clazz);
            } else if (souper != null) {
                queue.enqueue(souper);
            }
            for (final JavaClass iface : interfaces) {
                queue.enqueue(iface);
            }
        }
        return allInterfaces.toArray(JavaClass.EMPTY_ARRAY);
    }

    /**
     * @return Annotations on the class
     * @since 6.0
     */
    public AnnotationEntry[] getAnnotationEntries() {
        if (annotations == null) {
            annotations = AnnotationEntry.createAnnotationEntries(getAttributes());
        }

        return annotations;
    }

    /**
     * @return Attributes of the class.
     */
    public Attribute[] getAttributes() {
        return attributes;
    }

    /**
     * @return class in binary format
     */
    public byte[] getBytes() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dump(dos);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    /**
     * @return Class name.
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return Class name index.
     */
    public int getClassNameIndex() {
        return classNameIndex;
    }

    /**
     * @return Constant pool.
     */
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * @return Fields, i.e., variables of the class. Like the JVM spec mandates for the classfile format, these fields are
     *         those specific to this class, and not those of the superclass or superinterfaces.
     */
    public Field[] getFields() {
        return fields;
    }

    /**
     * @return File name of class, aka SourceFile attribute value
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @return Indices in constant pool of implemented interfaces.
     */
    public int[] getInterfaceIndices() {
        return interfaces;
    }

    /**
     * @return Names of implemented interfaces.
     */
    public String[] getInterfaceNames() {
        return interfaceNames;
    }

    /**
     * Get interfaces directly implemented by this JavaClass.
     *
     * @throws ClassNotFoundException if any of the class's interfaces can't be found.
     */
    public JavaClass[] getInterfaces() throws ClassNotFoundException {
        final String[] interfaces = getInterfaceNames();
        final JavaClass[] classes = new JavaClass[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            classes[i] = repository.loadClass(interfaces[i]);
        }
        return classes;
    }

    /**
     * @return Major number of class file version.
     */
    public int getMajor() {
        return major;
    }

    /**
     * @return A {@link Method} corresponding to java.lang.reflect.Method if any
     */
    public Method getMethod(final java.lang.reflect.Method m) {
        for (final Method method : methods) {
            if (m.getName().equals(method.getName()) && m.getModifiers() == method.getModifiers() && Type.getSignature(m).equals(method.getSignature())) {
                return method;
            }
        }
        return null;
    }

    /**
     * @return Methods of the class.
     */
    public Method[] getMethods() {
        return methods;
    }

    /**
     * @return Minor number of class file version.
     */
    public int getMinor() {
        return minor;
    }

    /**
     * @return Package name.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Gets the ClassRepository which holds its definition. By default this is the same as
     * SyntheticRepository.getInstance();
     */
    public com.sun.org.apache.bcel.internal.util.Repository getRepository() {
        return repository;
    }

    /**
     * @return returns either HEAP (generated), FILE, or ZIP
     */
    public final byte getSource() {
        return source;
    }

    /**
     * @return file name where this class was read from
     */
    public String getSourceFileName() {
        return sourceFileName;
    }

    /**
     * Gets the source file path including the package path.
     *
     * @return path to original source file of parsed class, relative to original source directory.
     * @since 6.7.0
     */
    public String getSourceFilePath() {
        final StringBuilder outFileName = new StringBuilder();
        if (!packageName.isEmpty()) {
            outFileName.append(Utility.packageToPath(packageName));
            outFileName.append('/');
        }
        outFileName.append(sourceFileName);
        return outFileName.toString();
    }

    /**
     * @return the superclass for this JavaClass object, or null if this is java.lang.Object
     * @throws ClassNotFoundException if the superclass can't be found
     */
    public JavaClass getSuperClass() throws ClassNotFoundException {
        if ("java.lang.Object".equals(getClassName())) {
            return null;
        }
        return repository.loadClass(getSuperclassName());
    }

    /**
     * @return list of super classes of this class in ascending order, i.e., java.lang.Object is always the last element
     * @throws ClassNotFoundException if any of the superclasses can't be found
     */
    public JavaClass[] getSuperClasses() throws ClassNotFoundException {
        JavaClass clazz = this;
        final List<JavaClass> allSuperClasses = new ArrayList<>();
        for (clazz = clazz.getSuperClass(); clazz != null; clazz = clazz.getSuperClass()) {
            allSuperClasses.add(clazz);
        }
        return allSuperClasses.toArray(JavaClass.EMPTY_ARRAY);
    }

    /**
     * returns the super class name of this class. In the case that this class is java.lang.Object, it will return itself
     * (java.lang.Object). This is probably incorrect but isn't fixed at this time to not break existing clients.
     *
     * @return Superclass name.
     */
    public String getSuperclassName() {
        return superclassName;
    }

    /**
     * @return Class name index.
     */
    public int getSuperclassNameIndex() {
        return superclassNameIndex;
    }

    /**
     * Return value as defined by given BCELComparator strategy. By default return the hashcode of the class name.
     *
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return bcelComparator.hashCode(this);
    }

    /**
     * @return true, if this class is an implementation of interface inter
     * @throws ClassNotFoundException if superclasses or superinterfaces of this class can't be found
     */
    public boolean implementationOf(final JavaClass inter) throws ClassNotFoundException {
        if (!inter.isInterface()) {
            throw new IllegalArgumentException(inter.getClassName() + " is no interface");
        }
        if (this.equals(inter)) {
            return true;
        }
        final JavaClass[] superInterfaces = getAllInterfaces();
        for (final JavaClass superInterface : superInterfaces) {
            if (superInterface.equals(inter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Equivalent to runtime "instanceof" operator.
     *
     * @return true if this JavaClass is derived from the super class
     * @throws ClassNotFoundException if superclasses or superinterfaces of this object can't be found
     */
    public final boolean instanceOf(final JavaClass superclass) throws ClassNotFoundException {
        if (this.equals(superclass)) {
            return true;
        }
        for (final JavaClass clazz : getSuperClasses()) {
            if (clazz.equals(superclass)) {
                return true;
            }
        }
        if (superclass.isInterface()) {
            return implementationOf(superclass);
        }
        return false;
    }

    /**
     * @since 6.0
     */
    public final boolean isAnonymous() {
        computeNestedTypeStatus();
        return this.isAnonymous;
    }

    public final boolean isClass() {
        return (super.getAccessFlags() & Const.ACC_INTERFACE) == 0;
    }

    /**
     * @since 6.0
     */
    public final boolean isNested() {
        computeNestedTypeStatus();
        return this.isNested;
    }

    public final boolean isSuper() {
        return (super.getAccessFlags() & Const.ACC_SUPER) != 0;
    }

    /**
     * @param attributes .
     */
    public void setAttributes(final Attribute[] attributes) {
        this.attributes = attributes;
    }

    /**
     * @param className .
     */
    public void setClassName(final String className) {
        this.className = className;
    }

    /**
     * @param classNameIndex .
     */
    public void setClassNameIndex(final int classNameIndex) {
        this.classNameIndex = classNameIndex;
    }

    /**
     * @param constantPool .
     */
    public void setConstantPool(final ConstantPool constantPool) {
        this.constantPool = constantPool;
    }

    /**
     * @param fields .
     */
    public void setFields(final Field[] fields) {
        this.fields = fields;
    }

    /**
     * Set File name of class, aka SourceFile attribute value
     */
    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    /**
     * @param interfaceNames .
     */
    public void setInterfaceNames(final String[] interfaceNames) {
        this.interfaceNames = interfaceNames;
    }

    /**
     * @param interfaces .
     */
    public void setInterfaces(final int[] interfaces) {
        this.interfaces = interfaces;
    }

    /**
     * @param major .
     */
    public void setMajor(final int major) {
        this.major = major;
    }

    /**
     * @param methods .
     */
    public void setMethods(final Method[] methods) {
        this.methods = methods;
    }

    /**
     * @param minor .
     */
    public void setMinor(final int minor) {
        this.minor = minor;
    }

    /**
     * Sets the ClassRepository which loaded the JavaClass. Should be called immediately after parsing is done.
     */
    public void setRepository(final com.sun.org.apache.bcel.internal.util.Repository repository) { // TODO make protected?
        this.repository = repository;
    }

    /**
     * Set absolute path to file this class was read from.
     */
    public void setSourceFileName(final String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    /**
     * @param superclassName .
     */
    public void setSuperclassName(final String superclassName) {
        this.superclassName = superclassName;
    }

    /**
     * @param superclassNameIndex .
     */
    public void setSuperclassNameIndex(final int superclassNameIndex) {
        this.superclassNameIndex = superclassNameIndex;
    }

    /**
     * @return String representing class contents.
     */
    @Override
    public String toString() {
        String access = Utility.accessToString(super.getAccessFlags(), true);
        access = access.isEmpty() ? "" : access + " ";
        final StringBuilder buf = new StringBuilder(128);
        buf.append(access).append(Utility.classOrInterface(super.getAccessFlags())).append(" ").append(className).append(" extends ")
            .append(Utility.compactClassName(superclassName, false)).append('\n');
        final int size = interfaces.length;
        if (size > 0) {
            buf.append("implements\t\t");
            for (int i = 0; i < size; i++) {
                buf.append(interfaceNames[i]);
                if (i < size - 1) {
                    buf.append(", ");
                }
            }
            buf.append('\n');
        }
        buf.append("file name\t\t").append(fileName).append('\n');
        buf.append("compiled from\t\t").append(sourceFileName).append('\n');
        buf.append("compiler version\t").append(major).append(".").append(minor).append('\n');
        buf.append("access flags\t\t").append(super.getAccessFlags()).append('\n');
        buf.append("constant pool\t\t").append(constantPool.getLength()).append(" entries\n");
        buf.append("ACC_SUPER flag\t\t").append(isSuper()).append("\n");
        if (attributes.length > 0) {
            buf.append("\nAttribute(s):\n");
            for (final Attribute attribute : attributes) {
                buf.append(indent(attribute));
            }
        }
        final AnnotationEntry[] annotations = getAnnotationEntries();
        if (annotations != null && annotations.length > 0) {
            buf.append("\nAnnotation(s):\n");
            for (final AnnotationEntry annotation : annotations) {
                buf.append(indent(annotation));
            }
        }
        if (fields.length > 0) {
            buf.append("\n").append(fields.length).append(" fields:\n");
            for (final Field field : fields) {
                buf.append("\t").append(field).append('\n');
            }
        }
        if (methods.length > 0) {
            buf.append("\n").append(methods.length).append(" methods:\n");
            for (final Method method : methods) {
                buf.append("\t").append(method).append('\n');
            }
        }
        return buf.toString();
    }
}
