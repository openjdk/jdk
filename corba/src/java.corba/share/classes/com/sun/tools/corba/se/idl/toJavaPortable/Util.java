/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * COMPONENT_NAME: idl.toJava
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.toJavaPortable;

// Notes:
// -F46838.4<klr> Ported -td option from toJava.
// -10/17/98  KLR Ported fix for d48911 from toJava
// -10/18/98  KLR Ported fix from toJava for "unsigned long" constants
// -F46082.51<daz> Removed code to collect makefile list generation inforamtion
//  from getStream(); see f46830.
// -F46082.51<daz> Removed -stateful feature: methods javaStatefulName(String)
//  and javaStatefulName(SymtabEntry) are obsolete, supplanted by javaName().
// -D54640<daz> Represent unsigned long long expressions with their computed
//  value rather than their actual representation (see notes in method
//  parseTerminal(), parseBinary(), and parseUnary().)
// -D58319<daz> Add getVersion() method.
// -D48034<daz> Import Helper classes for typedef struct members when generating
//  helper.  See method addImportLines().
// -D59851<daz> Modify to enable QuickTest build. (pending)
// -D42256<daz> Determine import lines for template types, which may specify any
//  positive int., constant expression for a boundary. Such expression containing
//  non-literal contansts previously caused problems when appearing in constructs
//  structs, unions, exceptions, typedefs, operation types and parameters,
//  attributes; and of course, sequences, strings.
// -D59063<daz> Add helper for global exception to stub import list.
// -D58951<daz> Publicise members for QuickTest.
// -D59421<klr> Change ValueBaseHolder to SerializableHolder
// -D59596<klr> Prevent accesses to elements of empty Vectors.
// -D59771<daz> Add import stmt for Helper of global type in stubs.
// -D59355<daz> Remove target dir. from filename when writing to prolog.
// -D59437<daz> Fill typename information for value boxes.
// -D62023<klr> Don't import ValueBase*
// -D62023<klr> Add corbaLevel

import java.io.File;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;

import com.sun.tools.corba.se.idl.ConstEntry;
import com.sun.tools.corba.se.idl.EnumEntry;
import com.sun.tools.corba.se.idl.ExceptionEntry;
import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.MethodEntry;
import com.sun.tools.corba.se.idl.NativeEntry;
import com.sun.tools.corba.se.idl.ParameterEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.StructEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.UnionBranch;
import com.sun.tools.corba.se.idl.UnionEntry;
import com.sun.tools.corba.se.idl.ValueEntry;
import com.sun.tools.corba.se.idl.ValueBoxEntry;
import com.sun.tools.corba.se.idl.InterfaceState;

import com.sun.tools.corba.se.idl.constExpr.*;

/**
 * Class Util is a repository of static members available for general
 * use by the IDL parser framework and any generator extensions.
 **/
public class Util extends com.sun.tools.corba.se.idl.Util
{
  // <d58319>
  /**
   * Fetch the version number of this build of the IDL-to-Java (portable)
   * compiler from the appropriate properties file.
   * @return the version number of this compiler build.
   **/
  public static String getVersion ()
  {
    return com.sun.tools.corba.se.idl.Util.getVersion ("com/sun/tools/corba/se/idl/toJavaPortable/toJavaPortable.prp");
  } // getVersion

  /**
   * This method is called by Setup.preEmit, so
   * symbolTable is available for all Util methods.
   **/
  static void setSymbolTable (Hashtable symtab)
  {
    symbolTable = symtab;
  } // setSymbolTable

  public static void setPackageTranslation( Hashtable pkgtrans )
  {
    packageTranslation = pkgtrans ;
  }

  public static boolean isInterface (String name)
  {
    return isInterface (name, symbolTable);
  } // isInterface

  static String arrayInfo (Vector arrayInfo)
  {
    int         arrays = arrayInfo.size ();
    String      info   = "";
    Enumeration e      = arrayInfo.elements ();
    while (e.hasMoreElements ())
      info = info + '[' + parseExpression ((Expression)e.nextElement ()) + ']';
    return info;
  } // arrayInfo

  // <d58951> static String sansArrayInfo (Vector arrayInfo)
  public static String sansArrayInfo (Vector arrayInfo)
  {
    int    arrays   = arrayInfo.size ();
    String brackets = "";
    for (int i = 0; i < arrays; ++i)
      brackets = brackets + "[]";
    return brackets;
  } // sansArrayInfo

  // <d58951> static String sansArrayInfo (String name)
  static public String sansArrayInfo (String name)
  {
    int index = name.indexOf ('[');
    if (index >= 0)
    {
      String array = name.substring (index);
      name = name.substring (0, index);
      while (!array.equals (""))
      {
        name = name + "[]";
        array = array.substring (array.indexOf (']') + 1);
      }
    }
    return name;
  } // sansArrayInfo

  /**
   * Given a symbol table entry, return the name of
   * the file which should be created.
   **/
  public static String fileName (SymtabEntry entry, String extension )
  {
    NameModifier nm = new NameModifierImpl() ;
    return fileName( entry, nm, extension ) ;
  } // fileName

  public static String fileName (SymtabEntry entry, NameModifier modifier, String extension )
  {
    // This may not be the most appropriate place for
    // the mkdir calls, but it's common to everything:
    String pkg = containerFullName (entry.container ());
    if (pkg != null && !pkg.equals (""))
      mkdir (pkg);

    String name = entry.name ();
    name = modifier.makeName( name ) + extension ;
    if (pkg != null && !pkg.equals (""))
      name = pkg + '/' + name;

    return name.replace ('/', File.separatorChar);
  } // fileName

  public static GenFileStream stream (SymtabEntry entry, String extension)
  {
    NameModifier nm = new NameModifierImpl() ;
    return stream(entry, nm, extension);
  } // stream

  public static GenFileStream stream (SymtabEntry entry, NameModifier modifier, String extension )
  {
    return getStream ( fileName (entry,modifier,extension), entry ) ;
  }

  public static GenFileStream getStream (String name, SymtabEntry entry)
  {
    // <f46838.4>
    String absPathName = ((Arguments)Compile.compiler.arguments).targetDir + name;
    if (Compile.compiler.arguments.keepOldFiles && new File (absPathName).exists ())
      return null;
    else
      // Write the data to the file stream
      return new GenFileStream (absPathName);
  } // getStream

  public static String containerFullName( SymtabEntry container)
  {
      String name = doContainerFullName( container ) ;
      if (packageTranslation.size() > 0)
          name = translate( name ) ;
      return name ;
  }

  public static String translate( String name )
  {
      String head = name ;
      String tail = "" ;
      int index ;
      String trname ;

      // Check for package name translations, starting with the
      // most specific match.
      do {
          trname = (String)(packageTranslation.get( head )) ;
          if (trname != null)
              return trname + tail ;

          index = head.lastIndexOf( '/' ) ;
          if (index >= 0) {
              tail = head.substring( index ) + tail ;
              head = head.substring( 0, index ) ;
          }
      } while (index >= 0) ;

      return name ;
  }

  private static String doContainerFullName (SymtabEntry container)
  {
    String name = "";

    if (container == null)
      name = "";
    else
    {
      if (container instanceof InterfaceEntry ||
          container instanceof StructEntry ||
          container instanceof UnionEntry)
        name = container.name () + "Package";
      else
        name = container.name ();

      if (container.container () != null &&
        !container.container ().name ().equals (""))
        name = doContainerFullName (container.container ()) + '/' + name;
    }

    return name;
  } // doContainerFullName

  /**
   * Given a SymtabEntry, return the string which should be used
   * for this entry. Enums are converted to ints, typedefs and
   * sequences are converted to their info types. javaQualifiedName
   * does not do any of these conversions.
   **/
  public static String javaName (SymtabEntry entry)
  {
    // First get the real name of this type
    String name = "";
    if (entry instanceof TypedefEntry || entry instanceof SequenceEntry)
      try
      {
        name = sansArrayInfo ((String)entry.dynamicVariable (Compile.typedefInfo));
      }
      catch (NoSuchFieldException e)
      {
        name = entry.name ();
      }
    else if (entry instanceof PrimitiveEntry)
      name = javaPrimName (entry.name ());
    else if (entry instanceof StringEntry)
      name = "String";
    else if (entry instanceof NativeEntry)
      name = javaNativeName (entry.name());
    else if (entry instanceof ValueEntry && entry.name ().equals ("ValueBase"))
        name = "java.io.Serializable";
    else if (entry instanceof ValueBoxEntry)
    {
      ValueBoxEntry v = (ValueBoxEntry) entry;
      TypedefEntry member = ((InterfaceState) v.state ().elementAt (0)).entry;
      SymtabEntry mType = member.type ();
      if (mType instanceof PrimitiveEntry)
      {
         name = containerFullName (entry.container ());
         if (!name.equals (""))
           name = name + '.';
         name = name + entry.name ();
      }
      else
         name = javaName (mType);
    }
    else
    {
      name = containerFullName (entry.container ());
      if (name.equals (""))
        name = entry.name ();
      else
        name = name + '.' + entry.name ();
    }

    // Make it a fully package-qualified name
    return name.replace ('/', '.');
  } // javaName

  public static String javaPrimName (String name)
  {
    if (name.equals ("long") || name.equals ("unsigned long"))
      name = "int";
    else if (name.equals ("octet"))
      name = "byte";
    // "unisigned long long" exceeds Java long.
    else if (name.equals ("long long") || name.equals ("unsigned long long"))
      name = "long";
    else if (name.equals ("wchar"))
      name = "char";
    else if (name.equals ("unsigned short"))
      name = "short";
    else if (name.equals ("any"))
      name = "org.omg.CORBA.Any";
    else if (name.equals ("TypeCode"))
      name = "org.omg.CORBA.TypeCode";
    else if (name.equals ("Principal")) // <d61961>
      name = "org.omg.CORBA.Principal";
    return name;
  } // javaPrimName

  public static String javaNativeName (String name)
  {

    // translations for Native declarations according to CORBA 2.3 spec

    if (name.equals ("AbstractBase") || name.equals ("Cookie"))
      name = "java.lang.Object";
    else if (name.equals ("Servant"))
      name = "org.omg.PortableServer.Servant";
    else if (name.equals ("ValueFactory"))
      name = "org.omg.CORBA.portable.ValueFactory";
    return name;
  }


  /**
   * Given a symtabEntry, return the name of this entry. This
   * method does not do any conversions like javaName does.
   **/
  public static String javaQualifiedName (SymtabEntry entry)
  {
    String name = "";
    if (entry instanceof PrimitiveEntry)
      name = javaPrimName (entry.name ());
    else if (entry instanceof StringEntry)
      name = "String";
    else if (entry instanceof ValueEntry && entry.name ().equals ("ValueBase"))
      name = "java.io.Serializable";
    else
    {
      SymtabEntry container = entry.container ();
      if (container != null)
        name = container.name ();
      if (name.equals (""))
        name = entry.name ();
      else
        name = containerFullName (entry.container ()) + '.' + entry.name ();
    }
    return name.replace ('/', '.');
  } // javaQualifiedName

  // <f46082.03> Publicize for extensions.
  //static String collapseName (String name)

  /**
   * Collapse primitive type names.
   **/
  public static String collapseName (String name)
  {
    if (name.equals ("unsigned short"))
      name = "ushort";
    else if (name.equals ("unsigned long"))
      name = "ulong";
    else if (name.equals ("unsigned long long"))
      name = "ulonglong";
    else if (name.equals ("long long"))
      name = "longlong";
    return name;
  } // collapseName

  /**
   *
   **/
  public static SymtabEntry typeOf (SymtabEntry entry)
  {
    while (entry instanceof TypedefEntry && ((TypedefEntry)entry).arrayInfo ().isEmpty () && !(entry.type () instanceof SequenceEntry))
      entry = entry.type ();
    return entry;
  } // typeOf

  /**
   * Fill the info field with the full name (with array info) of the type.
   **/
  static void fillInfo (SymtabEntry infoEntry)
  {
    String      arrayInfo   = "";
    SymtabEntry entry       = infoEntry;
    boolean     alreadyHave = false;

    do
    {
      try
      {
        alreadyHave = entry.dynamicVariable (Compile.typedefInfo) != null;
      }
      catch (NoSuchFieldException e)
      {}
      // If this entry's info has already been processed
      // don't bother processing it again, just take it.
      if (!alreadyHave)
      {
        if (entry instanceof TypedefEntry)
          arrayInfo = arrayInfo + arrayInfo (((TypedefEntry)entry).arrayInfo ());
        else if (entry instanceof SequenceEntry)
        {
          Expression maxSize = ((SequenceEntry)entry).maxSize ();
          if (maxSize == null)
            arrayInfo = arrayInfo + "[]";
          else
            arrayInfo = arrayInfo + '[' + parseExpression (maxSize) + ']';
        }
        if (entry.type () == null)
        {
          // <d59437> Suppress this message.  It tells the developer nothing, and
          // this path does not cause the algorithm to fail.  Value boxes may
          // contain anonymous types, like a struct or enum.
          //System.err.println (getMessage ("PreEmit.indeterminateTypeInfo", entry.typeName ()));
        }
        else
          entry = entry.type ();
      }
    } while (!alreadyHave && entry != null &&
        (entry instanceof TypedefEntry || entry instanceof SequenceEntry));
    // <d59437> Value boxes may contain types lacking typename info., which
    // causes the 2nd case, below, to fail with exception when retrieving the
    // javaName().
    if (entry instanceof ValueBoxEntry)
      fillValueBoxInfo ((ValueBoxEntry)entry);
    try
    {
      if (alreadyHave)
        infoEntry.dynamicVariable (Compile.typedefInfo, (String)entry.dynamicVariable (Compile.typedefInfo) + arrayInfo);
      else
        infoEntry.dynamicVariable (Compile.typedefInfo, javaName (entry) + arrayInfo);
    }
    catch (NoSuchFieldException e)
    {}
  } // fillInfo

  // <d59437>
  /**
   *
   **/
  static void fillValueBoxInfo (ValueBoxEntry vb)
  {
    SymtabEntry stateMember = (((InterfaceState) vb.state ().elementAt (0)).entry);
    if (stateMember.type() != null)
      Util.fillInfo (stateMember.type ());
    Util.fillInfo (stateMember);
  } // fillValueBoxInfo

  /**
   *
   **/
  public static String holderName (SymtabEntry entry)
  {
    String name;
    if (entry instanceof PrimitiveEntry)
      if (entry.name ().equals ("any"))
        name = "org.omg.CORBA.AnyHolder";
      else if (entry.name ().equals ("TypeCode"))
        name = "org.omg.CORBA.TypeCodeHolder";
      else if (entry.name ().equals ("Principal")) // <d61961>
        name = "org.omg.CORBA.PrincipalHolder";
      else
        name = "org.omg.CORBA." + capitalize (javaQualifiedName (entry)) + "Holder";
    else if (entry instanceof TypedefEntry)
    {
      TypedefEntry td = (TypedefEntry)entry;
      if (!td.arrayInfo ().isEmpty () || td.type () instanceof SequenceEntry)
        name = javaQualifiedName (entry) + "Holder";
      else
        name = holderName (entry.type ());
    }
    else if (entry instanceof StringEntry)
      name = "org.omg.CORBA.StringHolder";
    else if (entry instanceof ValueEntry)
    {
      if (entry.name ().equals ("ValueBase"))
          name = "org.omg.CORBA.ValueBaseHolder"; // <d59421>, <d60929>
      else
          name = javaName (entry) + "Holder";
    } else if (entry instanceof NativeEntry) {
      // do not attach holder to the translation for Native Entries, e.g.
      // for Cookie it should be CookieHolder instead of java.lang.ObjectHolder
      // returns the complete name for the package, etc.
      name = javaQualifiedName(entry) + "Holder";
    }
    else
      name = javaName (entry) + "Holder";
    return name;
  } // holderName

  /**
   * <d61056>
   **/
  public static String helperName (SymtabEntry entry, boolean qualifiedName)
  {
    if (entry instanceof ValueEntry)
      if (entry.name ().equals ("ValueBase"))
          return "org.omg.CORBA.ValueBaseHelper";

    if (qualifiedName)
      return javaQualifiedName (entry) + "Helper";
    else
      return javaName (entry) + "Helper";
  } // helperName

  public static final short
      TypeFile   = 0,
      StubFile   = 1,
      HelperFile = 2,
      HolderFile = 3,
      StateFile  = 4;

  /**
   *
   **/
  public static void writePackage (PrintWriter stream, SymtabEntry entry)
  {
    writePackage (stream, entry, TypeFile);
  } // writePackage

  /**
   *
   **/
  public static void writePackage (PrintWriter stream, SymtabEntry entry, String name, short type)
  {
    if (name != null && !name.equals (""))
    {
      stream.println ("package " + name.replace ('/', '.') + ';');

      // This type is in a module.  Just in case it refers to types
      // in the unnamed module, add an import statement for each of
      // those types.
      if (!Compile.compiler.importTypes.isEmpty ())
      {
        stream.println ();
        Vector v = addImportLines (entry, Compile.compiler.importTypes, type);
        printImports (v, stream);
      }
    }
  } // writePackage

  /**
   *
   **/
  public static void writePackage (PrintWriter stream, SymtabEntry entry, short type)
  {
    String fullName = containerFullName (entry.container ());
    if (fullName != null && !fullName.equals (""))
    {
      stream.println ("package " + fullName.replace ('/', '.') + ';');
       // This type is in a module.  Just in case it refers to types
      // in the unnamed module, add an import statement for each of
      // those types.
      if ((type != HolderFile || entry instanceof TypedefEntry) && !Compile.compiler.importTypes.isEmpty ())
      {
        stream.println ();
        Vector v = addImportLines (entry, Compile.compiler.importTypes, type);
        printImports (v, stream);
      }
      /*
      Enumeration e = Compile.compiler.importTypes.elements ();
      while (e.hasMoreElements ())
      {
        SymtabEntry i = (SymtabEntry)e.nextElement ();
        // Write import for type
        if (!(i instanceof TypedefEntry))
          stream.println ("import " + i.name () + ';');

        // Write import for Helper
        if (!(i instanceof ConstEntry))
          stream.println ("import " + i.name () + "Helper;");

        // Write import for Holder
        if (!(i instanceof ConstEntry))
          if (!(i instanceof TypedefEntry) || (i.type () instanceof SequenceEntry || !((TypedefEntry)i).arrayInfo ().isEmpty ()))
            stream.println ("import " + i.name () + "Holder;");
      }
      */
    }
  } // writePackage

  /**
   *
   **/
  static private void printImports (Vector importList, PrintWriter stream)
  {
    Enumeration e = importList.elements ();
    while (e.hasMoreElements ())
      stream.println ("import " + (String)e.nextElement () + ';');
  } // printImport

  /**
   *
   **/
  static private void addTo (Vector importList, String name)
  {
    // REVISIT - <d62023-klr> was also importing ValueBaseHolder and Helper
    if (name.startsWith ("ValueBase"))  // don't import ValueBase*
      if ((name.compareTo ("ValueBase") == 0) ||
          (name.compareTo ("ValueBaseHolder") == 0) ||
              (name.compareTo ("ValueBaseHelper") == 0))
        return;
    if (!importList.contains (name))
      importList.addElement (name);
  } // addTo

  /**
   *
   **/
  static private Vector addImportLines (SymtabEntry entry, Vector importTypes, short type)
  {
    Vector importList = new Vector ();
    if (entry instanceof ConstEntry)
    {
      ConstEntry c      = (ConstEntry)entry;
      Object     cvalue = c.value ().value ();
      if (cvalue instanceof ConstEntry && importTypes.contains (cvalue))
        addTo (importList, ((ConstEntry)cvalue).name ());
    }
    else if (entry instanceof ValueEntry && type == HelperFile) // <d59512>
    {
      // This code inspired by ValueGen.getConcreteBaseTypeCode().  Helper method
      // type() could be invoked against a global valuetype.
      if (((ValueEntry)entry).derivedFrom ().size () > 0) // <59596> KLR HACK
      {
        ValueEntry base = (ValueEntry)((ValueEntry)entry).derivedFrom ().elementAt (0);
        String baseName = base.name ();
        if (!"ValueBase".equals (baseName))
          if (importTypes.contains (base))
            addTo (importList, baseName + "Helper");
      }
    }
    else if (entry instanceof InterfaceEntry && (type == TypeFile || type == StubFile))
    {
      InterfaceEntry i = (InterfaceEntry)entry;

      if (i instanceof ValueEntry) // <d59512>
      {
        // Examine interface parents in supports vector.
        Enumeration e = ((ValueEntry)i).supports ().elements ();
        while (e.hasMoreElements ())
        {
          SymtabEntry parent = (SymtabEntry)e.nextElement ();
          if (importTypes.contains (parent))
          {
            addTo (importList, parent.name () + "Operations");
          }
          // If this is a stub, then recurse to the parents
          if (type == StubFile)
          {
            if (importTypes.contains (parent))
              addTo (importList, parent.name ());
            Vector subImportList = addImportLines (parent, importTypes, StubFile);
            Enumeration en = subImportList.elements ();
            while (en.hasMoreElements ())
            {
              addTo (importList, (String)en.nextElement ());
            }
          }
        }
      }
      // Interface or valuetype -- Examine interface and valuetype parents,
      // Look through derivedFrom vector
      Enumeration e = i.derivedFrom ().elements ();
      while (e.hasMoreElements ())
      {
        SymtabEntry parent = (SymtabEntry)e.nextElement ();
        if (importTypes.contains (parent))
        {
          addTo (importList, parent.name ());
          // <d59512> Always add both imports, even though superfluous.  Cannot
          // tell when writing Operations or Signature interface!
          if (!(parent instanceof ValueEntry)) // && parent.name ().equals ("ValueBase")))
            addTo (importList, parent.name () + "Operations");
        }
        // If this is a stub, then recurse to the parents
        if (type == StubFile)
        {
          Vector subImportList = addImportLines (parent, importTypes, StubFile);
          Enumeration en = subImportList.elements ();
          while (en.hasMoreElements ())
          {
            addTo (importList, (String)en.nextElement ());
          }
        }
      }
      // Look through methods vector
      e = i.methods ().elements ();
      while (e.hasMoreElements ())
      {
        MethodEntry m = (MethodEntry)e.nextElement ();

        // Look at method type
        SymtabEntry mtype = typeOf (m.type ());
        if (mtype != null && importTypes.contains (mtype))
          if (type == TypeFile || type == StubFile)
          {
            addTo (importList, mtype.name ());
            addTo (importList, mtype.name () + "Holder");
            if (type == StubFile)
              addTo (importList, mtype.name () + "Helper");
          }
        checkForArrays (mtype, importTypes, importList);
        // <d42256> Print import lines for globals constants and constants
        // within global interfaces.
        if (type == StubFile)
          checkForBounds (mtype, importTypes, importList);

        // Look through exceptions
        Enumeration exEnum = m.exceptions ().elements ();
        while (exEnum.hasMoreElements ())
        {
          ExceptionEntry ex = (ExceptionEntry)exEnum.nextElement ();
          if (importTypes.contains (ex))
          {
            addTo (importList, ex.name ());
            addTo (importList, ex.name () + "Helper"); // <d59063>
          }
        }

        // Look through parameters
        Enumeration parms = m.parameters ().elements ();
        while (parms.hasMoreElements ())
        {
          ParameterEntry parm = (ParameterEntry)parms.nextElement ();
          SymtabEntry parmType = typeOf (parm.type ());
          if (importTypes.contains (parmType))
          {
            // <d59771> Helper needed in stubs.
            if (type == StubFile)
              addTo (importList, parmType.name () + "Helper");
            if (parm.passType () == ParameterEntry.In)
              addTo (importList, parmType.name ());
            else
              addTo (importList, parmType.name () + "Holder");
          }
          checkForArrays (parmType, importTypes, importList);
          // <d42256>
          if (type == StubFile)
            checkForBounds (parmType, importTypes, importList);
        }
      }
    }
    else if (entry instanceof StructEntry)
    {
      StructEntry s = (StructEntry)entry;

      // Look through the members
      Enumeration members = s.members ().elements ();
      while (members.hasMoreElements ())
      {
        SymtabEntry member = (TypedefEntry)members.nextElement ();
        // <d48034> Need to add helper name for typedef members.  This name
        // is referenced at typecode generation in Helper class.
        SymtabEntry memberType = member.type ();
        member = typeOf (member);
        if (importTypes.contains (member))
        {
          // If this IS a typedef, then there are only Helper/Holder classes.
          //if (!(member instanceof TypedefEntry))
          // <d59437>  Valueboxes
          if (!(member instanceof TypedefEntry) && !(member instanceof ValueBoxEntry))
            addTo (importList, member.name ());
          // <d48034> Add helper name of alias, too, if member is a typedef.
          //if (type == HelperFile)
          //  addTo (importList, member.name () + "Helper");
          if (type == HelperFile)
          {
            addTo (importList, member.name () + "Helper");
            if (memberType instanceof TypedefEntry)
              addTo (importList, memberType.name () + "Helper");
          }
        }
        checkForArrays (member, importTypes, importList);
        checkForBounds (member, importTypes, importList);
      }
    }
    else if (entry instanceof TypedefEntry)
    {
      TypedefEntry t = (TypedefEntry)entry;
      String arrays = checkForArrayBase (t, importTypes, importList);
      if (type == HelperFile)
      {
        checkForArrayDimensions (arrays, importTypes, importList);
        try
        {
          String name = (String)t.dynamicVariable (Compile.typedefInfo);
          int index = name.indexOf ('[');
          if (index >= 0)
            name = name.substring (0, index);
          // See if the base type should be added to the list.
          SymtabEntry typeEntry = (SymtabEntry)symbolTable.get (name);
          if (typeEntry != null && importTypes.contains (typeEntry))
            addTo (importList, typeEntry.name () + "Helper");
        }
        catch (NoSuchFieldException e)
        {}

        // <d42256> Typedefs for global bounded strings need import
        // statement when bound expression contains non-literal constants.
        checkForBounds (typeOf (t), importTypes, importList);
      }
      Vector subImportList = addImportLines (t.type (), importTypes, type);
      Enumeration e = subImportList.elements ();
      while (e.hasMoreElements ())
        addTo (importList, (String)e.nextElement ());
    }
    else if (entry instanceof UnionEntry)
    {
      UnionEntry u = (UnionEntry)entry;

      // Look at the discriminant type
      SymtabEntry utype = typeOf (u.type ());
      if (utype instanceof EnumEntry && importTypes.contains (utype))
        addTo (importList, utype.name ());

      // Look through the branches
      Enumeration branches = u.branches ().elements ();
      while (branches.hasMoreElements ())
      {
        UnionBranch branch = (UnionBranch)branches.nextElement ();
        SymtabEntry branchEntry = typeOf (branch.typedef);
        if (importTypes.contains (branchEntry))
        {
          addTo (importList, branchEntry.name ());
          if (type == HelperFile)
            addTo (importList, branchEntry.name () + "Helper");
        }
        checkForArrays (branchEntry, importTypes, importList);
        // <d42256>
        checkForBounds (branchEntry, importTypes, importList);
      }
    }

    // If a typedef is not a sequence or an array, only holders and
    // helpers are generated for it.  Remove references to such
    // class names.
    Enumeration en = importList.elements ();
    while (en.hasMoreElements ())
    {
      String name = (String)en.nextElement ();
      SymtabEntry e = (SymtabEntry)symbolTable.get (name);
      if (e != null && e instanceof TypedefEntry)
      {
        TypedefEntry t = (TypedefEntry)e;
        if (t.arrayInfo ().size () == 0 || !(t.type () instanceof SequenceEntry))
          importList.removeElement (name);
      }
    }
    return importList;
  } // addImportLines

  /**
   *
   **/
  static private void checkForArrays (SymtabEntry entry, Vector importTypes, Vector importList)
  {
    if (entry instanceof TypedefEntry)
    {
      TypedefEntry t = (TypedefEntry)entry;
      String arrays = checkForArrayBase (t, importTypes, importList);
      checkForArrayDimensions (arrays, importTypes, importList);
    }
  } // checkForArrays

  /**
   *
   **/
  static private String checkForArrayBase (TypedefEntry t, Vector importTypes, Vector importList)
  {
    String arrays = "";
    try
    {
      String name = (String)t.dynamicVariable (Compile.typedefInfo);
      int index = name.indexOf ('[');
      if (index >= 0)
      {
        arrays = name.substring (index);
        name = name.substring (0, index);
      }

      // See if the base type should be added to the list.
      SymtabEntry typeEntry = (SymtabEntry)symbolTable.get (name);
      if (typeEntry != null && importTypes.contains (typeEntry))
        addTo (importList, typeEntry.name ());
    }
    catch (NoSuchFieldException e)
    {}
    return arrays;
  } // checkForArrayBase

  /**
   *
   **/
  static private void checkForArrayDimensions (String arrays, Vector importTypes, Vector importList)
  {
    // See if any of the arrays contain a constentry.
    // If so, see if it should be added to the list.
    while (!arrays.equals (""))
    {
      int index = arrays.indexOf (']');
      String dim = arrays.substring (1, index);
      arrays = arrays.substring (index + 1);
      SymtabEntry constant = (SymtabEntry)symbolTable.get (dim);
      if (constant == null)
      {
        // A constant expr could be of the form <const> OR
        // <interface>.<const>.  This if branch checks for that case.
        int i = dim.lastIndexOf ('.');
        if (i >= 0)
          constant = (SymtabEntry)symbolTable.get (dim.substring (0, i));
      }
      if (constant != null && importTypes.contains (constant))
        addTo (importList, constant.name ());
    }
  } // checkForArrayDimensions

  // <d42256> Call the following method when its necessary to determine the
  // the import types for IDL constructs containing arbitrary positive int.
  // expressions, which may specify non-literal constants.

  /**
   * Determine the import lines for template types.
   **/
  static private void checkForBounds (SymtabEntry entry, Vector importTypes, Vector importList)
  {
    // Obtain actual type, just to be complete.
    SymtabEntry entryType = entry;
    while (entryType instanceof TypedefEntry)
      entryType = entryType.type ();

    if (entryType instanceof StringEntry && ((StringEntry)entryType).maxSize () != null)
      checkForGlobalConstants (((StringEntry)entryType).maxSize ().rep (), importTypes, importList);
    else
      if (entryType instanceof SequenceEntry && ((SequenceEntry)entryType).maxSize () != null)
        checkForGlobalConstants (((SequenceEntry)entryType).maxSize ().rep (), importTypes, importList);
  } // checkForBounds

  /**
   * Extract the global constants from the supplied integer expression
   * representation (string) and add them to the supplied import list.
   **/
  static private void checkForGlobalConstants (String exprRep, Vector importTypes, Vector importList)
  {
    // NOTE: Do not use '/' as a delimiter. Symbol table names use '/' as a
    // delimiter and would not be otherwise properly collected. Blanks and
    // arithmetic symbols do not appear in tokens, except for '/'.
    java.util.StringTokenizer st = new java.util.StringTokenizer (exprRep, " +-*()~&|^%<>");
    while (st.hasMoreTokens ())
    {
      String token = st.nextToken ();
      // When token contains '/', it represents the division symbol or
      // a nested type (e.g., I/x). Ignore the division symbol, and don't
      // forget constants declared within global interfaces!
      if (!token.equals ("/"))
      {
        SymtabEntry typeEntry = (SymtabEntry)symbolTable.get (token);
        if (typeEntry instanceof ConstEntry)
        {
          int slashIdx = token.indexOf ('/');
          if (slashIdx < 0)  // Possible global constant
          {
            if (importTypes.contains (typeEntry))
              addTo (importList, typeEntry.name ());
          }
          else  // Possible constant in global interface
          {
            SymtabEntry constContainer = (SymtabEntry)symbolTable.get (token.substring (0, slashIdx));
            if (constContainer instanceof InterfaceEntry && importTypes.contains (constContainer))
              addTo (importList, constContainer.name ());
          }
        }
      }
    }
  } // checkForGlobalConstants

  /**
   *
   **/
  public static void writeInitializer (String indent, String name, String arrayDcl, SymtabEntry entry, PrintWriter stream)
  {
    if (entry instanceof TypedefEntry)
    {
      TypedefEntry td = (TypedefEntry)entry;
      writeInitializer (indent, name, arrayDcl + sansArrayInfo (td.arrayInfo ()), td.type (), stream);
    }
    else if (entry instanceof SequenceEntry)
      writeInitializer (indent, name, arrayDcl + "[]", entry.type (), stream);
    else if (entry instanceof EnumEntry)
      if (arrayDcl.length () > 0)
        stream.println (indent + javaName (entry) + ' ' + name + arrayDcl + " = null;");
      else
        stream.println (indent + javaName (entry) + ' ' + name + " = null;");
    else if (entry instanceof PrimitiveEntry)
    {
      boolean array = arrayDcl.length () > 0;
      String tname = javaPrimName (entry.name ());
      if (tname.equals ("boolean"))
        stream.println (indent + "boolean " + name + arrayDcl + " = " + (array ? "null;" : "false;"));
      else if (tname.equals ("org.omg.CORBA.TypeCode"))
        stream.println (indent + "org.omg.CORBA.TypeCode " + name + arrayDcl + " = null;");
      else if (tname.equals ("org.omg.CORBA.Any"))
        stream.println (indent + "org.omg.CORBA.Any " + name + arrayDcl + " = null;");
      else if (tname.equals ("org.omg.CORBA.Principal")) // <d61961>
        stream.println (indent + "org.omg.CORBA.Principal " + name + arrayDcl + " = null;");
      else
        stream.println (indent + tname + ' ' + name + arrayDcl + " = " + (array ? "null;" : '(' + tname + ")0;"));
    }
    // <f46082.51> Remove -stateful feature. This case is identical to next one
    // because javaName() supplants javaStatefulName().
    //else if (entry instanceof InterfaceEntry && ((InterfaceEntry)entry).state () != null)
    //  stream.println (indent + javaStatefulName ((InterfaceEntry)entry) + ' ' + name + arrayDcl + " = null;");
    else
      stream.println (indent + javaName (entry) + ' ' + name + arrayDcl + " = null;");
  } // writeInitializer

  /**
   *
   **/
  public static void writeInitializer (String indent, String name, String arrayDcl, SymtabEntry entry, String initializer, PrintWriter stream)
  {
    if (entry instanceof TypedefEntry)
    {
      TypedefEntry td = (TypedefEntry)entry;
      writeInitializer (indent, name, arrayDcl + sansArrayInfo (td.arrayInfo ()), td.type (), initializer, stream);
    }
    else if (entry instanceof SequenceEntry)
      writeInitializer (indent, name, arrayDcl + "[]", entry.type (), initializer, stream);
    else if (entry instanceof EnumEntry)
      if (arrayDcl.length () > 0)
        stream.println (indent + javaName (entry) + ' ' + name + arrayDcl + " = " + initializer + ';');
      else
        stream.println (indent + javaName (entry) + ' ' + name + " = " + initializer + ';');
    else if (entry instanceof PrimitiveEntry)
    {
      boolean array = arrayDcl.length () > 0;
      String tname = javaPrimName (entry.name ());
      if (tname.equals ("boolean"))
        stream.println (indent + "boolean " + name + arrayDcl + " = " + initializer + ';');
      else if (tname.equals ("org.omg.CORBA.TypeCode"))
        stream.println (indent + "org.omg.CORBA.TypeCode " + name + arrayDcl + " = " + initializer + ';');
      else if (tname.equals ("org.omg.CORBA.Any"))
        stream.println (indent + "org.omg.CORBA.Any " + name + arrayDcl + " = " + initializer + ';');
      else if (tname.equals ("org.omg.CORBA.Principal")) // <d61961>
        stream.println (indent + "org.omg.CORBA.Principal " + name + arrayDcl + " = " + initializer + ';');
      else
        stream.println (indent + tname + ' ' + name + arrayDcl + " = " + initializer + ';');
    }
    // <f46082.51> Remove -stateful feature. This case is identical to next one
    // because javaName() supplants javaStatefulName().
    //else if (entry instanceof InterfaceEntry && ((InterfaceEntry)entry).state () != null)
    //  stream.println (indent + javaStatefulName ((InterfaceEntry)entry) + ' ' + name + arrayDcl + " = " + initializer + ';');
    else
      stream.println (indent + javaName (entry) + ' ' + name + arrayDcl + " = " + initializer + ';');
  } // writeInitializer

  /**
   *
   **/
  public static void mkdir (String name)
  {
    String targetDir = ((Arguments)Compile.compiler.arguments).targetDir; // F46838.4
    name = (targetDir + name).replace ('/', File.separatorChar); // F46838.4
    File pkg = new File (name);
    if (!pkg.exists ())
      if (!pkg.mkdirs ())
        System.err.println (getMessage ("Util.cantCreatePkg", name));
  } // mkdir

  /**
   *
   **/
  public static void writeProlog (PrintWriter stream, String filename)
  {
    // <d59355> Remove target directory
    String targetDir = ((Arguments)Compile.compiler.arguments).targetDir;
    if (targetDir != null)
      filename = filename.substring (targetDir.length ());
    stream.println ();
    stream.println ("/**");
    stream.println ("* " + filename.replace (File.separatorChar, '/') +
        " .");
    stream.println ("* " + Util.getMessage ("toJavaProlog1",
        Util.getMessage ("Version.product", Util.getMessage ("Version.number"))));
    // <d48911> Do not introduce invalid escape characters into comment! <daz>
    //stream.println ("* " + Util.getMessage ("toJavaProlog2", Compile.compiler.arguments.file));
    stream.println ("* " + Util.getMessage ("toJavaProlog2", Compile.compiler.arguments.file.replace (File.separatorChar, '/')));

    ///////////////
    // This SHOULD work, but there's a bug in the JDK.
    //    stream.println ("* " + DateFormat.getDateTimeInstance (DateFormat.FULL, DateFormat.FULL, Locale.getDefault ()).format (new Date ()));
    // This gets around the bug:

    DateFormat formatter = DateFormat.getDateTimeInstance (DateFormat.FULL, DateFormat.FULL, Locale.getDefault ());

    // Japanese-specific workaround.  JDK bug 4069784 being repaired by JavaSoft.
    // Keep this transient solution until bug fix is reported.cd .

    if (Locale.getDefault () == Locale.JAPAN)
      formatter.setTimeZone (java.util.TimeZone.getTimeZone ("JST"));
    else
      formatter.setTimeZone (java.util.TimeZone.getDefault ());

    stream.println ("* " + formatter.format (new Date ()));

    // <daz>
    ///////////////

    stream.println ("*/");
    stream.println ();
  } // writeProlog

  // keywords ending in Holder or Helper or Package have '_' prepended.
  // These prepended underscores must not be part of anything sent
  // across the wire, so these two methods are provided to strip them
  // off.

  /**
   *
   **/
  public static String stripLeadingUnderscores (String string)
  {
    while (string.startsWith ("_"))
      string = string.substring (1);
    return string;
  } // stripLeadingUnderscores

  /**
   *
   **/
  public static String stripLeadingUnderscoresFromID (String string)
  {
    String stringPrefix = "";
    int slashIndex = string.indexOf (':');
    if (slashIndex >= 0)
      do
      {
        stringPrefix = stringPrefix + string.substring (0, slashIndex + 1);
        string = string.substring (slashIndex + 1);
        while (string.startsWith ("_"))
          string = string.substring (1);
        slashIndex = string.indexOf ('/');
      } while (slashIndex >= 0);
    return stringPrefix + string;
  } // stripLeadingUnderscoresFromID

  /**
   *
   **/
  public static String parseExpression (Expression e)
  {
    if (e instanceof Terminal)
      return parseTerminal ((Terminal)e);
    else if (e instanceof BinaryExpr)
      return parseBinary ((BinaryExpr)e);
    else if (e instanceof UnaryExpr)
      return parseUnary ((UnaryExpr)e);
    else
      return "(UNKNOWN_VALUE)"; // This shouldn't happen unless someone slips
                                // in another type of expression.
  } // parseExpression

  /**
   *
   **/
  static String parseTerminal (Terminal e)
  {
    if (e.value () instanceof ConstEntry)
    {
      ConstEntry c = (ConstEntry)e.value ();
      if (c.container () instanceof InterfaceEntry)
        return javaQualifiedName (c.container ()) + '.' + c.name ();
      else
        return javaQualifiedName (c) + ".value";
    }
    else if (e.value () instanceof Expression)
      return '(' + parseExpression ((Expression)e.value ()) + ')';
    else if (e.value () instanceof Character)
    {
      if (((Character)e.value ()).charValue () == '\013')
        // e.rep is \v.  \v for vertical tab is meaningless in Java.
        return "'\\013'";
      else if (((Character)e.value ()).charValue () == '\007')
        // e.rep is \a.  \a for alert is meaningless in Java.
        return "'\\007'";
      else if (e.rep ().startsWith ("'\\x"))
        return hexToOctal (e.rep ());
      else if (e.rep ().equals ("'\\?'"))
        return "'?'";
      else
        return e.rep ();
    }
    else if (e.value () instanceof Boolean)
      return e.value ().toString ();

    // <d54640> If value is type "unsigned long long" (ull) and its magnitude
    // is greater than the maximal Java long (i.e., IDL long long) value, then
    // return its signed representation rather than its actual representation.
    /*
    // Support long long
    //else if (e.value () instanceof Long)
    else if (e.value () instanceof BigInteger &&
             (e.type ().indexOf ("long long") >= 0 || e.type ().equals ("unsigned long"))) // <klr>
    {
      String rep   = e.rep ();
      int    index = rep.indexOf (')');
      if (index < 0)
        return rep + 'L';
      else
        return rep.substring (0, index) + 'L' + rep.substring (index);
    }
    */
    else if (e.value () instanceof BigInteger)
    {
      // Get the correct primitive type. Since integer types (octet, short,
      // long, long long, unsigned short, unsigned long, unsigned long long)
      // could be aliased (typedef'ed) to any arbitrary levels, the code
      // below walks up the alias chain to get to the primitive type.

      // Get the symbol table entry corresponding to the 'type'.
      SymtabEntry typeEntry = (SymtabEntry) symbolTable.get(e.type());

      // Get to the primitive type.
      while (typeEntry.type() != null) {
          typeEntry = typeEntry.type();
      }
      String type = typeEntry.name();

      if (type.equals("unsigned long long") &&
          ((BigInteger)e.value ()).compareTo (Expression.llMax) > 0) // value > long long Max?
      {
        // Convert to signed value, which will always be negative.
        BigInteger v = (BigInteger)e.value ();
        v = v.subtract (Expression.twoPow64);
        int index = e.rep ().indexOf (')');
        if (index < 0)
          return v.toString () + 'L';
        else
          return '(' + v.toString () + 'L' + ')';
      }
      else if ( type.indexOf("long long") >= 0 || type.equals("unsigned long") )
      {
        String rep   = e.rep ();
        int    index = rep.indexOf (')');
        if (index < 0)
          return rep + 'L';
        else
          return rep.substring (0, index) + 'L' + rep.substring (index);
      }
      else
        return e.rep ();
    } // end <d54640>
    else
      return e.rep ();
  } // parseTerminal

  /**
   *
   **/
  static String hexToOctal (String hex)
  {
    // The format of hex is '/xXX' where XX is one or two hex digits.
    // This statement pulls off XX.
    hex = hex.substring (3, hex.length () - 1);
    return "'\\" + Integer.toString (Integer.parseInt (hex, 16), 8) + "'";
  } // hexToOctal

  /**
   *
   **/
  static String parseBinary (BinaryExpr e)
  {
    String castString = "";
    if (e.value () instanceof Float || e.value () instanceof Double)
    {
      castString = "(double)";
      if (!(e instanceof Plus || e instanceof Minus ||
            e instanceof Times || e instanceof Divide))
        System.err.println ("Operator " + e.op () + " is invalid on floating point numbers");
    }
    else if (e.value () instanceof Number)
    {
      if (e.type (). indexOf ("long long") >= 0)
        castString = "(long)";
      else
        castString = "(int)";
    }
    else
    {
      castString = "";
      System.err.println ("Unknown type in constant expression");
    }

    // <d54640> Must emit value rather than representation when type "unsigned
    // long long" (ull) because emitted binary arithmetic expressions containing
    // ull's converted to long (i.e., IDL long long) do not always compute to
    // the correct result.

    //return castString + '(' + parseExpression (e.left ()) + ' ' + e.op () + ' ' + parseExpression (e.right ()) + ')';
    if (e.type ().equals ("unsigned long long"))
    {
      BigInteger value = (BigInteger)e.value ();
      if (value.compareTo (Expression.llMax) > 0) // value > long long max?
        value = value.subtract (Expression.twoPow64); // Convert to Java long (signed)
      return castString + '(' + value.toString () + 'L' + ')';
    }
    else
      return castString + '(' + parseExpression (e.left ()) + ' ' + e.op () + ' ' + parseExpression (e.right ()) + ')';
    // <d54640> end
  } // parseBinary

  /**
   *
   **/
  static String parseUnary (UnaryExpr e)
  {
    if (!(e.value () instanceof Number))
      return "(UNKNOWN_VALUE)"; // This shouldn't happen if the parser checked the expression types correctly.
    else if ((e.value () instanceof Float || e.value () instanceof Double) && e instanceof Not)
      return "(UNKNOWN_VALUE)"; // This shouldn't happen if the parser checked the expression types correctly.
    else
    {
      String castString = "";
      if (e.operand ().value () instanceof Float ||
          e.operand ().value () instanceof Double)
        castString = "(double)";
      // Support long long.
      //else
      //  castString = "(long)";
      else if (e.type (). indexOf ("long long") >= 0)
        castString = "(long)";
      else
        castString = "(int)";

      // <d54640> Must emit value rather than representation when type is
      // "unsigned long long" (ull) because emitted unary arithmetic expressions
      // containing a ull converted to long (i.e., IDL long long) do not always
      // compute to the correct result.

      //return castString + e.op () + parseExpression (e.operand ());
      if (e.type ().equals ("unsigned long long"))
      {
        BigInteger value = (BigInteger)e.value ();
        if (value.compareTo (Expression.llMax) > 0) // value > long long max?
          value = value.subtract (Expression.twoPow64); // Convert to Java long (signed)
        return castString + '(' + value.toString () + 'L' + ')';
      }
      else
        return castString + e.op () + parseExpression (e.operand ());
      // end <d54640>
    }
  } // parseUnary

  /**
   *
   **/
  public static boolean IDLEntity (SymtabEntry entry)
  {
    boolean rc = true;
    if (entry instanceof PrimitiveEntry || entry instanceof StringEntry)
       rc = false;
    else if (entry instanceof TypedefEntry)
       rc = IDLEntity (entry.type ());
    return rc;
  } // IDLEntity

  // <d62023>
  /**
   * @return true if the current setting of corbaLevel is within delta of
   *    the range min <= corbaLevel <= max
   **/
  public static boolean corbaLevel (float min, float max)
  {
    float level = Compile.compiler.arguments.corbaLevel;
    float delta = 0.001f;
    if ((level - min + delta >= 0.0f) && (max - level + delta >= 0.0f))
        return true;
    else
        return false;
  } // corbaLevel

  static Hashtable symbolTable = new Hashtable ();
  static Hashtable packageTranslation = new Hashtable() ;
} // class Util
