/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.presentation.rmi ;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.lang.reflect.Method;

import java.math.BigInteger;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.StringTokenizer;

import com.sun.corba.se.spi.presentation.rmi.IDLNameTranslator ;

import com.sun.corba.se.impl.presentation.rmi.IDLType ;
import com.sun.corba.se.impl.presentation.rmi.IDLTypeException ;
import com.sun.corba.se.impl.presentation.rmi.IDLTypesUtil ;
import com.sun.corba.se.impl.orbutil.ObjectUtility ;

/**
 * Bidirectional translator between RMI-IIOP interface methods and
 * and IDL Names.
 */
public class IDLNameTranslatorImpl implements IDLNameTranslator {

    // From CORBA Spec, Table 6 Keywords.
    // Note that since all IDL identifiers are case
    // insensitive, java identifier comparisons to these
    // will be case insensitive also.
    private static String[] IDL_KEYWORDS = {

        "abstract", "any", "attribute", "boolean", "case", "char",
        "const", "context", "custom", "default", "double", "enum",
        "exception", "factory", "FALSE", "fixed", "float", "in", "inout",
        "interface", "long", "module", "native", "Object", "octet",
        "oneway", "out", "private", "public", "raises", "readonly", "sequence",
        "short", "string", "struct", "supports", "switch", "TRUE", "truncatable",
        "typedef", "unsigned", "union", "ValueBase", "valuetype", "void",
        "wchar", "wstring"

    };

    private static char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final String UNDERSCORE = "_";

    // used to mangle java inner class names
    private static final String INNER_CLASS_SEPARATOR =
        UNDERSCORE + UNDERSCORE;

    // used to form IDL array type names
    private static final String[] BASE_IDL_ARRAY_MODULE_TYPE=
        new String[] { "org", "omg", "boxedRMI" } ;

    private static final String BASE_IDL_ARRAY_ELEMENT_TYPE = "seq";

    // used to mangling java identifiers that have a leading underscore
    private static final String LEADING_UNDERSCORE_CHAR = "J";
    private static final String ID_CONTAINER_CLASH_CHAR = UNDERSCORE;

    // separator used between types in a mangled overloaded method name
    private static final String OVERLOADED_TYPE_SEPARATOR =
        UNDERSCORE + UNDERSCORE;

    // string appended to attribute if it clashes with a method name
    private static final String ATTRIBUTE_METHOD_CLASH_MANGLE_CHARS =
        UNDERSCORE + UNDERSCORE;

    // strings prepended to the attribute names in order to form their
    // IDL names.
    private static final String GET_ATTRIBUTE_PREFIX = "_get_";
    private static final String SET_ATTRIBUTE_PREFIX = "_set_";
    private static final String IS_ATTRIBUTE_PREFIX  = "_get_";

    private static Set idlKeywords_;

    static {

        idlKeywords_ = new HashSet();
        for(int i = 0; i < IDL_KEYWORDS.length; i++) {
            String next = (String) IDL_KEYWORDS[i];
            // Convert keyword to all caps to ease equality
            // check.
            String keywordAllCaps = next.toUpperCase();
            idlKeywords_.add(keywordAllCaps);
        }

    }

    //
    // Instance state
    //

    // Remote interface for name translation.
    private Class[] interf_;

    // Maps used to hold name translations.  These do not need to be
    // synchronized since the translation is never modified after
    // initialization.
    private Map methodToIDLNameMap_;
    private Map IDLNameToMethodMap_;
    private Method[] methods_;

    /**
     * Return an IDLNameTranslator for the given interface.
     *
     * @throws IllegalStateException if given class is not a valid
     *         RMI/IIOP Remote Interface
     */
    public static IDLNameTranslator get( Class interf )
    {

        return new IDLNameTranslatorImpl(new Class[] { interf } );

    }

    /**
     * Return an IDLNameTranslator for the given interfacex.
     *
     * @throws IllegalStateException if given classes are not  valid
     *         RMI/IIOP Remote Interfaces
     */
    public static IDLNameTranslator get( Class[] interfaces )
    {

        return new IDLNameTranslatorImpl(interfaces );

    }

    public static String getExceptionId( Class cls )
    {
        // Requirements for this method:
        // 1. cls must be an exception but not a RemoteException.
        // 2. If cls has an IDL keyword name, an underscore is prepended (1.3.2.2).
        // 3. If cls jas a leading underscore, J is prepended (1.3.2.3).
        // 4. If cls has an illegal IDL ident char, it is mapped to UXXXX where
        //    XXXX is the unicode value in hex of the char (1.3.2.4).
        // 5. double underscore for inner class (1.3.2.5).
        // 6. The ID is "IDL:" + name with / separators + ":1.0".
        IDLType itype = classToIDLType( cls ) ;
        return itype.getExceptionName() ;
    }

    public Class[] getInterfaces()
    {
        return interf_;
    }

    public Method[] getMethods()
    {
        return methods_ ;
    }

    public Method getMethod( String idlName )
    {
        return (Method) IDLNameToMethodMap_.get(idlName);
    }

    public String getIDLName( Method method )
    {
        return (String) methodToIDLNameMap_.get(method);
    }

    /**
     * Initialize an IDLNameTranslator for the given interface.
     *
     * @throws IllegalStateException if given class is not a valid
     *         RMI/IIOP Remote Interface
     */
    private IDLNameTranslatorImpl(Class[] interfaces)
    {

        SecurityManager s = System.getSecurityManager();
        if (s != null) {
            s.checkPermission(new DynamicAccessPermission("access"));
        }
        try {
            IDLTypesUtil idlTypesUtil = new IDLTypesUtil();
            for (int ctr=0; ctr<interfaces.length; ctr++)
                idlTypesUtil.validateRemoteInterface(interfaces[ctr]);
            interf_ = interfaces;
            buildNameTranslation();
        } catch( IDLTypeException ite) {
            String msg = ite.getMessage();
            IllegalStateException ise = new IllegalStateException(msg);
            ise.initCause(ite);
            throw ise;
        }
    }

    private void buildNameTranslation()
    {
        // holds method info, keyed by method
        Map allMethodInfo = new HashMap() ;

        for (int ctr=0; ctr<interf_.length; ctr++) {
            Class interf = interf_[ctr] ;

            IDLTypesUtil idlTypesUtil = new IDLTypesUtil();
            final Method[] methods = interf.getMethods();
            // Handle the case of a non-public interface!
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    Method.setAccessible( methods, true ) ;
                    return null ;
                }
            } ) ;

            // Take an initial pass through all the methods and create some
            // information that will be used to track the IDL name
            // transformation.
            for(int i = 0; i < methods.length; i++) {

                Method nextMethod = methods[i];

                IDLMethodInfo methodInfo = new IDLMethodInfo();

                methodInfo.method = nextMethod;

                if (idlTypesUtil.isPropertyAccessorMethod(nextMethod, interf)) {
                    methodInfo.isProperty = true;
                    String attributeName = idlTypesUtil.
                        getAttributeNameForProperty(nextMethod.getName());
                    methodInfo.originalName = attributeName;
                    methodInfo.mangledName  = attributeName;
                } else {
                    methodInfo.isProperty = false;
                    methodInfo.originalName = nextMethod.getName();
                    methodInfo.mangledName  = nextMethod.getName();
                }

                allMethodInfo.put(nextMethod, methodInfo);
            }
        }

        //
        // Perform case sensitivity test first.  This applies to all
        // method names AND attributes.  Compare each method name and
        // attribute to all other method names and attributes.  If names
        // differ only in case, apply mangling as defined in section 1.3.2.7
        // of Java2IDL spec.  Note that we compare using the original names.
        //
        for(Iterator outerIter=allMethodInfo.values().iterator();
            outerIter.hasNext();) {
            IDLMethodInfo outer = (IDLMethodInfo) outerIter.next();
            for(Iterator innerIter = allMethodInfo.values().iterator();
                innerIter.hasNext();) {
                IDLMethodInfo inner = (IDLMethodInfo) innerIter.next();

                if( (outer != inner) &&
                    (!outer.originalName.equals(inner.originalName)) &&
                    outer.originalName.equalsIgnoreCase(inner.originalName) ) {
                    outer.mangledName =
                        mangleCaseSensitiveCollision(outer.originalName);
                    break;
                }

            }
        }

        for(Iterator iter = allMethodInfo.values().iterator();
            iter.hasNext();) {
            IDLMethodInfo next = (IDLMethodInfo) iter.next();
            next.mangledName =
                mangleIdentifier(next.mangledName, next.isProperty);
        }

        //
        // Now check for overloaded method names and apply 1.3.2.6.
        //
        for(Iterator outerIter=allMethodInfo.values().iterator();
            outerIter.hasNext();) {
            IDLMethodInfo outer = (IDLMethodInfo) outerIter.next();
            if( outer.isProperty ) {
                continue;
            }
            for(Iterator innerIter = allMethodInfo.values().iterator();
                innerIter.hasNext();) {
                IDLMethodInfo inner = (IDLMethodInfo) innerIter.next();

                if( (outer != inner) &&
                    !inner.isProperty &&
                    outer.originalName.equals(inner.originalName) ) {
                    outer.mangledName = mangleOverloadedMethod
                        (outer.mangledName, outer.method);
                    break;
                }
            }
        }

        //
        // Now mangle any properties that clash with method names.
        //
        for(Iterator outerIter=allMethodInfo.values().iterator();
            outerIter.hasNext();) {
            IDLMethodInfo outer = (IDLMethodInfo) outerIter.next();
            if( !outer.isProperty ) {
                continue;
            }
            for(Iterator innerIter = allMethodInfo.values().iterator();
                innerIter.hasNext();) {
                IDLMethodInfo inner = (IDLMethodInfo) innerIter.next();
                if( (outer != inner) &&
                    !inner.isProperty &&
                    outer.mangledName.equals(inner.mangledName) ) {
                    outer.mangledName = outer.mangledName +
                        ATTRIBUTE_METHOD_CLASH_MANGLE_CHARS;
                    break;
                }
            }
        }

        //
        // Ensure that no mapped method names clash with mapped name
        // of container(1.3.2.9).  This is a case insensitive comparison.
        //
        for (int ctr=0; ctr<interf_.length; ctr++ ) {
            Class interf = interf_[ctr] ;
            String mappedContainerName = getMappedContainerName(interf);
            for(Iterator iter = allMethodInfo.values().iterator();
                iter.hasNext();) {
                IDLMethodInfo next = (IDLMethodInfo) iter.next();
                if( !next.isProperty &&
                    identifierClashesWithContainer(mappedContainerName,
                                                   next.mangledName)) {
                    next.mangledName = mangleContainerClash(next.mangledName);
                }
            }
        }

        //
        // Populate name translation maps.
        //
        methodToIDLNameMap_ = new HashMap();
        IDLNameToMethodMap_ = new HashMap();
        methods_ = (Method[])allMethodInfo.keySet().toArray(
            new Method[0] ) ;

        for(Iterator iter = allMethodInfo.values().iterator();
            iter.hasNext();) {
            IDLMethodInfo next = (IDLMethodInfo) iter.next();
            String idlName = next.mangledName;
            if( next.isProperty ) {
                String origMethodName = next.method.getName();
                String prefix = "";

                if( origMethodName.startsWith("get") ) {
                    prefix = GET_ATTRIBUTE_PREFIX;
                } else if( origMethodName.startsWith("set") ) {
                    prefix = SET_ATTRIBUTE_PREFIX;
                } else {
                    prefix = IS_ATTRIBUTE_PREFIX;
                }

                idlName = prefix + next.mangledName;
            }

            methodToIDLNameMap_.put(next.method, idlName);

            // Final check to see if there are any clashes after all the
            // manglings have been applied.  If so, this is treated as an
            // invalid interface.  Currently, we do a CASE-SENSITIVE
            // comparison since that matches the rmic behavior.
            // @@@ Shouldn't this be a case-insensitive check?
            if( IDLNameToMethodMap_.containsKey(idlName) ) {
                // @@@ I18N
                Method clash = (Method) IDLNameToMethodMap_.get(idlName);
                throw new IllegalStateException("Error : methods " +
                    clash + " and " + next.method +
                    " both result in IDL name '" + idlName + "'");
            } else {
                IDLNameToMethodMap_.put(idlName, next.method);
            }
        }

        return;

    }


    /**
     * Perform all necessary stand-alone identifier mangling operations
     * on a java identifier that is being transformed into an IDL name.
     * That is, mangling operations that don't require looking at anything
     * else but the identifier itself.  This covers sections 1.3.2.2, 1.3.2.3,
     * and 1.3.2.4 of the Java2IDL spec.  Method overloading and
     * case-sensitivity checks are handled elsewhere.
     */

    private static String mangleIdentifier(String identifier) {
        return mangleIdentifier(identifier, false);
    }

    private static String mangleIdentifier(String identifier, boolean attribute) {

        String mangledName = identifier;

        //
        // Apply leading underscore test (1.3.2.3)
        // This should be done before IDL Keyword clash test, since clashing
        // IDL keywords are mangled by adding a leading underscore.
        //
        if( hasLeadingUnderscore(mangledName) ) {
            mangledName = mangleLeadingUnderscore(mangledName);
        }

        //
        // Apply IDL keyword clash test (1.3.2.2).
        // This is not needed for attributes since when the full property
        // name is composed it cannot clash with an IDL keyword.
        // (Also, rmic doesn't do it.)
        //

        if( !attribute && isIDLKeyword(mangledName) ) {
            mangledName = mangleIDLKeywordClash(mangledName);
        }

        //
        // Replace illegal IDL identifier characters (1.3.2.4)
        // for all method names and attributes.
        //
        if( !isIDLIdentifier(mangledName) ) {
            mangledName = mangleUnicodeChars(mangledName);
        }

        return mangledName;
    }

    // isIDLKeyword and mangleIDLKeywordClash are exposed here so that
    // IDLType can use them.
    //
    // XXX refactoring needed:
    // 1. Split off isIDLKeywork and mangleIDLKeywordClash (and possibly
    //    other methods) into a utility class.
    // 2. Move all of classToIDLType to a constructor inside IDLType.
    //
    // The problem appears to be that we need all of the code that
    // performs various checks for name problems and the corresponding
    // fixes into a utility class.  Then we need to see what other
    // refactorings present themselves.

    /**
     * Checks whether a java identifier clashes with an
     * IDL keyword.  Note that this is a case-insensitive
     * comparison.
     *
     * Used to implement section 1.3.2.2 of Java2IDL spec.
     */
    static boolean isIDLKeyword(String identifier) {

        String identifierAllCaps = identifier.toUpperCase();

        return idlKeywords_.contains(identifierAllCaps);
    }

    static String mangleIDLKeywordClash(String identifier) {
        return UNDERSCORE + identifier;
    }

    private static String mangleLeadingUnderscore(String identifier) {
        return LEADING_UNDERSCORE_CHAR + identifier;
    }

    /**
     * Checks whether a java identifier starts with an underscore.
     * Used to implement section 1.3.2.3 of Java2IDL spec.
     */
    private static boolean hasLeadingUnderscore(String identifier) {
        return identifier.startsWith(UNDERSCORE);
    }

    /**
     * Implements Section 1.3.2.4 of Java2IDL Mapping.
     * All non-IDL identifier characters must be replaced
     * with their Unicode representation.
     */
    static String mangleUnicodeChars(String identifier) {
        StringBuffer mangledIdentifier = new StringBuffer();

        for(int i = 0; i < identifier.length(); i++) {
            char nextChar = identifier.charAt(i);
            if( isIDLIdentifierChar(nextChar) ) {
                mangledIdentifier.append(nextChar);
            } else {
                String unicode = charToUnicodeRepresentation(nextChar);
                mangledIdentifier.append(unicode);
            }
        }

        return mangledIdentifier.toString();
    }

    /**
     * Implements mangling portion of Section 1.3.2.7 of Java2IDL spec.
     * This method only deals with the actual mangling.  Decision about
     * whether case-sensitive collision mangling is required is made
     * elsewhere.
     *
     *
     * "...a mangled name is generated consisting of the original name
     * followed by an underscore separated list of decimal indices
     * into the string, where the indices identify all the upper case
     * characters in the original string. Indices are zero based."
     *
     */
    String mangleCaseSensitiveCollision(String identifier) {

        StringBuffer mangledIdentifier = new StringBuffer(identifier);

        // There is always at least one trailing underscore, whether or
        // not the identifier has uppercase letters.
        mangledIdentifier.append(UNDERSCORE);

        boolean needUnderscore = false;
        for(int i = 0; i < identifier.length(); i++) {
            char next = identifier.charAt(i);
            if( Character.isUpperCase(next) ) {
                // This bit of logic is needed to ensure that we have
                // an underscore separated list of indices but no
                // trailing underscores.  Basically, after we have at least
                // one uppercase letter, we always put an undercore before
                // printing the next one.
                if( needUnderscore ) {
                    mangledIdentifier.append(UNDERSCORE);
                }
                mangledIdentifier.append(i);
                needUnderscore = true;
            }
        }

        return mangledIdentifier.toString();
    }

    private static String mangleContainerClash(String identifier) {
        return identifier + ID_CONTAINER_CLASH_CHAR;
    }

    /**
     * Implements Section 1.3.2.9 of Java2IDL Mapping. Container in this
     * context means the name of the java Class(excluding package) in which
     * the identifier is defined.  Comparison is case-insensitive.
     */
    private static boolean identifierClashesWithContainer
        (String mappedContainerName, String identifier) {

        return identifier.equalsIgnoreCase(mappedContainerName);
    }

    /**
     * Returns Unicode mangling as defined in Section 1.3.2.4 of
     * Java2IDL spec.
     *
     * "For Java identifiers that contain illegal OMG IDL identifier
     * characters such as '$' or Unicode characters outside of ISO Latin 1,
     * any such illegal characters are replaced by "U" followed by the
     * 4 hexadecimal characters(in upper case) representing the Unicode
     * value.  So, the Java name a$b is mapped to aU0024b and
     * x\u03bCy is mapped to xU03BCy."
     */
    public static String charToUnicodeRepresentation(char c) {

        int orig = (int) c;
        StringBuffer hexString = new StringBuffer();

        int value = orig;

        while( value > 0 ) {
            int div = value / 16;
            int mod = value % 16;
            hexString.insert(0, HEX_DIGITS[mod]);
            value = div;
        }

        int numZerosToAdd = 4 - hexString.length();
        for(int i = 0; i < numZerosToAdd; i++) {
            hexString.insert(0, "0");
        }

        hexString.insert(0, "U");
        return hexString.toString();
    }

    private static boolean isIDLIdentifier(String identifier) {

        boolean isIdentifier = true;

        for(int i = 0; i < identifier.length(); i++) {
            char nextChar = identifier.charAt(i);
            // 1st char must be alphbetic.
            isIdentifier  = (i == 0) ?
                isIDLAlphabeticChar(nextChar) :
                isIDLIdentifierChar(nextChar);
            if( !isIdentifier ) {
                break;
            }
        }

        return isIdentifier;

    }

    private static boolean isIDLIdentifierChar(char c) {
        return (isIDLAlphabeticChar(c) ||
                isIDLDecimalDigit(c)   ||
                isUnderscore(c));
    }

    /**
     * True if character is one of 114 Alphabetic characters as
     * specified in Table 2 of Chapter 3 in CORBA spec.
     */
    private static boolean isIDLAlphabeticChar(char c) {

        // NOTE that we can't use the java.lang.Character
        // isUpperCase, isLowerCase, etc. methods since they
        // include many characters other than the Alphabetic list in
        // the CORBA spec.  Instead, we test for inclusion in the
        // Unicode value ranges for the corresponding legal characters.

        boolean alphaChar =
            (
             // A - Z
             ((c >= 0x0041) && (c <= 0x005A))

             ||

             // a - z
             ((c >= 0x0061) && (c <= 0x007A))

             ||

             // other letter uppercase, other letter lowercase, which is
             // the entire upper half of C1 Controls except X and /
             ((c >= 0x00C0) && (c <= 0x00FF)
              && (c != 0x00D7) && (c != 0x00F7)));

        return alphaChar;
    }

    /**
     * True if character is one of 10 Decimal Digits
     * specified in Table 3 of Chapter 3 in CORBA spec.
     */
    private static boolean isIDLDecimalDigit(char c) {
        return ( (c >= 0x0030) && (c <= 0x0039) );
    }

    private static boolean isUnderscore(char c) {
        return ( c == 0x005F );
    }

    /**
     * Mangle an overloaded method name as defined in Section 1.3.2.6 of
     * Java2IDL spec.  Current value of method name is passed in as argument.
     * We can't start from original method name since the name might have
     * been partially mangled as a result of the other rules.
     */
    private static String mangleOverloadedMethod(String mangledName, Method m) {

        IDLTypesUtil idlTypesUtil = new IDLTypesUtil();

        // Start by appending the separator string
        String newMangledName = mangledName + OVERLOADED_TYPE_SEPARATOR;

        Class[] parameterTypes = m.getParameterTypes();

        for(int i = 0; i < parameterTypes.length; i++) {
            Class nextParamType = parameterTypes[i];

            if( i > 0 ) {
                newMangledName = newMangledName + OVERLOADED_TYPE_SEPARATOR;
            }
            IDLType idlType = classToIDLType(nextParamType);

            String moduleName = idlType.getModuleName();
            String memberName = idlType.getMemberName();

            String typeName = (moduleName.length() > 0) ?
                moduleName + UNDERSCORE + memberName : memberName;

            if( !idlTypesUtil.isPrimitive(nextParamType) &&
                (idlTypesUtil.getSpecialCaseIDLTypeMapping(nextParamType)
                 == null) &&
                isIDLKeyword(typeName) ) {
                typeName = mangleIDLKeywordClash(typeName);
            }

            typeName = mangleUnicodeChars(typeName);

            newMangledName = newMangledName + typeName;
        }

        return newMangledName;
    }


    private static IDLType classToIDLType(Class c) {

        IDLType idlType = null;
        IDLTypesUtil idlTypesUtil = new IDLTypesUtil();

        if( idlTypesUtil.isPrimitive(c) ) {

            idlType = idlTypesUtil.getPrimitiveIDLTypeMapping(c);

        } else if( c.isArray() ) {

            // Calculate array depth, as well as base element type.
            Class componentType = c.getComponentType();
            int numArrayDimensions = 1;
            while(componentType.isArray()) {
                componentType = componentType.getComponentType();
                numArrayDimensions++;
            }
            IDLType componentIdlType = classToIDLType(componentType);

            String[] modules = BASE_IDL_ARRAY_MODULE_TYPE;
            if( componentIdlType.hasModule() ) {
                modules = (String[])ObjectUtility.concatenateArrays( modules,
                    componentIdlType.getModules() ) ;
            }

            String memberName = BASE_IDL_ARRAY_ELEMENT_TYPE +
                numArrayDimensions + UNDERSCORE +
                componentIdlType.getMemberName();

            idlType = new IDLType(c, modules, memberName);

        } else {
            idlType = idlTypesUtil.getSpecialCaseIDLTypeMapping(c);

            if (idlType == null) {
                // Section 1.3.2.5 of Java2IDL spec defines mangling rules for
                // inner classes.
                String memberName = getUnmappedContainerName(c);

                // replace inner class separator with double underscore
                memberName = memberName.replaceAll("\\$",
                                                   INNER_CLASS_SEPARATOR);

                if( hasLeadingUnderscore(memberName) ) {
                    memberName = mangleLeadingUnderscore(memberName);
                }

                // Get raw package name.  If there is a package, it
                // will still have the "." separators and none of the
                // mangling rules will have been applied.
                String packageName = getPackageName(c);

                if (packageName == null) {
                    idlType = new IDLType( c, memberName ) ;
                } else {
                    // If this is a generated IDL Entity Type we need to
                    // prepend org_omg_boxedIDL per sections 1.3.5 and 1.3.9
                    if (idlTypesUtil.isEntity(c)) {
                        packageName = "org.omg.boxedIDL." + packageName ;
                    }

                    // Section 1.3.2.1 and 1.3.2.6 of Java2IDL spec defines
                    // rules for mapping java packages to IDL modules and for
                    // mangling module name portion of type name.  NOTE that
                    // of the individual identifier mangling rules,
                    // only the leading underscore test is done here.
                    // The other two(IDL Keyword, Illegal Unicode chars) are
                    // done in mangleOverloadedMethodName.
                    StringTokenizer tokenizer =
                        new StringTokenizer(packageName, ".");

                    String[] modules = new String[ tokenizer.countTokens() ] ;
                    int index = 0 ;
                    while (tokenizer.hasMoreElements()) {
                        String next = tokenizer.nextToken();
                        String moreMangled = hasLeadingUnderscore( next ) ?
                            mangleLeadingUnderscore( next ) : next;

                        modules[index++] = moreMangled ;
                    }

                    idlType = new IDLType(c, modules, memberName);
                }
            }
        }

        return idlType;
    }

    /**
     * Return Class' package name or null if there is no package.
     */
    private static String getPackageName(Class c) {
        Package thePackage = c.getPackage();
        String packageName = null;

        // Try to get package name by introspection.  Some classloaders might
        // not provide this information, so check for null.
        if( thePackage != null ) {
            packageName = thePackage.getName();
        } else {
            // brute force method
            String fullyQualifiedClassName = c.getName();
            int lastDot = fullyQualifiedClassName.indexOf('.');
            packageName = (lastDot == -1) ? null :
                fullyQualifiedClassName.substring(0, lastDot);
        }
        return packageName;
    }

    private static String getMappedContainerName(Class c) {
        String unmappedName = getUnmappedContainerName(c);

        return mangleIdentifier(unmappedName);
    }

    /**
     * Return portion of class name excluding package name.
     */
    private static String getUnmappedContainerName(Class c) {

        String memberName  = null;
        String packageName = getPackageName(c);

        String fullyQualifiedClassName = c.getName();

        if( packageName != null ) {
            int packageLength = packageName.length();
            memberName = fullyQualifiedClassName.substring(packageLength + 1);
        } else {
            memberName = fullyQualifiedClassName;

        }

        return memberName;
    }

    /**
     * Internal helper class for tracking information related to each
     * interface method while we're building the name translation table.
     */
    private static class IDLMethodInfo
    {
        public Method method;
        public boolean isProperty;

        // If this is a property, originalName holds the original
        // attribute name. Otherwise, it holds the original method name.
        public String originalName;

        // If this is a property, mangledName holds the mangled attribute
        // name. Otherwise, it holds the mangled method name.
        public String mangledName;

    }

    public String toString() {

        StringBuffer contents = new StringBuffer();
        contents.append("IDLNameTranslator[" );
        for( int ctr=0; ctr<interf_.length; ctr++) {
            if (ctr != 0)
                contents.append( " " ) ;
            contents.append( interf_[ctr].getName() ) ;
        }
        contents.append("]\n");
        for(Iterator iter = methodToIDLNameMap_.keySet().iterator();
            iter.hasNext();) {

            Method method  = (Method) iter.next();
            String idlName = (String) methodToIDLNameMap_.get(method);

            contents.append(idlName + ":" + method + "\n");

        }

        return contents.toString();
    }

    public static void main(String[] args) {

        Class remoteInterface = java.rmi.Remote.class;

        if( args.length > 0 ) {
            String className = args[0];
            try {
                remoteInterface = Class.forName(className);
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        System.out.println("Building name translation for " + remoteInterface);
        try {
            IDLNameTranslator nameTranslator =
                IDLNameTranslatorImpl.get(remoteInterface);
            System.out.println(nameTranslator);
        } catch(IllegalStateException ise) {
            ise.printStackTrace();
        }
    }
}
