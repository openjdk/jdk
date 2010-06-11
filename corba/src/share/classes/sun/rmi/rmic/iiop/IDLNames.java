/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic.iiop;

import java.util.Hashtable;
import java.util.Locale;
import sun.tools.java.Identifier;
import sun.tools.java.CompilerError;
import sun.tools.java.ClassDefinition;
import sun.tools.java.ClassNotFound;
import com.sun.corba.se.impl.util.RepositoryId;

/**
 * IDLNames provides static utility methods to perform the IDL
 * name mappings specified in Chapter 5 of the Java Language
 * to IDL specification.
 *
 * @author      Bryan Atsatt
 */
public class IDLNames implements sun.rmi.rmic.iiop.Constants {

    /**
     * Used to convert ascii to hex.
     */
    public static final byte ASCII_HEX[] =      {
        (byte)'0',
        (byte)'1',
        (byte)'2',
        (byte)'3',
        (byte)'4',
        (byte)'5',
        (byte)'6',
        (byte)'7',
        (byte)'8',
        (byte)'9',
        (byte)'A',
        (byte)'B',
        (byte)'C',
        (byte)'D',
        (byte)'E',
        (byte)'F',
    };

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Convert a name. The nameContext argument MUST be pre-filled with
     * all names from the appropriate context (e.g. all the method names
     * in a given class). The names must not have had any IDL conversions
     * applied.
     * <p>
     * Section 28.3.2.2
     * Section 28.3.2.3
     * Section 28.3.2.4
     * Section 28.3.2.7 (member and method names only)
     */
    public static String getMemberOrMethodName (NameContext nameContext,
                                                String name,
                                                BatchEnvironment env) {

        // Check namesCache...

        String result = (String) env.namesCache.get(name);

        if (result == null) {

            // 28.3.2.7 Case sensitive member names.

            // Note:    This must be done before any of
            //          the other conversions!

            result = nameContext.get(name);

            // 28.3.2.3 Leading underscores...

            result = convertLeadingUnderscores(result);

            // 28.3.2.2 IDL keywords (NOTE: must be done
            // after leading underscore conversion because
            // the mangling for IDL keywords creates a
            // leading underscore!)...

            result = convertIDLKeywords(result);

            // 28.3.2.4 Illegal IDL identifier characters...

            result = convertToISOLatin1(result);

            // Add to namesCache...

            env.namesCache.put(name,result);
        }

        return result;
    }

    /**
     * Convert names with illegal IDL identifier characters.
     * <p>
     * Section 28.3.2.4
     */
    public static String convertToISOLatin1 (String name) {

        // First, replace any escape sequences...

        String result = replace(name,"x\\u","U");
        result = replace(result,"x\\U","U");

        // Now see if we have any remaining illegal characters (see
        // RepositoryId.IDL_IDENTIFIER_CHARS array)...

        int length = result.length();
        StringBuffer buffer = null;

        for (int i = 0; i < length; i++) {

            char c = result.charAt(i);

            if (c > 255 || RepositoryId.IDL_IDENTIFIER_CHARS[c] == 0) {

                // We gotta convert. Have we already started?

                if (buffer == null) {

                    // No, so get set up...

                    buffer = new StringBuffer(result.substring(0,i));
                }

                // Convert the character into the IDL escape syntax...

                buffer.append("U");
                buffer.append((char)ASCII_HEX[(c & 0xF000) >>> 12]);
                buffer.append((char)ASCII_HEX[(c & 0x0F00) >>> 8]);
                buffer.append((char)ASCII_HEX[(c & 0x00F0) >>> 4]);
                buffer.append((char)ASCII_HEX[(c & 0x000F)]);

            } else {
                if (buffer != null) {
                    buffer.append(c);
                }
            }
        }

        if (buffer != null) {
            result = buffer.toString();
        }

        return result;
    }

    /**
     * Convert names which collide with IDL keywords.
     * <p>
     * Section 28.3.2.5
     */
    public static String convertIDLKeywords (String name) {

        for (int i = 0; i < IDL_KEYWORDS.length; i++) {
            if (name.equalsIgnoreCase(IDL_KEYWORDS[i])) {
                return "_" + name;
            }
        }

        return name;
    }

    /**
     * Convert names which have leading underscores
     * <p>
     * Section 28.3.2.3
     */
    public static String convertLeadingUnderscores (String name) {

        if (name.startsWith("_")) {
            return "J" + name;
        }

        return name;
    }

    /**
     * Convert a type name.
     * <p>
     * Section 28.3.2.5
     * Section 28.3.2.7 (class or interface names only)
     * Throws exception if fails 28.3.2.7.
     */
    public static String getClassOrInterfaceName (Identifier id,
                                                  BatchEnvironment env) throws Exception {

        // Get the type and package name...

        String typeName = id.getName().toString();
        String packageName = null;

        if (id.isQualified()) {
            packageName = id.getQualifier().toString();
        }

        // Check namesCache...

        String result = (String) env.namesCache.get(typeName);

        if (result == null) {

            // 28.3.2.5 Inner classes...

            result = replace(typeName,". ","__");

            // 28.3.2.4 Illegal identifier characters...

            result = convertToISOLatin1(result);

            // 28.3.2.7 Case sensitive class or interface names...

            NameContext context = NameContext.forName(packageName,false,env);
            context.assertPut(result);

            // Run it through the name checks...

            result = getTypeOrModuleName(result);

            // Add it to the namesCache...

            env.namesCache.put(typeName,result);
        }

        return result;
    }

    /**
     * Convert an Exception name.
     * <p>
     * Section 28.3.7.2    (see ValueType)
     */
    public static String getExceptionName (String idlName) {

        String result = idlName;
// d.11315 Incorrectly mangled exception names
        if (idlName.endsWith(EXCEPTION_SUFFIX)) {

            // Remove "Exception" and append "Ex". Strip leading underscore
            // in case the idlName is exactly "_Exception"...

            result = stripLeadingUnderscore(idlName.substring(0,idlName.lastIndexOf(EXCEPTION_SUFFIX)) + EX_SUFFIX);
        } else {
            result = idlName + EX_SUFFIX;
        }

        return result;
    }

    /**
     * Convert a qualified Identifier into an array of IDL names.
     * <p>
     * Section 28.3.2.1    (see CompoundType)
     * Throws exception if fails 28.3.2.7.
     */
    public static String[] getModuleNames (Identifier theID,
                                           boolean boxIt,
                                           BatchEnvironment env) throws Exception {

        String[] result = null;

        if (theID.isQualified()) {

            // Extract the qualifier...

            Identifier id = theID.getQualifier();

            // 28.3.2.7 Case sensitive module names.

            env.modulesContext.assertPut(id.toString());

            // Count them...

            int count = 1;
            Identifier current = id;
            while (current.isQualified()) {
                current = current.getQualifier();
                count++;
            }

            result = new String[count];
            int index = count-1;
            current = id;

            // Now walk them and fill our array (backwards)...

            for (int i = 0; i < count; i++) {

                String item = current.getName().toString();

                // Check namesCache...

                String cachedItem = (String) env.namesCache.get(item);

                if (cachedItem == null) {

                    // 28.3.2.4 Illegal identifier characters...

                    cachedItem = convertToISOLatin1(item);

                    // Run it through the name checks...

                    cachedItem = getTypeOrModuleName(cachedItem);

                    // Add it to the namesCache...

                    env.namesCache.put(item,cachedItem);
                }

                result[index--] = cachedItem;
                current = current.getQualifier();
            }
        }


        // If it is supposed to be "boxed", prepend
        // IDL_BOXEDIDL_MODULE...

        if (boxIt) {
            if (result == null) {
                result = IDL_BOXEDIDL_MODULE;
            } else {
            String[] boxed = new String[result.length+IDL_BOXEDIDL_MODULE.length];
            System.arraycopy(IDL_BOXEDIDL_MODULE,0,boxed,0,IDL_BOXEDIDL_MODULE.length);
            System.arraycopy(result,0,boxed,IDL_BOXEDIDL_MODULE.length,result.length);
            result = boxed;
        }
        }

        return result;
    }

    /**
     * Get an array name with the specified dimensions.
     * <p>
     * Section 28.3.6  (see ArrayType)
     */
    public static String getArrayName (Type theType, int arrayDimension) {

        StringBuffer idlName = new StringBuffer(64);

        // Prefix with seq<n>_...

        idlName.append(IDL_SEQUENCE);
        idlName.append(Integer.toString(arrayDimension));
        idlName.append("_");

        // Add the type name. We need to map any spaces in the
        // name to "_"...

        idlName.append(replace(stripLeadingUnderscore(theType.getIDLName())," ","_"));

        // And we're done...

        return idlName.toString();
    }

    /**
     * Get an array module names.
     */
    public static String[] getArrayModuleNames (Type theType) {

        String[] moduleName;
        String[] typeModule = theType.getIDLModuleNames();
        int typeModuleLength = typeModule.length;

        // Does the type have a module?

        if (typeModuleLength == 0) {

            // Nope, so just use the sequence module...

            moduleName = IDL_SEQUENCE_MODULE;
        } else {

            // Yes, so gotta concatenate...

            moduleName = new String[typeModuleLength + IDL_SEQUENCE_MODULE.length];
            System.arraycopy(IDL_SEQUENCE_MODULE,0,moduleName,0,IDL_SEQUENCE_MODULE.length);
            System.arraycopy(typeModule,0,moduleName,IDL_SEQUENCE_MODULE.length,typeModuleLength);
        }

        return moduleName;
    }

    private static int getInitialAttributeKind (CompoundType.Method method,
                                                BatchEnvironment env) throws ClassNotFound {

        int result = ATTRIBUTE_NONE;

        // First make sure it is not a constructor...

        if (!method.isConstructor()) {

            // Now check exceptions. It may not throw any checked
            // exception other than RemoteException or one of its
            // subclasses...

            boolean validExceptions = true;
            ClassType[] exceptions = method.getExceptions();

            if (exceptions.length > 0) {
                for (int i = 0; i < exceptions.length; i++) {
                    if (exceptions[i].isCheckedException() &&
                        !exceptions[i].isRemoteExceptionOrSubclass()) {
                        validExceptions = false;
                        break;
                    }
                }
            } else {

                // If this is a ValueType, it is ok to not have any exceptions,
                // otherwise this method does not qualify...

                validExceptions = method.getEnclosing().isType(TYPE_VALUE);
            }

            if (validExceptions) {
                String name = method.getName();
                int nameLength = name.length();
                int argCount = method.getArguments().length;
                Type returnType = method.getReturnType();
                boolean voidReturn = returnType.isType(TYPE_VOID);
                boolean booleanReturn = returnType.isType(TYPE_BOOLEAN);

                // It's a getter if name starts with "get" and it has no arguments
                // and a return type that is not void...

                if (name.startsWith("get") && nameLength > 3 && argCount == 0 && !voidReturn) {
                    result = ATTRIBUTE_GET;
                } else {

                    // It's a getter if name starts with "is" and it has no arguments
                    // and a boolean return type...

                    if (name.startsWith("is") && nameLength > 2 && argCount == 0 && booleanReturn) {
                        result = ATTRIBUTE_IS;
                    } else {

                        // It's a setter if name starts with "set" and it has 1 argument
                        // and a void return type...

                        if (name.startsWith("set") && nameLength > 3 && argCount == 1 && voidReturn) {
                            result = ATTRIBUTE_SET;
                        }
                    }
                }
            }
        }

        return result;
    }

    private static void setAttributeKinds (CompoundType.Method[] methods,
                                           int[] kinds,
                                           String[] names) {

        int count = methods.length;

        // Strip the prefixes off of the attribute names...

        for (int i = 0; i < count; i++) {
            switch (kinds[i]) {
                case ATTRIBUTE_GET: names[i] = names[i].substring(3); break;
                case ATTRIBUTE_IS: names[i] = names[i].substring(2); break;
                case ATTRIBUTE_SET: names[i] = names[i].substring(3); break;
            }
        }

        // Now, we need to look at all the IS attributes to see
        // if there is a corresponding getter or setter which has
        // a different return type. If so, mark it as not an
        // attribute. Do this before checking for invalid setters...

        for (int i = 0; i < count; i++) {
            if (kinds[i] == ATTRIBUTE_IS) {
                for (int j = 0; j < count; j++) {
                    if (j != i &&
                        (kinds[j] == ATTRIBUTE_GET || kinds[j] == ATTRIBUTE_SET) &&
                        names[i].equals(names[j])) {

                        // We have matching getter or setter. Do the types match?

                        Type isType = methods[i].getReturnType();
                        Type targetType;

                        if (kinds[j] == ATTRIBUTE_GET) {
                            targetType = methods[j].getReturnType();
                        } else {
                            targetType = methods[j].getArguments()[0];
                        }

                        if (!isType.equals(targetType)) {

                            // No, so forget this guy as an attribute...

                            kinds[i] = ATTRIBUTE_NONE;
                            names[i] = methods[i].getName();
                            break;
                        }
                    }
                }
            }
        }

        // Now, we need to look at all the setters to see if there
        // is a corresponding getter. If not, it is not a setter.
        // If there is, change the getter type to _RW and set the
        // pair index...

        for (int i = 0; i < count; i++) {
            if (kinds[i] == ATTRIBUTE_SET) {
                int getterIndex = -1;
                int isGetterIndex = -1;
                // First look for is-getters, then for getters.
                // This is preferred for boolean attributes.
                for (int j = 0; j < count; j++) {
                    if (j != i && names[i].equals(names[j])) {
                        // Yep, is the return type of the getter the same
                        // as the argument type of the setter?

                        Type getterReturn = methods[j].getReturnType();
                        Type setterArg = methods[i].getArguments()[0];

                        if (getterReturn.equals(setterArg)) {
                            if (kinds[j] == ATTRIBUTE_IS) {
                                isGetterIndex = j;
                                // continue looking for another getter
                            } else if (kinds[j] == ATTRIBUTE_GET) {
                                getterIndex = j;
                                // continue looking for an is-getter
                            }
                        }
                    }
                }

                if (getterIndex > -1) {
                    if (isGetterIndex > -1) {
                        // We have both, a boolean is-getter and a boolean getter.
                        // Use the is-getter and drop the getter.

                        // We have a matching getter. Change it to a read-write type...
                        kinds[isGetterIndex] = ATTRIBUTE_IS_RW;

                        // Now set the pair index for both the getter and the setter...
                        methods[isGetterIndex].setAttributePairIndex(i);
                        methods[i].setAttributePairIndex(isGetterIndex);

                        // We found a better matching is-getter.
                        // Forget this other getter as an attribute.
                        kinds[getterIndex] = ATTRIBUTE_NONE;
                        names[getterIndex] = methods[getterIndex].getName();
                    } else {
                        // We only have one getter.

                        // We have a matching getter. Change it to a read-write type...
                        kinds[getterIndex] = ATTRIBUTE_GET_RW;

                        // Now set the pair index for both the getter and the setter...
                        methods[getterIndex].setAttributePairIndex(i);
                        methods[i].setAttributePairIndex(getterIndex);
                    }
                } else {
                    if (isGetterIndex > -1) {
                        // We only have one is-getter.

                        // We have a matching getter. Change it to a read-write type...
                        kinds[isGetterIndex] = ATTRIBUTE_IS_RW;

                        // Now set the pair index for both the getter and the setter...
                        methods[isGetterIndex].setAttributePairIndex(i);
                        methods[i].setAttributePairIndex(isGetterIndex);
                    } else {
                        // We did not find a matching getter.
                        // Forget this setter as an attribute.
                        kinds[i] = ATTRIBUTE_NONE;
                        names[i] = methods[i].getName();
                    }
                }
            }
        }

        // Finally, do the case conversion and set the
        // attribute kinds for each method...

        for (int i = 0; i < count; i++) {

            if (kinds[i] != ATTRIBUTE_NONE) {

                String name = names[i];

                // Is the first character upper case?

                if (Character.isUpperCase(name.charAt(0))) {

                    // Yes, is the second?

                    if (name.length() == 1 || Character.isLowerCase(name.charAt(1))) {

                        // No, so convert the first character to lower case...

                        StringBuffer buffer = new StringBuffer(name);
                        buffer.setCharAt(0,Character.toLowerCase(name.charAt(0)));
                        names[i] = buffer.toString();
                    }
                }
            }

            methods[i].setAttributeKind(kinds[i]);
        }
    }

    /**
     * Set all the method names in a given class.
     * <p>
     * Section 28.3.2.7    (see CompoundType)
     * Section 28.3.2.7
     * Section 28.3.4.3 (RemoteType/AbstractType only).
     */
    public static void setMethodNames (CompoundType container,
                                       CompoundType.Method[] allMethods,
                                       BatchEnvironment env)
        throws Exception {

        // This method implements the following name mangling sequence:
        //
        //   1. If methods belong to a Remote interface, identify
        //      those which qualify as an attribute under 28.3.4.3.
        //      Those that do are referred to as 'attributes' below;
        //      those that do not are referred to as 'methods'.
        //
        //   2. Apply the 28.3.4.3 manglings, except "__", to all
        //      attribute names.
        //
        //   3. Apply all 28.3 manglings, except 28.3.2.7, to all names.
        //
        //   4. Apply 28.3.2.7 manglings to all method names.
        //
        //   5. Compare each attribute name to each method name. For
        //      any which compare equal, append "__" to the attribute
        //      name.
        //
        //   6. Compare each name (attribute and method) to all others.
        //      If any compare equal, throw an Exception with the
        //      conflicting name as the message.

        int count = allMethods.length;

        if (count == 0) return;

        // Make an array of all the method names...

        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = allMethods[i].getName();
        }

        // Are we dealing with a RemoteType, AbstractType, or ValueType?

        CompoundType enclosing = allMethods[0].getEnclosing();
        if (enclosing.isType(TYPE_REMOTE) ||
            enclosing.isType(TYPE_ABSTRACT) ||
            enclosing.isType(TYPE_VALUE)) {

            // Yes, so we must do the 28.3.4.3 attribute mapping. First, get
            // the initial attribute kind of each method...

            int[] kinds = new int[count];

            for (int i = 0; i < count; i++) {
                kinds[i] = getInitialAttributeKind(allMethods[i],env);
            }

            // Now set the attribute kind for each method and do the
            // 28.3.4.3 name mangling...

            setAttributeKinds(allMethods,kinds,names);
        }

        // Make and populate a new context from our names array...

        NameContext context = new NameContext(true);

        for (int i = 0; i < count; i++) {
            context.put(names[i]);
        }

        // Apply the appropriate 28.3 manglings to all the names...

        boolean haveConstructor = false;
        for (int i = 0; i < count; i++) {
            if (!allMethods[i].isConstructor()) {
                names[i] = getMemberOrMethodName(context,names[i],env);
            } else {
                names[i] = IDL_CONSTRUCTOR;
                haveConstructor = true;
            }
        }

        // Now do the 28.3.2.7 mangling for method name collisions...
        // Do this in two passes so that we don't change one during
        // the detection of collisions and then miss a real one...

        boolean overloaded[] = new boolean[count];
        for (int i = 0; i < count; i++) {
            overloaded[i] = (!allMethods[i].isAttribute() &&
                             !allMethods[i].isConstructor() &&
                         doesMethodCollide(names[i],allMethods[i],allMethods,names,true));
        }
        convertOverloadedMethods(allMethods,names,overloaded);

        // Now do the same mangling for constructor name collisions...

        for (int i = 0; i < count; i++) {
            overloaded[i] = (!allMethods[i].isAttribute() &&
                             allMethods[i].isConstructor() &&
                             doesConstructorCollide(names[i],allMethods[i],allMethods,names,true));
        }
        convertOverloadedMethods(allMethods,names,overloaded);

        // Now do the 28.3.4.3 mangling for attribute name collisions...

        for (int i = 0; i < count; i++) {

                CompoundType.Method method = allMethods[i];

            // If this is an attribute name, does it collide with a method?

            if (method.isAttribute() &&
                doesMethodCollide(names[i],method,allMethods,names,true)) {

                // Yes, so add double underscore...

                    names[i] += "__";
                }
            }

        // Do the same mangling for any constructors which collide with
        // methods...

        if (haveConstructor) {
        for (int i = 0; i < count; i++) {
            CompoundType.Method method = allMethods[i];

                // Is this a constructor which collides with a method?

                if (method.isConstructor() &&
                    doesConstructorCollide(names[i],method,allMethods,names,false)) {

                // Yes, so add double underscore...

                names[i] += "__";
            }
        }
        }

        // Now see if we have a collision with the container name (28.3.2.9).

        String containerName = container.getIDLName();
        for (int i = 0; i < count; i++) {
            if (names[i].equalsIgnoreCase(containerName)) {
                // Do not add underscore to attributes.
                // Otherwise getFoo will turn into _get_foo_.
                if (! allMethods[i].isAttribute()) {
                    names[i] += "_";
                }
            }
        }

        // Now see if we have any collisions (28.3.2.9). If we do,
        // it's an error.  Note: a get/set pair does not collide.

        for (int i = 0; i < count; i++) {

            // Does it collide with any other name?

            if (doesMethodCollide(names[i],allMethods[i],allMethods,names,false)) {

                // Yes, so bail...

                throw new Exception(allMethods[i].toString());
            }
        }

        // Ok. We have unique names. Create the appropriate 'wire' name
        // for each and set as the 'idl' name. If it is an attribute, also
        // set the attribute name...

        for (int i = 0; i < count; i++) {

            CompoundType.Method method = allMethods[i];
            String wireName = names[i];

            if (method.isAttribute()) {
                wireName = ATTRIBUTE_WIRE_PREFIX[method.getAttributeKind()] +
                    stripLeadingUnderscore(wireName);
                String attributeName = names[i];
                method.setAttributeName(attributeName);
            }
            method.setIDLName(wireName);
        }
    }

    private static String stripLeadingUnderscore (String name) {
        if (name != null && name.length() > 1
            && name.charAt(0) == '_')
        {
            return name.substring(1);
        }
        return name;
    }


    private static String stripTrailingUnderscore (String name) {
        if (name != null && name.length() > 1 &&
            name.charAt(name.length() - 1) == '_')
        {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }


    private static void convertOverloadedMethods(CompoundType.Method[] allMethods,
                                                 String[] names,
                                                 boolean[] overloaded) {

        for (int i = 0; i < names.length; i++) {

            // Do we need to mangle it?

            if (overloaded[i]) {

                // Yes, so add arguments...

                CompoundType.Method method = allMethods[i];
                Type[] args = method.getArguments();

                for (int k = 0; k < args.length; k++) {

                                // Add the separator...

                    names[i] += "__";

                                // Get the fully qualified IDL name, without the "::"
                                // prefix...

                    String argIDLName = args[k].getQualifiedIDLName(false);

                                // Replace any occurances of "::_" with "_" to
                                // undo any IDL keyword mangling and do next step
                                // at the same time...

                    argIDLName = replace(argIDLName,"::_","_");

                                // Replace any occurances of "::" with "_"...

                    argIDLName = replace(argIDLName,"::","_");

                                // Replace any occurances of " " with "_"...

                    argIDLName = replace(argIDLName," ","_");

                                // Add the argument type name...

                    names[i] += argIDLName;
                }

                if (args.length == 0) {
                    names[i] += "__";
                }

                // Remove any IDL keyword mangling...

                names[i] = stripLeadingUnderscore(names[i]);
            }
        }
    }

    private static boolean doesMethodCollide (String name,
                                              CompoundType.Method method,
                                              CompoundType.Method[] allMethods,
                                              String[] allNames,
                                              boolean ignoreAttributes) {

        // Scan all methods looking for a match...

        for (int i = 0; i < allMethods.length; i++) {

            CompoundType.Method target = allMethods[i];

            if (method != target &&                                 // Not same instance
                !target.isConstructor() &&                      // Not a constructor
                (!ignoreAttributes || !target.isAttribute()) && // Correct kind
                name.equals(allNames[i])) {                         // Same names

                // Are we looking at a get/set pair?

                int kind1 = method.getAttributeKind();
                int kind2 = target.getAttributeKind();

                if ((kind1 != ATTRIBUTE_NONE && kind2 != ATTRIBUTE_NONE) &&
                    ((kind1 == ATTRIBUTE_SET && kind2 != ATTRIBUTE_SET) ||
                     (kind1 != ATTRIBUTE_SET && kind2 == ATTRIBUTE_SET) ||
                     // one is a is-getter/setter pair and the other is just a getter
                     (kind1 == ATTRIBUTE_IS_RW && kind2 == ATTRIBUTE_GET) ||
                     (kind1 == ATTRIBUTE_GET && kind2 == ATTRIBUTE_IS_RW))) {

                    // Yes, so ignore it...

                } else {

                    // No, so we have a collision...

                    return true;
                }
            }
        }

        return false;
    }

    private static boolean doesConstructorCollide (String name,
                                                   CompoundType.Method method,
                                                   CompoundType.Method[] allMethods,
                                                   String[] allNames,
                                                   boolean compareConstructors) {

        // Scan all methods looking for a match...

        for (int i = 0; i < allMethods.length; i++) {

            CompoundType.Method target = allMethods[i];

            if (method != target &&                                     // Not same instance
                (target.isConstructor() == compareConstructors) &&  // Correct kind
                name.equals(allNames[i])) {                             // Same names

                // We have a collision...

                return true;
            }
        }

        return false;
    }


    /**
     * Set all the member names in a given class.
     * <p>
     * Section 28.3.2.7    (see CompoundType)
     * Section 28.3.2.7
     */
    public static void setMemberNames (CompoundType container,
                                       CompoundType.Member[] allMembers,
                                       CompoundType.Method[] allMethods,
                                       BatchEnvironment env)
        throws Exception {

        // Make and populate a new context...

        NameContext context = new NameContext(true);

        for (int i = 0; i < allMembers.length; i++) {
            context.put(allMembers[i].getName());
        }

        // Now set all the idl names...

        for (int i = 0; i < allMembers.length; i++) {

            CompoundType.Member member = allMembers[i];
            String idlName = getMemberOrMethodName(context,member.getName(),env);
            member.setIDLName(idlName);
        }

        // First see if we have a collision with the container name (28.3.2.9).

        String containerName = container.getIDLName();
        for (int i = 0; i < allMembers.length; i++) {
            String name = allMembers[i].getIDLName();
            if (name.equalsIgnoreCase(containerName)) {
                // REVISIT - How is this different than line 788
                allMembers[i].setIDLName(name+"_");
            }
        }

        // Check for collisions between member names...

        for (int i = 0; i < allMembers.length; i++) {
            String name = allMembers[i].getIDLName();
            for (int j = 0; j < allMembers.length; j++) {
                if (i != j && allMembers[j].getIDLName().equals(name)) {

                    // Collision...

                    throw new Exception(name);
                }
            }
        }

        // Now check for collisions between member names and
        // method names...

        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < allMembers.length; i++) {
                String name = allMembers[i].getIDLName();
                for (int j = 0; j < allMethods.length; j++) {
                    if (allMethods[j].getIDLName().equals(name)) {

                        // Collision, so append "_" to member name...

                        allMembers[i].setIDLName(name+"_");
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
    }

    /**
     * Get the name for the specified type code.
     * <p>
     * Section 28.3..3     (see PrimitiveType)
     * Section 28.3.5.10   (see SpecialClassType)
     * Section 28.3.4.1    (see SpecialInterfaceType)
     * Section 28.3.10.1   (see SpecialInterfaceType)
     * Section 28.3.10.2   (see SpecialClassType)
     */
    public static String getTypeName(int typeCode, boolean isConstant) {

        String idlName = null;

        switch (typeCode) {
        case TYPE_VOID:             idlName = IDL_VOID; break;
        case TYPE_BOOLEAN:          idlName = IDL_BOOLEAN; break;
        case TYPE_BYTE:             idlName = IDL_BYTE; break;
        case TYPE_CHAR:             idlName = IDL_CHAR; break;
        case TYPE_SHORT:            idlName = IDL_SHORT; break;
        case TYPE_INT:              idlName = IDL_INT; break;
        case TYPE_LONG:             idlName = IDL_LONG; break;
        case TYPE_FLOAT:            idlName = IDL_FLOAT; break;
        case TYPE_DOUBLE:           idlName = IDL_DOUBLE; break;
        case TYPE_ANY:                  idlName = IDL_ANY; break;
        case TYPE_CORBA_OBJECT: idlName = IDL_CORBA_OBJECT; break;
        case TYPE_STRING:
            {
                if (isConstant) {
                    idlName = IDL_CONSTANT_STRING;
                } else {
                    idlName = IDL_STRING;
                }

                break;
            }
        }

        return idlName;
    }

    /**
     * Create a qualified name.
     */
    public static String getQualifiedName (String[] idlModuleNames, String idlName) {
        String result = null;
        if (idlModuleNames != null && idlModuleNames.length > 0) {
            for (int i = 0; i < idlModuleNames.length;i++) {
                if (i == 0) {
                    result = idlModuleNames[0];
                } else {
                    result += IDL_NAME_SEPARATOR;
                    result += idlModuleNames[i];
                }
            }
            result += IDL_NAME_SEPARATOR;
            result += idlName;
        } else {
            result = idlName;
        }
        return result;
    }

    /**
     * Replace substrings
     * @param source The source string.
     * @param match The string to search for within the source string.
     * @param replace The replacement for any matching components.
     * @return
     */
    public static String replace (String source, String match, String replace) {

        int index = source.indexOf(match,0);

        if (index >=0) {

            // We have at least one match, so gotta do the
            // work...

            StringBuffer result = new StringBuffer(source.length() + 16);
            int matchLength = match.length();
            int startIndex = 0;

            while (index >= 0) {
                result.append(source.substring(startIndex,index));
                result.append(replace);
                startIndex = index + matchLength;
                index = source.indexOf(match,startIndex);
            }

            // Grab the last piece, if any...

            if (startIndex < source.length()) {
                result.append(source.substring(startIndex));
            }

            return result.toString();

        } else {

            // No matches, just return the source...

            return source;
        }
    }

    /**
     * Get an IDL style repository id for
     */
    public static String getIDLRepositoryID (String idlName) {
        return  IDL_REPOSITORY_ID_PREFIX +
            replace(idlName,"::", "/") +
            IDL_REPOSITORY_ID_VERSION;
    }

    //_____________________________________________________________________
    // Internal Interfaces
    //_____________________________________________________________________


    /**
     * Convert a type or module name.
     * <p>
     * Section 28.3.2.2
     * Section 28.3.2.3
     */
    private static String getTypeOrModuleName (String name) {

        // 28.3.2.3 Leading underscores...

        String result = convertLeadingUnderscores(name);

        // 28.3.2.2 IDL keywords (NOTE: must be done
        // after leading underscore conversion because
        // the mangling for IDL keywords creates a
        // leading underscore!)...

        return convertIDLKeywords(result);
    }
}
