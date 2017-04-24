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

include-before: 'CONTENTS | PREV | [NEXT](changelog.html)'
include-after: 'CONTENTS | PREV | [NEXT](changelog.html)'

title: 'Java Object Serialization Specification: Contents'
---

-------------------------------------------------------------------------------

## Table of Contents

### 0 [Change History](changelog.html)

### 1 [System Architecture](serial-arch.html)

-   1.1 [Overview](serial-arch.html#overview)
-   1.2 [Writing to an Object
    Stream](serial-arch.html#writing-to-an-object-stream)
-   1.3 [Reading from an Object
    Stream](serial-arch.html#reading-from-an-object-stream)
-   1.4 [Object Streams as
    Containers](serial-arch.html#object-streams-as-containers)
-   1.5 [Defining Serializable Fields for a
    Class](serial-arch.html#defining-serializable-fields-for-a-class)
-   1.6 [Documenting Serializable Fields and Data for a
    Class](serial-arch.html#documenting-serializable-fields-and-data-for-a-class)
-   1.7 [Accessing Serializable Fields of a
    Class](serial-arch.html#accessing-serializable-fields-of-a-class)
-   1.8 [The ObjectOutput
    Interface](serial-arch.html#the-objectoutput-interface)
-   1.9 [The ObjectInput Interface](serial-arch.html#the-objectinput-interface)
-   1.10 [The Serializable
    Interface](serial-arch.html#the-serializable-interface)
-   1.11 [The Externalizable
    Interface](serial-arch.html#the-externalizable-interface)
-   1.12 [Serialization of Enum
    Constants](serial-arch.html#serialization-of-enum-constants)
-   1.13 [Protecting Sensitive
    Information](serial-arch.html#protecting-sensitive-information)

### 2 [Object Output Classes](output.html)

-   2.1 [The ObjectOutputStream
    Class](output.html#the-objectoutputstream-class)
-   2.2 [The ObjectOutputStream.PutField
    Class](output.html#the-objectoutputstream.putfield-class)
-   2.3 [The writeObject Method](output.html#the-writeobject-method)
-   2.4 [The writeExternal Method](output.html#the-writeexternal-method)
-   2.5 [The writeReplace Method](output.html#the-writereplace-method)
-   2.6 [The useProtocolVersion
    Method](output.html#the-useprotocolversion-method)

### 3 [Object Input Classes](input.html)

-   3.1 [The ObjectInputStream Class](input.html#the-objectinputstream-class)
-   3.2 [The ObjectInputStream.GetField
    Class](input.html#the-objectinputstream.getfield-class)
-   3.3 [The ObjectInputValidation
    Interface](input.html#the-objectinputvalidation-interface)
-   3.4 [The readObject Method](input.html#the-readobject-method)
-   3.5 [The readObjectNoData Method](input.html#the-readobjectnodata-method)
-   3.6 [The readExternal Method](input.html#the-readexternal-method)
-   3.7 [The readResolve Method](input.html#the-readresolve-method)

### 4 [Class Descriptors](class.html)

-   4.1 [The ObjectStreamClass Class](class.html#the-objectstreamclass-class)
-   4.2 [Dynamic Proxy Class
    Descriptors](class.html#dynamic-proxy-class-descriptors)
-   4.3 [Serialized Form](class.html#serialized-form)
-   4.4 [The ObjectStreamField Class](class.html#the-objectstreamfield-class)
-   4.5 [Inspecting Serializable
    Classes](class.html#inspecting-serializable-classes)
-   4.6 [Stream Unique Identifiers](class.html#stream-unique-identifiers)

### 5 [Versioning of Serializable Objects](version.html)

-   5.1 [Overview](version.html#overview)
-   5.2 [Goals](version.html#goals)
-   5.3 [Assumptions](version.html#assumptions)
-   5.4 [Who's Responsible for Versioning of
    Streams](version.html#whos-responsible-for-versioning-of-streams)
-   5.5 [Compatible Java Type
    Evolution](version.html#compatible-java-type-evolution)
-   5.6 [Type Changes Affecting
    Serialization](version.html#type-changes-affecting-serialization)
    -   5.6.1 [Incompatible Changes](version.html#incompatible-changes)
    -   5.6.2 [Compatible Changes](version.html#compatible-changes)

### 6 [Object Serialization Stream Protocol](protocol.html)

-   6.1 [Overview](protocol.html#overview)
-   6.2 [Stream Elements](protocol.html#stream-elements)
-   6.3 [Stream Protocol Versions](protocol.html#stream-protocol-versions)
-   6.4 [Grammar for the Stream
    Format](protocol.html#grammar-for-the-stream-format)
    -   6.4.1 [Rules of the Grammar](protocol.html#rules-of-the-grammar)
    -   6.4.2 [Terminal Symbols and
        Constants](protocol.html#terminal-symbols-and-constants)

### A [Security in Object Serialization](security.html)

### B [Exceptions In Object Serialization](exceptions.html)

### C [Example of Serializable Fields](examples.html)

-   [C.1 Example Alternate Implementation of
    `java.io.File`](examples.html#c.1-example-alternate-implementation-of-java.io.file)

-------------------------------------------------------------------------------

*[Copyright](../../../legal/SMICopyright.html) &copy; 2005, 2017, Oracle
and/or its affiliates. All rights reserved.*
