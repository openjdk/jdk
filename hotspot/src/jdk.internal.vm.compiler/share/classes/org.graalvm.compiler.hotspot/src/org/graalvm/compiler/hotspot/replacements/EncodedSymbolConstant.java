/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;

import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.type.DataPointerConstant;

import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;

/**
 * Represents an encoded representation of a constant.
 */
public final class EncodedSymbolConstant extends DataPointerConstant {
    private final Constant constant;
    private byte[] bytes;

    public EncodedSymbolConstant(Constant constant) {
        super(1);
        this.constant = constant;
    }

    @Override
    public int getSerializedSize() {
        return getEncodedConstant().length;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        buffer.put(getEncodedConstant());
    }

    /**
     * Converts a string to a byte array with modified UTF-8 encoding. The first two bytes of the
     * byte array store the length of the string in bytes.
     *
     * @param s a java.lang.String in UTF-16
     */
    private static byte[] toUTF8String(String s) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            DataOutputStream stream = new DataOutputStream(bytes);
            stream.writeUTF(s);
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new PermanentBailoutException(e, "String conversion failed: %s", s);
        }
    }

    private static byte[] encodeConstant(Constant constant) {
        assert constant != null;
        if (constant instanceof HotSpotObjectConstant) {
            return toUTF8String(((HotSpotObjectConstant) constant).asObject(String.class));
        } else if (constant instanceof HotSpotMetaspaceConstant) {
            HotSpotMetaspaceConstant metaspaceConstant = ((HotSpotMetaspaceConstant) constant);
            HotSpotResolvedObjectType klass = metaspaceConstant.asResolvedJavaType();
            if (klass != null) {
                return toUTF8String(klass.getName());
            }
            HotSpotResolvedJavaMethod method = metaspaceConstant.asResolvedJavaMethod();
            if (method != null) {
                byte[] methodName = toUTF8String(method.getName());
                byte[] signature = toUTF8String(method.getSignature().toMethodDescriptor());
                byte[] result = new byte[methodName.length + signature.length];
                int resultPos = 0;
                System.arraycopy(methodName, 0, result, resultPos, methodName.length);
                resultPos += methodName.length;
                System.arraycopy(signature, 0, result, resultPos, signature.length);
                resultPos += signature.length;
                assert resultPos == result.length;
                return result;
            }

        }
        throw new PermanentBailoutException("Encoding of constant %s failed", constant);
    }

    public byte[] getEncodedConstant() {
        if (bytes == null) {
            bytes = encodeConstant(constant);
        }
        return bytes;
    }

    @Override
    public String toValueString() {
        return "encoded symbol\"" + constant.toValueString() + "\"";
    }

}
