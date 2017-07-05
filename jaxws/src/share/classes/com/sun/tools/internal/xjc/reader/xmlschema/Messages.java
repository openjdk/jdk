/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.internal.xjc.reader.xmlschema;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Formats error messages.
 */
public class Messages
{
    /** Loads a string resource and formats it with specified arguments. */
    public static String format( String property, Object... args ) {
        String text = ResourceBundle.getBundle(Messages.class.getPackage().getName() +".MessageBundle").getString(property);
        return MessageFormat.format(text,args);
    }


    static final String WARN_NO_GLOBAL_ELEMENT =
        "BGMBuilder.NoGlobalElement";

    static final String ERR_MULTIPLE_SCHEMA_BINDINGS =
        "BGMBuilder.MultipleSchemaBindings"; // arg:1

    static final String ERR_MULTIPLE_SCHEMA_BINDINGS_LOCATION =
        "BGMBuilder.MultipleSchemaBindings.Location"; // arg:0

    static final String JAVADOC_HEADING = // 1 arg
        "ClassSelector.JavadocHeading";

    static final String ERR_RESERVED_CLASS_NAME = // 1 arg
        "ClassSelector.ReservedClassName";

    static final String ERR_CLASS_NAME_IS_REQUIRED =
        "ClassSelector.ClassNameIsRequired";    // arg:0

    static final String ERR_INCORRECT_CLASS_NAME =
        "ClassSelector.IncorrectClassName";     // arg:1

    static final String ERR_INCORRECT_PACKAGE_NAME =
        "ClassSelector.IncorrectPackageName";   // arg:2

    static final String ERR_CANNOT_BE_TYPE_SAFE_ENUM =
        "ConversionFinder.CannotBeTypeSafeEnum";            // arg:0

    static final String ERR_CANNOT_BE_TYPE_SAFE_ENUM_LOCATION =
        "ConversionFinder.CannotBeTypeSafeEnum.Location";    // arg:0

    static final String ERR_NO_ENUM_NAME_AVAILABLE =
        "ConversionFinder.NoEnumNameAvailable"; // arg:0

    static final String ERR_ILLEGAL_EXPECTED_MIME_TYPE =
        "ERR_ILLEGAL_EXPECTED_MIME_TYPE"; // args:2

    static final String ERR_DATATYPE_ERROR =
        "DatatypeBuilder.DatatypeError"; // arg:1

    static final String ERR_UNABLE_TO_GENERATE_NAME_FROM_MODELGROUP =
        "DefaultParticleBinder.UnableToGenerateNameFromModelGroup"; // arg:0

    static final String ERR_INCORRECT_FIXED_VALUE =
        "FieldBuilder.IncorrectFixedValue"; // arg:1

    static final String ERR_INCORRECT_DEFAULT_VALUE =
        "FieldBuilder.IncorrectDefaultValue"; // arg:1

    static final String ERR_CONFLICT_BETWEEN_USERTYPE_AND_ACTUALTYPE_ATTUSE =
        "FieldBuilder.ConflictBetweenUserTypeAndActualType.AttUse"; // arg:2

    static final String ERR_CONFLICT_BETWEEN_USERTYPE_AND_ACTUALTYPE_ATTUSE_SOURCE =
        "FieldBuilder.ConflictBetweenUserTypeAndActualType.AttUse.Source"; // arg:0

    static final String ERR_UNNESTED_JAVATYPE_CUSTOMIZATION_ON_SIMPLETYPE =
        "SimpleTypeBuilder.UnnestedJavaTypeCustomization"; // arg:0

    static final String JAVADOC_NIL_PROPERTY =
        "FieldBuilder.Javadoc.NilProperty"; // arg:1

    static final String JAVADOC_LINE_UNKNOWN = // 0 args
        "ClassSelector.JavadocLineUnknown";

    static final String JAVADOC_VALUEOBJECT_PROPERTY =
        "FieldBuilder.Javadoc.ValueObject"; // args:2

    static final String MSG_COLLISION_INFO =
        "CollisionInfo.CollisionInfo"; // args:3

    static final String MSG_UNKNOWN_FILE =
        "CollisionInfo.UnknownFile"; // arg:1

    static final String MSG_LINE_X_OF_Y =
        "CollisionInfo.LineXOfY"; // args:2

    static final String MSG_FALLBACK_JAVADOC =
        "DefaultParticleBinder.FallbackJavadoc"; // arg:1

    static final String ERR_ENUM_MEMBER_NAME_COLLISION =
        "ERR_ENUM_MEMBER_NAME_COLLISION";
    static final String ERR_ENUM_MEMBER_NAME_COLLISION_RELATED =
        "ERR_ENUM_MEMBER_NAME_COLLISION_RELATED";
    static final String ERR_CANNOT_GENERATE_ENUM_NAME =
        "ERR_CANNOT_GENERATE_ENUM_NAME";


    // they are shared from the model
    public static final String ERR_UNACKNOWLEDGED_CUSTOMIZATION =
        "UnusedCustomizationChecker.UnacknolwedgedCustomization"; // arg:1
    public static final String ERR_UNACKNOWLEDGED_CUSTOMIZATION_LOCATION =
        "UnusedCustomizationChecker.UnacknolwedgedCustomization.Relevant"; // arg:0

    public static final String ERR_MULTIPLE_GLOBAL_BINDINGS =
        "ERR_MULTIPLE_GLOBAL_BINDINGS";
    public static final String ERR_MULTIPLE_GLOBAL_BINDINGS_OTHER =
        "ERR_MULTIPLE_GLOBAL_BINDINGS_OTHER";

    public static final String ERR_REFERENCE_TO_NONEXPORTED_CLASS =
        "ERR_REFERENCE_TO_NONEXPORTED_CLASS";
    public static final String ERR_REFERENCE_TO_NONEXPORTED_CLASS_MAP_FALSE =
        "ERR_REFERENCE_TO_NONEXPORTED_CLASS_MAP_FALSE";
    public static final String ERR_REFERENCE_TO_NONEXPORTED_CLASS_REFERER =
        "ERR_REFERENCE_TO_NONEXPORTED_CLASS_REFERER";
}
