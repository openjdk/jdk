/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.internal.access.JavaObjectStreamDefaultSupportAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.util.ByteArray;

/**
 *
 */
final class ObjectStreamDefaultSupport {
    private static final MethodHandle DRO_HANDLE;
    private static final MethodHandle DWO_HANDLE;

    private static final ClassValue<ObjectStreamDefaultSupport> cache = new ClassValue<ObjectStreamDefaultSupport>() {
        protected ObjectStreamDefaultSupport computeValue(final Class<?> type) {
            ObjectStreamClass objectStreamClass = ObjectStreamClass.lookup(type);
            if (objectStreamClass == null) {
                return null;
            }
            try {
                objectStreamClass.checkDefaultSerialize();
            } catch (InvalidClassException e) {
                return null;
            }
            return new ObjectStreamDefaultSupport(objectStreamClass);
        }
    };

    static {
        try {
            MethodType droType = MethodType.methodType(void.class, Object.class, ObjectInputStream.class);
            DRO_HANDLE = MethodHandles.lookup().findVirtual(ObjectStreamDefaultSupport.class, "defaultReadObject", droType);
            MethodType dwoType = MethodType.methodType(void.class, Object.class, ObjectOutputStream.class);
            DWO_HANDLE = MethodHandles.lookup().findVirtual(ObjectStreamDefaultSupport.class, "defaultWriteObject", dwoType);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    private final ObjectStreamClass streamClass;
    private final MethodHandle boundDefaultReadObject;
    private final MethodHandle boundDefaultWriteObject;

    ObjectStreamDefaultSupport(ObjectStreamClass streamClass) {
        this.streamClass = streamClass;
        boundDefaultReadObject = DRO_HANDLE.bindTo(this).asType(MethodType.methodType(void.class, streamClass.forClass(), ObjectInputStream.class));
        boundDefaultWriteObject = DWO_HANDLE.bindTo(this).asType(MethodType.methodType(void.class, streamClass.forClass(), ObjectOutputStream.class));
    }

    MethodHandle boundDefaultReadObject() {
        return boundDefaultReadObject;
    }

    MethodHandle boundDefaultWriteObject() {
        return boundDefaultWriteObject;
    }

    private void defaultReadObject(Object obj, ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField getField = ois.readFields();
        byte[] bytes = new byte[streamClass.getPrimDataSize()];
        Object[] objs = new Object[streamClass.getNumObjFields()];
        for (ObjectStreamField field : streamClass.getFields(false)) {
            int offset = field.getOffset();
            String fieldName = field.getName();
            switch (field.getTypeCode()) {
                case 'B' -> bytes[offset] = getField.get(fieldName, (byte) 0);
                case 'C' -> ByteArray.setChar(bytes, offset, getField.get(fieldName, (char) 0));
                case 'D' -> ByteArray.setDoubleRaw(bytes, offset, getField.get(fieldName, 0.0));
                case 'F' -> ByteArray.setFloatRaw(bytes, offset, getField.get(fieldName, 0.0f));
                case 'I' -> ByteArray.setInt(bytes, offset, getField.get(fieldName, 0));
                case 'J' -> ByteArray.setLong(bytes, offset, getField.get(fieldName, 0L));
                case 'S' -> ByteArray.setShort(bytes, offset, getField.get(fieldName, (short) 0));
                case 'Z' -> ByteArray.setBoolean(bytes, offset, getField.get(fieldName, false));
                case '[', 'L' -> objs[offset] = getField.get(fieldName, null);
                default -> throw new IllegalStateException();
            }
        }
        streamClass.setPrimFieldValues(obj, bytes);
        streamClass.setObjFieldValues(obj, objs);
    }

    private void defaultWriteObject(Object obj, ObjectOutputStream oos) throws IOException {
        ObjectOutputStream.PutField putField = oos.putFields();
        byte[] bytes = new byte[streamClass.getPrimDataSize()];
        Object[] objs = new Object[streamClass.getNumObjFields()];
        streamClass.getPrimFieldValues(obj, bytes);
        streamClass.getObjFieldValues(obj, objs);
        for (ObjectStreamField field : streamClass.getFields(false)) {
            int offset = field.getOffset();
            String fieldName = field.getName();
            switch (field.getTypeCode()) {
                case 'B' -> putField.put(fieldName, bytes[offset]);
                case 'C' -> putField.put(fieldName, ByteArray.getChar(bytes, offset));
                case 'D' -> putField.put(fieldName, ByteArray.getDouble(bytes, offset));
                case 'F' -> putField.put(fieldName, ByteArray.getFloat(bytes, offset));
                case 'I' -> putField.put(fieldName, ByteArray.getInt(bytes, offset));
                case 'J' -> putField.put(fieldName, ByteArray.getLong(bytes, offset));
                case 'S' -> putField.put(fieldName, ByteArray.getShort(bytes, offset));
                case 'Z' -> putField.put(fieldName, ByteArray.getBoolean(bytes, offset));
                case '[', 'L' -> putField.put(fieldName, objs[offset]);
                default -> throw new IllegalStateException();
            }
        }
        oos.writeFields();
    }

    static final class Access implements JavaObjectStreamDefaultSupportAccess {
        static {
            SharedSecrets.setJavaObjectStreamDefaultSupportAccess(new Access());
        }

        public MethodHandle defaultReadObject(Class<?> clazz) {
            ObjectStreamDefaultSupport osds = cache.get(clazz);
            return osds == null ? null : osds.boundDefaultReadObject();
        }

        public MethodHandle defaultWriteObject(Class<?> clazz) {
            ObjectStreamDefaultSupport osds = cache.get(clazz);
            return osds == null ? null : osds.boundDefaultWriteObject();
        }
    }
}
