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

include-before: '[CONTENTS](index.html) | [PREV](exceptions.html) | NEXT'
include-after: '[CONTENTS](index.html) | [PREV](exceptions.html) | NEXT'

title: 'Java Object Serialization Specification: C - Example of Serializable Fields'
---

-   [Example Alternate Implementation of
    java.io.File](#c.1-example-alternate-implementation-of-java.io.file)

-------------------------------------------------------------------------------

## C.1 Example Alternate Implementation of java.io.File

This appendix provides a brief example of how an existing class could be
specified and implemented to interoperate with the existing implementation but
without requiring the same assumptions about the representation of the file
name as a *String*.

The system class `java.io.File` represents a filename and has methods for
parsing, manipulating files and directories by name. It has a single private
field that contains the current file name. The semantics of the methods that
parse paths depend on the current path separator which is held in a static
field. This path separator is part of the serialized state of a file so that
file name can be adjusted when read.

The serialized state of a `File` object is defined as the serializable fields
and the sequence of data values for the file. In this case, there is one of
each.

```
Serializable Fields:
    String path;     // path name with embedded separators
Serializable Data:
    char            // path name separator for path name
```

An alternate implementation might be defined as follows:

```
class File implements java.io.Serializable {
    ...
    private String[] pathcomponents;
    // Define serializable fields with the ObjectStreamClass

    /**
     * @serialField path String
     *              Path components separated by separator.
     */

    private static final ObjectStreamField[] serialPersistentFields
        = { new ObjectStreamField("path", String.class) };
    ...
        /**
         * @serialData  Default fields followed by separator character.
         */

    private void writeObject(ObjectOutputStream s)
        throws IOException
    {
        ObjectOutputStream.PutField fields = s.putFields();
        StringBuffer str = new StringBuffer();
        for(int i = 0; i < pathcomponents; i++) {
            str.append(separator);
            str.append(pathcomponents[i]);
        }
        fields.put("path", str.toString());
        s.writeFields();
        s.writeChar(separatorChar); // Add the separator character
    }
    ...

    private void readObject(ObjectInputStream s)
        throws IOException
    {
        ObjectInputStream.GetField fields = s.readFields();
        String path = (String)fields.get("path", null);
        ...
        char sep = s.readChar(); // read the previous separator char

        // parse path into components using the separator
        // and store into pathcomponents array.
    }
}
```

-------------------------------------------------------------------------------

*[Copyright](../../../legal/SMICopyright.html) &copy; 2005, 2017, Oracle
and/or its affiliates. All rights reserved.*
