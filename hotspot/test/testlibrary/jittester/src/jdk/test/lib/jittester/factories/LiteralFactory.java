/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester.factories;

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeBoolean;
import jdk.test.lib.jittester.types.TypeByte;
import jdk.test.lib.jittester.types.TypeChar;
import jdk.test.lib.jittester.types.TypeDouble;
import jdk.test.lib.jittester.types.TypeFloat;
import jdk.test.lib.jittester.types.TypeInt;
import jdk.test.lib.jittester.types.TypeLong;
import jdk.test.lib.jittester.types.TypeShort;
import jdk.test.lib.jittester.utils.PseudoRandom;

class LiteralFactory extends Factory {
    protected final Type resultType;

    LiteralFactory(Type resultType) {
        this.resultType = resultType;
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        Literal literal;
        if (resultType.equals(new TypeBoolean())) {
            literal = new Literal(PseudoRandom.randomBoolean(), new TypeBoolean());
        } else if (resultType.equals(new TypeChar())) {
            literal = new Literal((char) ((char) (PseudoRandom.random() * ('z' - 'A')) + 'A'), new TypeChar());
        } else if (resultType.equals(new TypeInt())) {
            literal = new Literal((int) (PseudoRandom.random() * Integer.MAX_VALUE), new TypeInt());
        } else if (resultType.equals(new TypeLong())) {
            literal = new Literal((long) (PseudoRandom.random() * Long.MAX_VALUE), new TypeLong());
        } else if (resultType.equals(new TypeFloat())) {
            literal = new Literal((float) (PseudoRandom.random() * Float.MAX_VALUE), new TypeFloat());
        } else if (resultType.equals(new TypeDouble())) {
            literal = new Literal(PseudoRandom.random() * Double.MAX_VALUE, new TypeDouble());
        } else if (resultType.equals(new TypeByte())) {
            literal = new Literal((byte)(PseudoRandom.random() * Byte.MAX_VALUE),new TypeByte());
        } else if (resultType.equals(new TypeShort())) {
            literal = new Literal((short)(PseudoRandom.random() * Short.MAX_VALUE), new TypeShort());
        } else if (resultType.equals(TypeList.find("java.lang.String"))) {
            int size = (int) (PseudoRandom.random() * ProductionParams.stringLiteralSizeLimit.value());
            byte[] str = new byte[size];
            for (int i = 0; i < size; i++) {
                str[i] = (byte) ((int) (('z' - 'a') * PseudoRandom.random()) + 'a');
            }
            literal = new Literal("\"" + new String(str) + "\"", TypeList.find("java.lang.String"));
        } else {
            throw new ProductionFailedException();
        }
        return literal;
    }
}
