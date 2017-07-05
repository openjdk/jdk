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

include-before: '[CONTENTS](index.html) | [PREV](security.html) | [NEXT](examples.html)'
include-after: '[CONTENTS](index.html) | [PREV](security.html) | [NEXT](examples.html)'

title: 'Java Object Serialization Specification: B - Exceptions In Object Serialization'
---

-------------------------------------------------------------------------------

All exceptions thrown by serialization classes are subclasses of
`ObjectStreamException` which is a subclass of `IOException`.

### `ObjectStreamException`

Superclass of all serialization exceptions.

### `InvalidClassException`

Thrown when a class cannot be used to restore objects for any of these reasons:

-   The class does not match the serial version of the class in the stream.
-   The class contains fields with invalid primitive data types.
-   The `Externalizable` class does not have a public no-arg constructor.
-   The `Serializable` class can not access the no-arg constructor of its
    closest non-Serializable superclass.

### `NotSerializableException`

Thrown by a `readObject` or `writeObject` method to terminate serialization or
deserialization.

### `StreamCorruptedException`

Thrown:

-   If the stream header is invalid.
-   If control information not found.
-   If control information is invalid.
-   JDK 1.1.5 or less attempts to call `readExternal` on a `PROTOCOL_VERSION_2`
    stream.

### `NotActiveException`

Thrown if `writeObject` state is invalid within the following
`ObjectOutputStream` methods:

-   `defaultWriteObject`
-   `putFields`
-   `writeFields`

Thrown if `readObject` state is invalid within the following
`ObjectInputStream` methods:

-   `defaultReadObject`
-   `readFields`
-   `registerValidation`

### `InvalidObjectException`

Thrown when a restored object cannot be made valid.

### `OptionalDataException`

Thrown by `readObject` when there is primitive data in the stream and an object
is expected. The length field of the exception indicates the number of bytes
that are available in the current block.

### `WriteAbortedException`

Thrown when reading a stream terminated by an exception that occurred while the
stream was being written.

-------------------------------------------------------------------------------

*[Copyright](../../../legal/SMICopyright.html) &copy; 2005, 2017, Oracle
and/or its affiliates. All rights reserved.*
