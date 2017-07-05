/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.dyn.anon;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static sun.dyn.anon.ConstantPoolVisitor.*;

/** A constant pool parser.
 */
public class ConstantPoolParser {
    final byte[] classFile;
    final byte[] tags;
    final char[] firstHeader;  // maghi, maglo, minor, major, cplen

    // these are filled in on first parse:
    int endOffset;
    char[] secondHeader;       // flags, this_class, super_class, intlen

    // used to decode UTF8 array
    private char[] charArray = new char[80];

    /** Creates a constant pool parser.
     * @param classFile an array of bytes containing a class.
     * @throws InvalidConstantPoolFormatException if the header of the class has errors.
     */
    public ConstantPoolParser(byte[] classFile) throws InvalidConstantPoolFormatException {
        this.classFile = classFile;
        this.firstHeader = parseHeader(classFile);
        this.tags = new byte[firstHeader[4]];
    }

    /** Create a constant pool parser by loading the bytecodes of the
     *  class taken as argument.
     *
     * @param templateClass the class to parse.
     *
     * @throws IOException raised if an I/O occurs when loading
     *  the bytecode of the template class.
     * @throws InvalidConstantPoolFormatException if the header of the class has errors.
     *
     * @see #ConstantPoolParser(byte[])
     * @see AnonymousClassLoader#readClassFile(Class)
     */
    public ConstantPoolParser(Class<?> templateClass) throws IOException, InvalidConstantPoolFormatException {
        this(AnonymousClassLoader.readClassFile(templateClass));
    }

    /** Creates an empty patch to patch the class file
     *  used by the current parser.
     * @return a new class patch.
     */
    public ConstantPoolPatch createPatch() {
        return new ConstantPoolPatch(this);
    }

    /** Report the tag of the indicated CP entry.
     * @param index
     * @return one of {@link ConstantPoolVisitor#CONSTANT_Utf8}, etc.
     */
    public byte getTag(int index) {
        getEndOffset();  // trigger an exception if we haven't parsed yet
        return tags[index];
    }

    /** Report the length of the constant pool. */
    public int getLength() {
        return firstHeader[4];
    }

    /** Report the offset, within the class file, of the start of the constant pool. */
    public int getStartOffset() {
        return firstHeader.length * 2;
    }

    /** Report the offset, within the class file, of the end of the constant pool. */
    public int getEndOffset() {
        if (endOffset == 0)
            throw new IllegalStateException("class file has not yet been parsed");
        return endOffset;
    }

    /** Report the CP index of this class's own name. */
    public int getThisClassIndex() {
        getEndOffset();   // provoke exception if not yet parsed
        return secondHeader[1];
    }

    /** Report the total size of the class file. */
    public int getTailLength() {
        return classFile.length - getEndOffset();
    }

    /** Write the head (header plus constant pool)
     *  of the class file to the indicated stream.
     */
    public void writeHead(OutputStream out) throws IOException {
        out.write(classFile, 0, getEndOffset());
    }

    /** Write the head (header plus constant pool)
     *  of the class file to the indicated stream,
     *  incorporating the non-null entries of the given array
     *  as patches.
     */
    void writePatchedHead(OutputStream out, Object[] patchArray) {
        // this will be useful to partially emulate the class loader on old JVMs
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /** Write the tail (everything after the constant pool)
     *  of the class file to the indicated stream.
     */
    public void writeTail(OutputStream out) throws IOException {
        out.write(classFile, getEndOffset(), getTailLength());
    }

    private static char[] parseHeader(byte[] classFile) throws InvalidConstantPoolFormatException {
        char[] result = new char[5];
        ByteBuffer buffer = ByteBuffer.wrap(classFile);
        for (int i = 0; i < result.length; i++)
            result[i] = (char) getUnsignedShort(buffer);
        int magic = result[0] << 16 | result[1] << 0;
        if (magic != 0xCAFEBABE)
            throw new InvalidConstantPoolFormatException("invalid magic number "+magic);
        // skip major, minor version
        int len = result[4];
        if (len < 1)
            throw new InvalidConstantPoolFormatException("constant pool length < 1");
        return result;
    }

    /** Parse the constant pool of the class
     *  calling a method visit* each time a constant pool entry is parsed.
     *
     *  The order of the calls to visit* is not guaranteed to be the same
     *  than the order of the constant pool entry in the bytecode array.
     *
     * @param visitor
     * @throws InvalidConstantPoolFormatException
     */
    public void parse(ConstantPoolVisitor visitor) throws InvalidConstantPoolFormatException {
        ByteBuffer buffer = ByteBuffer.wrap(classFile);
        buffer.position(getStartOffset()); //skip header

        Object[] values = new Object[getLength()];
        try {
            parseConstantPool(buffer, values, visitor);
        } catch(BufferUnderflowException e) {
            throw new InvalidConstantPoolFormatException(e);
        }
        if (endOffset == 0) {
            endOffset = buffer.position();
            secondHeader = new char[4];
            for (int i = 0; i < secondHeader.length; i++) {
                secondHeader[i] = (char) getUnsignedShort(buffer);
            }
        }
        resolveConstantPool(values, visitor);
    }

    private char[] getCharArray(int utfLength) {
        if (utfLength <= charArray.length)
            return charArray;
        return charArray = new char[utfLength];
    }

    private void parseConstantPool(ByteBuffer buffer, Object[] values, ConstantPoolVisitor visitor) throws InvalidConstantPoolFormatException {
        for (int i = 1; i < tags.length; ) {
            byte tag = (byte) getUnsignedByte(buffer);
            assert(tags[i] == 0 || tags[i] == tag);
            tags[i] = tag;
            switch (tag) {
                case CONSTANT_Utf8:
                    int utfLen = getUnsignedShort(buffer);
                    String value = getUTF8(buffer, utfLen, getCharArray(utfLen));
                    visitor.visitUTF8(i, CONSTANT_Utf8, value);
                    tags[i] = tag;
                    values[i++] = value;
                    break;
                case CONSTANT_Integer:
                    visitor.visitConstantValue(i, tag, buffer.getInt());
                    i++;
                    break;
                case CONSTANT_Float:
                    visitor.visitConstantValue(i, tag, buffer.getFloat());
                    i++;
                    break;
                case CONSTANT_Long:
                    visitor.visitConstantValue(i, tag, buffer.getLong());
                    i+=2;
                    break;
                case CONSTANT_Double:
                    visitor.visitConstantValue(i, tag, buffer.getDouble());
                    i+=2;
                    break;

                case CONSTANT_Class:    // fall through:
                case CONSTANT_String:
                    tags[i] = tag;
                    values[i++] = new int[] { getUnsignedShort(buffer) };
                    break;

                case CONSTANT_Fieldref:           // fall through:
                case CONSTANT_Methodref:          // fall through:
                case CONSTANT_InterfaceMethodref: // fall through:
                case CONSTANT_NameAndType:
                    tags[i] = tag;
                    values[i++] = new int[] { getUnsignedShort(buffer), getUnsignedShort(buffer) };
                    break;
                default:
                    throw new AssertionError("invalid constant "+tag);
            }
        }
    }

    private void resolveConstantPool(Object[] values, ConstantPoolVisitor visitor) {
        // clean out the int[] values, which are temporary
        for (int beg = 1, end = values.length-1, beg2, end2;
             beg <= end;
             beg = beg2, end = end2) {
             beg2 = end; end2 = beg-1;
             //System.out.println("CP resolve pass: "+beg+".."+end);
             for (int i = beg; i <= end; i++) {
                  Object value = values[i];
                  if (!(value instanceof int[]))
                      continue;
                  int[] array = (int[]) value;
                  byte tag = tags[i];
                  switch (tag) {
                      case CONSTANT_String:
                          String stringBody = (String) values[array[0]];
                          visitor.visitConstantString(i, tag, stringBody, array[0]);
                          values[i] = null;
                          break;
                      case CONSTANT_Class: {
                          String className = (String) values[array[0]];
                          // use the external form favored by Class.forName:
                          className = className.replace('/', '.');
                          visitor.visitConstantString(i, tag, className, array[0]);
                          values[i] = className;
                          break;
                      }
                      case CONSTANT_NameAndType: {
                          String memberName = (String) values[array[0]];
                          String signature  = (String) values[array[1]];
                          visitor.visitDescriptor(i, tag, memberName, signature,
                                                  array[0], array[1]);
                          values[i] = new String[] {memberName, signature};
                          break;
                      }
                      case CONSTANT_Fieldref:           // fall through:
                      case CONSTANT_Methodref:          // fall through:
                      case CONSTANT_InterfaceMethodref: {
                              Object className   = values[array[0]];
                              Object nameAndType = values[array[1]];
                              if (!(className instanceof String) ||
                                  !(nameAndType instanceof String[])) {
                                   // one more pass is needed
                                   if (beg2 > i)  beg2 = i;
                                   if (end2 < i)  end2 = i;
                                   continue;
                              }
                              String[] nameAndTypeArray = (String[]) nameAndType;
                              visitor.visitMemberRef(i, tag,
                                  (String)className,
                                  nameAndTypeArray[0],
                                  nameAndTypeArray[1],
                                  array[0], array[1]);
                              values[i] = null;
                          }
                          break;
                      default:
                          continue;
                }
            }
        }
    }

    private static int getUnsignedByte(ByteBuffer buffer) {
        return buffer.get() & 0xFF;
    }

    private static int getUnsignedShort(ByteBuffer buffer) {
        int b1 = getUnsignedByte(buffer);
        int b2 = getUnsignedByte(buffer);
        return (b1 << 8) + (b2 << 0);
    }

    private static String getUTF8(ByteBuffer buffer, int utfLen, char[] charArray) throws InvalidConstantPoolFormatException {
      int utfLimit = buffer.position() + utfLen;
      int index = 0;
      while (buffer.position() < utfLimit) {
          int c = buffer.get() & 0xff;
          if (c > 127) {
              buffer.position(buffer.position() - 1);
              return getUTF8Extended(buffer, utfLimit, charArray, index);
          }
          charArray[index++] = (char)c;
      }
      return new String(charArray, 0, index);
    }

    private static String getUTF8Extended(ByteBuffer buffer, int utfLimit, char[] charArray, int index) throws InvalidConstantPoolFormatException {
        int c, c2, c3;
        while (buffer.position() < utfLimit) {
            c = buffer.get() & 0xff;
            switch (c >> 4) {
                case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7:
                    /* 0xxxxxxx*/
                    charArray[index++] = (char)c;
                    break;
                case 12: case 13:
                    /* 110x xxxx   10xx xxxx*/
                    c2 = buffer.get();
                    if ((c2 & 0xC0) != 0x80)
                        throw new InvalidConstantPoolFormatException(
                            "malformed input around byte " + buffer.position());
                     charArray[index++] = (char)(((c  & 0x1F) << 6) |
                                                  (c2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    c2 = buffer.get();
                    c3 = buffer.get();
                    if (((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80))
                       throw new InvalidConstantPoolFormatException(
                          "malformed input around byte " + (buffer.position()));
                    charArray[index++] = (char)(((c  & 0x0F) << 12) |
                                                ((c2 & 0x3F) << 6)  |
                                                ((c3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    throw new InvalidConstantPoolFormatException(
                        "malformed input around byte " + buffer.position());
            }
        }
        // The number of chars produced may be less than utflen
        return new String(charArray, 0, index);
    }
}
