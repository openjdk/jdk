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

include-before: '[CONTENTS](index.html) | [PREV](output.html) | [NEXT](class.html)'
include-after: '[CONTENTS](index.html) | [PREV](output.html) | [NEXT](class.html)'

title: 'Java Object Serialization Specification: 3 - Object Input Classes'
---

-   [The ObjectInputStream Class](#the-objectinputstream-class)
-   [The ObjectInputStream.GetField
    Class](#the-objectinputstream.getfield-class)
-   [The ObjectInputValidation Interface](#the-objectinputvalidation-interface)
-   [The readObject Method](#the-readobject-method)
-   [The readExternal Method](#the-readexternal-method)
-   [The readResolve Method](#the-readresolve-method)

-------------------------------------------------------------------------------

## 3.1 The ObjectInputStream Class

Class `ObjectInputStream` implements object deserialization. It maintains the
state of the stream including the set of objects already deserialized. Its
methods allow primitive types and objects to be read from a stream written by
`ObjectOutputStream`. It manages restoration of the object and the objects that
it refers to from the stream.

```
package java.io;

public class ObjectInputStream
    extends InputStream
    implements ObjectInput, ObjectStreamConstants
{
    public ObjectInputStream(InputStream in)
        throws StreamCorruptedException, IOException;

    public final Object readObject()
        throws OptionalDataException, ClassNotFoundException,
            IOException;

    public Object readUnshared()
        throws OptionalDataException, ClassNotFoundException,
            IOException;

    public void defaultReadObject()
        throws IOException, ClassNotFoundException,
            NotActiveException;

    public GetField readFields()
        throws IOException;

    public synchronized void registerValidation(
        ObjectInputValidation obj, int prio)
        throws NotActiveException, InvalidObjectException;

    protected ObjectStreamClass readClassDescriptor()
        throws IOException, ClassNotFoundException;

    protected Class resolveClass(ObjectStreamClass v)
        throws IOException, ClassNotFoundException;

    protected Object resolveObject(Object obj)
        throws IOException;

    protected boolean enableResolveObject(boolean enable)
        throws SecurityException;

    protected void readStreamHeader()
        throws IOException, StreamCorruptedException;

    public int read() throws IOException;

    public int read(byte[] data, int offset, int length)
        throws IOException

    public int available() throws IOException;

    public void close() throws IOException;

    public boolean readBoolean() throws IOException;

    public byte readByte() throws IOException;

    public int readUnsignedByte() throws IOException;

    public short readShort() throws IOException;

    public int readUnsignedShort() throws IOException;

    public char readChar() throws IOException;

    public int readInt() throws IOException;

    public long readLong() throws IOException;

    public float readFloat() throws IOException;

    public double readDouble() throws IOException;

    public void readFully(byte[] data) throws IOException;

    public void readFully(byte[] data, int offset, int size)
        throws IOException;

    public int skipBytes(int len) throws IOException;

    public String readLine() throws IOException;

    public String readUTF() throws IOException;

    // Class to provide access to serializable fields.
    static abstract public class GetField
    {
        public ObjectStreamClass getObjectStreamClass();

        public boolean defaulted(String name)
            throws IOException, IllegalArgumentException;

        public char get(String name, char default)
            throws IOException, IllegalArgumentException;

        public boolean get(String name, boolean default)
            throws IOException, IllegalArgumentException;

        public byte get(String name, byte default)
            throws IOException, IllegalArgumentException;

        public short get(String name, short default)
            throws IOException, IllegalArgumentException;

        public int get(String name, int default)
            throws IOException, IllegalArgumentException;

        public long get(String name, long default)
            throws IOException, IllegalArgumentException;

        public float get(String name, float default)
            throws IOException, IllegalArgumentException;

        public double get(String name, double default)
            throws IOException, IllegalArgumentException;

        public Object get(String name, Object default)
            throws IOException, IllegalArgumentException;
    }

    protected ObjectInputStream()
        throws StreamCorruptedException, IOException;

    protected readObjectOverride()
        throws OptionalDataException, ClassNotFoundException,
            IOException;
}
```

The single-argument `ObjectInputStream` constructor requires an `InputStream`.
The constructor calls `readStreamHeader` to read and verifies the header and
version written by the corresponding `ObjectOutputStream.writeStreamHeader`
method. If a security manager is installed, this constructor checks for the
`"enableSubclassImplementation"` `SerializablePermission` when invoked directly
or indirectly by the constructor of a subclass which overrides the `readFields`
and/or `readUnshared` methods.

**Note:** The `ObjectInputStream` constructor blocks until it completes reading
the serialization stream header. Code which waits for an `ObjectInputStream` to
be constructed before creating the corresponding `ObjectOutputStream` for that
stream will deadlock, since the `ObjectInputStream` constructor will block
until a header is written to the stream, and the header will not be written to
the stream until the `ObjectOutputStream` constructor executes. This problem
can be resolved by creating the `ObjectOutputStream` before the
`ObjectInputStream`, or otherwise removing the timing dependency between
completion of `ObjectInputStream` construction and the creation of the
`ObjectOutputStream`.

The `readObject` method is used to deserialize an object from the stream. It
reads from the stream to reconstruct an object.

1.  If the `ObjectInputStream` subclass is overriding the implementation, call
    the `readObjectOverride` method and return. Reimplementation is described
    at the end of this section.

2.  If a block data record occurs in the stream, throw a `BlockDataException`
    with the number of available bytes.

3.  If the object in the stream is null, return null.

4.  If the object in the stream is a handle to a previous object, return the
    object.

5.  If the object in the stream is a `Class`, read its `ObjectStreamClass`
    descriptor, add it and its handle to the set of known objects, and return
    the corresponding `Class` object.

6.  If the object in the stream is an `ObjectStreamClass`, read in its data
    according to the formats described in [Section 4.3, "Serialized
    Form"](class.html#serialized-form). Add it and its handle to the set of
    known objects. In versions 1.3 and later of the Java 2 SDK, Standard
    Edition, the `readClassDescriptor` method is called to read in the
    `ObjectStreamClass` if it represents a class that is not a dynamic proxy
    class, as indicated in the stream data. If the class descriptor represents
    a dynamic proxy class, call the `resolveProxyClass` method on the stream to
    get the local class for the descriptor; otherwise, call the `resolveClass`
    method on the stream to get the local class. If the class cannot be
    resolved, throw a ClassNotFoundException. Return the resulting
    `ObjectStreamClass` object.

7.  If the object in the stream is a `String`, read its length information
    followed by the contents of the string encoded in modified UTF-8. For
    details, refer to [Section 6.2, "Stream
    Elements"](protocol.html#stream-elements). Add the `String` and its handle
    to the set of known objects, and proceed to Step 12.

8.  If the object in the stream is an array, read its `ObjectStreamClass` and
    the length of the array. Allocate the array, and add it and its handle in
    the set of known objects. Read each element using the appropriate method
    for its type and assign it to the array. Proceed to Step 12.

9.  If the object in the stream is an enum constant, read its
    `ObjectStreamClass` and the enum constant name. If the `ObjectStreamClass`
    represents a class that is not an enum type, an `InvalidClassException` is
    thrown. Obtain a reference to the enum constant by calling the
    `java.lang.Enum.valueOf` method, passing the enum type bound to the
    received `ObjectStreamClass` along with the received name as arguments. If
    the `valueOf` method throws an `IllegalArgumentException`, an
    `InvalidObjectException` is thrown with the `IllegalArgumentException` as
    its cause. Add the enum constant and its handle in the set of known
    objects, and proceed to Step 12.

10. For all other objects, the `ObjectStreamClass` of the object is read from
    the stream. The local class for that `ObjectStreamClass` is retrieved. The
    class must be serializable or externalizable, and must not be an enum type.
    If the class does not satisfy these criteria, an `InvalidClassException` is
    thrown.

11. An instance of the class is allocated. The instance and its handle are
    added to the set of known objects. The contents restored appropriately:

    a.  For serializable objects, the no-arg constructor for the first
        non-serializable supertype is run. For serializable classes, the fields
        are initialized to the default value appropriate for its type. Then the
        fields of each class are restored by calling class-specific
        `readObject` methods, or if these are not defined, by calling the
        `defaultReadObject` method. Note that field initializers and
        constructors are not executed for serializable classes during
        deserialization. In the normal case, the version of the class that
        wrote the stream will be the same as the class reading the stream. In
        this case, all of the supertypes of the object in the stream will match
        the supertypes in the currently-loaded class. If the version of the
        class that wrote the stream had different supertypes than the loaded
        class, the `ObjectInputStream` must be more careful about restoring or
        initializing the state of the differing classes. It must step through
        the classes, matching the available data in the stream with the classes
        of the object being restored. Data for classes that occur in the
        stream, but do not occur in the object, is discarded. For classes that
        occur in the object, but not in the stream, the class fields are set to
        default values by default serialization.

    b.  For externalizable objects, the no-arg constructor for the class is run
        and then the `readExternal` method is called to restore the contents of
        the object.

12. Process potential substitutions by the class of the object and/or by a
    subclass of `ObjectInputStream`:

    a.  If the class of the object is not an enum type and defines the
        appropriate `readResolve` method, the method is called to allow the
        object to replace itself.

    b.  Then if previously enabled by `enableResolveObject,` the
        `resolveObject` method is called to allow subclasses of the stream to
        examine and replace the object. If the previous step did replace the
        original object, the `resolveObject` method is called with the
        replacement object. If a replacement took place, the table of known
        objects is updated so the replacement object is associated with the
        handle. The replacement object is then returned from `readObject`.

All of the methods for reading primitives types only consume bytes from the
block data records in the stream. If a read for primitive data occurs when the
next item in the stream is an object, the read methods return *-1* or the
`EOFException` as appropriate. The value of a primitive type is read by a
`DataInputStream` from the block data record.

The exceptions thrown reflect errors during the traversal or exceptions that
occur on the underlying stream. If any exception is thrown, the underlying
stream is left in an unknown and unusable state.

When the reset token occurs in the stream, all of the state of the stream is
discarded. The set of known objects is cleared.

When the exception token occurs in the stream, the exception is read and a new
`WriteAbortedException` is thrown with the terminating exception as an
argument. The stream context is reset as described earlier.

The `readUnshared` method is used to read "unshared" objects from the stream.
This method is identical to `readObject`, except that it prevents subsequent
calls to `readObject` and `readUnshared` from returning additional references
to the deserialized instance returned by the original call to `readUnshared`.
Specifically:

-   If `readUnshared` is called to deserialize a back-reference (the stream
    representation of an object which has been written previously to the
    stream), an `ObjectStreamException` will be thrown.

-   If `readUnshared` returns successfully, then any subsequent attempts to
    deserialize back-references to the stream handle deserialized by
    `readUnshared` will cause an `ObjectStreamException` to be thrown.

Deserializing an object via `readUnshared` invalidates the stream handle
associated with the returned object. Note that this in itself does not always
guarantee that the reference returned by `readUnshared` is unique; the
deserialized object may define a `readResolve` method which returns an object
visible to other parties, or `readUnshared` may return a `Class` object or enum
constant obtainable elsewhere in the stream or through external means. If the
deserialized object defines a `readResolve` method and the invocation of that
method returns an array, then `readUnshared` returns a shallow clone of that
array; this guarantees that the returned array object is unique and cannot be
obtained a second time from an invocation of `readObject` or `readUnshared` on
the `ObjectInputStream`, even if the underlying data stream has been
manipulated.

The `defaultReadObject` method is used to read the fields and object from the
stream. It uses the class descriptor in the stream to read the fields in the
canonical order by name and type from the stream. The values are assigned to
the matching fields by name in the current class. Details of the versioning
mechanism can be found in [Section 5.5, "Compatible Java Type
Evolution"](version.html#compatible-java-type-evolution). Any field of the
object that does not appear in the stream is set to its default value. Values
that appear in the stream, but not in the object, are discarded. This occurs
primarily when a later version of a class has written additional fields that do
not occur in the earlier version. This method may only be called from the
`readObject` method while restoring the fields of a class. When called at any
other time, the `NotActiveException` is thrown.

The `readFields` method reads the values of the serializable fields from the
stream and makes them available via the `GetField` class. The `readFields`
method is only callable from within the `readObject` method of a serializable
class. It cannot be called more than once or if `defaultReadObject` has been
called. The `GetFields` object uses the current object's `ObjectStreamClass` to
verify the fields that can be retrieved for this class. The `GetFields` object
returned by `readFields` is only valid during this call to the classes
`readObject` method. The fields may be retrieved in any order. Additional data
may only be read directly from stream after `readFields` has been called.

The `registerValidation` method can be called to request a callback when the
entire graph has been restored but before the object is returned to the
original caller of `readObject`. The order of validate callbacks can be
controlled using the priority. Callbacks registered with higher values are
called before those with lower values. The object to be validated must support
the `ObjectInputValidation` interface and implement the `validateObject`
method. It is only correct to register validations during a call to a class's
`readObject` method. Otherwise, a `NotActiveException` is thrown. If the
callback object supplied to `registerValidation` is null, an
`InvalidObjectException` is thrown.

Starting with the Java SDK, Standard Edition, v1.3, the `readClassDescriptor`
method is used to read in all `ObjectStreamClass` objects.
`readClassDescriptor` is called when the `ObjectInputStream` expects a class
descriptor as the next item in the serialization stream. Subclasses of
`ObjectInputStream` may override this method to read in class descriptors that
have been written in non-standard formats (by subclasses of
`ObjectOutputStream` which have overridden the `writeClassDescriptor` method).
By default, this method reads class descriptors according to the format
described in [Section 6.4, "Grammar for the Stream
Format"](protocol.html#grammar-for-the-stream-format).

The `resolveClass` method is called while a class is being deserialized, and
after the class descriptor has been read. Subclasses may extend this method to
read other information about the class written by the corresponding subclass of
`ObjectOutputStream`. The method must find and return the class with the given
name and `serialVersionUID`. The default implementation locates the class by
calling the class loader of the closest caller of `readObject` that has a class
loader. If the class cannot be found `ClassNotFoundException` should be thrown.
Prior to JDK 1.1.6, the `resolveClass` method was required to return the same
fully qualified class name as the class name in the stream. In order to
accommodate package renaming across releases, `method` `resolveClass` only
needs to return a class with the same base class name and `SerialVersionUID` in
JDK 1.1.6 and later versions.

The `resolveObject` method is used by trusted subclasses to monitor or
substitute one object for another during deserialization. Resolving objects
must be enabled explicitly by calling `enableResolveObject` before calling
`readObject` for the first object to be resolved. Once enabled, `resolveObject`
is called once for each serializable object just prior to the first time it is
being returned from `readObject`. Note that the `resolveObject` method is not
called for objects of the specially handled classes, `Class`,
`ObjectStreamClass`, `String`, and arrays. A subclass's implementation of
`resolveObject` may return a substitute object that will be assigned or
returned instead of the original. The object returned must be of a type that is
consistent and assignable to every reference of the original object or else a
`ClassCastException` will be thrown. All assignments are type-checked. All
references in the stream to the original object will be replaced by references
to the substitute object.

The `enableResolveObject` method is called by trusted subclasses of
`ObjectOutputStream` to enable the monitoring or substitution of one object for
another during deserialization. Replacing objects is disabled until
`enableResolveObject` is called with a `true` value. It may thereafter be
disabled by setting it to `false`. The previous setting is returned. The
`enableResolveObject` method checks if the stream has permission to request
substitution during serialization. To ensure that the private state of objects
is not unintentionally exposed, only trusted streams may use `resolveObject`.
Trusted classes are those classes with a class loader equal to null or belong
to a security protection domain that provides permission to enable
substitution.

If the subclass of `ObjectInputStream` is not considered part of the system
domain, a line has to be added to the security policy file to provide to a
subclass of `ObjectInputStream` permission to call `enableResolveObject`. The
`SerializablePermission` to add is `"enableSubstitution"`.
`AccessControlException` is thrown if the protection domain of the subclass of
`ObjectStreamClass` does not have permission to `"enableSubstitution"` by
calling `enableResolveObject`. See the document Java Security Architecture (JDK
1.2) for additional information about the security model.

The `readStreamHeader` method reads and verifies the magic number and version
of the stream. If they do not match, the `StreamCorruptedMismatch` is thrown.

To override the implementation of deserialization, a subclass of
`ObjectInputStream` should call the protected no-arg `ObjectInputStream`,
constructor. There is a security check within the no-arg constructor for
`SerializablePermission "enableSubclassImplementation"` to ensure that only
trusted classes are allowed to override the default implementation. This
constructor does not allocate any private data for `ObjectInputStream` and sets
a flag that indicates that the final `readObject` method should invoke the
`readObjectOverride` method and return. All other `ObjectInputStream` methods
are not final and can be directly overridden by the subclass.

## 3.2 The ObjectInputStream.GetField Class

The class `ObjectInputStream.GetField` provides the API for getting the values
of serializable fields. The protocol of the stream is the same as used by
`defaultReadObject.` Using `readFields` to access the serializable fields does
not change the format of the stream. It only provides an alternate API to
access the values which does not require the class to have the corresponding
non-transient and non-static fields for each named serializable field. The
serializable fields are those declared using `serialPersistentFields` or if it
is not declared the non-transient and non-static fields of the object. When the
stream is read the available serializable fields are those written to the
stream when the object was serialized. If the class that wrote the stream is a
different version not all fields will correspond to the serializable fields of
the current class. The available fields can be retrieved from the
`ObjectStreamClass` of the `GetField` object.

The `getObjectStreamClass` method returns an `ObjectStreamClass` object
representing the class in the stream. It contains the list of serializable
fields.

The `defaulted` method returns *true* if the field is not present in the
stream. An `IllegalArgumentException` is thrown if the requested field is not a
serializable field of the current class.

Each `get` method returns the specified serializable field from the stream. I/O
exceptions will be thrown if the underlying stream throws an exception. An
`IllegalArgumentException` is thrown if the name or type does not match the
name and type of an field serializable field of the current class. The default
value is returned if the stream does not contain an explicit value for the
field.

## 3.3 The ObjectInputValidation Interface

This interface allows an object to be called when a complete graph of objects
has been deserialized. If the object cannot be made valid, it should throw the
`ObjectInvalidException`. Any exception that occurs during a call to
`validateObject` will terminate the validation process, and the
`InvalidObjectException` will be thrown.

```
package java.io;

public interface ObjectInputValidation
{
    public void validateObject()
        throws InvalidObjectException;
}
```

## 3.4 The readObject Method

For serializable objects, the `readObject` method allows a class to control the
deserialization of its own fields. Here is its signature:

```
private void readObject(ObjectInputStream stream)
    throws IOException, ClassNotFoundException;
```

Each subclass of a serializable object may define its own `readObject` method.
If a class does not implement the method, the default serialization provided by
`defaultReadObject` will be used. When implemented, the class is only
responsible for restoring its own fields, not those of its supertypes or
subtypes.

The `readObject` method of the class, if implemented, is responsible for
restoring the state of the class. The values of every field of the object
whether transient or not, static or not are set to the default value for the
fields type. Either `ObjectInputStream`'s `defaultReadObject` or `readFields`
method must be called once (and only once) before reading any optional data
written by the corresponding `writeObject` method; even if no optional data is
read, `defaultReadObject` or `readFields` must still be invoked once. If the
`readObject` method of the class attempts to read more data than is present in
the optional part of the stream for this class, the stream will return `-1` for
bytewise reads, throw an `EOFException` for primitive data reads (e.g.,
`readInt`, `readFloat`), or throw an `OptionalDataException` with the `eof`
field set to `true` for object reads.

The responsibility for the format, structure, and versioning of the optional
data lies completely with the class. The `@serialData` javadoc tag within the
javadoc comment for the `readObject` method should be used to document the
format and structure of the optional data.

If the class being restored is not present in the stream being read, then its
`readObjectNoData` method, if defined, is invoked (instead of `readObject`);
otherwise, its fields are initialized to the appropriate default values. For
further detail, see [Section 3.5, "The readObjectNoData
Method"](#the-readobjectnodata-method).

Reading an object from the `ObjectInputStream` is analogous to creating a new
object. Just as a new object's constructors are invoked in the order from the
superclass to the subclass, an object being read from a stream is deserialized
from superclass to subclass. The `readObject` or `readObjectNoData` method is
called instead of the constructor for each `Serializable` subclass during
deserialization.

One last similarity between a constructor and a `readObject` method is that
both provide the opportunity to invoke a method on an object that is not fully
constructed. Any overridable (neither private, static nor final) method called
while an object is being constructed can potentially be overridden by a
subclass. Methods called during the construction phase of an object are
resolved by the actual type of the object, not the type currently being
initialized by either its constructor or `readObject`/`readObjectNoData`
method. Therefore, calling an overridable method from within a `readObject` or
`readObjectNoData` method may result in the unintentional invocation of a
subclass method before the superclass has been fully initialized.

## 3.5 The readObjectNoData Method

For serializable objects, the `readObjectNoData` method allows a class to
control the initialization of its own fields in the event that a subclass
instance is deserialized and the serialization stream does not list the class
in question as a superclass of the deserialized object. This may occur in cases
where the receiving party uses a different version of the deserialized
instance's class than the sending party, and the receiver's version extends
classes that are not extended by the sender's version. This may also occur if
the serialization stream has been tampered; hence, `readObjectNoData` is useful
for initializing deserialized objects properly despite a "hostile" or
incomplete source stream.

```
private void readObjectNoData() throws ObjectStreamException;
```

Each serializable class may define its own `readObjectNoData` method. If a
serializable class does not define a `readObjectNoData` method, then in the
circumstances listed above the fields of the class will be initialized to their
default values (as listed in The Java Language Specification); this behavior is
consistent with that of `ObjectInputStream` prior to version 1.4 of the Java 2
SDK, Standard Edition, when support for `readObjectNoData` methods was
introduced. If a serializable class does define a `readObjectNoData` method and
the aforementioned conditions arise, then `readObjectNoData` will be invoked at
the point during deserialization when a class-defined `readObject` method would
otherwise be called had the class in question been listed by the stream as a
superclass of the instance being deserialized.

## 3.6 The readExternal Method

Objects implementing `java.io.Externalizable` must implement the `readExternal`
method to restore the entire state of the object. It must coordinate with its
superclasses to restore their state. All of the methods of `ObjectInput` are
available to restore the object's primitive typed fields and object fields.

```
public void readExternal(ObjectInput stream)
    throws IOException;
```

**Note:** The `readExternal` method is public, and it raises the risk of a
client being able to overwrite an existing object from a stream. The class may
add its own checks to insure that this is only called when appropriate.

A new stream protocol version has been introduced in JDK 1.2 to correct a
problem with `Externalizable` objects. The old definition of `Externalizable`
objects required the local virtual machine to find a `readExternal` method to
be able to properly read an `Externalizable` object from the stream. The new
format adds enough information to the stream protocol so serialization can skip
an `Externalizable` object when the local `readExternal` method is not
available. Due to class evolution rules, serialization must be able to skip an
`Externalizable` object in the input stream if there is not a mapping for the
object using the local classes.

An additional benefit of the new `Externalizable` stream format is that
`ObjectInputStream` can detect attempts to read more External data than is
available, and can also skip by any data that is left unconsumed by a
`readExternal` method. The behavior of `ObjectInputStream` in response to a
read past the end of External data is the same as the behavior when a
class-defined `readObject` method attempts to read past the end of its optional
data: bytewise reads will return `-1`, primitive reads will throw
`EOFException`s, and object reads will throw `OptionalDataException`s with the
`eof` field set to `true`.

Due to the format change, JDK 1.1.6 and earlier releases are not able to read
the new format. `StreamCorruptedException` is thrown when JDK 1.1.6 or earlier
attempts to read an `Externalizable` object from a stream written in
`PROTOCOL_VERSION_2`. Compatibility issues are discussed in more detail in
[Section 6.3, "Stream Protocol
Versions"](protocol.html#stream-protocol-versions).

## 3.7 The readResolve Method

For Serializable and Externalizable classes, the `readResolve` method allows a
class to replace/resolve the object read from the stream before it is returned
to the caller. By implementing the `readResolve` method, a class can directly
control the types and instances of its own instances being deserialized. The
method is defined as follows:

```
ANY-ACCESS-MODIFIER Object readResolve()
            throws ObjectStreamException;
```

The `readResolve` method is called when `ObjectInputStream` has read an object
from the stream and is preparing to return it to the caller.
`ObjectInputStream` checks whether the class of the object defines the
`readResolve` method. If the method is defined, the `readResolve` method is
called to allow the object in the stream to designate the object to be
returned. The object returned should be of a type that is compatible with all
uses. If it is not compatible, a `ClassCastException` will be thrown when the
type mismatch is discovered.

For example, a `Symbol` class could be created for which only a single instance
of each symbol binding existed within a virtual machine. The `readResolve`
method would be implemented to determine if that symbol was already defined and
substitute the preexisting equivalent `Symbol` object to maintain the identity
constraint. In this way the uniqueness of `Symbol` objects can be maintained
across serialization.

**Note:** The `readResolve` method is not invoked on the object until the
object is fully constructed, so any references to this object in its object
graph will not be updated to the new object nominated by `readResolve`.
However, during the serialization of an object with the `writeReplace` method,
all references to the original object in the replacement object's object graph
are replaced with references to the replacement object. Therefore in cases
where an object being serialized nominates a replacement object whose object
graph has a reference to the original object, deserialization will result in an
incorrect graph of objects. Furthermore, if the reference types of the object
being read (nominated by `writeReplace`) and the original object are not
compatible, the construction of the object graph will raise a
`ClassCastException`.

-------------------------------------------------------------------------------

*[Copyright](../../../legal/SMICopyright.html) &copy; 2005, 2017, Oracle
and/or its affiliates. All rights reserved.*
