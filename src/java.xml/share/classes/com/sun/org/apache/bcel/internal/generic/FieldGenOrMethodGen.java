/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.AccessFlags;
import com.sun.org.apache.bcel.internal.classfile.Attribute;

/**
 * Super class for FieldGen and MethodGen objects, since they have
 * some methods in common!
 *
 * @LastModified: Jun 2020
 */
public abstract class FieldGenOrMethodGen extends AccessFlags implements NamedAndTyped, Cloneable {

    private String name;
    private Type type;
    private ConstantPoolGen cp;

    private final List<Attribute> attribute_vec = new ArrayList<>();

    // @since 6.0
    private final List<AnnotationEntryGen>       annotation_vec= new ArrayList<>();


    protected FieldGenOrMethodGen() {
    }


    /**
     * @since 6.0
     */
    protected FieldGenOrMethodGen(final int access_flags) { // TODO could this be package protected?
        super(access_flags);
    }

    @Override
    public void setType( final Type type ) { // TODO could be package-protected?
        if (type.getType() == Const.T_ADDRESS) {
            throw new IllegalArgumentException("Type can not be " + type);
        }
        this.type = type;
    }


    @Override
    public Type getType() {
        return type;
    }


    /** @return name of method/field.
     */
    @Override
    public String getName() {
        return name;
    }


    @Override
    public void setName( final String name ) { // TODO could be package-protected?
        this.name = name;
    }


    public ConstantPoolGen getConstantPool() {
        return cp;
    }


    public void setConstantPool( final ConstantPoolGen cp ) { // TODO could be package-protected?
        this.cp = cp;
    }


    /**
     * Add an attribute to this method. Currently, the JVM knows about
     * the `Code', `ConstantValue', `Synthetic' and `Exceptions'
     * attributes. Other attributes will be ignored by the JVM but do no
     * harm.
     *
     * @param a attribute to be added
     */
    public void addAttribute( final Attribute a ) {
        attribute_vec.add(a);
    }

    /**
     * @since 6.0
     */
    protected void addAnnotationEntry(final AnnotationEntryGen ag) // TODO could this be package protected?
    {
        annotation_vec.add(ag);
    }


    /**
     * Remove an attribute.
     */
    public void removeAttribute( final Attribute a ) {
        attribute_vec.remove(a);
    }

    /**
     * @since 6.0
     */
    protected void removeAnnotationEntry(final AnnotationEntryGen ag) // TODO could this be package protected?
    {
        annotation_vec.remove(ag);
    }


    /**
     * Remove all attributes.
     */
    public void removeAttributes() {
        attribute_vec.clear();
    }

    /**
     * @since 6.0
     */
    protected void removeAnnotationEntries() // TODO could this be package protected?
    {
        annotation_vec.clear();
    }


    /**
     * @return all attributes of this method.
     */
    public Attribute[] getAttributes() {
        final Attribute[] attributes = new Attribute[attribute_vec.size()];
        attribute_vec.toArray(attributes);
        return attributes;
    }

    public AnnotationEntryGen[] getAnnotationEntries() {
        final AnnotationEntryGen[] annotations = new AnnotationEntryGen[annotation_vec.size()];
          annotation_vec.toArray(annotations);
          return annotations;
      }


    /** @return signature of method/field.
     */
    public abstract String getSignature();


    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new Error("Clone Not Supported"); // never happens
        }
    }
}
