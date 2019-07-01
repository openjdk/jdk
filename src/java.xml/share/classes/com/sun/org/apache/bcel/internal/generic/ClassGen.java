/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.AccessFlags;
import com.sun.org.apache.bcel.internal.classfile.AnnotationEntry;
import com.sun.org.apache.bcel.internal.classfile.Annotations;
import com.sun.org.apache.bcel.internal.classfile.Attribute;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.Field;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.classfile.RuntimeInvisibleAnnotations;
import com.sun.org.apache.bcel.internal.classfile.RuntimeVisibleAnnotations;
import com.sun.org.apache.bcel.internal.classfile.SourceFile;
import com.sun.org.apache.bcel.internal.util.BCELComparator;

/**
 * Template class for building up a java class. May be initialized with an
 * existing java class (file).
 *
 * @see JavaClass
 * @version $Id$
 * @LastModified: Jun 2019
 */
public class ClassGen extends AccessFlags implements Cloneable {

    /* Corresponds to the fields found in a JavaClass object.
     */
    private String class_name;
    private String super_class_name;
    private final String file_name;
    private int class_name_index = -1;
    private int superclass_name_index = -1;
    private int major = Const.MAJOR;
    private int minor = Const.MINOR;
    private ConstantPoolGen cp; // Template for building up constant pool
    // ArrayLists instead of arrays to gather fields, methods, etc.
    private final List<Field> field_vec = new ArrayList<>();
    private final List<Method> method_vec = new ArrayList<>();
    private final List<Attribute> attribute_vec = new ArrayList<>();
    private final List<String> interface_vec = new ArrayList<>();
    private final List<AnnotationEntryGen> annotation_vec = new ArrayList<>();

    private static BCELComparator _cmp = new BCELComparator() {

        @Override
        public boolean equals( final Object o1, final Object o2 ) {
            final ClassGen THIS = (ClassGen) o1;
            final ClassGen THAT = (ClassGen) o2;
            return Objects.equals(THIS.getClassName(), THAT.getClassName());
        }


        @Override
        public int hashCode( final Object o ) {
            final ClassGen THIS = (ClassGen) o;
            return THIS.getClassName().hashCode();
        }
    };


    /** Convenience constructor to set up some important values initially.
     *
     * @param class_name fully qualified class name
     * @param super_class_name fully qualified superclass name
     * @param file_name source file name
     * @param access_flags access qualifiers
     * @param interfaces implemented interfaces
     * @param cp constant pool to use
     */
    public ClassGen(final String class_name, final String super_class_name, final String file_name, final int access_flags,
            final String[] interfaces, final ConstantPoolGen cp) {
        super(access_flags);
        this.class_name = class_name;
        this.super_class_name = super_class_name;
        this.file_name = file_name;
        this.cp = cp;
        // Put everything needed by default into the constant pool and the vectors
        if (file_name != null) {
            addAttribute(new SourceFile(cp.addUtf8("SourceFile"), 2, cp.addUtf8(file_name), cp
                    .getConstantPool()));
        }
        class_name_index = cp.addClass(class_name);
        superclass_name_index = cp.addClass(super_class_name);
        if (interfaces != null) {
            for (final String interface1 : interfaces) {
                addInterface(interface1);
            }
        }
    }


    /** Convenience constructor to set up some important values initially.
     *
     * @param class_name fully qualified class name
     * @param super_class_name fully qualified superclass name
     * @param file_name source file name
     * @param access_flags access qualifiers
     * @param interfaces implemented interfaces
     */
    public ClassGen(final String class_name, final String super_class_name, final String file_name, final int access_flags,
            final String[] interfaces) {
        this(class_name, super_class_name, file_name, access_flags, interfaces,
                new ConstantPoolGen());
    }


    /**
     * Initialize with existing class.
     * @param clazz JavaClass object (e.g. read from file)
     */
    public ClassGen(final JavaClass clazz) {
        super(clazz.getAccessFlags());
        class_name_index = clazz.getClassNameIndex();
        superclass_name_index = clazz.getSuperclassNameIndex();
        class_name = clazz.getClassName();
        super_class_name = clazz.getSuperclassName();
        file_name = clazz.getSourceFileName();
        cp = new ConstantPoolGen(clazz.getConstantPool());
        major = clazz.getMajor();
        minor = clazz.getMinor();
        final Attribute[] attributes = clazz.getAttributes();
        // J5TODO: Could make unpacking lazy, done on first reference
        final AnnotationEntryGen[] annotations = unpackAnnotations(attributes);
        final Method[] methods = clazz.getMethods();
        final Field[] fields = clazz.getFields();
        final String[] interfaces = clazz.getInterfaceNames();
        for (final String interface1 : interfaces) {
            addInterface(interface1);
        }
        for (final Attribute attribute : attributes) {
            if (!(attribute instanceof Annotations)) {
                addAttribute(attribute);
            }
        }
        for (final AnnotationEntryGen annotation : annotations) {
            addAnnotationEntry(annotation);
        }
        for (final Method method : methods) {
            addMethod(method);
        }
        for (final Field field : fields) {
            addField(field);
        }
    }

    /**
     * Look for attributes representing annotations and unpack them.
     */
    private AnnotationEntryGen[] unpackAnnotations(final Attribute[] attrs)
    {
        final List<AnnotationEntryGen> annotationGenObjs = new ArrayList<>();
        for (final Attribute attr : attrs) {
            if (attr instanceof RuntimeVisibleAnnotations)
            {
                final RuntimeVisibleAnnotations rva = (RuntimeVisibleAnnotations) attr;
                final AnnotationEntry[] annos = rva.getAnnotationEntries();
                for (final AnnotationEntry a : annos) {
                    annotationGenObjs.add(new AnnotationEntryGen(a,
                            getConstantPool(), false));
                }
            }
            else
                if (attr instanceof RuntimeInvisibleAnnotations)
                {
                    final RuntimeInvisibleAnnotations ria = (RuntimeInvisibleAnnotations) attr;
                    final AnnotationEntry[] annos = ria.getAnnotationEntries();
                    for (final AnnotationEntry a : annos) {
                        annotationGenObjs.add(new AnnotationEntryGen(a,
                                getConstantPool(), false));
                    }
                }
        }
        return annotationGenObjs.toArray(new AnnotationEntryGen[annotationGenObjs.size()]);
    }


    /**
     * @return the (finally) built up Java class object.
     */
    public JavaClass getJavaClass() {
        final int[] interfaces = getInterfaces();
        final Field[] fields = getFields();
        final Method[] methods = getMethods();
        Attribute[] attributes = null;
        if (annotation_vec.isEmpty()) {
            attributes = getAttributes();
        } else {
            // TODO: Sometime later, trash any attributes called 'RuntimeVisibleAnnotations' or 'RuntimeInvisibleAnnotations'
            final Attribute[] annAttributes  = AnnotationEntryGen.getAnnotationAttributes(cp, getAnnotationEntries());
            attributes = new Attribute[attribute_vec.size()+annAttributes.length];
            attribute_vec.toArray(attributes);
            System.arraycopy(annAttributes,0,attributes,attribute_vec.size(),annAttributes.length);
        }
        // Must be last since the above calls may still add something to it
        final ConstantPool _cp = this.cp.getFinalConstantPool();
        return new JavaClass(class_name_index, superclass_name_index, file_name, major, minor,
                super.getAccessFlags(), _cp, interfaces, fields, methods, attributes);
    }


    /**
     * Add an interface to this class, i.e., this class has to implement it.
     * @param name interface to implement (fully qualified class name)
     */
    public void addInterface( final String name ) {
        interface_vec.add(name);
    }


    /**
     * Remove an interface from this class.
     * @param name interface to remove (fully qualified name)
     */
    public void removeInterface( final String name ) {
        interface_vec.remove(name);
    }


    /**
     * @return major version number of class file
     */
    public int getMajor() {
        return major;
    }


    /** Set major version number of class file, default value is 45 (JDK 1.1)
     * @param major major version number
     */
    public void setMajor( final int major ) { // TODO could be package-protected - only called by test code
        this.major = major;
    }


    /** Set minor version number of class file, default value is 3 (JDK 1.1)
     * @param minor minor version number
     */
    public void setMinor( final int minor ) {  // TODO could be package-protected - only called by test code
        this.minor = minor;
    }

    /**
     * @return minor version number of class file
     */
    public int getMinor() {
        return minor;
    }


    /**
     * Add an attribute to this class.
     * @param a attribute to add
     */
    public void addAttribute( final Attribute a ) {
        attribute_vec.add(a);
    }

    public void addAnnotationEntry(final AnnotationEntryGen a) {
        annotation_vec.add(a);
    }


    /**
     * Add a method to this class.
     * @param m method to add
     */
    public void addMethod( final Method m ) {
        method_vec.add(m);
    }


    /**
     * Convenience method.
     *
     * Add an empty constructor to this class that does nothing but calling super().
     * @param access_flags rights for constructor
     */
    public void addEmptyConstructor( final int access_flags ) {
        final InstructionList il = new InstructionList();
        il.append(InstructionConst.THIS); // Push `this'
        il.append(new INVOKESPECIAL(cp.addMethodref(super_class_name, "<init>", "()V")));
        il.append(InstructionConst.RETURN);
        final MethodGen mg = new MethodGen(access_flags, Type.VOID, Type.NO_ARGS, null, "<init>",
                class_name, il, cp);
        mg.setMaxStack(1);
        addMethod(mg.getMethod());
    }


    /**
     * Add a field to this class.
     * @param f field to add
     */
    public void addField( final Field f ) {
        field_vec.add(f);
    }


    public boolean containsField( final Field f ) {
        return field_vec.contains(f);
    }


    /** @return field object with given name, or null
     */
    public Field containsField( final String name ) {
        for (final Field f : field_vec) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }


    /** @return method object with given name and signature, or null
     */
    public Method containsMethod( final String name, final String signature ) {
        for (final Method m : method_vec) {
            if (m.getName().equals(name) && m.getSignature().equals(signature)) {
                return m;
            }
        }
        return null;
    }


    /**
     * Remove an attribute from this class.
     * @param a attribute to remove
     */
    public void removeAttribute( final Attribute a ) {
        attribute_vec.remove(a);
    }


    /**
     * Remove a method from this class.
     * @param m method to remove
     */
    public void removeMethod( final Method m ) {
        method_vec.remove(m);
    }


    /** Replace given method with new one. If the old one does not exist
     * add the new_ method to the class anyway.
     */
    public void replaceMethod( final Method old, final Method new_ ) {
        if (new_ == null) {
            throw new ClassGenException("Replacement method must not be null");
        }
        final int i = method_vec.indexOf(old);
        if (i < 0) {
            method_vec.add(new_);
        } else {
            method_vec.set(i, new_);
        }
    }


    /** Replace given field with new one. If the old one does not exist
     * add the new_ field to the class anyway.
     */
    public void replaceField( final Field old, final Field new_ ) {
        if (new_ == null) {
            throw new ClassGenException("Replacement method must not be null");
        }
        final int i = field_vec.indexOf(old);
        if (i < 0) {
            field_vec.add(new_);
        } else {
            field_vec.set(i, new_);
        }
    }


    /**
     * Remove a field to this class.
     * @param f field to remove
     */
    public void removeField( final Field f ) {
        field_vec.remove(f);
    }


    public String getClassName() {
        return class_name;
    }


    public String getSuperclassName() {
        return super_class_name;
    }


    public String getFileName() {
        return file_name;
    }


    public void setClassName( final String name ) {
        class_name = name.replace('/', '.');
        class_name_index = cp.addClass(name);
    }


    public void setSuperclassName( final String name ) {
        super_class_name = name.replace('/', '.');
        superclass_name_index = cp.addClass(name);
    }


    public Method[] getMethods() {
        return method_vec.toArray(new Method[method_vec.size()]);
    }


    public void setMethods( final Method[] methods ) {
        method_vec.clear();
        for (final Method method : methods) {
            addMethod(method);
        }
    }


    public void setMethodAt( final Method method, final int pos ) {
        method_vec.set(pos, method);
    }


    public Method getMethodAt( final int pos ) {
        return method_vec.get(pos);
    }


    public String[] getInterfaceNames() {
        final int size = interface_vec.size();
        final String[] interfaces = new String[size];
        interface_vec.toArray(interfaces);
        return interfaces;
    }


    public int[] getInterfaces() {
        final int size = interface_vec.size();
        final int[] interfaces = new int[size];
        for (int i = 0; i < size; i++) {
            interfaces[i] = cp.addClass(interface_vec.get(i));
        }
        return interfaces;
    }


    public Field[] getFields() {
        return field_vec.toArray(new Field[field_vec.size()]);
    }


    public Attribute[] getAttributes() {
        return attribute_vec.toArray(new Attribute[attribute_vec.size()]);
    }

    //  J5TODO: Should we make calling unpackAnnotations() lazy and put it in here?
    public AnnotationEntryGen[] getAnnotationEntries() {
        return annotation_vec.toArray(new AnnotationEntryGen[annotation_vec.size()]);
    }


    public ConstantPoolGen getConstantPool() {
        return cp;
    }


    public void setConstantPool( final ConstantPoolGen constant_pool ) {
        cp = constant_pool;
    }


    public void setClassNameIndex( final int class_name_index ) {
        this.class_name_index = class_name_index;
        class_name = cp.getConstantPool().getConstantString(class_name_index,
                Const.CONSTANT_Class).replace('/', '.');
    }


    public void setSuperclassNameIndex( final int superclass_name_index ) {
        this.superclass_name_index = superclass_name_index;
        super_class_name = cp.getConstantPool().getConstantString(superclass_name_index,
                Const.CONSTANT_Class).replace('/', '.');
    }


    public int getSuperclassNameIndex() {
        return superclass_name_index;
    }


    public int getClassNameIndex() {
        return class_name_index;
    }

    private List<ClassObserver> observers;


    /** Add observer for this object.
     */
    public void addObserver( final ClassObserver o ) {
        if (observers == null) {
            observers = new ArrayList<>();
        }
        observers.add(o);
    }


    /** Remove observer for this object.
     */
    public void removeObserver( final ClassObserver o ) {
        if (observers != null) {
            observers.remove(o);
        }
    }


    /** Call notify() method on all observers. This method is not called
     * automatically whenever the state has changed, but has to be
     * called by the user after he has finished editing the object.
     */
    public void update() {
        if (observers != null) {
            for (final ClassObserver observer : observers) {
                observer.notify(this);
            }
        }
    }


    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new Error("Clone Not Supported"); // never happens
        }
    }


    /**
     * @return Comparison strategy object
     */
    public static BCELComparator getComparator() {
        return _cmp;
    }


    /**
     * @param comparator Comparison strategy object
     */
    public static void setComparator( final BCELComparator comparator ) {
        _cmp = comparator;
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default two ClassGen objects are said to be equal when
     * their class names are equal.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals( final Object obj ) {
        return _cmp.equals(this, obj);
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default return the hashcode of the class name.
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return _cmp.hashCode(this);
    }
}
