---
# Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

include-before: '[CONTENTS](index.html) | [PREV](serial-arch.html) | [NEXT](input.html)'
include-after: '[CONTENTS](index.html) | [PREV](serial-arch.html) | [NEXT](input.html)'

title: 'Java Object Serialization Specification: 2 - Object Output Classes'
---

-   [The ObjectOutputStream Class](#the-objectoutputstream-class)
-   [The ObjectOutputStream.PutField
    Class](#the-objectoutputstream.putfield-class)
-   [The writeObject Method](#the-writeobject-method)
-   [The writeExternal Method](#the-writeexternal-method)
-   [The writeReplace Method](#the-writereplace-method)
-   [The useProtocolVersion Method](#the-useprotocolversion-method)

-------------------------------------------------------------------------------

## 2.1 The ObjectOutputStream Class

Class `ObjectOutputStream` implements object serialization. It maintains the
state of the stream including the set of objects already serialized. Its
methods control the traversal of objects to be serialized to save the specified
objects and the objects to which they refer.

```
package java.io;

public class ObjectOutputStream
    extends OutputStream
    implements ObjectOutput, ObjectStreamConstants
{
    public ObjectOutputStream(OutputStream out)
        throws IOException;

    public final void writeObject(Object obj)
        throws IOException;

    public void writeUnshared(Object obj)
        throws IOException;

    public void defaultWriteObject()
        throws IOException, NotActiveException;

    public PutField putFields()
        throws IOException;

    public writeFields()
        throws IOException;

    public void reset() throws IOException;

    protected void annotateClass(Class cl) throws IOException;

    protected void writeClassDescriptor(ObjectStreamClass desc)
        throws IOException;

    protected Object replaceObject(Object obj) throws IOException;

    protected boolean enableReplaceObject(boolean enable)
        throws SecurityException;

    protected void writeStreamHeader() throws IOException;

    public void write(int data) throws IOException;

    public void write(byte b[]) throws IOException;

    public void write(byte b[], int off, int len) throws IOException;

    public void flush() throws IOException;

    protected void drain() throws IOException;

    public void close() throws IOException;

    public void writeBoolean(boolean data) throws IOException;

    public void writeByte(int data) throws IOException;

    public void writeShort(int data) throws IOException;

    public void writeChar(int data) throws IOException;

    public void writeInt(int data) throws IOException;

    public void writeLong(long data) throws IOException;

    public void writeFloat(float data) throws IOException;

    public void writeDouble(double data) throws IOException;

    public void writeBytes(String data) throws IOException;

    public void writeChars(String data) throws IOException;

    public void writeUTF(String data) throws IOException;

    // Inner class to provide access to serializable fields.
    abstract static public class PutField
    {
        public void put(String name, boolean value)
            throws IOException, IllegalArgumentException;

        public void put(String name, char data)
            throws IOException, IllegalArgumentException;

        public void put(String name, byte data)
            throws IOException, IllegalArgumentException;

        public void put(String name, short data)
            throws IOException, IllegalArgumentException;

        public void put(String name, int data)
            throws IOException, IllegalArgumentException;

        public void put(String name, long data)
            throws IOException, IllegalArgumentException;

        public void put(String name, float data)
            throws IOException, IllegalArgumentException;

        public void put(String name, double data)
            throws IOException, IllegalArgumentException;

        public void put(String name, Object data)
            throws IOException, IllegalArgumentException;
    }

    public void useProtocolVersion(int version) throws IOException;

    protected ObjectOutputStream()
        throws IOException;

     protected writeObjectOverride()
        throws NotActiveException, IOException;
}
```

The single-argument `ObjectOutputStream` constructor creates an
`ObjectOutputStream` that serializes objects to the given `OutputStream`. The
constructor calls `writeStreamHeader` to write a magic number and version to
the stream that will be read and verified by a corresponding call to
`readStreamHeader` in the single-argument `ObjectInputStream` constructor. If a
security manager is installed, this constructor checks for the
`"enableSubclassImplementation"` `SerializablePermission` when invoked directly
or indirectly by the constructor of a subclass which overrides the `putFields`
and/or `writeUnshared` methods.

The `writeObject` method is used to serialize an object to the stream. An
object is serialized as follows:

1.  If a subclass is overriding the implementation, call the
    `writeObjectOverride` method and return. Overriding the implementation is
    described at the end of this section.

2.  If there is data in the block-data buffer, the data is written to the
    stream and the buffer is reset.

3.  If the object is null, null is put in the stream and `writeObject` returns.

4.  If the object has been previously replaced, as described in Step 8, write
    the handle of the replacement to the stream and `writeObject` returns.

5.  If the object has already been written to the stream, its handle is written
    to the stream and `writeObject` returns.

6.  If the object is a `Class`, the corresponding `ObjectStreamClass` is
    written to the stream, a handle is assigned for the class, and
    `writeObject` returns.

7.  If the object is an `ObjectStreamClass`, a handle is assigned to the
    object, after which it is written to the stream using one of the class
    descriptor formats described in [Section 4.3, "Serialized
    Form"](class.html#serialized-form). In versions 1.3 and later of the Java 2
    SDK, Standard Edition, the `writeClassDescriptor` method is called to
    output the `ObjectStreamClass` if it represents a class that is not a
    dynamic proxy class, as determined by passing the associated `Class` object
    to the `isProxyClass` method of `java.lang.reflect.Proxy`. Afterwards, an
    annotation for the represented class is written: if the class is a dynamic
    proxy class, then the `annotateProxyClass` method is called; otherwise, the
    `annotateClass` method is called. The `writeObject` method then returns.

8.  Process potential substitutions by the class of the object and/or by a
    subclass of `ObjectInputStream`.

    a.  If the class of an object is not an enum type and defines the
        appropriate `writeReplace` method, the method is called. Optionally, it
        can return a substitute object to be serialized.

    b.  Then, if enabled by calling the `enableReplaceObject` method, the
        `replaceObject` method is called to allow subclasses of
        `ObjectOutputStream` to substitute for the object being serialized. If
        the original object was replaced in the previous step, the
        `replaceObject` method is called with the replacement object.

    If the original object was replaced by either one or both steps above, the
    mapping from the original object to the replacement is recorded for later
    use in Step 4. Then, Steps 3 through 7 are repeated on the new object. 

    If the replacement object is not one of the types covered by Steps 3
    through 7, processing resumes using the replacement object at Step 10.

9.  If the object is a `java.lang.String,` the string is written as length
    information followed by the contents of the string encoded in modified
    UTF-8. For details, refer to [Section 6.2, "Stream
    Elements"](protocol.html#stream-elements). A handle is assigned to the
    string, and `writeObject` returns.

10. If the object is an array, `writeObject` is called recursively to write the
    `ObjectStreamClass` of the array. The handle for the array is assigned. It
    is followed by the length of the array. Each element of the array is then
    written to the stream, after which `writeObject` returns.

11. If the object is an enum constant, the `ObjectStreamClass` for the enum
    type of the constant is written by recursively calling `writeObject`. It
    will appear in the stream only the first time it is referenced. A handle is
    assigned for the enum constant. Next, the value returned by the `name`
    method of the enum constant is written as a `String` object, as described
    in step 9. Note that if the same name string has appeared previously in the
    stream, a back reference to it will be written. The `writeObject` method
    then returns.

12. For regular objects, the `ObjectStreamClass` for the class of the object is
    written by recursively calling `writeObject`. It will appear in the stream
    only the first time it is referenced. A handle is assigned for the object.

13. The contents of the object are written to the stream.

    a.  If the object is serializable, the highest serializable class is
        located. For that class, and each derived class, that class's fields
        are written. If the class does not have a `writeObject` method, the
        `defaultWriteObject` method is called to write the serializable fields
        to the stream. If the class does have a `writeObject` method, it is
        called. It may call `defaultWriteObject` or `putFields` and
        `writeFields` to save the state of the object, and then it can write
        other information to the stream.

    b.  If the object is externalizable, the `writeExternal` method of the
        object is called.

    c.  If the object is neither serializable or externalizable, the
        `NotSerializableException` is thrown.

Exceptions may occur during the traversal or may occur in the underlying
stream. For any subclass of `IOException`, the exception is written to the
stream using the exception protocol and the stream state is discarded. If a
second `IOException` is thrown while attempting to write the first exception
into the stream, the stream is left in an unknown state and
`StreamCorruptedException` is thrown from `writeObject`. For other exceptions,
the stream is aborted and left in an unknown and unusable state.

The `writeUnshared` method writes an "unshared" object to the
`ObjectOutputStream`. This method is identical to `writeObject`, except that it
always writes the given object as a new, unique object in the stream (as
opposed to a back-reference pointing to a previously serialized instance).
Specifically:

-   An object written via `writeUnshared` is always serialized in the same
    manner as a newly appearing object (an object that has not been written to
    the stream yet), regardless of whether or not the object has been written
    previously.

-   If `writeObject` is used to write an object that has been previously
    written with `writeUnshared`, the previous `writeUnshared` operation is
    treated as if it were a write of a separate object. In other words,
    `ObjectOutputStream` will never generate back-references to object data
    written by calls to `writeUnshared`.

While writing an object via `writeUnshared` does not in itself guarantee a
unique reference to the object when it is deserialized, it allows a single
object to be defined multiple times in a stream, so that multiple calls to the
`ObjectInputStream.readUnshared` method (see [Section 3.1, "The
ObjectInputStream Class"](input.html#the-objectinputstream-class)) by the
receiver will not conflict. Note that the rules described above only apply to
the base-level object written with `writeUnshared`, and not to any transitively
referenced sub-objects in the object graph to be serialized.

The `defaultWriteObject` method implements the default serialization mechanism
for the current class. This method may be called only from a class's
`writeObject` method. The method writes all of the serializable fields of the
current class to the stream. If called from outside the `writeObject` method,
the `NotActiveException` is thrown.

The `putFields` method returns a `PutField` object the caller uses to set the
values of the serializable fields in the stream. The fields may be set in any
order. After all of the fields have been set, `writeFields` must be called to
write the field values in the canonical order to the stream. If a field is not
set, the default value appropriate for its type will be written to the stream.
This method may only be called from within the `writeObject` method of a
serializable class. It may not be called more than once or if
`defaultWriteObject` has been called. Only after `writeFields` has been called
can other data be written to the stream.

The `reset` method resets the stream state to be the same as if it had just
been constructed. `Reset` will discard the state of any objects already written
to the stream. The current point in the stream is marked as reset, so the
corresponding `ObjectInputStream` will reset at the same point. Objects
previously written to the stream will not be remembered as already having been
written to the stream. They will be written to the stream again. This is useful
when the contents of an object or objects must be sent again. `Reset` may not
be called while objects are being serialized. If called inappropriately, an
`IOException` is thrown.

Starting with the Java 2 SDK, Standard Edition, v1.3, the
`writeClassDescriptor` method is called when an `ObjectStreamClass` needs to be
serialized. `writeClassDescriptor` is responsible for writing a representation
of the `ObjectStreamClass` to the serialization stream. Subclasses may override
this method to customize the way in which class descriptors are written to the
serialization stream. If this method is overridden, then the corresponding
`readClassDescriptor` method in `ObjectInputStream` should also be overridden
to reconstitute the class descriptor from its custom stream representation. By
default, `writeClassDescriptor` writes class descriptors according to the
format specified in [Section 6.4, "Grammar for the Stream
Format"](protocol.html#grammar-for-the-stream-format). Note that this method
will only be called if the `ObjectOutputStream` is not using the old
serialization stream format (see [Section 6.3, "Stream Protocol
Versions"](protocol.html#stream-protocol-versions)). If the serialization
stream is using the old format (`ObjectStreamConstants.PROTOCOL_VERSION_1`),
the class descriptor will be written internally in a manner that cannot be
overridden or customized.

The `annotateClass` method is called while a `Class` is being serialized, and
after the class descriptor has been written to the stream. Subclasses may
extend this method and write other information to the stream about the class.
This information must be read by the `resolveClass` method in a corresponding
`ObjectInputStream` subclass.

An `ObjectOutputStream` subclass can implement the `replaceObject` method to
monitor or replace objects during serialization. Replacing objects must be
enabled explicitly by calling `enableReplaceObject` before calling
`writeObject` with the first object to be replaced. Once enabled,
`replaceObject` is called for each object just prior to serializing the object
for the first time. Note that the `replaceObject` method is not called for
objects of the specially handled classes, `Class` and `ObjectStreamClass`. An
implementation of a subclass may return a substitute object that will be
serialized instead of the original. The substitute object must be serializable.
All references in the stream to the original object will be replaced by the
substitute object.

When objects are being replaced, the subclass must ensure that the substituted
object is compatible with every field where the reference will be stored, or
that a complementary substitution will be made during deserialization. Objects,
whose type is not a subclass of the type of the field or array element, will
later abort the deserialization by raising a `ClassCastException` and the
reference will not be stored.

The `enableReplaceObject` method can be called by trusted subclasses of
`ObjectOutputStream` to enable the substitution of one object for another
during serialization. Replacing objects is disabled until `enableReplaceObject`
is called with a `true` value. It may thereafter be disabled by setting it to
`false`. The previous setting is returned. The `enableReplaceObject` method
checks that the stream requesting the replacement can be trusted. To ensure
that the private state of objects is not unintentionally exposed, only trusted
stream subclasses may use `replaceObject`. Trusted classes are those classes
that belong to a security protection domain with permission to enable
Serializable substitution.

If the subclass of `ObjectOutputStream` is not considered part of the system
domain, `SerializablePermission "enableSubstitution"` must be added to the
security policy file. `AccessControlException` is thrown if the protection
domain of the subclass of `ObjectInputStream` does not have permission to
`"enableSubstitution"` by calling `enableReplaceObject`. See the document Java
Security Architecture (JDK1.2) for additional information about the security
model.

The `writeStreamHeader` method writes the magic number and version to the
stream. This information must be read by the `readStreamHeader` method of
`ObjectInputStream`. Subclasses may need to implement this method to identify
the stream's unique format.

The `flush` method is used to empty any buffers being held by the stream and to
forward the flush to the underlying stream. The `drain` method may be used by
subclassers to empty only the `ObjectOutputStream`'s buffers without forcing
the underlying stream to be flushed.

All of the write methods for primitive types encode their values using a
`DataOutputStream` to put them in the standard stream format. The bytes are
buffered into block data records so they can be distinguished from the encoding
of objects. This buffering allows primitive data to be skipped if necessary for
class versioning. It also allows the stream to be parsed without invoking
class-specific methods.

To override the implementation of serialization, the subclass of
`ObjectOutputStream` should call the protected no-arg `ObjectOutputStream`,
constructor. There is a security check within the no-arg constructor for
`SerializablePermission "enableSubclassImplementation"` to ensure that only
trusted classes are allowed to override the default implementation. This
constructor does not allocate any private data for `ObjectOutputStream` and
sets a flag that indicates that the final `writeObject` method should invoke
the `writeObjectOverride` method and return. All other `ObjectOutputStream`
methods are not final and can be directly overridden by the subclass.

## 2.2 The ObjectOutputStream.PutField Class

Class `PutField` provides the API for setting values of the serializable fields
for a class when the class does not use default serialization. Each method puts
the specified named value into the stream. An `IllegalArgumentException` is
thrown if `name` does not match the name of a serializable field for the class
whose fields are being written, or if the type of the named field does not
match the second parameter type of the specific `put` method invoked.

## 2.3 The writeObject Method

For serializable objects, the `writeObject` method allows a class to control
the serialization of its own fields. Here is its signature:

```
private void writeObject(ObjectOutputStream stream)
    throws IOException;
```

Each subclass of a serializable object may define its own `writeObject` method.
If a class does not implement the method, the default serialization provided by
`defaultWriteObject` will be used. When implemented, the class is only
responsible for writing its own fields, not those of its supertypes or
subtypes.

The class's `writeObject` method, if implemented, is responsible for saving the
state of the class. Either `ObjectOutputStream`'s `defaultWriteObject` or
`writeFields` method must be called once (and only once) before writing any
optional data that will be needed by the corresponding `readObject` method to
restore the state of the object; even if no optional data is written,
`defaultWriteObject` or `writeFields` must still be invoked once. If
`defaultWriteObject` or `writeFields` is not invoked once prior to the writing
of optional data (if any), then the behavior of instance deserialization is
undefined in cases where the `ObjectInputStream` cannot resolve the class which
defined the `writeObject` method in question.

The responsibility for the format, structure, and versioning of the optional
data lies completely with the class.

## 2.4 The writeExternal Method

Objects implementing `java.io.Externalizable` must implement the
`writeExternal` method to save the entire state of the object. It must
coordinate with its superclasses to save their state. All of the methods of
`ObjectOutput` are available to save the object's primitive typed fields and
object fields.

```
public void writeExternal(ObjectOutput stream)
    throws IOException;
```

A new default format for writing Externalizable data has been introduced in JDK
1.2. The new format specifies that primitive data will be written in block data
mode by `writeExternal` methods. Additionally, a tag denoting the end of the
External object is appended to the stream after the `writeExternal` method
returns. The benefits of this format change are discussed in [Section 3.6, "The
readExternal Method"](input.html#the-readexternal-method). Compatibility issues
caused by this change are discussed in [Section 2.6, "The useProtocolVersion
Method"](#the-useprotocolversion-method).

## 2.5 The writeReplace Method

For Serializable and Externalizable classes, the `writeReplace` method allows a
class of an object to nominate its own replacement in the stream before the
object is written. By implementing the `writeReplace` method, a class can
directly control the types and instances of its own instances being serialized.

The method is defined as follows:

```
ANY-ACCESS-MODIFIER Object writeReplace()
             throws ObjectStreamException;
```

The `writeReplace` method is called when `ObjectOutputStream` is preparing to
write the object to the stream. The `ObjectOutputStream` checks whether the
class defines the `writeReplace` method. If the method is defined, the
`writeReplace` method is called to allow the object to designate its
replacement in the stream. The object returned should be either of the same
type as the object passed in or an object that when read and resolved will
result in an object of a type that is compatible with all references to the
object. If it is not, a `ClassCastException` will occur when the type mismatch
is discovered.

## 2.6 The useProtocolVersion Method

Due to a stream protocol change that was not backwards compatible, a mechanism
has been added to enable the current Virtual Machine to write a serialization
stream that is readable by a previous release. Of course, the problems that are
corrected by the new stream format will exist when using the backwards
compatible protocol.

Stream protocol versions are discussed in [Section 6.3, "Stream Protocol
Versions"](protocol.html#stream-protocol-versions).

-------------------------------------------------------------------------------

*[Copyright](../../../legal/SMICopyright.html) &copy; 2005, 2017, Oracle
and/or its affiliates. All rights reserved.*
