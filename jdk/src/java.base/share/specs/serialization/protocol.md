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

include-before: '[CONTENTS](index.html) | [PREV](version.html) | [NEXT](security.html)'
include-after: '[CONTENTS](index.html) | [PREV](version.html) | [NEXT](security.html)'

title: 'Java Object Serialization Specification: 6 - Object Serialization Stream Protocol'
---

-   [Overview](#overview)
-   [Stream Elements](#stream-elements)
-   [Stream Protocol Versions](#stream-protocol-versions)
-   [Grammar for the Stream Format](#grammar-for-the-stream-format)
-   [Example](#example)

-------------------------------------------------------------------------------

## 6.1 Overview

The stream format satisfies the following design goals:

-   Is compact and is structured for efficient reading.
-   Allows skipping through the stream using only the knowledge of the
    structure and format of the stream. Does not require invoking any per class
    code.
-   Requires only stream access to the data.

## 6.2 Stream Elements

A basic structure is needed to represent objects in a stream. Each attribute of
the object needs to be represented: its classes, its fields, and data written
and later read by class-specific methods. The representation of objects in the
stream can be described with a grammar. There are special representations for
null objects, new objects, classes, arrays, strings, and back references to any
object already in the stream. Each object written to the stream is assigned a
handle that is used to refer back to the object. Handles are assigned
sequentially starting from 0x7E0000. The handles restart at 0x7E0000 when the
stream is reset.

A class object is represented by the following:

-   Its `ObjectStreamClass` object.

An `ObjectStreamClass` object for a Class that is not a dynamic proxy class is
represented by the following:

-   The Stream Unique Identifier (SUID) of compatible classes.

-   A set of flags indicating various properties of the class, such as whether
    the class defines a `writeObject` method, and whether the class is
    serializable, externalizable, or an enum type

-   The number of serializable fields

-   The array of fields of the class that are serialized by the default
    mechanismFor arrays and object fields, the type of the field is included as
    a string which must be in "field descriptor" format (e.g.,
    "`Ljava/lang/Object;`") as specified in The Java Virtual Machine
    Specification.

-   Optional block-data records or objects written by the `annotateClass`
    method

-   The `ObjectStreamClass` of its supertype (null if the superclass is not
    serializable)

An `ObjectStreamClass` object for a dynamic proxy class is represented by the
following:

-   The number of interfaces that the dynamic proxy class implements

-   The names of all of the interfaces implemented by the dynamic proxy class,
    listed in the order that they are returned by invoking the `getInterfaces`
    method on the Class object.

-   Optional block-data records or objects written by the `annotateProxyClass`
    method.

-   The ObjectStreamClass of its supertype, `java.lang.reflect.Proxy`.

The representation of `String` objects consists of length information followed
by the contents of the string encoded in modified UTF-8. The modified UTF-8
encoding is the same as used in the Java Virtual Machine and in the
`java.io.DataInput` and `DataOutput` interfaces; it differs from standard UTF-8
in the representation of supplementary characters and of the null character.
The form of the length information depends on the length of the string in
modified UTF-8 encoding. If the modified UTF-8 encoding of the given `String`
is less than 65536 bytes in length, the length is written as 2 bytes
representing an unsigned 16-bit integer. Starting with the Java 2 platform,
Standard Edition, v1.3, if the length of the string in modified UTF-8 encoding
is 65536 bytes or more, the length is written in 8 bytes representing a signed
64-bit integer. The typecode preceding the `String` in the serialization stream
indicates which format was used to write the `String`.

Arrays are represented by the following:

-   Their `ObjectStreamClass` object.

-   The number of elements.

-   The sequence of values. The type of the values is implicit in the type of
    the array. for example the values of a byte array are of type byte.

Enum constants are represented by the following:

-   The `ObjectStreamClass` object of the constant's base enum type.

-   The constant's name string.

New objects in the stream are represented by the following:

-   The most derived class of the object.

-   Data for each serializable class of the object, with the highest superclass
    first. For each class the stream contains the following:

    -   The serializable fields.See [Section 1.5, "Defining Serializable Fields
        for a
        Class"](serial-arch.html#defining-serializable-fields-for-a-class).

    -   If the class has `writeObject`/`readObject` methods, there may be
        optional objects and/or block-data records of primitive types written
        by the `writeObject` method followed by an `endBlockData` code.

All primitive data written by classes is buffered and wrapped in block-data
records, regardless if the data is written to the stream within a `writeObject`
method or written directly to the stream from outside a `writeObject` method.
This data can only be read by the corresponding `readObject` methods or be read
directly from the stream. Objects written by the `writeObject` method terminate
any previous block-data record and are written either as regular objects or
null or back references, as appropriate. The block-data records allow error
recovery to discard any optional data. When called from within a class, the
stream can discard any data or objects until the `endBlockData`.

## 6.3 Stream Protocol Versions

It was necessary to make a change to the serialization stream format in JDK 1.2
that is not backwards compatible to all minor releases of JDK 1.1. To provide
for cases where backwards compatibility is required, a capability has been
added to indicate what `PROTOCOL_VERSION` to use when writing a serialization
stream. The method `ObjectOutputStream.useProtocolVersion` takes as a parameter
the protocol version to use to write the serialization stream.

The Stream Protocol Versions are as follows:

-   `ObjectStreamConstants.PROTOCOL_VERSION_1`: Indicates the initial stream
    format.

-   `ObjectStreamConstants.PROTOCOL_VERSION_2`: Indicates the new external data
    format. Primitive data is written in block data mode and is terminated with
    `TC_ENDBLOCKDATA`.

    Block data boundaries have been standardized. Primitive data written in
    block data mode is normalized to not exceed 1024 byte chunks. The benefit
    of this change was to tighten the specification of serialized data format
    within the stream. This change is fully backward and forward compatible.

JDK 1.2 defaults to writing `PROTOCOL_VERSION_2`.

JDK 1.1 defaults to writing `PROTOCOL_VERSION_1`.

JDK 1.1.7 and greater can read both versions.

Releases prior to JDK 1.1.7 can only read `PROTOCOL_VERSION_1`.

## 6.4 Grammar for the Stream Format

The table below contains the grammar for the stream format. Nonterminal symbols
are shown in italics. Terminal symbols in a *fixed width font*. Definitions of
nonterminals are followed by a ":". The definition is followed by one or more
alternatives, each on a separate line. The following table describes the
notation:

  -------------  --------------------------------------------------------------
  **Notation**   **Meaning**
  -------------  --------------------------------------------------------------
  (*datatype*)   This token has the data type specified, such as byte.

  *token*\[n\]   A predefined number of occurrences of the token, that is an
                 array.

  *x0001*        A literal value expressed in hexadecimal. The number of hex
                 digits reflects the size of the value.

  &lt;*xxx*&gt;  A value read from the stream used to indicate the length of an
                 array.
  -------------  --------------------------------------------------------------

Note that the symbol (utf) is used to designate a string written using 2-byte
length information, and (long-utf) is used to designate a string written using
8-byte length information. For details, refer to [Section 6.2, "Stream
Elements"](#stream-elements).

### 6.4.1 Rules of the Grammar

A Serialized stream is represented by any stream satisfying the *stream* rule.

```
stream:
  magic version contents

contents:
  content
  contents content

content:
  object
  blockdata

object:
  newObject
  newClass
  newArray
  newString
  newEnum
  newClassDesc
  prevObject
  nullReference
  exception
  TC_RESET

newClass:
  TC_CLASS classDesc newHandle

classDesc:
  newClassDesc
  nullReference
  (ClassDesc)prevObject      // an object required to be of type ClassDesc

superClassDesc:
  classDesc

newClassDesc:
  TC_CLASSDESC className serialVersionUID newHandle classDescInfo
  TC_PROXYCLASSDESC newHandle proxyClassDescInfo

classDescInfo:
  classDescFlags fields classAnnotation superClassDesc

className:
  (utf)

serialVersionUID:
  (long)

classDescFlags:
  (byte)                  // Defined in Terminal Symbols and Constants

proxyClassDescInfo:
  (int)<count> proxyInterfaceName[count] classAnnotation
      superClassDesc

proxyInterfaceName:
  (utf)

fields:
  (short)<count> fieldDesc[count]

fieldDesc:
  primitiveDesc
  objectDesc

primitiveDesc:
  prim_typecode fieldName

objectDesc:
  obj_typecode fieldName className1

fieldName:
  (utf)

className1:
  (String)object             // String containing the field's type,
                             // in field descriptor format

classAnnotation:
  endBlockData
  contents endBlockData      // contents written by annotateClass

prim_typecode:
  'B'       // byte
  'C'       // char
  'D'       // double
  'F'       // float
  'I'       // integer
  'J'       // long
  'S'       // short
  'Z'       // boolean

obj_typecode:
  '['       // array
  'L'       // object

newArray:
  TC_ARRAY classDesc newHandle (int)<size> values[size]

newObject:
  TC_OBJECT classDesc newHandle classdata[]  // data for each class

classdata:
  nowrclass                 // SC_SERIALIZABLE & classDescFlag &&
                            // !(SC_WRITE_METHOD & classDescFlags)
  wrclass objectAnnotation  // SC_SERIALIZABLE & classDescFlag &&
                            // SC_WRITE_METHOD & classDescFlags
  externalContents          // SC_EXTERNALIZABLE & classDescFlag &&
                            // !(SC_BLOCKDATA  & classDescFlags
  objectAnnotation          // SC_EXTERNALIZABLE & classDescFlag&&
                            // SC_BLOCKDATA & classDescFlags

nowrclass:
  values                    // fields in order of class descriptor

wrclass:
  nowrclass

objectAnnotation:
  endBlockData
  contents endBlockData     // contents written by writeObject
                            // or writeExternal PROTOCOL_VERSION_2.

blockdata:
  blockdatashort
  blockdatalong

blockdatashort:
  TC_BLOCKDATA (unsigned byte)<size> (byte)[size]

blockdatalong:
  TC_BLOCKDATALONG (int)<size> (byte)[size]

endBlockData:
  TC_ENDBLOCKDATA

externalContent:         // Only parseable by readExternal
  (bytes)                // primitive data
   object

externalContents:         // externalContent written by
  externalContent         // writeExternal in PROTOCOL_VERSION_1.
  externalContents externalContent

newString:
  TC_STRING newHandle (utf)
  TC_LONGSTRING newHandle (long-utf)

newEnum:
  TC_ENUM classDesc newHandle enumConstantName

enumConstantName:
  (String)object

prevObject:
  TC_REFERENCE (int)handle

nullReference:
  TC_NULL

exception:
  TC_EXCEPTION reset (Throwable)object reset

magic:
  STREAM_MAGIC

version:
  STREAM_VERSION

values:          // The size and types are described by the
                 // classDesc for the current object

newHandle:       // The next number in sequence is assigned
                 // to the object being serialized or deserialized

reset:           // The set of known objects is discarded
                 // so the objects of the exception do not
                 // overlap with the previously sent objects
                 // or with objects that may be sent after
                 // the exception
```

### 6.4.2 Terminal Symbols and Constants

The following symbols in `java.io.ObjectStreamConstants` define the terminal
and constant values expected in a stream.

```
final static short STREAM_MAGIC = (short)0xaced;
final static short STREAM_VERSION = 5;
final static byte TC_NULL = (byte)0x70;
final static byte TC_REFERENCE = (byte)0x71;
final static byte TC_CLASSDESC = (byte)0x72;
final static byte TC_OBJECT = (byte)0x73;
final static byte TC_STRING = (byte)0x74;
final static byte TC_ARRAY = (byte)0x75;
final static byte TC_CLASS = (byte)0x76;
final static byte TC_BLOCKDATA = (byte)0x77;
final static byte TC_ENDBLOCKDATA = (byte)0x78;
final static byte TC_RESET = (byte)0x79;
final static byte TC_BLOCKDATALONG = (byte)0x7A;
final static byte TC_EXCEPTION = (byte)0x7B;
final static byte TC_LONGSTRING = (byte) 0x7C;
final static byte TC_PROXYCLASSDESC = (byte) 0x7D;
final static byte TC_ENUM = (byte) 0x7E;
final static  int   baseWireHandle = 0x7E0000;
```

The flag byte *classDescFlags* may include values of

```
final static byte SC_WRITE_METHOD = 0x01; //if SC_SERIALIZABLE
final static byte SC_BLOCK_DATA = 0x08;    //if SC_EXTERNALIZABLE
final static byte SC_SERIALIZABLE = 0x02;
final static byte SC_EXTERNALIZABLE = 0x04;
final static byte SC_ENUM = 0x10;
```

The flag `SC_WRITE_METHOD` is set if the Serializable class writing the stream
had a `writeObject` method that may have written additional data to the stream.
In this case a `TC_ENDBLOCKDATA` marker is always expected to terminate the
data for that class.

The flag `SC_BLOCKDATA` is set if the `Externalizable` class is written into
the stream using `STREAM_PROTOCOL_2`. By default, this is the protocol used to
write `Externalizable` objects into the stream in JDK 1.2. JDK 1.1 writes
`STREAM_PROTOCOL_1`.

The flag `SC_SERIALIZABLE` is set if the class that wrote the stream extended
`java.io.Serializable` but not `java.io.Externalizable`, the class reading the
stream must also extend `java.io.Serializable` and the default serialization
mechanism is to be used.

The flag `SC_EXTERNALIZABLE` is set if the class that wrote the stream extended
`java.io.Externalizable`, the class reading the data must also extend
`Externalizable` and the data will be read using its `writeExternal` and
`readExternal` methods.

The flag `SC_ENUM` is set if the class that wrote the stream was an enum type.
The receiver's corresponding class must also be an enum type. Data for
constants of the enum type will be written and read as described in [Section
1.12, "Serialization of Enum
Constants"](serial-arch.html#serialization-of-enum-constants).

#### Example

Consider the case of an original class and two instances in a linked list:

```
class List implements java.io.Serializable {
    int value;
    List next;
    public static void main(String[] args) {
        try {
            List list1 = new List();
            List list2 = new List();
            list1.value = 17;
            list1.next = list2;
            list2.value = 19;
            list2.next = null;

            ByteArrayOutputStream o = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(o);
            out.writeObject(list1);
            out.writeObject(list2);
            out.flush();
            ...
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
```

The resulting stream contains:

```
    00: ac ed 00 05 73 72 00 04 4c 69 73 74 69 c8 8a 15 >....sr..Listi...<
    10: 40 16 ae 68 02 00 02 49 00 05 76 61 6c 75 65 4c >Z......I..valueL<
    20: 00 04 6e 65 78 74 74 00 06 4c 4c 69 73 74 3b 78 >..nextt..LList;x<
    30: 70 00 00 00 11 73 71 00 7e 00 00 00 00 00 13 70 >p....sq.~......p<
    40: 71 00 7e 00 03                                  >q.~..<
```

-------------------------------------------------------------------------------

*[Copyright](../../../legal/SMICopyright.html) &copy; 2005, 2017, Oracle
and/or its affiliates. All rights reserved.*
