/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.consumer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jdk.jfr.Configuration;
import jdk.jfr.EventType;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.consumer.JdkJfrConsumer;
import jdk.jfr.internal.consumer.ObjectContext;
import jdk.jfr.internal.consumer.ObjectFactory;
import jdk.jfr.internal.tool.PrettyWriter;

/**
 * A complex data type that consists of one or more fields.
 * <p>
 * This class provides methods to select and query nested objects by passing a
 * dot {@code "."} delimited {@code String} object (for instance,
 * {@code "aaa.bbb"}). A method evaluates a nested object from left to right,
 * and if a part is {@code null}, it throws {@code NullPointerException}.
 *
 * @since 9
 */
public sealed class RecordedObject
   permits RecordedEvent, RecordedClassLoader, RecordedClass, RecordedMethod,
           RecordedStackTrace, RecordedFrame, RecordedThread, RecordedThreadGroup {
    static{
        JdkJfrConsumer access = new JdkJfrConsumer() {
            @Override
            public List<Type> readTypes(RecordingFile file) throws IOException {
                return file.readTypes();
            }

            @Override
            public boolean isLastEventInChunk(RecordingFile file) {
                return file.isLastEventInChunk();
            }

            @Override
            public Object getOffsetDataTime(RecordedObject event, String name) {
                return event.getOffsetDateTime(name);
            }

            @Override
            public RecordedClass newRecordedClass(ObjectContext objectContext, long id, Object[] values) {
                return new RecordedClass(objectContext, id, values);
            }

            @Override
            public RecordedClassLoader newRecordedClassLoader(ObjectContext objectContext, long id, Object[] values) {
                return new RecordedClassLoader(objectContext, id, values);
            }

            @Override
            public Comparator<? super RecordedEvent> eventComparator() {
                return new Comparator<RecordedEvent>()  {
                    @Override
                    public int compare(RecordedEvent e1, RecordedEvent e2) {
                        return Long.compare(e1.endTimeTicks, e2.endTimeTicks);
                    }
                };
            }

            @Override
            public RecordedStackTrace newRecordedStackTrace(ObjectContext objectContext, Object[] values) {
                return new RecordedStackTrace(objectContext, values);
            }

            @Override
            public RecordedThreadGroup newRecordedThreadGroup(ObjectContext objectContext, Object[] values) {
                return new RecordedThreadGroup(objectContext, values);
            }

            @Override
            public RecordedFrame newRecordedFrame(ObjectContext objectContext, Object[] values) {
                return new RecordedFrame(objectContext, values);
            }

            @Override
            public RecordedThread newRecordedThread(ObjectContext objectContext, long id, Object[] values) {
                return new RecordedThread(objectContext, id, values);
            }

            @Override
            public RecordedMethod newRecordedMethod(ObjectContext objectContext, Object[] values) {
                return new RecordedMethod(objectContext, values);
            }

            @Override
            public RecordedEvent newRecordedEvent(ObjectContext objectContext, Object[] values, long startTimeTicks, long endTimeTicks) {
                return new RecordedEvent(objectContext, values, startTimeTicks, endTimeTicks);
            }

            @Override
            public void setStartTicks(RecordedEvent event, long startTicks) {
               event.startTimeTicks = startTicks;
            }

            @Override
            public void setEndTicks(RecordedEvent event, long endTicks) {
               event.endTimeTicks = endTicks;
            }

            @Override
            public Object[] eventValues(RecordedEvent event) {
                return event.objects;
            }

            @Override
            public MetadataEvent newMetadataEvent(List<EventType> previous, List<EventType> current,
                    List<Configuration> configurations) {
                return new MetadataEvent(previous, current, configurations);
            }
        };
        JdkJfrConsumer.setAccess(access);
    }

    private static final record UnsignedValue(Object value) {
    }
    static final Integer INTEGER_MINUS_ONE = Integer.valueOf(-1);
    static final Integer INTEGER_ZERO = Integer.valueOf(0);
    static final Long LONG_MINUS_ONE = Long.valueOf(-1L);

    final Object[] objects;
    final ObjectContext objectContext;

    // package private, not to be subclassed outside this package
    RecordedObject(ObjectContext objectContext, Object[] objects) {
        this.objectContext = objectContext;
        this.objects = objects;
    }

    // package private
    final <T> T getTyped(String name, Class<T> clazz, T defaultValue) {
        // Unnecessary to check field presence twice, but this
        // will do for now.
        if (!hasField(name)) {
            return defaultValue;
        }
        T object = getValue(name);
        if (object == null || object.getClass().isAssignableFrom(clazz)) {
            return object;
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns {@code true} if a field with the given name exists, {@code false}
     * otherwise.
     * <p>
     * It's possible to index into a nested field by using {@code "."} (for
     * instance {@code "thread.group.parent.name}").
     *
     * @param name name of the field to get, not {@code null}
     *
     * @return {@code true} if the field exists, {@code false} otherwise
     *
     * @see #getFields()
     */
    public boolean hasField(String name) {
        Objects.requireNonNull(name, "name");
        for (ValueDescriptor v : objectContext.fields) {
            if (v.getName().equals(name)) {
                return true;
            }
        }
        int dotIndex = name.indexOf(".");
        if (dotIndex > 0) {
            String structName = name.substring(0, dotIndex);
            for (ValueDescriptor v : objectContext.fields) {
                if (!v.getFields().isEmpty() && v.getName().equals(structName)) {
                    RecordedObject child = getValue(structName);
                    if (child != null) {
                        return child.hasField(name.substring(dotIndex + 1));
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the value of the field with the given name.
     * <p>
     * The return type may be a primitive type or a subclass of
     * {@link RecordedObject}.
     * <p>
     * It's possible to index into a nested object by using {@code "."} (for
     * instance {@code "thread.group.parent.name}").
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     * <p>
     * Example
     * {@snippet class="Snippets" region="RecordedObjectGetValue"}
     *
     * @param <T> the return type
     * @param  name of the field to get, not {@code null}
     * @throws IllegalArgumentException if no field called {@code name} exists
     *
     * @return the value, can be {@code null}
     *
     * @see #hasField(String)
     *
     */
    public final <T> T getValue(String name) {
        @SuppressWarnings("unchecked")
        T t = (T) getValue(name, false);
        return t;
    }

    Object objectAt(int index) {
        return objects[index];
    }

    private Object getValue(String name, boolean allowUnsigned) {
        Objects.requireNonNull(name, "name");
        int index = 0;
        for (ValueDescriptor v : objectContext.fields) {
            if (name.equals(v.getName())) {
                Object object = objectAt(index);
                if (object == null) {
                    // error or missing
                    return null;
                }
                if (v.getFields().isEmpty()) {
                    if (allowUnsigned && PrivateAccess.getInstance().isUnsigned(v)) {
                        // Types that are meaningless to widen
                        if (object instanceof Character || object instanceof Long) {
                            return object;
                        }
                        return new UnsignedValue(object);
                    }
                    return object; // primitives and primitive arrays
                } else {
                    if (object instanceof RecordedObject) {
                        // known types from factory
                        return object;
                    }
                    // must be array type
                    Object[] array = (Object[]) object;
                    if (v.isArray()) {
                        // struct array
                        return structifyArray(v, array, 0);
                    }
                    // struct
                    return new RecordedObject(objectContext.getInstance(v), (Object[]) object);
                }
            }
            index++;
        }

        int dotIndex = name.indexOf(".");
        if (dotIndex > 0) {
            String structName = name.substring(0, dotIndex);
            for (ValueDescriptor v : objectContext.fields) {
                if (!v.getFields().isEmpty() && v.getName().equals(structName)) {
                    RecordedObject child = getValue(structName);
                    String subName = name.substring(dotIndex + 1);
                    if (child != null) {
                        return child.getValue(subName, allowUnsigned);
                    } else {
                        // Call getValueDescriptor to trigger IllegalArgumentException if the name is
                        // incorrect. Type can't be validate due to type erasure
                        getValueDescriptor(v.getFields(), subName, null);
                        throw new NullPointerException("Field value for \"" + structName + "\" was null. Can't access nested field \"" + subName + "\"");
                    }
                }
            }
        }
        throw new IllegalArgumentException("Could not find field with name " + name);
    }

    // Returns the leaf value descriptor matches both name or value, or throws an
    // IllegalArgumentException
    private ValueDescriptor getValueDescriptor(List<ValueDescriptor> descriptors, String name, String leafType) {
        int dotIndex = name.indexOf(".");
        if (dotIndex > 0) {
            String first = name.substring(0, dotIndex);
            String second = name.substring(dotIndex + 1);
            for (ValueDescriptor v : descriptors) {
                if (v.getName().equals(first)) {
                    List<ValueDescriptor> fields = v.getFields();
                    if (!fields.isEmpty()) {
                        return getValueDescriptor(v.getFields(), second, leafType);
                    }
                }
            }
            throw new IllegalArgumentException("Attempt to get unknown field \"" + first + "\"");
        }
        for (ValueDescriptor v : descriptors) {
            if (v.getName().equals(name)) {
                if (leafType != null && !v.getTypeName().equals(leafType)) {
                    throw new IllegalArgumentException("Attempt to get " + v.getTypeName() + " field \"" + name + "\" with illegal data type conversion " + leafType);
                }
                return v;
            }
        }
        throw new IllegalArgumentException("Attempt to get unknown field \"" + name + "\"");
    }

    // Gets a value, but checks that type and name is correct first
    // This is to prevent a call to getString on a thread field, that is
    // null to succeed.
    private <T> T getTypedValue(String name, String typeName) {
        Objects.requireNonNull(name, "name");
        // Validate name and type first
        getValueDescriptor(objectContext.fields, name, typeName);
        return getValue(name);
    }

    private Object[] structifyArray(ValueDescriptor v, Object[] array, int dimension) {
        if (array == null) {
            return null;
        }
        Object[] structArray = new Object[array.length];
        ObjectContext objContext = objectContext.getInstance(v);
        for (int i = 0; i < structArray.length; i++) {
            Object arrayElement = array[i];
            if (dimension == 0) {
                // No general way to handle structarrays
                // without invoking ObjectFactory for every instance (which may require id)
                if (isStackFrameType(v.getTypeName())) {
                    structArray[i] = new RecordedFrame(objContext, (Object[]) arrayElement);
                } else {
                    structArray[i] = new RecordedObject(objContext, (Object[]) arrayElement);
                }
            } else {
                structArray[i] = structifyArray(v, (Object[]) arrayElement, dimension - 1);
            }
        }
        return structArray;
    }

    private boolean isStackFrameType(String typeName) {
        if (ObjectFactory.STACK_FRAME_VERSION_1.equals(typeName)) {
            return true;
        }
        if (ObjectFactory.STACK_FRAME_VERSION_2.equals(typeName)) {
            return true;
        }
        return false;
    }

    /**
     * Returns an immutable list of the fields for this object.
     *
     * @return the fields, not {@code null}
     */
    public List<ValueDescriptor> getFields() {
        return objectContext.fields;
    }

    /**
     * Returns the value of a field of type {@code boolean}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name name of the field to get, not {@code null}
     *
     * @return the value of the field, {@code true} or {@code false}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field is
     *         not of type {@code boolean}
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final boolean getBoolean(String name) {
        Object o = getValue(name);
        if (o instanceof Boolean b) {
            return b;
        }
        throw newIllegalArgumentException(name, "boolean");
    }

    /**
     * Returns the value of a field of type {@code byte}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "foo.bar"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field is
     *         not of type {@code byte}
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final byte getByte(String name) {
        Object o = getValue(name);
        if (o instanceof Byte b) {
            return b;
        }
        throw newIllegalArgumentException(name, "byte");
    }

    /**
     * Returns the value of a field of type {@code char}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field as a {@code char}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field is
     *         not of type {@code char}
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final char getChar(String name) {
        Object o = getValue(name);
        if (o instanceof Character c) {
            return c;
        }
        throw newIllegalArgumentException(name, "char");
    }

    /**
     * Returns the value of a field of type {@code short} or of another primitive
     * type convertible to type {@code short} by a widening conversion.
     * <p>
     * This method can be used on the following types: {@code short} and {@code byte}.
     * <p>
     * If the field has the {@code @Unsigned} annotation and is of a narrower type
     * than {@code short}, then the value is returned as an unsigned.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field converted to type {@code short}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         value can't be converted to the type {@code short} by a widening
     *         conversion
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final short getShort(String name) {
        return switch (getValue(name, true)) {
            case Short s -> s;
            case Byte b -> b;
            case UnsignedValue(Short s) -> s;
            case UnsignedValue(Byte b) -> (short) Byte.toUnsignedInt(b);
            case null, default -> throw newIllegalArgumentException(name, "short");
        };
    }

    /**
     * Returns the value of a field of type {@code int} or of another primitive type
     * that is convertible to type {@code int} by a widening conversion.
     * <p>
     * This method can be used on fields of the following types: {@code int},
     * {@code short}, {@code char}, and {@code byte}.
     * <p>
     * If the field has the {@code @Unsigned} annotation and is of a narrower type
     * than {@code int}, then the value will be returned as an unsigned.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field converted to type {@code int}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         value can't be converted to the type {@code int} by a widening
     *         conversion
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final int getInt(String name) {
        return switch(getValue(name, true)) {
            case Integer i -> i;
            case Short s -> s;
            case Character c -> c;
            case Byte b -> b;
            case UnsignedValue(Integer i) -> i;
            case UnsignedValue(Short s) -> Short.toUnsignedInt(s);
            case UnsignedValue(Byte b) -> Byte.toUnsignedInt(b);
            case null, default -> throw newIllegalArgumentException(name, "int");
        };
    }

    /**
     * Returns the value of a field of type {@code float} or of another primitive
     * type convertible to type {@code float} by a widening conversion.
     * <p>
     * This method can be used on fields of the following types: {@code float},
     * {@code long}, {@code int}, {@code short}, {@code char}, and {@code byte}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field converted to type {@code float}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         value can't be converted to the type {@code float} by a widening
     *         conversion
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final float getFloat(String name) {
        return switch(getValue(name)) {
            case Float f -> f;
            case Long l -> l;
            case Integer i -> i;
            case Short s -> s;
            case Character c -> c;
            case Byte b -> b;
            case null, default -> throw newIllegalArgumentException(name, "float");
        };
    }

    /**
     * Returns the value of a field of type {@code long} or of another primitive
     * type that is convertible to type {@code long} by a widening conversion.
     * <p>
     * This method can be used on fields of the following types: {@code long},
     * {@code int}, {@code short}, {@code char}, and {@code byte}.
     * <p>
     * If the field has the {@code @Unsigned} annotation and is of a narrower type
     * than {@code long}, then the value will be returned as an unsigned.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field converted to type {@code long}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         value can't be converted to the type {@code long} via a widening
     *         conversion
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final long getLong(String name) {
        return switch(getValue(name, true)) {
            case Long l -> l;
            case Integer i -> i;
            case Short s -> s;
            case Character c -> c;
            case Byte b -> b;
            case UnsignedValue(Integer i) -> Integer.toUnsignedLong(i);
            case UnsignedValue(Short s) -> Short.toUnsignedLong(s);
            case UnsignedValue(Byte b) -> Byte.toUnsignedLong(b);
            case null, default -> throw newIllegalArgumentException(name, "long");
        };
    }

    /**
     * Returns the value of a field of type {@code double} or of another primitive
     * type that is convertible to type {@code double} by a widening conversion.
     * <p>
     * This method can be used on fields of the following types: {@code double}, {@code float},
     * {@code long}, {@code int}, {@code short}, {@code char}, and {@code byte}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field converted to type {@code double}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         value can't be converted to the type {@code double} by a widening
     *         conversion
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final double getDouble(String name) {
        return switch(getValue(name)) {
            case Double d -> d;
            case Float f -> f;
            case Long l -> l.doubleValue();
            case Integer i -> i.doubleValue();
            case Short s -> s.doubleValue();
            case Character c -> c;
            case Byte b -> b.doubleValue();
            case null, default -> throw newIllegalArgumentException(name, "double");
        };
    }

    /**
     * Returns the value of a field of type {@code String}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "foo.bar"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field as a {@code String}, can be {@code null}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         isn't of type {@code String}
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final String getString(String name) {
        return getTypedValue(name, "java.lang.String");
    }

    /**
     * Returns the value of a timespan field.
     * <p>
     * This method can be used on fields annotated with {@code @Timespan}, and of
     * the following types: {@code long}, {@code int}, {@code short}, {@code char},
     * and {@code byte}.
     * <p>
     * If the committed event value was {@code Long.MAX_VALUE},
     * regardless of the unit set by {@code @Timespan}, this method returns
     * {@link ChronoUnit#FOREVER}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return a time span represented as a {@code Duration}, not {@code null}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         value can't be converted to a {@code Duration} object
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final Duration getDuration(String name) {
        return switch (getValue(name, true)) {
            case Long l -> getDuration(l, name);
            case Integer i -> getDuration(i,name);
            case Short s -> getDuration(s, name);
            case Character c -> getDuration(c, name);
            case Byte b -> getDuration(b, name);
            case UnsignedValue(Integer i) -> getDuration(Integer.toUnsignedLong(i), name);
            case UnsignedValue(Short s) -> getDuration(Short.toUnsignedLong(s), name);
            case UnsignedValue(Byte b) -> getDuration(Short.toUnsignedLong(b), name);
            case null, default ->  throw newIllegalArgumentException(name, "java.time.Duration");
        };
    }

    private Duration getDuration(long timespan, String name) {
        ValueDescriptor v = getValueDescriptor(objectContext.fields, name, null);
        if (timespan == 0) {
            return Duration.ZERO;
        }
        if (timespan == Long.MIN_VALUE) {
            return Duration.ofSeconds(Long.MIN_VALUE, 0);
        }
        if (timespan == Long.MAX_VALUE) {
            return ChronoUnit.FOREVER.getDuration();
        }
        Timespan ts = v.getAnnotation(Timespan.class);
        if (ts != null) {
            return switch (ts.value()) {
                case Timespan.MICROSECONDS -> Duration.ofNanos(1000 * timespan);
                case Timespan.SECONDS -> Duration.ofSeconds(timespan);
                case Timespan.MILLISECONDS -> Duration.ofMillis(timespan);
                case Timespan.NANOSECONDS -> Duration.ofNanos(timespan);
                case Timespan.TICKS -> Duration.ofNanos(objectContext.convertTimespan(timespan));
                default ->  throw new IllegalArgumentException("Attempt to get " + v.getTypeName() + " field \"" + name + "\" with illegal timespan unit " + ts.value());
            };
        }
        throw new IllegalArgumentException("Attempt to get " + v.getTypeName() + " field \"" + name + "\" with missing @Timespan");
    }

    /**
     * Returns the value of a timestamp field.
     * <p>
     * This method can be used on fields annotated with {@code @Timestamp}, and of
     * the following types: {@code long}, {@code int}, {@code short}, {@code char}
     * and {@code byte}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return a timstamp represented as an {@code Instant}, not {@code null}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         value can't be converted to an {@code Instant} object
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final Instant getInstant(String name) {
        return switch (getValue(name, true)) {
            case Long l -> getInstant(l, name);
            case Integer i -> getInstant(i, name);
            case Short s -> getInstant(s, name);
            case Character c -> getInstant(c, name);
            case Byte b -> getInstant(b, name);
            case UnsignedValue(Integer i) -> getInstant(Integer.toUnsignedLong(i), name);
            case UnsignedValue(Short s) -> getInstant(Short.toUnsignedLong(s), name);
            case UnsignedValue(Byte b) -> getInstant(Short.toUnsignedLong(b), name);
            case null, default -> throw newIllegalArgumentException(name, "java.time.Instant");
        };
    }

    private Instant getInstant(long timestamp, String name) {
        ValueDescriptor v = getValueDescriptor(objectContext.fields, name, null);
        Timestamp ts = v.getAnnotation(Timestamp.class);
        if (ts != null) {
            if (timestamp == Long.MIN_VALUE) {
                return Instant.MIN;
            }
            return switch (ts.value()) {
                case Timestamp.MILLISECONDS_SINCE_EPOCH -> Instant.ofEpochMilli(timestamp);
                case Timestamp.TICKS -> Instant.ofEpochSecond(0, objectContext.convertTimestamp(timestamp));
                default -> throw new IllegalArgumentException("Attempt to get " + v.getTypeName() + " field \"" + name + "\" with illegal timestamp unit " + ts.value());
            };
        }
        throw new IllegalArgumentException("Attempt to get " + v.getTypeName() + " field \"" + name + "\" with missing @Timestamp");
    }

    /**
     * Returns the value of a field of type {@code Class}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "aaa.bbb"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field as a {@code RecordedClass}, can be
     *         {@code null}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         isn't of type {@code Class}
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final RecordedClass getClass(String name) {
        return getTypedValue(name, "java.lang.Class");
    }

    /**
     * Returns the value of a field of type {@code Thread}.
     * <p>
     * It's possible to index into a nested object using {@code "."} (for example,
     * {@code "foo.bar"}).
     * <p>
     * A field might change or be removed in a future JDK release. A best practice
     * for callers of this method is to validate the field before attempting access.
     *
     * @param name of the field to get, not {@code null}
     *
     * @return the value of the field as a {@code RecordedThread} object, can be
     *         {@code null}
     *
     * @throws IllegalArgumentException if the field doesn't exist, or the field
     *         isn't of type {@code Thread}
     *
     * @see #hasField(String)
     * @see #getValue(String)
     */
    public final RecordedThread getThread(String name) {
        return getTypedValue(name, "java.lang.Thread");
    }

    /**
     * Returns a textual representation of this object.
     *
     * @return textual description of this object
     */
    @Override
    public final String toString() {
        StringWriter s = new StringWriter();
        PrettyWriter p = new PrettyWriter(new PrintWriter(s));
        p.setStackDepth(5);
        if (this instanceof RecordedEvent event) {
            p.print(event);
        } else {
            p.print(this, "");
        }
        p.flush(true);
        return s.toString();
    }

    // package private for now. Used by EventWriter
    private OffsetDateTime getOffsetDateTime(String name) {
        Instant instant = getInstant(name);
        if (instant.equals(Instant.MIN)) {
            return OffsetDateTime.MIN;
        }
        return OffsetDateTime.ofInstant(instant, objectContext.getZoneOffset());
    }

    private static IllegalArgumentException newIllegalArgumentException(String name, String typeName) {
        return new IllegalArgumentException("Attempt to get field \"" + name + "\" with illegal data type conversion " + typeName);
    }
}
