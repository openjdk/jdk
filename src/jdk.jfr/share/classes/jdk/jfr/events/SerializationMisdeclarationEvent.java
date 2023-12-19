/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.internal.MirrorEvent;
import jdk.jfr.internal.RemoveFields;
import jdk.jfr.internal.Type;

@Name(Type.EVENT_NAME_PREFIX + "SerializationMisdeclaration")
@Label("Serialization Deserialization")
@Category({"Java Development Kit", "Serialization"})
@Description("Methods and fields misdeclarations")
@MirrorEvent(className = "jdk.internal.event.SerializationMisdeclarationEvent")
@RemoveFields({"duration", "stackTrace", "eventThread"})
public final class SerializationMisdeclarationEvent extends AbstractJDKEvent {

    @Label("MisdeclaredClass")
    public Class<?> cls;

    @Label("Kind")
    public int kind;

    @Label("Message")
    public String message;

    /*
     * These constants are not final on purpose.
     */
    public static int SUID_EXPLICIT                   = jdk.internal.event.SerializationMisdeclarationEvent.SUID_EXPLICIT;
    public static int SUID_INEFFECTIVE_ENUM           = jdk.internal.event.SerializationMisdeclarationEvent.SUID_INEFFECTIVE_ENUM;
    public static int SUID_PRIVATE                    = jdk.internal.event.SerializationMisdeclarationEvent.SUID_PRIVATE;
    public static int SUID_STATIC                     = jdk.internal.event.SerializationMisdeclarationEvent.SUID_STATIC;
    public static int SUID_FINAL                      = jdk.internal.event.SerializationMisdeclarationEvent.SUID_FINAL;
    public static int SUID_LONG                       = jdk.internal.event.SerializationMisdeclarationEvent.SUID_LONG;
    public static int SUID_CONVERTIBLE_TO_LONG        = jdk.internal.event.SerializationMisdeclarationEvent.SUID_CONVERTIBLE_TO_LONG;

    public static int SER_PERS_INEFFECTIVE_ENUM       = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_INEFFECTIVE_ENUM;
    public static int SER_PERS_INEFFECTIVE_RECORD     = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_INEFFECTIVE_RECORD;
    public static int SER_PERS_PRIVATE                = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_PRIVATE;
    public static int SER_PERS_STATIC                 = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_STATIC;
    public static int SER_PERS_FINAL                  = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_FINAL;
    public static int SER_PERS_NOT_NULL               = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_NOT_NULL;
    public static int SER_PERS_TYPE_OSF_ARRAY         = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_TYPE_OSF_ARRAY;
    public static int SER_PERS_VALUE_OSF_ARRAY        = jdk.internal.event.SerializationMisdeclarationEvent.SER_PERS_VALUE_OSF_ARRAY;

    public static int PRIV_METH_INEFFECTIVE_ENUM      = jdk.internal.event.SerializationMisdeclarationEvent.PRIV_METH_INEFFECTIVE_ENUM;
    public static int PRIV_METH_INEFFECTIVE_RECORD    = jdk.internal.event.SerializationMisdeclarationEvent.PRIV_METH_INEFFECTIVE_RECORD;
    public static int PRIV_METH_PRIV                  = jdk.internal.event.SerializationMisdeclarationEvent.PRIV_METH_PRIV;
    public static int PRIV_METH_NON_STATIC            = jdk.internal.event.SerializationMisdeclarationEvent.PRIV_METH_NON_STATIC;
    public static int PRIV_METH_RET_TYPE              = jdk.internal.event.SerializationMisdeclarationEvent.PRIV_METH_RET_TYPE;
    public static int PRIV_METH_PARAM_TYPES           = jdk.internal.event.SerializationMisdeclarationEvent.PRIV_METH_PARAM_TYPES;

    public static int ACC_METH_INEFFECTIVE_ENUM       = jdk.internal.event.SerializationMisdeclarationEvent.ACC_METH_INEFFECTIVE_ENUM;
    public static int ACC_METH_NON_ABSTRACT           = jdk.internal.event.SerializationMisdeclarationEvent.ACC_METH_NON_ABSTRACT;
    public static int ACC_METH_NON_STATIC             = jdk.internal.event.SerializationMisdeclarationEvent.ACC_METH_NON_STATIC;
    public static int ACC_METH_RET_TYPE               = jdk.internal.event.SerializationMisdeclarationEvent.ACC_METH_RET_TYPE;
    public static int ACC_METH_PARAM_TYPES            = jdk.internal.event.SerializationMisdeclarationEvent.ACC_METH_PARAM_TYPES;
    public static int ACC_METH_NON_ACCESSIBLE         = jdk.internal.event.SerializationMisdeclarationEvent.ACC_METH_NON_ACCESSIBLE;

}
