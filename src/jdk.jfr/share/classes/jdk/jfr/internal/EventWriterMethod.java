/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.internal;

import jdk.jfr.internal.util.Bytecode.FieldDesc;
import jdk.jfr.internal.util.Bytecode.MethodDesc;
import jdk.jfr.internal.util.ImplicitFields;

public enum EventWriterMethod {

     BEGIN_EVENT("beginEvent", "(Ljdk/jfr/internal/event/EventConfiguration;J)Z", "???"),
     END_EVENT("endEvent", "()Z", "???"),
     PUT_BYTE("putByte", "(B)V", "B"),
     PUT_SHORT("putShort", "(S)V", "S"),
     PUT_INT("putInt", "(I)V", "I"),
     PUT_LONG("putLong", "(J)V", "J"),
     PUT_FLOAT("putFloat", "(F)V", "F"),
     PUT_DOUBLE("putDouble", "(D)V", "D"),
     PUT_CHAR("putChar", "(C)V", "C"),
     PUT_BOOLEAN("putBoolean", "(Z)V", "Z"),
     PUT_THREAD("putThread", "(Ljava/lang/Thread;)V", "Ljava/lang/Thread;"),
     PUT_CLASS("putClass", "(Ljava/lang/Class;)V", "Ljava/lang/Class;"),
     PUT_STRING("putString", "(Ljava/lang/String;)V", "Ljava/lang/String;"),
     PUT_EVENT_THREAD("putEventThread", "()V", "???"),
     PUT_STACK_TRACE("putStackTrace", "()V", "???");

    final MethodDesc method;
    final String fieldType;

    EventWriterMethod(String methodName, String paramType, String fieldType) {
        this.fieldType = fieldType;
        this.method = MethodDesc.of(methodName, paramType);
    }

    public MethodDesc method() {
        return method;
    }

    /**
     * Return method in {@link EventWriter} class to use when writing event of
     * a certain type.
     *
     * @param v field info
     *
     * @return the method
     */
    public static EventWriterMethod lookupMethod(FieldDesc field) {
        // event thread
        if (field.name().equals(ImplicitFields.EVENT_THREAD)) {
            return EventWriterMethod.PUT_EVENT_THREAD;
        }
        for (EventWriterMethod m : EventWriterMethod.values()) {
            if (field.type().descriptorString().equals(m.fieldType)) {
                return m;
            }
        }
        throw new Error("Unknown field type " + field.type());
    }
}
