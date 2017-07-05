/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug     4684279
 * @summary Empty utility collections should be singletons
 * @author  Josh Bloch
 */

import java.util.*;
import java.io.*;

public class EmptyCollectionSerialization {
    private static Object patheticDeepCopy(Object o) throws Exception {
        // Serialize
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        byte[] serializedForm = bos.toByteArray();

        // Deserialize
        InputStream is = new ByteArrayInputStream(serializedForm);
        ObjectInputStream ois = new ObjectInputStream(is);
        return ois.readObject();
    }

    private static boolean isSingleton(Object o) throws Exception {
        return patheticDeepCopy(o) == o;
    }

    public static void main(String[] args) throws Exception {
        if (!isSingleton(Collections.EMPTY_SET))
            throw new Exception("EMPTY_SET");
        if (!isSingleton(Collections.EMPTY_LIST))
            throw new Exception("EMPTY_LIST");
        if (!isSingleton(Collections.EMPTY_MAP))
            throw new Exception("EMPTY_MAP");
    }
}
