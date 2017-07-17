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
package org.graalvm.compiler.word;

import static org.graalvm.compiler.word.UnsafeAccess.UNSAFE;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedDivNode;
import org.graalvm.compiler.nodes.calc.UnsignedRemNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

public abstract class Word extends WordFactory implements SignedWord, UnsignedWord, Pointer {

    static {
        assert WordFactory.boxFactory == null : "BoxFactory must be initialized only once.";
        WordFactory.boxFactory = new BoxFactoryImpl();
    }

    public static void ensureInitialized() {
        /* Calling this method ensures that the static initializer has been executed. */
    }

    /**
     * Links a method to a canonical operation represented by an {@link Opcode} val.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Operation {

        Class<? extends ValueNode> node() default ValueNode.class;

        boolean rightOperandIsInt() default false;

        Opcode opcode() default Opcode.NODE_CLASS;

        Condition condition() default Condition.EQ;
    }

    /**
     * The canonical {@link Operation} represented by a method in the {@link Word} class.
     */
    public enum Opcode {
        NODE_CLASS,
        COMPARISON,
        IS_NULL,
        IS_NON_NULL,
        NOT,
        READ_POINTER,
        READ_OBJECT,
        READ_BARRIERED,
        READ_HEAP,
        WRITE_POINTER,
        WRITE_OBJECT,
        WRITE_BARRIERED,
        CAS_POINTER,
        INITIALIZE,
        FROM_ADDRESS,
        OBJECT_TO_TRACKED,
        OBJECT_TO_UNTRACKED,
        TO_OBJECT,
        TO_OBJECT_NON_NULL,
        TO_RAW_VALUE,
    }

    public static class BoxFactoryImpl implements BoxFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T extends WordBase> T box(long val) {
            return (T) HostedWord.boxLong(val);
        }
    }

    /*
     * Outside users must use the different signed() and unsigned() methods to ensure proper
     * expansion of 32-bit values on 64-bit systems.
     */
    @SuppressWarnings("unchecked")
    private static <T extends WordBase> T box(long val) {
        return (T) HostedWord.boxLong(val);
    }

    protected abstract long unbox();

    private static Word intParam(int val) {
        return box(val);
    }

    @Override
    @Operation(opcode = Opcode.TO_RAW_VALUE)
    public long rawValue() {
        return unbox();
    }

    /**
     * Convert an {@link Object} to a {@link Pointer}, keeping the reference information. If the
     * returned pointer or any value derived from it is alive across a safepoint, it will be
     * tracked. Depending on the arithmetic on the pointer and the capabilities of the backend to
     * deal with derived references, this may work correctly, or result in a compiler error.
     */
    @Operation(opcode = Opcode.OBJECT_TO_TRACKED)
    public static native Word objectToTrackedPointer(Object val);

    /**
     * Convert an {@link Object} to a {@link Pointer}, dropping the reference information. If the
     * returned pointer or any value derived from it is alive across a safepoint, it will be treated
     * as a simple integer and not tracked by the garbage collector.
     * <p>
     * This is a dangerous operation, the GC could move the object without updating the pointer! Use
     * only in combination with some mechanism to prevent the GC from moving or freeing the object
     * as long as the pointer is in use.
     * <p>
     * If the result value should not be alive across a safepoint, it's better to use
     * {@link #objectToTrackedPointer(Object)} instead.
     */
    @Operation(opcode = Opcode.OBJECT_TO_UNTRACKED)
    public static native Word objectToUntrackedPointer(Object val);

    @Operation(opcode = Opcode.FROM_ADDRESS)
    public static native Word fromAddress(Address address);

    @Override
    @Operation(opcode = Opcode.TO_OBJECT)
    public native Object toObject();

    @Override
    @Operation(opcode = Opcode.TO_OBJECT_NON_NULL)
    public native Object toObjectNonNull();

    @Override
    @Operation(node = AddNode.class)
    public Word add(SignedWord val) {
        return add((Word) val);
    }

    @Override
    @Operation(node = AddNode.class)
    public Word add(UnsignedWord val) {
        return add((Word) val);
    }

    @Override
    @Operation(node = AddNode.class)
    public Word add(int val) {
        return add(intParam(val));
    }

    @Operation(node = AddNode.class)
    public Word add(Word val) {
        return box(unbox() + val.unbox());
    }

    @Override
    @Operation(node = SubNode.class)
    public Word subtract(SignedWord val) {
        return subtract((Word) val);
    }

    @Override
    @Operation(node = SubNode.class)
    public Word subtract(UnsignedWord val) {
        return subtract((Word) val);
    }

    @Override
    @Operation(node = SubNode.class)
    public Word subtract(int val) {
        return subtract(intParam(val));
    }

    @Operation(node = SubNode.class)
    public Word subtract(Word val) {
        return box(unbox() - val.unbox());
    }

    @Override
    @Operation(node = MulNode.class)
    public Word multiply(SignedWord val) {
        return multiply((Word) val);
    }

    @Override
    @Operation(node = MulNode.class)
    public Word multiply(UnsignedWord val) {
        return multiply((Word) val);
    }

    @Override
    @Operation(node = MulNode.class)
    public Word multiply(int val) {
        return multiply(intParam(val));
    }

    @Operation(node = MulNode.class)
    public Word multiply(Word val) {
        return box(unbox() * val.unbox());
    }

    @Override
    @Operation(node = SignedDivNode.class)
    public Word signedDivide(SignedWord val) {
        return signedDivide((Word) val);
    }

    @Override
    @Operation(node = SignedDivNode.class)
    public Word signedDivide(int val) {
        return signedDivide(intParam(val));
    }

    @Operation(node = SignedDivNode.class)
    public Word signedDivide(Word val) {
        return box(unbox() / val.unbox());
    }

    @Override
    @Operation(node = UnsignedDivNode.class)
    public Word unsignedDivide(UnsignedWord val) {
        return unsignedDivide((Word) val);
    }

    @Override
    @Operation(node = UnsignedDivNode.class)
    public Word unsignedDivide(int val) {
        return signedDivide(intParam(val));
    }

    @Operation(node = UnsignedDivNode.class)
    public Word unsignedDivide(Word val) {
        return box(Long.divideUnsigned(unbox(), val.unbox()));
    }

    @Override
    @Operation(node = SignedRemNode.class)
    public Word signedRemainder(SignedWord val) {
        return signedRemainder((Word) val);
    }

    @Override
    @Operation(node = SignedRemNode.class)
    public Word signedRemainder(int val) {
        return signedRemainder(intParam(val));
    }

    @Operation(node = SignedRemNode.class)
    public Word signedRemainder(Word val) {
        return box(unbox() % val.unbox());
    }

    @Override
    @Operation(node = UnsignedRemNode.class)
    public Word unsignedRemainder(UnsignedWord val) {
        return unsignedRemainder((Word) val);
    }

    @Override
    @Operation(node = UnsignedRemNode.class)
    public Word unsignedRemainder(int val) {
        return signedRemainder(intParam(val));
    }

    @Operation(node = UnsignedRemNode.class)
    public Word unsignedRemainder(Word val) {
        return box(Long.remainderUnsigned(unbox(), val.unbox()));
    }

    @Override
    @Operation(node = LeftShiftNode.class, rightOperandIsInt = true)
    public Word shiftLeft(UnsignedWord val) {
        return shiftLeft((Word) val);
    }

    @Override
    @Operation(node = LeftShiftNode.class, rightOperandIsInt = true)
    public Word shiftLeft(int val) {
        return shiftLeft(intParam(val));
    }

    @Operation(node = LeftShiftNode.class, rightOperandIsInt = true)
    public Word shiftLeft(Word val) {
        return box(unbox() << val.unbox());
    }

    @Override
    @Operation(node = RightShiftNode.class, rightOperandIsInt = true)
    public Word signedShiftRight(UnsignedWord val) {
        return signedShiftRight((Word) val);
    }

    @Override
    @Operation(node = RightShiftNode.class, rightOperandIsInt = true)
    public Word signedShiftRight(int val) {
        return signedShiftRight(intParam(val));
    }

    @Operation(node = RightShiftNode.class, rightOperandIsInt = true)
    public Word signedShiftRight(Word val) {
        return box(unbox() >> val.unbox());
    }

    @Override
    @Operation(node = UnsignedRightShiftNode.class, rightOperandIsInt = true)
    public Word unsignedShiftRight(UnsignedWord val) {
        return unsignedShiftRight((Word) val);
    }

    @Override
    @Operation(node = UnsignedRightShiftNode.class, rightOperandIsInt = true)
    public Word unsignedShiftRight(int val) {
        return unsignedShiftRight(intParam(val));
    }

    @Operation(node = UnsignedRightShiftNode.class, rightOperandIsInt = true)
    public Word unsignedShiftRight(Word val) {
        return box(unbox() >>> val.unbox());
    }

    @Override
    @Operation(node = AndNode.class)
    public Word and(SignedWord val) {
        return and((Word) val);
    }

    @Override
    @Operation(node = AndNode.class)
    public Word and(UnsignedWord val) {
        return and((Word) val);
    }

    @Override
    @Operation(node = AndNode.class)
    public Word and(int val) {
        return and(intParam(val));
    }

    @Operation(node = AndNode.class)
    public Word and(Word val) {
        return box(unbox() & val.unbox());
    }

    @Override
    @Operation(node = OrNode.class)
    public Word or(SignedWord val) {
        return or((Word) val);
    }

    @Override
    @Operation(node = OrNode.class)
    public Word or(UnsignedWord val) {
        return or((Word) val);
    }

    @Override
    @Operation(node = OrNode.class)
    public Word or(int val) {
        return or(intParam(val));
    }

    @Operation(node = OrNode.class)
    public Word or(Word val) {
        return box(unbox() | val.unbox());
    }

    @Override
    @Operation(node = XorNode.class)
    public Word xor(SignedWord val) {
        return xor((Word) val);
    }

    @Override
    @Operation(node = XorNode.class)
    public Word xor(UnsignedWord val) {
        return xor((Word) val);
    }

    @Override
    @Operation(node = XorNode.class)
    public Word xor(int val) {
        return xor(intParam(val));
    }

    @Operation(node = XorNode.class)
    public Word xor(Word val) {
        return box(unbox() ^ val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.NOT)
    public Word not() {
        return box(~unbox());
    }

    @Override
    @Operation(opcode = Opcode.IS_NULL)
    public boolean isNull() {
        return equal(WordFactory.zero());
    }

    @Override
    @Operation(opcode = Opcode.IS_NON_NULL)
    public boolean isNonNull() {
        return notEqual(WordFactory.zero());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(ComparableWord val) {
        return equal((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(SignedWord val) {
        return equal((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(UnsignedWord val) {
        return equal((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(int val) {
        return equal(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(Word val) {
        return unbox() == val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(ComparableWord val) {
        return notEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(SignedWord val) {
        return notEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(UnsignedWord val) {
        return notEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(int val) {
        return notEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(Word val) {
        return unbox() != val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LT)
    public boolean lessThan(SignedWord val) {
        return lessThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LT)
    public boolean lessThan(int val) {
        return lessThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LT)
    public boolean lessThan(Word val) {
        return unbox() < val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LE)
    public boolean lessOrEqual(SignedWord val) {
        return lessOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LE)
    public boolean lessOrEqual(int val) {
        return lessOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LE)
    public boolean lessOrEqual(Word val) {
        return unbox() <= val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GT)
    public boolean greaterThan(SignedWord val) {
        return greaterThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GT)
    public boolean greaterThan(int val) {
        return greaterThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GT)
    public boolean greaterThan(Word val) {
        return unbox() > val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GE)
    public boolean greaterOrEqual(SignedWord val) {
        return greaterOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GE)
    public boolean greaterOrEqual(int val) {
        return greaterOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GE)
    public boolean greaterOrEqual(Word val) {
        return unbox() >= val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BT)
    public boolean belowThan(UnsignedWord val) {
        return belowThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BT)
    public boolean belowThan(int val) {
        return belowThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BT)
    public boolean belowThan(Word val) {
        return UnsignedMath.belowThan(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BE)
    public boolean belowOrEqual(UnsignedWord val) {
        return belowOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BE)
    public boolean belowOrEqual(int val) {
        return belowOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BE)
    public boolean belowOrEqual(Word val) {
        return UnsignedMath.belowOrEqual(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AT)
    public boolean aboveThan(UnsignedWord val) {
        return aboveThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AT)
    public boolean aboveThan(int val) {
        return aboveThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AT)
    public boolean aboveThan(Word val) {
        return UnsignedMath.aboveThan(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AE)
    public boolean aboveOrEqual(UnsignedWord val) {
        return aboveOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AE)
    public boolean aboveOrEqual(int val) {
        return aboveOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AE)
    public boolean aboveOrEqual(Word val) {
        return UnsignedMath.aboveOrEqual(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public byte readByte(WordBase offset, LocationIdentity locationIdentity) {
        return UNSAFE.getByte(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public char readChar(WordBase offset, LocationIdentity locationIdentity) {
        return UNSAFE.getChar(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public short readShort(WordBase offset, LocationIdentity locationIdentity) {
        return UNSAFE.getShort(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public int readInt(WordBase offset, LocationIdentity locationIdentity) {
        return UNSAFE.getInt(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public long readLong(WordBase offset, LocationIdentity locationIdentity) {
        return UNSAFE.getLong(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public float readFloat(WordBase offset, LocationIdentity locationIdentity) {
        return UNSAFE.getFloat(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public double readDouble(WordBase offset, LocationIdentity locationIdentity) {
        return UNSAFE.getDouble(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public <T extends WordBase> T readWord(WordBase offset, LocationIdentity locationIdentity) {
        return box(UNSAFE.getAddress(add((Word) offset).unbox()));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public native Object readObject(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public byte readByte(int offset, LocationIdentity locationIdentity) {
        return readByte(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public char readChar(int offset, LocationIdentity locationIdentity) {
        return readChar(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public short readShort(int offset, LocationIdentity locationIdentity) {
        return readShort(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public int readInt(int offset, LocationIdentity locationIdentity) {
        return readInt(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public long readLong(int offset, LocationIdentity locationIdentity) {
        return readLong(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public float readFloat(int offset, LocationIdentity locationIdentity) {
        return readFloat(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public double readDouble(int offset, LocationIdentity locationIdentity) {
        return readDouble(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public <T extends WordBase> T readWord(int offset, LocationIdentity locationIdentity) {
        return readWord(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public Object readObject(int offset, LocationIdentity locationIdentity) {
        return readObject(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity) {
        UNSAFE.putByte(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeChar(WordBase offset, char val, LocationIdentity locationIdentity) {
        UNSAFE.putChar(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeShort(WordBase offset, short val, LocationIdentity locationIdentity) {
        UNSAFE.putShort(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeInt(WordBase offset, int val, LocationIdentity locationIdentity) {
        UNSAFE.putInt(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeLong(WordBase offset, long val, LocationIdentity locationIdentity) {
        UNSAFE.putLong(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity) {
        UNSAFE.putFloat(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity) {
        UNSAFE.putDouble(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity) {
        UNSAFE.putAddress(add((Word) offset).unbox(), ((Word) val).unbox());
    }

    @Override
    @Operation(opcode = Opcode.INITIALIZE)
    public void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity) {
        UNSAFE.putLong(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public native void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeByte(int offset, byte val, LocationIdentity locationIdentity) {
        writeByte(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeChar(int offset, char val, LocationIdentity locationIdentity) {
        writeChar(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeShort(int offset, short val, LocationIdentity locationIdentity) {
        writeShort(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeInt(int offset, int val, LocationIdentity locationIdentity) {
        writeInt(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeLong(int offset, long val, LocationIdentity locationIdentity) {
        writeLong(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeFloat(int offset, float val, LocationIdentity locationIdentity) {
        writeFloat(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeDouble(int offset, double val, LocationIdentity locationIdentity) {
        writeDouble(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeWord(int offset, WordBase val, LocationIdentity locationIdentity) {
        writeWord(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.INITIALIZE)
    public void initializeLong(int offset, long val, LocationIdentity locationIdentity) {
        initializeLong(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeObject(int offset, Object val, LocationIdentity locationIdentity) {
        writeObject(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public byte readByte(WordBase offset) {
        return UNSAFE.getByte(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public char readChar(WordBase offset) {
        return UNSAFE.getChar(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public short readShort(WordBase offset) {
        return UNSAFE.getShort(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public int readInt(WordBase offset) {
        return UNSAFE.getInt(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public long readLong(WordBase offset) {
        return UNSAFE.getLong(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public float readFloat(WordBase offset) {
        return UNSAFE.getFloat(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public double readDouble(WordBase offset) {
        return UNSAFE.getDouble(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public <T extends WordBase> T readWord(WordBase offset) {
        return box(UNSAFE.getAddress(add((Word) offset).unbox()));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public native Object readObject(WordBase offset);

    @Operation(opcode = Opcode.READ_HEAP)
    public native Object readObject(WordBase offset, BarrierType barrierType);

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public byte readByte(int offset) {
        return readByte(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public char readChar(int offset) {
        return readChar(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public short readShort(int offset) {
        return readShort(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public int readInt(int offset) {
        return readInt(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public long readLong(int offset) {
        return readLong(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public float readFloat(int offset) {
        return readFloat(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public double readDouble(int offset) {
        return readDouble(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public <T extends WordBase> T readWord(int offset) {
        return readWord(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ_POINTER)
    public Object readObject(int offset) {
        return readObject(signed(offset));
    }

    @Operation(opcode = Opcode.READ_HEAP)
    public Object readObject(int offset, BarrierType barrierType) {
        return readObject(signed(offset), barrierType);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeByte(WordBase offset, byte val) {
        UNSAFE.putByte(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeChar(WordBase offset, char val) {
        UNSAFE.putChar(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeShort(WordBase offset, short val) {
        UNSAFE.putShort(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeInt(WordBase offset, int val) {
        UNSAFE.putInt(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeLong(WordBase offset, long val) {
        UNSAFE.putLong(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeFloat(WordBase offset, float val) {
        UNSAFE.putFloat(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeDouble(WordBase offset, double val) {
        UNSAFE.putDouble(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public native int compareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public native long compareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public native <T extends WordBase> T compareAndSwapWord(WordBase offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public native Object compareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        return UNSAFE.compareAndSwapInt(this.toObject(), ((Word) offset).unbox(), expectedValue, newValue);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        return UNSAFE.compareAndSwapLong(this.toObject(), ((Word) offset).unbox(), expectedValue, newValue);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapWord(WordBase offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        return UNSAFE.compareAndSwapObject(this.toObject(), ((Word) offset).unbox(), expectedValue, newValue);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeWord(WordBase offset, WordBase val) {
        UNSAFE.putAddress(add((Word) offset).unbox(), ((Word) val).unbox());
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public native void writeObject(WordBase offset, Object val);

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeByte(int offset, byte val) {
        writeByte(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeChar(int offset, char val) {
        writeChar(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeShort(int offset, short val) {
        writeShort(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeInt(int offset, int val) {
        writeInt(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeLong(int offset, long val) {
        writeLong(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeFloat(int offset, float val) {
        writeFloat(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeDouble(int offset, double val) {
        writeDouble(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeWord(int offset, WordBase val) {
        writeWord(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE_POINTER)
    public void writeObject(int offset, Object val) {
        writeObject(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public int compareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        return compareAndSwapInt(signed(offset), expectedValue, newValue, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public long compareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        return compareAndSwapLong(signed(offset), expectedValue, newValue, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public <T extends WordBase> T compareAndSwapWord(int offset, T expectedValue, T newValue, LocationIdentity locationIdentity) {
        return compareAndSwapWord(signed(offset), expectedValue, newValue, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public Object compareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        return compareAndSwapObject(signed(offset), expectedValue, newValue, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        return logicCompareAndSwapInt(signed(offset), expectedValue, newValue, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        return logicCompareAndSwapLong(signed(offset), expectedValue, newValue, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapWord(int offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity) {
        return logicCompareAndSwapWord(signed(offset), expectedValue, newValue, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        return logicCompareAndSwapObject(signed(offset), expectedValue, newValue, locationIdentity);
    }

    /**
     * This is deprecated because of the easy to mistype name collision between {@link #equals} and
     * the other equals routines like {@link #equal(Word)}. In general you should never be
     * statically calling this method for Word types.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public final boolean equals(Object obj) {
        throw GraalError.shouldNotReachHere("equals must not be called on words");
    }

    @Override
    public final int hashCode() {
        throw GraalError.shouldNotReachHere("hashCode must not be called on words");
    }

    @Override
    public String toString() {
        throw GraalError.shouldNotReachHere("toString must not be called on words");
    }
}

final class HostedWord extends Word {

    private static final int SMALL_FROM = -1;
    private static final int SMALL_TO = 100;

    private static final HostedWord[] smallCache = new HostedWord[SMALL_TO - SMALL_FROM + 1];

    static {
        for (int i = SMALL_FROM; i <= SMALL_TO; i++) {
            smallCache[i - SMALL_FROM] = new HostedWord(i);
        }
    }

    private final long rawValue;

    private HostedWord(long rawValue) {
        this.rawValue = rawValue;
    }

    protected static Word boxLong(long val) {
        if (val >= SMALL_FROM && val <= SMALL_TO) {
            return smallCache[(int) val - SMALL_FROM];
        }
        return new HostedWord(val);
    }

    @Override
    protected long unbox() {
        return rawValue;
    }

    @Override
    public String toString() {
        return "Word<" + rawValue + ">";
    }
}
