/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.org.apache.bcel.internal.Const;

/**
 * Super class for all objects that have modifiers like private, final, ... I.e.
 * classes, fields, and methods.
 *
 * @LastModified: Sept 2025
 */
public abstract class AccessFlags {

    /**
     * Access flags.
     *
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter.
     */
    @java.lang.Deprecated
    protected int access_flags; // TODO not used externally at present

    /**
     * Constructs a new instance.
     */
    public AccessFlags() {
    }

    /**
     * Constructs a new instance.
     *
     * @param accessFlags initial access flags.
     */
    public AccessFlags(final int accessFlags) {
        access_flags = accessFlags;
    }

    /**
     * Gets access flags.
     *
     * @return Access flags of the object aka. "modifiers".
     */
    public final int getAccessFlags() {
        return access_flags;
    }

    /**
     * Gets access flags.
     *
     * @return Access flags of the object also known as modifiers.
     */
    public final int getModifiers() {
        return access_flags;
    }

    /**
     * Tests whether the abstract bit is on.
     *
     * @return whether the abstract bit is on.
     */
    public final boolean isAbstract() {
        return test(Const.ACC_ABSTRACT);
    }

    /**
     * Sets the abstract bit.
     *
     * @param flag The new value.
     */
    public final void isAbstract(final boolean flag) {
        setFlag(Const.ACC_ABSTRACT, flag);
    }

    /**
     * Tests whether the annotation bit is on.
     *
     * @return whether the annotation bit is on.
     */
    public final boolean isAnnotation() {
        return test(Const.ACC_ANNOTATION);
    }

    /**
     * Sets the annotation bit.
     *
     * @param flag The new value.
     */
    public final void isAnnotation(final boolean flag) {
        setFlag(Const.ACC_ANNOTATION, flag);
    }
    /**
     * Tests whether the enum bit is on.
     *
     * @return whether the enum bit is on.
     */
    public final boolean isEnum() {
        return test(Const.ACC_ENUM);
    }

    /**
     * Sets the enum bit.
     *
     * @param flag The new value.
     */
    public final void isEnum(final boolean flag) {
        setFlag(Const.ACC_ENUM, flag);
    }

    /**
     * Tests whether the final bit is on.
     *
     * @return whether the final bit is on.
     */
    public final boolean isFinal() {
        return test(Const.ACC_FINAL);
    }

    /**
     * Sets the final bit.
     *
     * @param flag The new value.
     */
    public final void isFinal(final boolean flag) {
        setFlag(Const.ACC_FINAL, flag);
    }

    /**
     * Tests whether the interface bit is on.
     *
     * @return whether the interface bit is on.
     */
    public final boolean isInterface() {
        return test(Const.ACC_INTERFACE);
    }

    /**
     * Sets the interface bit.
     *
     * @param flag The new value.
     */
    public final void isInterface(final boolean flag) {
        setFlag(Const.ACC_INTERFACE, flag);
    }

    /**
     * Tests whether the native bit is on.
     *
     * @return whether the native bit is on.
     */
    public final boolean isNative() {
        return test(Const.ACC_NATIVE);
    }

    /**
     * Sets the native bit.
     *
     * @param flag The new value.
     */
    public final void isNative(final boolean flag) {
        setFlag(Const.ACC_NATIVE, flag);
    }

    /**
     * Tests whether the private bit is on.
     *
     * @return whether the private bit is on.
     */
    public final boolean isPrivate() {
        return test(Const.ACC_PRIVATE);
    }

    /**
     * Sets the private bit.
     *
     * @param flag The new value.
     */
    public final void isPrivate(final boolean flag) {
        setFlag(Const.ACC_PRIVATE, flag);
    }

    /**
     * Tests whether the protected bit is on.
     *
     * @return whether the protected bit is on.
     */
    public final boolean isProtected() {
        return test(Const.ACC_PROTECTED);
    }

    /**
     * Sets the protected bit.
     *
     * @param flag The new value.
     */
    public final void isProtected(final boolean flag) {
        setFlag(Const.ACC_PROTECTED, flag);
    }

    /**
     * Tests whether the public bit is on.
     *
     * @return whether the public bit is on.
     */
    public final boolean isPublic() {
        return test(Const.ACC_PUBLIC);
    }

    /**
     * Sets the public bit.
     *
     * @param flag The new value.
     */
    public final void isPublic(final boolean flag) {
        setFlag(Const.ACC_PUBLIC, flag);
    }

    /**
     * Tests whether the static bit is on.
     *
     * @return whether the static bit is on.
     */
    public final boolean isStatic() {
        return test(Const.ACC_STATIC);
    }

    /**
     * Sets the static bit.
     *
     * @param flag The new value.
     */
    public final void isStatic(final boolean flag) {
        setFlag(Const.ACC_STATIC, flag);
    }

    /**
     * Tests whether the strict bit is on.
     *
     * @return whether the strict bit is on.
     */
    public final boolean isStrictfp() {
        return test(Const.ACC_STRICT);
    }

    /**
     * Sets the strict bit.
     *
     * @param flag The new value.
     */
    public final void isStrictfp(final boolean flag) {
        setFlag(Const.ACC_STRICT, flag);
    }

    /**
     * Tests whether the synchronized bit is on.
     *
     * @return whether the synchronized bit is on.
     */
    public final boolean isSynchronized() {
        return test(Const.ACC_SYNCHRONIZED);
    }

    /**
     * Sets the synchronized bit.
     *
     * @param flag The new value.
     */
    public final void isSynchronized(final boolean flag) {
        setFlag(Const.ACC_SYNCHRONIZED, flag);
    }

    /**
     * Tests whether the synthetic bit is on.
     *
     * @return whether the synthetic bit is on.
     */
    public final boolean isSynthetic() {
        return test(Const.ACC_SYNTHETIC);
    }

    /**
     * Sets the synthetic bit.
     *
     * @param flag The new value.
     */
    public final void isSynthetic(final boolean flag) {
        setFlag(Const.ACC_SYNTHETIC, flag);
    }

    /**
     * Tests whether the transient bit is on.
     *
     * @return whether the varargs bit is on.
     */
    public final boolean isTransient() {
        return test(Const.ACC_TRANSIENT);
    }

    /**
     * Sets the varargs bit.
     *
     * @param flag The new value.
     */
    public final void isTransient(final boolean flag) {
        setFlag(Const.ACC_TRANSIENT, flag);
    }

    /**
     * Tests whether the varargs bit is on.
     *
     * @return whether the varargs bit is on.
     */
    public final boolean isVarArgs() {
        return test(Const.ACC_VARARGS);
    }

    /**
     * Sets the varargs bit.
     *
     * @param flag The new value.
     */
    public final void isVarArgs(final boolean flag) {
        setFlag(Const.ACC_VARARGS, flag);
    }

    /**
     * Tests whether the volatile bit is on.
     *
     * @return whether the volatile bit is on.
     */
    public final boolean isVolatile() {
        return test(Const.ACC_VOLATILE);
    }

    /**
     * Sets the volatile bit.
     *
     * @param flag The new value.
     */
    public final void isVolatile(final boolean flag) {
        setFlag(Const.ACC_VOLATILE, flag);
    }

    /**
     * Sets access flags also known as modifiers.
     *
     * @param accessFlags Access flags of the object.
     */
    public final void setAccessFlags(final int accessFlags) {
        this.access_flags = accessFlags;
    }

    private void setFlag(final int flag, final boolean set) {
        if ((access_flags & flag) != 0) { // Flag is set already
            if (!set) {
                access_flags ^= flag;
            }
        } else if (set) {
            access_flags |= flag;
        }
    }

    /**
     * Sets access flags aka "modifiers".
     *
     * @param accessFlags Access flags of the object.
     */
    public final void setModifiers(final int accessFlags) {
        setAccessFlags(accessFlags);
    }

    /**
     * Tests whether the bit is on.
     *
     * @param test the bit to test.
     * @return whether the bit is on.
     */
    private boolean test(final short test) {
        return (access_flags & test) != 0;
    }
}
