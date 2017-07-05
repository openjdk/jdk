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

include-before: '[CONTENTS](index.html) | [PREV](index.html) | [NEXT](serial-arch.html)'
include-after: '[CONTENTS](index.html) | [PREV](index.html) | [NEXT](serial-arch.html)'

title: 'Java Object Serialization Specification: 0 - Change History'
---

-------------------------------------------------------------------------------

May 12, 2005 Updates for Java^TM^ SE Development Kit, v6 Beta 1

-   Added statement about how an array object returned by a `readResolve`
    invocation for an unshared read is handled.
-   Clarified the behavior in the event of an invalid `serialPersistentFields`
    value.
-   Clarified that `serialVersionUID` matching is waived for array classes.
-   Clarified when `IllegalArgumentException` is thrown by
    `ObjectOutputStream.PutFields` methods.

July 24, 2003 Updates for Java^TM^ 2 SDK, Standard Edition, v1.5 Beta 1

-   Added support for serializing enum constants.
-   Added specification of class modifier flags used in the computation of
    default `serialVersionUID` values to [Section 4.6, "Stream Unique
    Identifiers"](class.html#stream-unique-identifiers).

Aug. 16, 2001 Updates for Java^TM^ 2 SDK, Standard Edition, v1.4 Beta 2

-   Added support for class-defined `readObjectNoData` methods, to be used for
    initializing serializable class fields in cases not covered by
    class-defined readObject methods. See [Section 3.5, "The readObjectNoData
    Method"](input.html#the-readobjectnodata-method), as well as Appendix A,
    "Security in Object Serialization".
-   New methods `ObjectOutputStream.writeUnshared` and
    `ObjectInputStream.readUnshared` provide a mechanism for ensuring unique
    references to deserialized objects. See [Section 2.1, "The
    ObjectOutputStream Class"](output.html#the-objectoutputstream-class),
    [Section 3.1, "The ObjectInputStream
    Class"](input.html#the-objectinputstream-class), as well as Appendix A,
    "Security in Object Serialization".
-   Documented new security checks in the one-argument constructors for
    `ObjectOutputStream` and `ObjectInputStream`. See [Section 2.1, "The
    ObjectOutputStream Class"](output.html#the-objectoutputstream-class) and
    [Section 3.1, "The ObjectInputStream
    Class"](input.html#the-objectinputstream-class).
-   Added caution against using inner classes for serialization in [Section
    1.10, "The Serializable
    Interface"](serial-arch.html#the-serializable-interface).
-   Clarified requirement that class-defined `writeObject` methods invoke
    `ObjectOutputStream.defaultWriteObject` or `writeFields` once before
    writing optional data, and that class-defined `readObject` methods invoke
    `ObjectInputStream.defaultReadObject` or `readFields` once before reading
    optional data. See [Section 2.3, "The writeObject
    Method"](output.html#the-writeobject-method) and [Section 3.4, "The
    readObject Method"](input.html#the-readobject-method).
-   Clarified the behavior of `ObjectInputStream` when class-defined
    `readObject` or `readExternal` methods attempt read operations which exceed
    the bounds of available data; see [Section 3.4, "The readObject
    Method"](input.html#the-readobject-method) and [Section 3.6, "The
    readExternal Method"](input.html#the-readexternal-method).
-   Clarified the description of non-proxy class descriptor field type strings
    to require that they be written in "field descriptor" format; see [Section
    6.2, "Stream Elements"](protocol.html#stream-elements).

July 30, 1999 Updates for Java^TM^ 2 SDK, Standard Edition, v1.3 Beta

-   Added the ability to write `String` objects for which the UTF encoding is
    longer than 65535 bytes in length. See [Section 6.2, "Stream
    Elements"](protocol.html#stream-elements).
-   New methods `ObjectOutputStream.writeClassDescriptor` and
    `ObjectInputStream.readClassDescriptor` provide a means of customizing the
    serialized representation of `ObjectStreamClass` class descriptors. See
    [Section 2.1, "The ObjectOutputStream
    Class"](output.html#the-objectoutputstream-class) and [Section 3.1, "The
    ObjectInputStream Class"](input.html#the-objectinputstream-class).
-   Expanded Appendix A, "[Security in Object
    Serialization"](security.html#security-in-object-serialization).

Sept. 30, 1998 Updates for JDK^TM^ 1.2 Beta4 RC1

-   Documentation corrections only.

June 22, 1998 Updates for JDK^TM^ 1.2 Beta4

-   Eliminated JDK^TM^ 1.2 `java.io` interfaces, `Replaceable` and
    `Resolvable`.References to either of these classes as an interface should
    be replaced with `java.io.Serializable`. Serialization will use reflection
    to invoke the methods, `writeReplace` and `readResolve`, if the
    Serializable class defines these methods. See [Section 2.5, "The
    writeReplace Method"](output.html#the-writereplace-method) and [Section
    3.7, "The readResolve Method"](input.html#the-readresolve-method).
-   New javadoc tags *@serial*, *@serialField*, and *@serialData* provide a way
    to document the Serialized Form of a Serializable class. Javadoc generates
    a serialization specification based on the contents of these tags. See
    [Section 1.6, "Documenting Serializable Fields and Data for a
    Class"](serial-arch.html#documenting-serializable-fields-and-data-for-a-class).
-   Special Serializable class member, `serialPersistentFields`, must be
    declared private. See [Section 1.5, "Defining Serializable Fields for a
    Class"](serial-arch.html#defining-serializable-fields-for-a-class).
-   Clarified the steps involved in computing the `serialVersionUID` in
    [Section 4.6, "Stream Unique
    Identifiers"](class.html#stream-unique-identifiers).

Feb. 6, 1998 Updates for JDK^TM^ 1.2 Beta 3

-   Introduced the concept of `STREAM_PROTOCOL` versions. Added the
    `STREAM_PROTOCOL_2` version to indicate a new format for `Externalizable`
    objects that enable skipping by an `Externalizable` object within the
    stream, even when the object's class is not available in the local Virtual
    Machine. Compatibility issues are discussed in [Section 6.3, "Stream
    Protocol Versions"](protocol.html#stream-protocol-versions).
-   `The ObjectInputStream.resolveClass` method can return a local class in a
    different package than the name of the class within the stream. This
    capability enables renaming of packages between releases. The
    `serialVersionUID` and the base class name must be the same in the stream
    and in the local version of the class. See [Section 3.1, "The
    ObjectInputStream Class"](input.html#the-objectinputstream-class).
-   Allow substitution of `String` or `array` objects when writing them to or
    reading them from the stream. See [Section 2.1, "The ObjectOutputStream
    Class"](output.html#the-objectoutputstream-class) and [Section 3.1, "The
    ObjectInputStream Class"](input.html#the-objectinputstream-class).

Sept. 4, 1997 Updates for JDK^TM^ 1.2 Beta1

-   Separated the Replaceable interface into two interfaces: Replaceable and
    Resolvable. The Replaceable interface allows a class to nominate its own
    replacement just before serializing the object to the stream. The
    Resolvable interface allows a class to nominate its own replacement when
    reading an object from the stream.
-   Modified serialization to use the JDK^TM^ 1.2 security model. There is a
    check for `SerializablePermission "enableSubstitution"` within the
    `ObjectInputStream.enableReplace` and `ObjectOutputStream.enableResolve`
    methods. See [Section 2.1, "The ObjectOutputStream
    Class"](output.html#the-objectoutputstream-class) and [Section 3.1, "The
    ObjectInputStream Class"](input.html#the-objectinputstream-class).
-   Updated `writeObject`'s exception handler to write handled `IOException`s
    into the stream. See [Section 2.1, "The ObjectOutputStream
    Class"](output.html#the-objectoutputstream-class).

July 3, 1997 Updates for JDK^TM^ 1.2 Alpha

-   Documented the requirements for specifying the serialized state of classes.
    See [Section 1.5, "Defining Serializable Fields for a
    Class"](serial-arch.html#defining-serializable-fields-for-a-class).
-   Added the Serializable Fields API to allow classes more flexibility in
    accessing the serialized fields of a class. The stream protocol is
    unchanged. See [Section 1.7, "Accessing Serializable Fields of a
    Class](serial-arch.html#accessing-serializable-fields-of-a-class),"
    [Section 2.2, "The ObjectOutputStream.PutField
    Class](output.html#the-objectoutputstream.putfield-class)," and [Section
    3.2, "The ObjectInputStream.GetField
    Class"](input.html#the-objectinputstream.getfield-class).
-   Clarified that field descriptors and data are written to and read from the
    stream in canonical order. See [Section 4.1, "The ObjectStreamClass
    Class"](class.html#the-objectstreamclass-class).

-------------------------------------------------------------------------------

*[Copyright](../../../legal/SMICopyright.html) &copy; 2005, 2017, Oracle
and/or its affiliates. All rights reserved.*
