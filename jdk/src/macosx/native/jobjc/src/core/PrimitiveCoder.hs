#!/usr/bin/env runhaskell

{-
/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
-}
{-
The simplest way to get Haskell is through MacPorts: sudo port install ghc

Otherwise, see http://www.haskell.org/ghc/
-}

import Data.List
import Data.Maybe
import Char

data Width = W32 | W64
             deriving (Show, Eq, Bounded, Enum)

data NType = NBOOL | Nschar | Nuchar | Nsshort | Nushort | Nsint | Nuint
           | Nslong | Nulong | Nslonglong | Nulonglong | Nfloat | Ndouble
             deriving (Show, Eq, Bounded, Enum)

data JPrim = Jboolean | Jbyte | Jchar | Jshort | Jint | Jlong | Jfloat | Jdouble
             deriving (Show, Eq, Bounded, Enum)

data JClass = JBoolean | JByte | JCharacter | JShort | JInteger | JLong
            | JFloat | JDouble
              deriving (Show, Eq, Bounded, Enum)

data FFIType = SINT8 | UINT8 | SINT16 | UINT16 | SINT32 | UINT32
             | SINT64 | UINT64 | FLOAT | DOUBLE
             deriving (Show, Eq, Bounded, Enum)

widths = [minBound..maxBound] :: [Width]
ntypes = [minBound..maxBound] :: [NType]
jprims = [minBound..maxBound] :: [JPrim]
jclasses = [minBound..maxBound] :: [JClass]
ffitypes = [minBound..maxBound] :: [FFIType]

-- What's the FFIType for a given Width and NType? For example: W32 NBOOL -> SINT8
ffitype :: Width -> NType -> FFIType
ffitype _ NBOOL   = SINT8
ffitype _ Nschar  = SINT8
ffitype _ Nuchar  = UINT8
ffitype _ Nsshort = SINT16
ffitype _ Nushort = UINT16
ffitype _ Nsint   = SINT32
ffitype _ Nuint   = UINT32
ffitype W32 Nslong = SINT32
ffitype W64 Nslong = SINT64
ffitype W32 Nulong = UINT32
ffitype W64 Nulong = UINT64
ffitype _ Nslonglong = SINT64
ffitype _ Nulonglong = UINT64
ffitype _ Nfloat  = FLOAT
ffitype _ Ndouble = DOUBLE

sizeof :: FFIType -> Int
sizeof SINT8  = 1
sizeof UINT8  = 1
sizeof SINT16 = 2
sizeof UINT16 = 2
sizeof SINT32 = 4
sizeof UINT32 = 4
sizeof SINT64 = 8
sizeof UINT64 = 8
sizeof FLOAT  = 4
sizeof DOUBLE = 8

-- What's the Obj-C encoding for a given NType? For example: unsigned char -> 'C'
encoding nt = fromJust $ lookup nt $
              [(NBOOL, 'B'), (Nschar, 'c'), (Nuchar, 'C'), (Nsshort, 's'),
               (Nushort, 'S'), (Nsint, 'i'), (Nuint, 'I'), (Nslong, 'l'),
               (Nulong, 'L'), (Nslonglong, 'q'), (Nulonglong, 'Q'),
               (Nfloat, 'f'), (Ndouble, 'd')]

-- What's the JPrim for a given NType? For example: native signed long long -> java long
ntype2jprim nt = fromJust $ lookup nt $
                 [(NBOOL, Jboolean), (Nschar, Jbyte), (Nuchar, Jbyte),
                  (Nsshort, Jshort), (Nushort, Jshort), (Nsint, Jint), (Nuint, Jint),
                  (Nslong, Jlong), (Nulong, Jlong),
                  (Nslonglong, Jlong), (Nulonglong, Jlong),
                  (Nfloat, Jfloat), (Ndouble, Jdouble)]

-- What's the JClass for a given JPrim? For example: int -> Integer
jprim2jclass jp = fromJust $ lookup jp $
                  [(Jboolean, JBoolean), (Jbyte, JByte), (Jchar, JCharacter),
                   (Jshort, JShort), (Jint, JInteger), (Jlong, JLong),
                   (Jfloat, JFloat), (Jdouble, JDouble)]

-- Convert a type to something suitable for Java code. For example: Jboolean -> boolean
ntype2js nt = tail $ show nt
jclass2js t = tail $ show t
jprim2js p = tail $ show p
ffitype2js f = "FFI_" ++ (show f)

-- Capitalize the first letter of a String
capitalize [] = []
capitalize s = [toUpper $ head s] ++ tail s

-- Given an Width and NType, return the Java code for reading said NType from memory.
popAddr :: Width -> NType -> String
popAddr _ NBOOL   = "rt.unsafe.getByte(addr) != 0"
popAddr _ Nschar  = "rt.unsafe.getByte(addr)"
popAddr _ Nuchar  = "rt.unsafe.getByte(addr)"
popAddr W32 Nslong = "rt.unsafe.getInt(addr)"
popAddr W32 Nulong = "rt.unsafe.getInt(addr)"
popAddr _ ntype = "rt.unsafe.get" ++ (capitalize.jprim2js.ntype2jprim $ ntype) ++ "(addr)"

-- Given an Width and NType, return the Java code for writing said NType to memory.
pushAddr :: Width -> NType -> String
pushAddr _ NBOOL   = "rt.unsafe.putByte(addr, (byte) (x ? 1 : 0));"
pushAddr _ Nschar  = "rt.unsafe.putByte(addr, x);"
pushAddr _ Nuchar  = "rt.unsafe.putByte(addr, x);"
pushAddr W32 Nslong = "rt.unsafe.putInt(addr, (int) x);"
pushAddr W32 Nulong = "rt.unsafe.putInt(addr, (int) x);"
pushAddr _ ntype = "rt.unsafe.put" ++ (capitalize jprimS) ++ "(addr, (" ++ jprimS ++ ") x);"
    where jprimS = jprim2js.ntype2jprim $ ntype

-- Helpers for generating Java ternarnies and conditionals.
archExpr x32 x64 = if x32 /= x64 then retdiff else x32
    where retdiff = "(JObjCRuntime.IS64 ? (" ++ x64 ++ ") : (" ++ x32 ++ "))"

archStmt x32 x64 = if x32 /= x64 then retdiff else x32
    where retdiff = "if(JObjCRuntime.IS64){ " ++ x64 ++ " }else{ " ++ x32 ++ " }"

-- Get a Java expression for the correct FFIType at runtime. For example: (JObjCRuntime.IS64 ? FFI_SINT64 : FFI_SINT32)
ffitypeVal nt = archExpr (ffitype2js $ ffitype W32 nt)
                         (ffitype2js $ ffitype W64 nt)

-- Similar to ffiTypeVal. Get the correct pop expression and push statement.
popAddrVal nt = archExpr (popAddr W32 nt) (popAddr W64 nt)
pushAddrVal nt = archStmt (pushAddr W32 nt) (pushAddr W64 nt)

-- What's the Coder class name we're using for a given NType?
coderName nt = aux nt ++ "Coder"
    where
      aux NBOOL   = "Bool"
      aux Nschar  = "SChar"
      aux Nuchar  = "UChar"
      aux Nsshort = "SShort"
      aux Nushort = "UShort"
      aux Nsint   = "SInt"
      aux Nuint   = "UInt"
      aux Nslong  = "SLong"
      aux Nulong  = "ULong"
      aux Nslonglong   = "SLongLong"
      aux Nulonglong   = "ULongLong"
      aux Nfloat  = "Float"
      aux Ndouble = "Double"

-- Operation for converting between primitives. Usually it just casts, but booleans are special.
jconvertPrims sym Jboolean Jboolean = sym
jconvertPrims sym Jboolean b = "((" ++ jprim2js b ++ ")(" ++ sym ++ " ? 1 : 0))"
jconvertPrims sym a Jboolean = "(" ++ sym ++ " != 0)"
jconvertPrims sym a b = if a == b then sym else "((" ++ jprim2js b ++ ")" ++ sym ++ ")"

sizeofRet nt =
    let ffitypes = map (\w -> ffitype w nt) widths
        sizes = map sizeof ffitypes in
    if (length $ nub sizes) == 1
    then "\t\treturn " ++ (show.head $ sizes) ++ ";"
    else unlines [
              "\t\tswitch(w){",
              (unlines $ map casestmt widths),
              "\t\tdefault: return -1;",
               "\t\t}"]
    where
      casestmt w = "\t\t\tcase " ++ (show w) ++ ": return " ++
                   (show.sizeof $ ffitype w nt) ++ ";"

-- Generate a coder class for a given NType.
c2java ntype =
    unlines [
 "// native " ++ ntypeS ++ " -> java " ++ jprimS,
 "public static final class " ++ className ++ " extends PrimitiveCoder<" ++ jclassS ++ ">{",
 "\tpublic static final " ++ className ++ " INST = new " ++ className ++ "();",
 "\tpublic " ++ className ++ "(){ super("++ffitypeVal ntype++", \"" ++ [encoding ntype] ++ "\", "++jclassS++".class, "++jprimS++".class); }",
 "\t// compile time",
 "\t@Override public void push(JObjCRuntime rt, long addr, " ++ jprimS ++ " x){",
 "\t\t" ++ pushAddrVal ntype,
 "\t}",
 "\t@Override public " ++ jprimS ++ " pop" ++ capitalize jprimS ++ "(JObjCRuntime rt, long addr){",
 "\t\treturn " ++ popAddrVal ntype ++ ";",
 "\t}",
 "\t// for runtime coding",
 "\t@Override public int sizeof(Width w){",
 sizeofRet ntype,
 "\t}",
 "\t@Override public void push(JObjCRuntime rt, long addr, " ++ jclassS ++ " x){ " ++
 "push(rt, addr, (" ++ jprimS ++ ") x); }",
 "\t@Override public " ++ jclassS ++ " pop(JObjCRuntime rt, long addr){ " ++
 "return pop" ++ capitalize jprimS ++ "(rt, addr); }",
 "\t// proxies for mixed encoding",
 makeProxyMethods ntype,
 "}"
 ]
     where
       jprim = ntype2jprim ntype
       jclass = jprim2jclass jprim
       jprimS = jprim2js jprim
       jclassS = jclass2js jclass
       ntypeS = ntype2js ntype
       className = coderName ntype

-- Generate push and pop methods that convert and proxy to actual implementation.
makeProxyMethods nt = unlines $ map aux jprims
    where
      targetJPrim = ntype2jprim nt
      targetJPrimS = jprim2js targetJPrim
      aux jprim = if targetJPrim == jprim then "" else unlines [
                   "\t@Override public void push(JObjCRuntime rt, long addr, " ++ jprimS ++ " x){ " ++
                   "push(rt, addr, " ++ pushConversion "x" ++ "); }",
                   "\t@Override public " ++ jprimS ++ " pop" ++ capitalize jprimS ++ "(JObjCRuntime rt, long addr){ " ++
                   "return " ++ (popConversion ("pop" ++ capitalize targetJPrimS ++ "(rt, addr)")) ++ "; }"
                  ]
          where
            jprimS = jprim2js jprim
            pushConversion sym = jconvertPrims sym jprim targetJPrim
            popConversion sym = jconvertPrims sym targetJPrim jprim

main = do
  putStrLn "package com.apple.jobjc;"

  putStrLn "import com.apple.jobjc.JObjCRuntime.Width;"

  putStrLn "// Auto generated by PrimitiveCoder.hs"
  putStrLn "// Do not edit by hand."

  putStrLn "public abstract class PrimitiveCoder<T> extends Coder<T>{"

  putStrLn "\tpublic PrimitiveCoder(int ffiTypeCode, String objCEncoding, Class jclass, Class jprim){"
  putStrLn "\t\tsuper(ffiTypeCode, objCEncoding, jclass, jprim);"
  putStrLn "\t}"

  mapM_ (\p -> putStrLn $ unlines [makePopI p, makePushI p]) jprims

  mapM_ (putStrLn . c2java) ntypes

  putStrLn "}"
    where
      makePopI jprim = unlines ["\tpublic final " ++ jprim2js jprim ++ " pop" ++ (capitalize.jprim2js $ jprim)
                                   ++ "(NativeArgumentBuffer args){\n"
                                   ++ "\t\treturn pop" ++ (capitalize.jprim2js $ jprim) ++ "(args.runtime, args.retValPtr);\n"
                                   ++ "\t}",
                                "\tpublic abstract " ++ jprim2js jprim ++ " pop" ++ (capitalize.jprim2js $ jprim) ++ "(JObjCRuntime runtime, long addr);"]
      makePushI jprim = unlines ["\tpublic final void push"
          ++ "(NativeArgumentBuffer args, " ++ jprim2js jprim ++ " x){\n"
          ++ "\t\tpush(args.runtime, args.argValuesPtr, x);\n"
          ++ "\t\targs.didPutArgValue(sizeof());\n"
          ++ "\t}",
        "\tpublic abstract void push(JObjCRuntime runtime, long addr, " ++ jprim2js jprim ++ " x);"]
