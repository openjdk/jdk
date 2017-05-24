/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.api.word;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;

public abstract class WordFactory {

    /**
     * Links a method to a canonical operation represented by an {@link FactoryOpcode} val.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface FactoryOperation {
        FactoryOpcode opcode();
    }

    /**
     * The canonical {@link FactoryOperation} represented by a method in a word type.
     */
    protected enum FactoryOpcode {
        ZERO,
        FROM_UNSIGNED,
        FROM_SIGNED,
    }

    protected interface BoxFactory {
        <T extends WordBase> T box(long val);
    }

    protected static final BoxFactory boxFactory;

    static {
        try {
            /*
             * We know the implementation class, but cannot reference it statically because we need
             * to break the dependency between the interface and the implementation.
             */
            boxFactory = (BoxFactory) Class.forName("org.graalvm.compiler.word.Word$BoxFactoryImpl").getConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new ExceptionInInitializerError("Could not find and initialize the word type factory. The Graal compiler needs to be on the class path to use the word type.");
        }
    }

    /**
     * We allow subclassing, because only subclasses can access the protected inner classes that we
     * use to mark the operations.
     */
    protected WordFactory() {
    }

    /**
     * The constant 0, i.e., the word with no bits set. There is no difference between a signed and
     * unsigned zero.
     *
     * @return the constant 0.
     */
    @FactoryOperation(opcode = FactoryOpcode.ZERO)
    public static <T extends WordBase> T zero() {
        return boxFactory.box(0L);
    }

    /**
     * Unsafe conversion from a Java long value to a Word. The parameter is treated as an unsigned
     * 64-bit value (in contrast to the semantics of a Java long).
     *
     * @param val a 64 bit unsigned value
     * @return the value cast to Word
     */
    @FactoryOperation(opcode = FactoryOpcode.FROM_UNSIGNED)
    public static <T extends Unsigned> T unsigned(long val) {
        return boxFactory.box(val);
    }

    /**
     * Unsafe conversion from a Java long value to a {@link PointerBase pointer}. The parameter is
     * treated as an unsigned 64-bit value (in contrast to the semantics of a Java long).
     *
     * @param val a 64 bit unsigned value
     * @return the value cast to PointerBase
     */
    @FactoryOperation(opcode = FactoryOpcode.FROM_UNSIGNED)
    public static <T extends PointerBase> T pointer(long val) {
        return boxFactory.box(val);
    }

    /**
     * Unsafe conversion from a Java int value to a Word. The parameter is treated as an unsigned
     * 32-bit value (in contrast to the semantics of a Java int).
     *
     * @param val a 32 bit unsigned value
     * @return the value cast to Word
     */
    @FactoryOperation(opcode = FactoryOpcode.FROM_UNSIGNED)
    public static <T extends Unsigned> T unsigned(int val) {
        return boxFactory.box(val & 0xffffffffL);
    }

    /**
     * Unsafe conversion from a Java long value to a Word. The parameter is treated as a signed
     * 64-bit value (unchanged semantics of a Java long).
     *
     * @param val a 64 bit signed value
     * @return the value cast to Word
     */
    @FactoryOperation(opcode = FactoryOpcode.FROM_SIGNED)
    public static <T extends Signed> T signed(long val) {
        return boxFactory.box(val);
    }

    /**
     * Unsafe conversion from a Java int value to a Word. The parameter is treated as a signed
     * 32-bit value (unchanged semantics of a Java int).
     *
     * @param val a 32 bit signed value
     * @return the value cast to Word
     */
    @FactoryOperation(opcode = FactoryOpcode.FROM_SIGNED)
    public static <T extends Signed> T signed(int val) {
        return boxFactory.box(val);
    }
}
