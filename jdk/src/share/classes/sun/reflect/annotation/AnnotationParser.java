/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.annotation;

import java.lang.annotation.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.lang.reflect.*;
import sun.reflect.ConstantPool;

import sun.reflect.generics.parser.SignatureParser;
import sun.reflect.generics.tree.TypeSignature;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.visitor.Reifier;
import sun.reflect.generics.scope.ClassScope;

/**
 * Parser for Java programming language annotations.  Translates
 * annotation byte streams emitted by compiler into annotation objects.
 *
 * @author  Josh Bloch
 * @since   1.5
 */
public class AnnotationParser {
    /**
     * Parses the annotations described by the specified byte array.
     * resolving constant references in the specified constant pool.
     * The array must contain an array of annotations as described
     * in the RuntimeVisibleAnnotations_attribute:
     *
     *   u2 num_annotations;
     *   annotation annotations[num_annotations];
     *
     * @throws AnnotationFormatError if an annotation is found to be
     *         malformed.
     */
    public static Map<Class<? extends Annotation>, Annotation> parseAnnotations(
                byte[] rawAnnotations,
                ConstantPool constPool,
                Class<?> container) {
        if (rawAnnotations == null)
            return Collections.emptyMap();

        try {
            return parseAnnotations2(rawAnnotations, constPool, container);
        } catch(BufferUnderflowException e) {
            throw new AnnotationFormatError("Unexpected end of annotations.");
        } catch(IllegalArgumentException e) {
            // Type mismatch in constant pool
            throw new AnnotationFormatError(e);
        }
    }

    private static Map<Class<? extends Annotation>, Annotation> parseAnnotations2(
                byte[] rawAnnotations,
                ConstantPool constPool,
                Class<?> container) {
        Map<Class<? extends Annotation>, Annotation> result =
            new LinkedHashMap<Class<? extends Annotation>, Annotation>();
        ByteBuffer buf = ByteBuffer.wrap(rawAnnotations);
        int numAnnotations = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numAnnotations; i++) {
            Annotation a = parseAnnotation(buf, constPool, container, false);
            if (a != null) {
                Class<? extends Annotation> klass = a.annotationType();
                AnnotationType type = AnnotationType.getInstance(klass);
                if (type.retention() == RetentionPolicy.RUNTIME)
                    if (result.put(klass, a) != null)
                        throw new AnnotationFormatError(
                            "Duplicate annotation for class: "+klass+": " + a);
            }
        }
        return result;
    }

    /**
     * Parses the parameter annotations described by the specified byte array.
     * resolving constant references in the specified constant pool.
     * The array must contain an array of annotations as described
     * in the RuntimeVisibleParameterAnnotations_attribute:
     *
     *    u1 num_parameters;
     *    {
     *        u2 num_annotations;
     *        annotation annotations[num_annotations];
     *    } parameter_annotations[num_parameters];
     *
     * Unlike parseAnnotations, rawAnnotations must not be null!
     * A null value must be handled by the caller.  This is so because
     * we cannot determine the number of parameters if rawAnnotations
     * is null.  Also, the caller should check that the number
     * of parameters indicated by the return value of this method
     * matches the actual number of method parameters.  A mismatch
     * indicates that an AnnotationFormatError should be thrown.
     *
     * @throws AnnotationFormatError if an annotation is found to be
     *         malformed.
     */
    public static Annotation[][] parseParameterAnnotations(
                    byte[] rawAnnotations,
                    ConstantPool constPool,
                    Class<?> container) {
        try {
            return parseParameterAnnotations2(rawAnnotations, constPool, container);
        } catch(BufferUnderflowException e) {
            throw new AnnotationFormatError(
                "Unexpected end of parameter annotations.");
        } catch(IllegalArgumentException e) {
            // Type mismatch in constant pool
            throw new AnnotationFormatError(e);
        }
    }

    private static Annotation[][] parseParameterAnnotations2(
                    byte[] rawAnnotations,
                    ConstantPool constPool,
                    Class<?> container) {
        ByteBuffer buf = ByteBuffer.wrap(rawAnnotations);
        int numParameters = buf.get() & 0xFF;
        Annotation[][] result = new Annotation[numParameters][];

        for (int i = 0; i < numParameters; i++) {
            int numAnnotations = buf.getShort() & 0xFFFF;
            List<Annotation> annotations =
                new ArrayList<Annotation>(numAnnotations);
            for (int j = 0; j < numAnnotations; j++) {
                Annotation a = parseAnnotation(buf, constPool, container, false);
                if (a != null) {
                    AnnotationType type = AnnotationType.getInstance(
                                              a.annotationType());
                    if (type.retention() == RetentionPolicy.RUNTIME)
                        annotations.add(a);
                }
            }
            result[i] = annotations.toArray(EMPTY_ANNOTATIONS_ARRAY);
        }
        return result;
    }

    private static final Annotation[] EMPTY_ANNOTATIONS_ARRAY =
                    new Annotation[0];

    /**
     * Parses the annotation at the current position in the specified
     * byte buffer, resolving constant references in the specified constant
     * pool.  The cursor of the byte buffer must point to an "annotation
     * structure" as described in the RuntimeVisibleAnnotations_attribute:
     *
     * annotation {
     *    u2    type_index;
     *       u2    num_member_value_pairs;
     *       {    u2    member_name_index;
     *             member_value value;
     *       }    member_value_pairs[num_member_value_pairs];
     *    }
     * }
     *
     * Returns the annotation, or null if the annotation's type cannot
     * be found by the VM, or is not a valid annotation type.
     *
     * @param exceptionOnMissingAnnotationClass if true, throw
     * TypeNotPresentException if a referenced annotation type is not
     * available at runtime
     */
    private static Annotation parseAnnotation(ByteBuffer buf,
                                              ConstantPool constPool,
                                              Class<?> container,
                                              boolean exceptionOnMissingAnnotationClass) {
        int typeIndex = buf.getShort() & 0xFFFF;
        Class<? extends Annotation> annotationClass = null;
        String sig = "[unknown]";
        try {
            try {
                sig = constPool.getUTF8At(typeIndex);
                annotationClass = (Class<? extends Annotation>)parseSig(sig, container);
            } catch (IllegalArgumentException ex) {
                // support obsolete early jsr175 format class files
                annotationClass = constPool.getClassAt(typeIndex);
            }
        } catch (NoClassDefFoundError e) {
            if (exceptionOnMissingAnnotationClass)
                // note: at this point sig is "[unknown]" or VM-style
                // name instead of a binary name
                throw new TypeNotPresentException(sig, e);
            skipAnnotation(buf, false);
            return null;
        }
        catch (TypeNotPresentException e) {
            if (exceptionOnMissingAnnotationClass)
                throw e;
            skipAnnotation(buf, false);
            return null;
        }
        AnnotationType type = null;
        try {
            type = AnnotationType.getInstance(annotationClass);
        } catch (IllegalArgumentException e) {
            skipAnnotation(buf, false);
            return null;
        }

        Map<String, Class<?>> memberTypes = type.memberTypes();
        Map<String, Object> memberValues =
            new LinkedHashMap<String, Object>(type.memberDefaults());

        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            int memberNameIndex = buf.getShort() & 0xFFFF;
            String memberName = constPool.getUTF8At(memberNameIndex);
            Class<?> memberType = memberTypes.get(memberName);

            if (memberType == null) {
                // Member is no longer present in annotation type; ignore it
                skipMemberValue(buf);
            } else {
                Object value = parseMemberValue(memberType, buf, constPool, container);
                if (value instanceof AnnotationTypeMismatchExceptionProxy)
                    ((AnnotationTypeMismatchExceptionProxy) value).
                        setMember(type.members().get(memberName));
                memberValues.put(memberName, value);
            }
        }
        return annotationForMap(annotationClass, memberValues);
    }

    /**
     * Returns an annotation of the given type backed by the given
     * member -> value map.
     */
    public static Annotation annotationForMap(
        Class<? extends Annotation> type, Map<String, Object> memberValues)
    {
        return (Annotation) Proxy.newProxyInstance(
            type.getClassLoader(), new Class[] { type },
            new AnnotationInvocationHandler(type, memberValues));
    }

    /**
     * Parses the annotation member value at the current position in the
     * specified byte buffer, resolving constant references in the specified
     * constant pool.  The cursor of the byte buffer must point to a
     * "member_value structure" as described in the
     * RuntimeVisibleAnnotations_attribute:
     *
     *  member_value {
     *    u1 tag;
     *    union {
     *       u2   const_value_index;
     *       {
     *           u2   type_name_index;
     *           u2   const_name_index;
     *       } enum_const_value;
     *       u2   class_info_index;
     *       annotation annotation_value;
     *       {
     *           u2    num_values;
     *           member_value values[num_values];
     *       } array_value;
     *    } value;
     * }
     *
     * The member must be of the indicated type. If it is not, this
     * method returns an AnnotationTypeMismatchExceptionProxy.
     */
    public static Object parseMemberValue(Class<?> memberType,
                                          ByteBuffer buf,
                                          ConstantPool constPool,
                                          Class<?> container) {
        Object result = null;
        int tag = buf.get();
        switch(tag) {
          case 'e':
              return parseEnumValue((Class<? extends Enum<?>>)memberType, buf, constPool, container);
          case 'c':
              result = parseClassValue(buf, constPool, container);
              break;
          case '@':
              result = parseAnnotation(buf, constPool, container, true);
              break;
          case '[':
              return parseArray(memberType, buf, constPool, container);
          default:
              result = parseConst(tag, buf, constPool);
        }

        if (!(result instanceof ExceptionProxy) &&
            !memberType.isInstance(result))
            result = new AnnotationTypeMismatchExceptionProxy(
                result.getClass() + "[" + result + "]");
        return result;
    }

    /**
     * Parses the primitive or String annotation member value indicated by
     * the specified tag byte at the current position in the specified byte
     * buffer, resolving constant reference in the specified constant pool.
     * The cursor of the byte buffer must point to an annotation member value
     * of the type indicated by the specified tag, as described in the
     * RuntimeVisibleAnnotations_attribute:
     *
     *       u2   const_value_index;
     */
    private static Object parseConst(int tag,
                                     ByteBuffer buf, ConstantPool constPool) {
        int constIndex = buf.getShort() & 0xFFFF;
        switch(tag) {
          case 'B':
            return Byte.valueOf((byte) constPool.getIntAt(constIndex));
          case 'C':
            return Character.valueOf((char) constPool.getIntAt(constIndex));
          case 'D':
            return Double.valueOf(constPool.getDoubleAt(constIndex));
          case 'F':
            return Float.valueOf(constPool.getFloatAt(constIndex));
          case 'I':
            return Integer.valueOf(constPool.getIntAt(constIndex));
          case 'J':
            return Long.valueOf(constPool.getLongAt(constIndex));
          case 'S':
            return Short.valueOf((short) constPool.getIntAt(constIndex));
          case 'Z':
            return Boolean.valueOf(constPool.getIntAt(constIndex) != 0);
          case 's':
            return constPool.getUTF8At(constIndex);
          default:
            throw new AnnotationFormatError(
                "Invalid member-value tag in annotation: " + tag);
        }
    }

    /**
     * Parses the Class member value at the current position in the
     * specified byte buffer, resolving constant references in the specified
     * constant pool.  The cursor of the byte buffer must point to a "class
     * info index" as described in the RuntimeVisibleAnnotations_attribute:
     *
     *       u2   class_info_index;
     */
    private static Object parseClassValue(ByteBuffer buf,
                                          ConstantPool constPool,
                                          Class<?> container) {
        int classIndex = buf.getShort() & 0xFFFF;
        try {
            try {
                String sig = constPool.getUTF8At(classIndex);
                return parseSig(sig, container);
            } catch (IllegalArgumentException ex) {
                // support obsolete early jsr175 format class files
                return constPool.getClassAt(classIndex);
            }
        } catch (NoClassDefFoundError e) {
            return new TypeNotPresentExceptionProxy("[unknown]", e);
        }
        catch (TypeNotPresentException e) {
            return new TypeNotPresentExceptionProxy(e.typeName(), e.getCause());
        }
    }

    private static Class<?> parseSig(String sig, Class<?> container) {
        if (sig.equals("V")) return void.class;
        SignatureParser parser = SignatureParser.make();
        TypeSignature typeSig = parser.parseTypeSig(sig);
        GenericsFactory factory = CoreReflectionFactory.make(container, ClassScope.make(container));
        Reifier reify = Reifier.make(factory);
        typeSig.accept(reify);
        Type result = reify.getResult();
        return toClass(result);
    }
    static Class<?> toClass(Type o) {
        if (o instanceof GenericArrayType)
            return Array.newInstance(toClass(((GenericArrayType)o).getGenericComponentType()),
                                     0)
                .getClass();
        return (Class)o;
    }

    /**
     * Parses the enum constant member value at the current position in the
     * specified byte buffer, resolving constant references in the specified
     * constant pool.  The cursor of the byte buffer must point to a
     * "enum_const_value structure" as described in the
     * RuntimeVisibleAnnotations_attribute:
     *
     *       {
     *           u2   type_name_index;
     *           u2   const_name_index;
     *       } enum_const_value;
     */
    private static Object parseEnumValue(Class<? extends Enum> enumType, ByteBuffer buf,
                                         ConstantPool constPool,
                                         Class<?> container) {
        int typeNameIndex = buf.getShort() & 0xFFFF;
        String typeName  = constPool.getUTF8At(typeNameIndex);
        int constNameIndex = buf.getShort() & 0xFFFF;
        String constName = constPool.getUTF8At(constNameIndex);

        if (!typeName.endsWith(";")) {
            // support now-obsolete early jsr175-format class files.
            if (!enumType.getName().equals(typeName))
            return new AnnotationTypeMismatchExceptionProxy(
                typeName + "." + constName);
        } else if (enumType != parseSig(typeName, container)) {
            return new AnnotationTypeMismatchExceptionProxy(
                typeName + "." + constName);
        }

        try {
            return  Enum.valueOf(enumType, constName);
        } catch(IllegalArgumentException e) {
            return new EnumConstantNotPresentExceptionProxy(
                (Class<? extends Enum>)enumType, constName);
        }
    }

    /**
     * Parses the array value at the current position in the specified byte
     * buffer, resolving constant references in the specified constant pool.
     * The cursor of the byte buffer must point to an array value struct
     * as specified in the RuntimeVisibleAnnotations_attribute:
     *
     *       {
     *           u2    num_values;
     *           member_value values[num_values];
     *       } array_value;
     *
     * If the array values do not match arrayType, an
     * AnnotationTypeMismatchExceptionProxy will be returned.
     */
    private static Object parseArray(Class<?> arrayType,
                                     ByteBuffer buf,
                                     ConstantPool constPool,
                                     Class<?> container) {
        int length = buf.getShort() & 0xFFFF;  // Number of array components
        Class<?> componentType = arrayType.getComponentType();

        if (componentType == byte.class) {
            return parseByteArray(length, buf, constPool);
        } else if (componentType == char.class) {
            return parseCharArray(length, buf, constPool);
        } else if (componentType == double.class) {
            return parseDoubleArray(length, buf, constPool);
        } else if (componentType == float.class) {
            return parseFloatArray(length, buf, constPool);
        } else if (componentType == int.class) {
            return parseIntArray(length, buf, constPool);
        } else if (componentType == long.class) {
            return parseLongArray(length, buf, constPool);
        } else if (componentType == short.class) {
            return parseShortArray(length, buf, constPool);
        } else if (componentType == boolean.class) {
            return parseBooleanArray(length, buf, constPool);
        } else if (componentType == String.class) {
            return parseStringArray(length, buf, constPool);
        } else if (componentType == Class.class) {
            return parseClassArray(length, buf, constPool, container);
        } else if (componentType.isEnum()) {
            return parseEnumArray(length, (Class<? extends Enum>)componentType, buf,
                                  constPool, container);
        } else {
            assert componentType.isAnnotation();
            return parseAnnotationArray(length, (Class <? extends Annotation>)componentType, buf,
                                        constPool, container);
        }
    }

    private static Object parseByteArray(int length,
                                  ByteBuffer buf, ConstantPool constPool) {
        byte[] result = new byte[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'B') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = (byte) constPool.getIntAt(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseCharArray(int length,
                                  ByteBuffer buf, ConstantPool constPool) {
        char[] result = new char[length];
        boolean typeMismatch = false;
        byte tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'C') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = (char) constPool.getIntAt(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseDoubleArray(int length,
                                    ByteBuffer buf, ConstantPool constPool) {
        double[] result = new  double[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'D') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = constPool.getDoubleAt(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseFloatArray(int length,
                                   ByteBuffer buf, ConstantPool constPool) {
        float[] result = new float[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'F') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = constPool.getFloatAt(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseIntArray(int length,
                                 ByteBuffer buf, ConstantPool constPool) {
        int[] result = new  int[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'I') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = constPool.getIntAt(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseLongArray(int length,
                                  ByteBuffer buf, ConstantPool constPool) {
        long[] result = new long[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'J') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = constPool.getLongAt(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseShortArray(int length,
                                   ByteBuffer buf, ConstantPool constPool) {
        short[] result = new short[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'S') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = (short) constPool.getIntAt(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseBooleanArray(int length,
                                     ByteBuffer buf, ConstantPool constPool) {
        boolean[] result = new boolean[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'Z') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = (constPool.getIntAt(index) != 0);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseStringArray(int length,
                                    ByteBuffer buf,  ConstantPool constPool) {
        String[] result = new String[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 's') {
                int index = buf.getShort() & 0xFFFF;
                result[i] = constPool.getUTF8At(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseClassArray(int length,
                                          ByteBuffer buf,
                                          ConstantPool constPool,
                                          Class<?> container) {
        Object[] result = new Class<?>[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'c') {
                result[i] = parseClassValue(buf, constPool, container);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseEnumArray(int length, Class<? extends Enum> enumType,
                                         ByteBuffer buf,
                                         ConstantPool constPool,
                                         Class<?> container) {
        Object[] result = (Object[]) Array.newInstance(enumType, length);
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'e') {
                result[i] = parseEnumValue(enumType, buf, constPool, container);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    private static Object parseAnnotationArray(int length,
                                               Class<? extends Annotation> annotationType,
                                               ByteBuffer buf,
                                               ConstantPool constPool,
                                               Class<?> container) {
        Object[] result = (Object[]) Array.newInstance(annotationType, length);
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == '@') {
                result[i] = parseAnnotation(buf, constPool, container, true);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    /**
     * Return an appropriate exception proxy for a mismatching array
     * annotation where the erroneous array has the specified tag.
     */
    private static ExceptionProxy exceptionProxy(int tag) {
        return new AnnotationTypeMismatchExceptionProxy(
            "Array with component tag: " + tag);
    }

    /**
     * Skips the annotation at the current position in the specified
     * byte buffer.  The cursor of the byte buffer must point to
     * an "annotation structure" OR two bytes into an annotation
     * structure (i.e., after the type index).
     *
     * @parameter complete true if the byte buffer points to the beginning
     *     of an annotation structure (rather than two bytes in).
     */
    private static void skipAnnotation(ByteBuffer buf, boolean complete) {
        if (complete)
            buf.getShort();   // Skip type index
        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            buf.getShort();   // Skip memberNameIndex
            skipMemberValue(buf);
        }
    }

    /**
     * Skips the annotation member value at the current position in the
     * specified byte buffer.  The cursor of the byte buffer must point to a
     * "member_value structure."
     */
    private static void skipMemberValue(ByteBuffer buf) {
        int tag = buf.get();
        skipMemberValue(tag, buf);
    }

    /**
     * Skips the annotation member value at the current position in the
     * specified byte buffer.  The cursor of the byte buffer must point
     * immediately after the tag in a "member_value structure."
     */
    private static void skipMemberValue(int tag, ByteBuffer buf) {
        switch(tag) {
          case 'e': // Enum value
            buf.getInt();  // (Two shorts, actually.)
            break;
          case '@':
            skipAnnotation(buf, true);
            break;
          case '[':
            skipArray(buf);
            break;
          default:
            // Class, primitive, or String
            buf.getShort();
        }
    }

    /**
     * Skips the array value at the current position in the specified byte
     * buffer.  The cursor of the byte buffer must point to an array value
     * struct.
     */
    private static void skipArray(ByteBuffer buf) {
        int length = buf.getShort() & 0xFFFF;
        for (int i = 0; i < length; i++)
            skipMemberValue(buf);
    }

    /*
     * This method converts the annotation map returned by the parseAnnotations()
     * method to an array.  It is called by Field.getDeclaredAnnotations(),
     * Method.getDeclaredAnnotations(), and Constructor.getDeclaredAnnotations().
     * This avoids the reflection classes to load the Annotation class until
     * it is needed.
     */
    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];
    public static Annotation[] toArray(Map<Class<? extends Annotation>, Annotation> annotations) {
        return annotations.values().toArray(EMPTY_ANNOTATION_ARRAY);
    }
}
