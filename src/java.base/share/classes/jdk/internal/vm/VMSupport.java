/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm;

import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.ConstantPool;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy;
import sun.reflect.annotation.EnumValue;
import sun.reflect.annotation.EnumValueArray;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/*
 * Support class used by JVMCI, JVMTI and VM attach mechanism.
 */
public class VMSupport {

    private static final Unsafe U = Unsafe.getUnsafe();
    private static Properties agentProps = null;

    /**
     * Returns the agent properties.
     */
    public static synchronized Properties getAgentProperties() {
        if (agentProps == null) {
            agentProps = new Properties();
            initAgentProperties(agentProps);
        }
        return agentProps;
    }
    private static native Properties initAgentProperties(Properties props);

    /**
     * Writes the given properties list to a byte array and return it. The stream written
     * to the byte array is ISO 8859-1 encoded.
     */
    private static byte[] serializePropertiesToByteArray(Properties p) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        p.store(out, null);
        return out.toByteArray();
    }

    /**
     * @return a Properties object containing only the entries in {@code p}
     *          whose key and value are both Strings
     */
    private static Properties onlyStrings(Properties p) {
        Properties props = new Properties();

        // stringPropertyNames() returns a snapshot of the property keys
        Set<String> keyset = p.stringPropertyNames();
        for (String key : keyset) {
            String value = p.getProperty(key);
            props.put(key, value);
        }
        return props;
    }

    public static byte[] serializePropertiesToByteArray() throws IOException {
        return serializePropertiesToByteArray(onlyStrings(System.getProperties()));
    }

    public static byte[] serializeAgentPropertiesToByteArray() throws IOException {
        return serializePropertiesToByteArray(onlyStrings(getAgentProperties()));
    }

    /*
     * Return the temporary directory that the VM uses for the attach
     * and perf data files.
     *
     * It is important that this directory is well-known and the
     * same for all VM instances. It cannot be affected by configuration
     * variables such as java.io.tmpdir.
     */
    public static native String getVMTemporaryDirectory();

    /**
     * Decodes the exception described by {@code format} and {@code buffer} and throws it.
     *
     * @param format specifies how to interpret {@code buffer}:
     *            <pre>
     *             0: {@code buffer} was created by {@link #encodeThrowable}
     *             1: native memory for {@code buffer} could not be allocated
     *             2: an OutOfMemoryError was thrown while encoding the exception
     *             3: some other problem occured while encoding the exception. If {@code buffer != 0},
     *                it contains a {@code struct { u4 len; char[len] desc}} where {@code desc} describes the problem
     *             4: an OutOfMemoryError thrown from within VM code on a
     *                thread that cannot call Java (OOME has no stack trace)
     *            </pre>
     * @param buffer encoded info about the exception to throw (depends on {@code format})
     * @param inJVMHeap [@code true} if executing in the JVM heap, {@code false} otherwise
     * @param debug specifies whether debug stack traces should be enabled in case of translation failure
     */
    public static void decodeAndThrowThrowable(int format, long buffer, boolean inJVMHeap, boolean debug) throws Throwable {
        if (format != 0) {
            if (format == 4) {
                throw new TranslatedException(new OutOfMemoryError("in VM code and current thread cannot call Java"));
            }
            String context = String.format("while encoding an exception to translate it %s the JVM heap",
                    inJVMHeap ? "to" : "from");
            if (format == 1) {
                throw new InternalError("native buffer could not be allocated " + context);
            }
            if (format == 2) {
                throw new OutOfMemoryError(context);
            }
            if (format == 3 && buffer != 0L) {
                byte[] bytes = bufferToBytes(buffer);
                throw new InternalError("unexpected problem occurred " + context + ": " + new String(bytes, StandardCharsets.UTF_8));
            }
            throw new InternalError("unexpected problem occurred " + context);
        }
        throw TranslatedException.decodeThrowable(bufferToBytes(buffer), debug);
    }

    private static byte[] bufferToBytes(long buffer) {
        if (buffer == 0) {
            return null;
        }
        int len = U.getInt(buffer);
        byte[] bytes = new byte[len];
        U.copyMemory(null, buffer + 4, bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, len);
        return bytes;
    }

    /**
     * If {@code bufferSize} is large enough, encodes {@code throwable} into a byte array and writes
     * it to {@code buffer}. The encoding in {@code buffer} can be decoded by
     * {@link #decodeAndThrowThrowable}.
     *
     * @param throwable the exception to encode
     * @param buffer a native byte buffer
     * @param bufferSize the size of {@code buffer} in bytes
     * @return the number of bytes written into {@code buffer} if {@code bufferSize} is large
     *         enough, otherwise {@code -N} where {@code N} is the value {@code bufferSize} needs to
     *         be to fit the encoding
     */
    public static int encodeThrowable(Throwable throwable, long buffer, int bufferSize) {
        byte[] encoding = TranslatedException.encodeThrowable(throwable);
        int requiredSize = 4 + encoding.length;
        if (bufferSize < requiredSize) {
            return -requiredSize;
        }
        U.putInt(buffer, encoding.length);
        U.copyMemory(encoding, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, buffer + 4, encoding.length);
        return requiredSize;
    }

    /**
     * Parses {@code rawAnnotations} into a list of {@link Annotation}s and then
     * serializes them to a byte array with {@link #encodeAnnotations(Collection)}.
     */
    public static byte[] encodeAnnotations(byte[] rawAnnotations,
                                           Class<?> declaringClass,
                                           ConstantPool cp,
                                           Class<? extends Annotation>[] selectAnnotationClasses) {
        if (selectAnnotationClasses != null) {
            if (selectAnnotationClasses.length == 0) {
                throw new IllegalArgumentException("annotation class selection must be null or non-empty");
            }
            for (Class<?> c : selectAnnotationClasses) {
                if (!c.isAnnotation()) {
                    throw new IllegalArgumentException(c + " is not an annotation interface");
                }
            }
        }
        return encodeAnnotations(AnnotationParser.parseSelectAnnotations(rawAnnotations, cp, declaringClass, false, selectAnnotationClasses).values());
    }

    /**
     * Encodes annotations to a byte array. The byte array can be decoded with {@link #decodeAnnotations(byte[], AnnotationDecoder)}.
     */
    public static byte[] encodeAnnotations(Collection<Annotation> annotations) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                writeLength(dos, annotations.size());
                for (Annotation a : annotations) {
                    encodeAnnotation(dos, a);
                }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private static void encodeAnnotation(DataOutputStream dos, Annotation a) throws Exception {
        Class<? extends Annotation> type = a.annotationType();
        Map<String, Object> values = AnnotationSupport.memberValues(a);
        dos.writeUTF(type.getName());
        writeLength(dos, values.size());
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object value = e.getValue();
            if (value == null) {
                // IncompleteAnnotationException
                continue;
            }
            Class<?> valueType = value.getClass();
            dos.writeUTF(e.getKey());
            if (valueType == Byte.class) {
                dos.writeByte('B');
                dos.writeByte((byte) value);
            } else if (valueType == Character.class) {
                dos.writeByte('C');
                dos.writeChar((char) value);
            } else if (valueType == Double.class) {
                dos.writeByte('D');
                dos.writeDouble((double) value);
            } else if (valueType == Float.class) {
                dos.writeByte('F');
                dos.writeFloat((float) value);
            } else if (valueType == Integer.class) {
                dos.writeByte('I');
                dos.writeInt((int) value);
            } else if (valueType == Long.class) {
                dos.writeByte('J');
                dos.writeLong((long) value);
            } else if (valueType == Short.class) {
                dos.writeByte('S');
                dos.writeShort((short) value);
            } else if (valueType == Boolean.class) {
                dos.writeByte('Z');
                dos.writeBoolean((boolean) value);
            } else if (valueType == String.class) {
                dos.writeByte('s');
                dos.writeUTF((String) value);
            } else if (valueType == Class.class) {
                dos.writeByte('c');
                dos.writeUTF(((Class<?>) value).getName());
            } else if (valueType == EnumValue.class) {
                EnumValue enumValue = (EnumValue) value;
                dos.writeByte('e');
                dos.writeUTF(enumValue.enumType.getName());
                dos.writeUTF(enumValue.constName);
            } else if (valueType == EnumValueArray.class) {
                EnumValueArray enumValueArray = (EnumValueArray) value;
                List<String> array = enumValueArray.constNames;
                dos.writeByte('[');
                dos.writeByte('e');
                dos.writeUTF(enumValueArray.enumType.getName());
                writeLength(dos, array.size());
                for (String s : array) {
                    dos.writeUTF(s);
                }
            } else if (valueType.isEnum()) {
                dos.writeByte('e');
                dos.writeUTF(valueType.getName());
                dos.writeUTF(((Enum<?>) value).name());
            } else if (value instanceof Annotation) {
                dos.writeByte('@');
                encodeAnnotation(dos, (Annotation) value);
            } else if (valueType.isArray()) {
                Class<?> componentType = valueType.getComponentType();
                if (componentType == byte.class) {
                    byte[] array = (byte[]) value;
                    dos.writeByte('[');
                    dos.writeByte('B');
                    writeLength(dos, array.length);
                    dos.write(array);
                } else if (componentType == char.class) {
                    char[] array = (char[]) value;
                    dos.writeByte('[');
                    dos.writeByte('C');
                    writeLength(dos, array.length);
                    for (char c : array) {
                        dos.writeChar(c);
                    }
                } else if (componentType == double.class) {
                    double[] array = (double[]) value;
                    dos.writeByte('[');
                    dos.writeByte('D');
                    writeLength(dos, array.length);
                    for (double v : array) {
                        dos.writeDouble(v);
                    }
                } else if (componentType == float.class) {
                    float[] array = (float[]) value;
                    dos.writeByte('[');
                    dos.writeByte('F');
                    writeLength(dos, array.length);
                    for (float v : array) {
                        dos.writeFloat(v);
                    }
                } else if (componentType == int.class) {
                    int[] array = (int[]) value;
                    dos.writeByte('[');
                    dos.writeByte('I');
                    writeLength(dos, array.length);
                    for (int j : array) {
                        dos.writeInt(j);
                    }
                } else if (componentType == long.class) {
                    long[] array = (long[]) value;
                    dos.writeByte('[');
                    dos.writeByte('J');
                    writeLength(dos, array.length);
                    for (long l : array) {
                        dos.writeLong(l);
                    }
                } else if (componentType == short.class) {
                    short[] array = (short[]) value;
                    dos.writeByte('[');
                    dos.writeByte('S');
                    writeLength(dos, array.length);
                    for (short item : array) {
                        dos.writeShort(item);
                    }
                } else if (componentType == boolean.class) {
                    boolean[] array = (boolean[]) value;
                    dos.writeByte('[');
                    dos.writeByte('Z');
                    writeLength(dos, array.length);
                    for (boolean b : array) {
                        dos.writeBoolean(b);
                    }
                } else if (componentType == String.class) {
                    String[] array = (String[]) value;
                    dos.writeByte('[');
                    dos.writeByte('s');
                    writeLength(dos, array.length);
                    for (String s : array) {
                        dos.writeUTF(s);
                    }
                } else if (componentType == Class.class) {
                    Class<?>[] array = (Class<?>[]) value;
                    dos.writeByte('[');
                    dos.writeByte('c');
                    writeLength(dos, array.length);
                    for (Class<?> aClass : array) {
                        dos.writeUTF(aClass.getName());
                    }
                } else if (componentType.isEnum()) {
                    Enum<?>[] array = (Enum<?>[]) value;
                    dos.writeByte('[');
                    dos.writeByte('e');
                    dos.writeUTF(componentType.getName());
                    writeLength(dos, array.length);
                    for (Enum<?> anEnum : array) {
                        dos.writeUTF(anEnum.name());
                    }
                } else if (componentType.isAnnotation()) {
                    Annotation[] array = (Annotation[]) value;
                    dos.writeByte('[');
                    dos.writeByte('@');
                    writeLength(dos, array.length);
                    for (Annotation annotation : array) {
                        encodeAnnotation(dos, annotation);
                    }
                } else {
                    throw new InternalError("Unsupported annotation element component type " + componentType);
                }
            } else if (value instanceof TypeNotPresentExceptionProxy proxy) {
                dos.writeByte('x');
                dos.writeUTF(proxy.typeName());
            } else if (value instanceof AnnotationTypeMismatchExceptionProxy proxy) {
                dos.writeByte('y');
                dos.writeUTF(proxy.foundType());
            } else {
                throw new InternalError("Unsupported annotation element type " + valueType);
            }
        }
    }

    /**
     * Helper for {@link #decodeAnnotations(byte[], AnnotationDecoder)} to convert a byte
     * array (ostensibly produced by {@link VMSupport#encodeAnnotations}) into objects.
     *
     * @param <T> type to which a type name is {@linkplain #resolveType(String) resolved}
     * @param <A> type of the object representing a decoded annotation
     * @param <E> type of the object representing a decoded enum constant
     * @param <EA> type of the object representing a decoded array of enum constants
     * @param <MT> type of the object representing a missing type
     * @param <ETM> type of the object representing a decoded element type mismatch
     */
    public interface AnnotationDecoder<T, A, E, EA, MT, ETM> {
        /**
         * Resolves a name in {@link Class#getName()} format to an object of type {@code T}.
         */
        T resolveType(String name);

        /**
         * Creates an object representing a decoded annotation.
         *
         * @param type the annotation interface of the annotation
         * @param elements elements of the annotation
         */
        A newAnnotation(T type, Map.Entry<String, Object>[] elements);

        /**
         * Creates an object representing a decoded enum.
         *
         * @param enumType the enum type
         * @param name the name of the enum constant
         */
        E newEnum(T enumType, String name);

        /**
         * Creates an object representing a decoded enum array.
         *
         * @param enumType the enum type
         * @param names the names of the enum constants
         */
        EA newEnumArray(T enumType, List<String> names);

        /**
         * Creates an object representing a missing type.
         *
         * @param typeName see {@link TypeNotPresentException#typeName()}
         */
        MT newMissingType(String typeName);

        /**
         * Creates an object representing element of an annotation whose type
         * has changed after the annotation was compiled.
         *
         * @param foundType see {@link AnnotationTypeMismatchException#foundType()}
         */
        ETM newElementTypeMismatch(String foundType);

    }

    /**
     * Decodes annotations serialized in {@code encoded} to objects.
     *
     * @param <T> type to which a type name is resolved
     * @param <A> type of the object representing a decoded annotation
     * @param <E> type of the object representing a decoded enum constant
     * @param <MT> type of the object representing a missing type
     * @param <ETM> type of the object representing a decoded element type mismatch
     * @return an immutable list of {@code A} objects
     */
    @SuppressWarnings("unchecked")
    public static <T, A, E, EA, MT, ETM> List<A> decodeAnnotations(byte[] encoded, AnnotationDecoder<T, A, E, EA, MT, ETM> decoder) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(encoded);
            DataInputStream dis = new DataInputStream(bais);
            return (List<A>) readArray(dis, () -> decodeAnnotation(dis, decoder));
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T, A, E, EA, MT, ETM> A decodeAnnotation(DataInputStream dis, AnnotationDecoder<T, A, E, EA, MT, ETM> decoder) throws IOException {
        String typeName = dis.readUTF();
        T type = decoder.resolveType(typeName);
        int n = readLength(dis);
        Map.Entry[] elements = new Map.Entry[n];
        for (int i = 0; i < n; i++) {
            String name = dis.readUTF();
            byte tag = dis.readByte();
            elements[i] = Map.entry(name, switch (tag) {
                case 'B' -> dis.readByte();
                case 'C' -> dis.readChar();
                case 'D' -> dis.readDouble();
                case 'F' -> dis.readFloat();
                case 'I' -> dis.readInt();
                case 'J' -> dis.readLong();
                case 'S' -> dis.readShort();
                case 'Z' -> dis.readBoolean();
                case 's' -> dis.readUTF();
                case 'c' -> decoder.resolveType(dis.readUTF());
                case 'e' -> decoder.newEnum(decoder.resolveType(dis.readUTF()), dis.readUTF());
                case '@' -> decodeAnnotation(dis, decoder);
                case '[' -> decodeArray(dis, decoder);
                case 'x' -> decoder.newMissingType(dis.readUTF());
                case 'y' -> decoder.newElementTypeMismatch(dis.readUTF());
                default -> throw new InternalError("Unsupported tag: " + tag);
            });
        }
        return decoder.newAnnotation(type, (Map.Entry<String, Object>[]) elements);
    }
    @FunctionalInterface
    interface IOReader {
        Object read() throws IOException;
    }

    private static <T, A, E, EA, MT, ETM> Object decodeArray(DataInputStream dis, AnnotationDecoder<T, A, E, EA, MT, ETM> decoder) throws IOException {
        byte componentTag = dis.readByte();
        return switch (componentTag) {
            case 'B' -> readArray(dis, dis::readByte);
            case 'C' -> readArray(dis, dis::readChar);
            case 'D' -> readArray(dis, dis::readDouble);
            case 'F' -> readArray(dis, dis::readFloat);
            case 'I' -> readArray(dis, dis::readInt);
            case 'J' -> readArray(dis, dis::readLong);
            case 'S' -> readArray(dis, dis::readShort);
            case 'Z' -> readArray(dis, dis::readBoolean);
            case 's' -> readArray(dis, dis::readUTF);
            case 'c' -> readArray(dis, () -> readClass(dis, decoder));
            case 'e' -> {
                T enumType = decoder.resolveType(dis.readUTF());
                yield readEnumArray(dis, decoder, enumType);
            }
            case '@' -> readArray(dis, () -> decodeAnnotation(dis, decoder));
            default -> throw new InternalError("Unsupported component tag: " + componentTag);
        };
    }

    /**
     * Reads an enum encoded at the current read position of {@code dis} and
     * returns it as an object of type {@code E}.
     */
    @SuppressWarnings("unchecked")
    private static <T, A, E, EA, MT, ETM> EA readEnumArray(DataInputStream dis, AnnotationDecoder<T, A, E, EA, MT, ETM> decoder, T enumType) throws IOException {
        List<String> names = (List<String>) readArray(dis, dis::readUTF);
        return decoder.newEnumArray(enumType, names);
    }

    /**
     * Reads a class encoded at the current read position of {@code dis} and
     * returns it as an object of type {@code T}.
     */
    private static <T, A, E, EA, MT, ETM> T readClass(DataInputStream dis, AnnotationDecoder<T, A, E, EA, MT, ETM> decoder) throws IOException {
        return decoder.resolveType(dis.readUTF());
    }

    /**
     * Reads an array encoded at the current read position of {@code dis} and
     * returns it in an immutable list.
     *
     * @param reader reads array elements from {@code dis}
     * @return an immutable list of {@code A} objects
     */
    private static List<?> readArray(DataInputStream dis, IOReader reader) throws IOException {
        Object[] array = new Object[readLength(dis)];
        for (int i = 0; i < array.length; i++) {
            array[i] = reader.read();
        }
        return List.of(array);
    }

    /**
     * Encodes {@code length} in 1 byte if it is less than 128.
     */
    private static void writeLength(DataOutputStream dos, int length) throws IOException {
        if (length < 0) {
            throw new NegativeArraySizeException();
        } else if (length <= 127) {
            dos.writeByte((byte) (0x80 | length));
        } else {
            dos.writeInt(length);
        }
    }

    private static int readLength(DataInputStream dis) throws IOException {
        int ch1 = dis.readByte();
        int length;
        if (ch1 < 0) {
            length = ch1 & 0x7F;
        } else {
            int ch2 = dis.read();
            int ch3 = dis.read();
            int ch4 = dis.read();
            length = (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0);
        }
        return length;
    }
}
