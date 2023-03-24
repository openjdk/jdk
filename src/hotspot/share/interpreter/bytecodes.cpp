/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "utilities/align.hpp"
#include "utilities/bytes.hpp"

#define JVM_BYTECODES(XX)                                                                                                        \
  XX(_fast_agetfield            , "fast_agetfield"            , "bJJ"  , nullptr    , T_OBJECT ,  0, true , _getfield          ) \
  XX(_fast_bgetfield            , "fast_bgetfield"            , "bJJ"  , nullptr    , T_INT    ,  0, true , _getfield          ) \
  XX(_fast_cgetfield            , "fast_cgetfield"            , "bJJ"  , nullptr    , T_CHAR   ,  0, true , _getfield          ) \
  XX(_fast_dgetfield            , "fast_dgetfield"            , "bJJ"  , nullptr    , T_DOUBLE ,  0, true , _getfield          ) \
  XX(_fast_fgetfield            , "fast_fgetfield"            , "bJJ"  , nullptr    , T_FLOAT  ,  0, true , _getfield          ) \
  XX(_fast_igetfield            , "fast_igetfield"            , "bJJ"  , nullptr    , T_INT    ,  0, true , _getfield          ) \
  XX(_fast_lgetfield            , "fast_lgetfield"            , "bJJ"  , nullptr    , T_LONG   ,  0, true , _getfield          ) \
  XX(_fast_sgetfield            , "fast_sgetfield"            , "bJJ"  , nullptr    , T_SHORT  ,  0, true , _getfield          ) \
                                                                                                                                 \
  XX(_fast_aputfield            , "fast_aputfield"            , "bJJ"  , nullptr    , T_OBJECT ,  0, true , _putfield          ) \
  XX(_fast_bputfield            , "fast_bputfield"            , "bJJ"  , nullptr    , T_INT    ,  0, true , _putfield          ) \
  XX(_fast_zputfield            , "fast_zputfield"            , "bJJ"  , nullptr    , T_INT    ,  0, true , _putfield          ) \
  XX(_fast_cputfield            , "fast_cputfield"            , "bJJ"  , nullptr    , T_CHAR   ,  0, true , _putfield          ) \
  XX(_fast_dputfield            , "fast_dputfield"            , "bJJ"  , nullptr    , T_DOUBLE ,  0, true , _putfield          ) \
  XX(_fast_fputfield            , "fast_fputfield"            , "bJJ"  , nullptr    , T_FLOAT  ,  0, true , _putfield          ) \
  XX(_fast_iputfield            , "fast_iputfield"            , "bJJ"  , nullptr    , T_INT    ,  0, true , _putfield          ) \
  XX(_fast_lputfield            , "fast_lputfield"            , "bJJ"  , nullptr    , T_LONG   ,  0, true , _putfield          ) \
  XX(_fast_sputfield            , "fast_sputfield"            , "bJJ"  , nullptr    , T_SHORT  ,  0, true , _putfield          ) \
                                                                                                                                 \
  XX(_fast_aload_0              , "fast_aload_0"              , "b"    , nullptr    , T_OBJECT ,  1, true , _aload_0           ) \
  XX(_fast_iaccess_0            , "fast_iaccess_0"            , "b_JJ" , nullptr    , T_INT    ,  1, true , _aload_0           ) \
  XX(_fast_aaccess_0            , "fast_aaccess_0"            , "b_JJ" , nullptr    , T_OBJECT ,  1, true , _aload_0           ) \
  XX(_fast_faccess_0            , "fast_faccess_0"            , "b_JJ" , nullptr    , T_OBJECT ,  1, true , _aload_0           ) \
                                                                                                                                 \
  XX(_fast_iload                , "fast_iload"                , "bi"   , nullptr    , T_INT    ,  1, false, _iload             ) \
  XX(_fast_iload2               , "fast_iload2"               , "bi_i" , nullptr    , T_INT    ,  2, false, _iload             ) \
  XX(_fast_icaload              , "fast_icaload"              , "bi_"  , nullptr    , T_INT    ,  0, false, _iload             ) \
                                                                                                                                 \
  XX(_fast_invokevfinal         , "fast_invokevfinal"         , "bJJ"  , nullptr    , T_ILLEGAL, -1, true , _invokevirtual     ) \
  XX(_fast_linearswitch         , "fast_linearswitch"         , ""     , nullptr    , T_VOID   , -1, false, _lookupswitch      ) \
  XX(_fast_binaryswitch         , "fast_binaryswitch"         , ""     , nullptr    , T_VOID   , -1, false, _lookupswitch      ) \
                                                                                                                                 \
  XX(_fast_aldc                 , "fast_aldc"                 , "bj"   , nullptr    , T_OBJECT ,  1, true ,  _ldc              ) \
  XX(_fast_aldc_w               , "fast_aldc_w"               , "bJJ"  , nullptr    , T_OBJECT ,  1, true ,  _ldc_w            ) \
                                                                                                                                 \
  XX(_return_register_finalizer , "return_register_finalizer" , "b"     , nullptr    , T_VOID  ,  0, true , _return            ) \
                                                                                                                                 \
  XX(_invokehandle              , "invokehandle"              , "bJJ"  , nullptr    , T_ILLEGAL, -1, true , _invokevirtual     ) \
                                                                                                                                 \
  XX(_nofast_getfield           , "nofast_getfield"           , "bJJ"  , nullptr    , T_ILLEGAL,  0, true ,  _getfield         ) \
  XX(_nofast_putfield           , "nofast_putfield"           , "bJJ"  , nullptr    , T_ILLEGAL, -2, true , _putfield          ) \
  XX(_nofast_aload_0            , "nofast_aload_0"            , "b"    , nullptr    , T_OBJECT ,  1, true , _aload_0           ) \
  XX(_nofast_iload              , "nofast_iload"              , "bi"   , nullptr    , T_INT    ,  1, false, _iload             ) \
                                                                                                                                 \
  XX(_shouldnotreachhere        , "_shouldnotreachhere"       , "b"    , nullptr    , T_VOID   ,  0, false, _shouldnotreachhere)

#define BYTECODES(XX) \
  XX(_nop             , "nop"             , "b"    , nullptr    , T_VOID   ,  0, false, _nop            ) \
  XX(_aconst_null     , "aconst_null"     , "b"    , nullptr    , T_OBJECT ,  1, false, _aconst_null    ) \
  XX(_iconst_m1       , "iconst_m1"       , "b"    , nullptr    , T_INT    ,  1, false, _iconst_m1      ) \
  XX(_iconst_0        , "iconst_0"        , "b"    , nullptr    , T_INT    ,  1, false, _iconst_0       ) \
  XX(_iconst_1        , "iconst_1"        , "b"    , nullptr    , T_INT    ,  1, false, _iconst_1       ) \
  XX(_iconst_2        , "iconst_2"        , "b"    , nullptr    , T_INT    ,  1, false, _iconst_2       ) \
  XX(_iconst_3        , "iconst_3"        , "b"    , nullptr    , T_INT    ,  1, false, _iconst_3       ) \
  XX(_iconst_4        , "iconst_4"        , "b"    , nullptr    , T_INT    ,  1, false, _iconst_4       ) \
  XX(_iconst_5        , "iconst_5"        , "b"    , nullptr    , T_INT    ,  1, false, _iconst_5       ) \
  XX(_lconst_0        , "lconst_0"        , "b"    , nullptr    , T_LONG   ,  2, false, _lconst_0       ) \
  XX(_lconst_1        , "lconst_1"        , "b"    , nullptr    , T_LONG   ,  2, false, _lconst_1       ) \
  XX(_fconst_0        , "fconst_0"        , "b"    , nullptr    , T_FLOAT  ,  1, false, _fconst_0       ) \
  XX(_fconst_1        , "fconst_1"        , "b"    , nullptr    , T_FLOAT  ,  1, false, _fconst_1       ) \
  XX(_fconst_2        , "fconst_2"        , "b"    , nullptr    , T_FLOAT  ,  1, false, _fconst_2       ) \
  XX(_dconst_0        , "dconst_0"        , "b"    , nullptr    , T_DOUBLE ,  2, false, _dconst_0       ) \
  XX(_dconst_1        , "dconst_1"        , "b"    , nullptr    , T_DOUBLE ,  2, false, _dconst_1       ) \
  XX(_bipush          , "bipush"          , "bc"   , nullptr    , T_INT    ,  1, false, _bipush         ) \
  XX(_sipush          , "sipush"          , "bcc"  , nullptr    , T_INT    ,  1, false, _sipush         ) \
  XX(_ldc             , "ldc"             , "bk"   , nullptr    , T_ILLEGAL,  1, true , _ldc            ) \
  XX(_ldc_w           , "ldc_w"           , "bkk"  , nullptr    , T_ILLEGAL,  1, true , _ldc_w          ) \
  XX(_ldc2_w          , "ldc2_w"          , "bkk"  , nullptr    , T_ILLEGAL,  2, true , _ldc2_w         ) \
  XX(_iload           , "iload"           , "bi"   , "wbii"     , T_INT    ,  1, false, _iload          ) \
  XX(_lload           , "lload"           , "bi"   , "wbii"     , T_LONG   ,  2, false, _lload          ) \
  XX(_fload           , "fload"           , "bi"   , "wbii"     , T_FLOAT  ,  1, false, _fload          ) \
  XX(_dload           , "dload"           , "bi"   , "wbii"     , T_DOUBLE ,  2, false, _dload          ) \
  XX(_aload           , "aload"           , "bi"   , "wbii"     , T_OBJECT ,  1, false, _aload          ) \
  XX(_iload_0         , "iload_0"         , "b"    , nullptr    , T_INT    ,  1, false, _iload_0        ) \
  XX(_iload_1         , "iload_1"         , "b"    , nullptr    , T_INT    ,  1, false, _iload_1        ) \
  XX(_iload_2         , "iload_2"         , "b"    , nullptr    , T_INT    ,  1, false, _iload_2        ) \
  XX(_iload_3         , "iload_3"         , "b"    , nullptr    , T_INT    ,  1, false, _iload_3        ) \
  XX(_lload_0         , "lload_0"         , "b"    , nullptr    , T_LONG   ,  2, false, _lload_0        ) \
  XX(_lload_1         , "lload_1"         , "b"    , nullptr    , T_LONG   ,  2, false, _lload_1        ) \
  XX(_lload_2         , "lload_2"         , "b"    , nullptr    , T_LONG   ,  2, false, _lload_2        ) \
  XX(_lload_3         , "lload_3"         , "b"    , nullptr    , T_LONG   ,  2, false, _lload_3        ) \
  XX(_fload_0         , "fload_0"         , "b"    , nullptr    , T_FLOAT  ,  1, false, _fload_0        ) \
  XX(_fload_1         , "fload_1"         , "b"    , nullptr    , T_FLOAT  ,  1, false, _fload_1        ) \
  XX(_fload_2         , "fload_2"         , "b"    , nullptr    , T_FLOAT  ,  1, false, _fload_2        ) \
  XX(_fload_3         , "fload_3"         , "b"    , nullptr    , T_FLOAT  ,  1, false, _fload_3        ) \
  XX(_dload_0         , "dload_0"         , "b"    , nullptr    , T_DOUBLE ,  2, false, _dload_0        ) \
  XX(_dload_1         , "dload_1"         , "b"    , nullptr    , T_DOUBLE ,  2, false, _dload_1        ) \
  XX(_dload_2         , "dload_2"         , "b"    , nullptr    , T_DOUBLE ,  2, false, _dload_2        ) \
  XX(_dload_3         , "dload_3"         , "b"    , nullptr    , T_DOUBLE ,  2, false, _dload_3        ) \
  XX(_aload_0         , "aload_0"         , "b"    , nullptr    , T_OBJECT ,  1, true , _aload_0        ) \
  XX(_aload_1         , "aload_1"         , "b"    , nullptr    , T_OBJECT ,  1, false, _aload_1        ) \
  XX(_aload_2         , "aload_2"         , "b"    , nullptr    , T_OBJECT ,  1, false, _aload_2        ) \
  XX(_aload_3         , "aload_3"         , "b"    , nullptr    , T_OBJECT ,  1, false, _aload_3        ) \
  XX(_iaload          , "iaload"          , "b"    , nullptr    , T_INT    , -1, true , _iaload         ) \
  XX(_laload          , "laload"          , "b"    , nullptr    , T_LONG   ,  0, true , _laload         ) \
  XX(_faload          , "faload"          , "b"    , nullptr    , T_FLOAT  , -1, true , _faload         ) \
  XX(_daload          , "daload"          , "b"    , nullptr    , T_DOUBLE ,  0, true , _daload         ) \
  XX(_aaload          , "aaload"          , "b"    , nullptr    , T_OBJECT , -1, true , _aaload         ) \
  XX(_baload          , "baload"          , "b"    , nullptr    , T_INT    , -1, true , _baload         ) \
  XX(_caload          , "caload"          , "b"    , nullptr    , T_INT    , -1, true , _caload         ) \
  XX(_saload          , "saload"          , "b"    , nullptr    , T_INT    , -1, true , _saload         ) \
  XX(_istore          , "istore"          , "bi"   , "wbii"     , T_VOID   , -1, false, _istore         ) \
  XX(_lstore          , "lstore"          , "bi"   , "wbii"     , T_VOID   , -2, false, _lstore         ) \
  XX(_fstore          , "fstore"          , "bi"   , "wbii"     , T_VOID   , -1, false, _fstore         ) \
  XX(_dstore          , "dstore"          , "bi"   , "wbii"     , T_VOID   , -2, false, _dstore         ) \
  XX(_astore          , "astore"          , "bi"   , "wbii"     , T_VOID   , -1, false, _astore         ) \
  XX(_istore_0        , "istore_0"        , "b"    , nullptr    , T_VOID   , -1, false, _istore_0       ) \
  XX(_istore_1        , "istore_1"        , "b"    , nullptr    , T_VOID   , -1, false, _istore_1       ) \
  XX(_istore_2        , "istore_2"        , "b"    , nullptr    , T_VOID   , -1, false, _istore_2       ) \
  XX(_istore_3        , "istore_3"        , "b"    , nullptr    , T_VOID   , -1, false, _istore_3       ) \
  XX(_lstore_0        , "lstore_0"        , "b"    , nullptr    , T_VOID   , -2, false, _lstore_0       ) \
  XX(_lstore_1        , "lstore_1"        , "b"    , nullptr    , T_VOID   , -2, false, _lstore_1       ) \
  XX(_lstore_2        , "lstore_2"        , "b"    , nullptr    , T_VOID   , -2, false, _lstore_2       ) \
  XX(_lstore_3        , "lstore_3"        , "b"    , nullptr    , T_VOID   , -2, false, _lstore_3       ) \
  XX(_fstore_0        , "fstore_0"        , "b"    , nullptr    , T_VOID   , -1, false, _fstore_0       ) \
  XX(_fstore_1        , "fstore_1"        , "b"    , nullptr    , T_VOID   , -1, false, _fstore_1       ) \
  XX(_fstore_2        , "fstore_2"        , "b"    , nullptr    , T_VOID   , -1, false, _fstore_2       ) \
  XX(_fstore_3        , "fstore_3"        , "b"    , nullptr    , T_VOID   , -1, false, _fstore_3       ) \
  XX(_dstore_0        , "dstore_0"        , "b"    , nullptr    , T_VOID   , -2, false, _dstore_0       ) \
  XX(_dstore_1        , "dstore_1"        , "b"    , nullptr    , T_VOID   , -2, false, _dstore_1       ) \
  XX(_dstore_2        , "dstore_2"        , "b"    , nullptr    , T_VOID   , -2, false, _dstore_2       ) \
  XX(_dstore_3        , "dstore_3"        , "b"    , nullptr    , T_VOID   , -2, false, _dstore_3       ) \
  XX(_astore_0        , "astore_0"        , "b"    , nullptr    , T_VOID   , -1, false, _astore_0       ) \
  XX(_astore_1        , "astore_1"        , "b"    , nullptr    , T_VOID   , -1, false, _astore_1       ) \
  XX(_astore_2        , "astore_2"        , "b"    , nullptr    , T_VOID   , -1, false, _astore_2       ) \
  XX(_astore_3        , "astore_3"        , "b"    , nullptr    , T_VOID   , -1, false, _astore_3       ) \
  XX(_iastore         , "iastore"         , "b"    , nullptr    , T_VOID   , -3, true , _iastore        ) \
  XX(_lastore         , "lastore"         , "b"    , nullptr    , T_VOID   , -4, true , _lastore        ) \
  XX(_fastore         , "fastore"         , "b"    , nullptr    , T_VOID   , -3, true , _fastore        ) \
  XX(_dastore         , "dastore"         , "b"    , nullptr    , T_VOID   , -4, true , _dastore        ) \
  XX(_aastore         , "aastore"         , "b"    , nullptr    , T_VOID   , -3, true , _aastore        ) \
  XX(_bastore         , "bastore"         , "b"    , nullptr    , T_VOID   , -3, true , _bastore        ) \
  XX(_castore         , "castore"         , "b"    , nullptr    , T_VOID   , -3, true , _castore        ) \
  XX(_sastore         , "sastore"         , "b"    , nullptr    , T_VOID   , -3, true , _sastore        ) \
  XX(_pop             , "pop"             , "b"    , nullptr    , T_VOID   , -1, false, _pop            ) \
  XX(_pop2            , "pop2"            , "b"    , nullptr    , T_VOID   , -2, false, _pop2           ) \
  XX(_dup             , "dup"             , "b"    , nullptr    , T_VOID   ,  1, false, _dup            ) \
  XX(_dup_x1          , "dup_x1"          , "b"    , nullptr    , T_VOID   ,  1, false, _dup_x1         ) \
  XX(_dup_x2          , "dup_x2"          , "b"    , nullptr    , T_VOID   ,  1, false, _dup_x2         ) \
  XX(_dup2            , "dup2"            , "b"    , nullptr    , T_VOID   ,  2, false, _dup2           ) \
  XX(_dup2_x1         , "dup2_x1"         , "b"    , nullptr    , T_VOID   ,  2, false, _dup2_x1        ) \
  XX(_dup2_x2         , "dup2_x2"         , "b"    , nullptr    , T_VOID   ,  2, false, _dup2_x2        ) \
  XX(_swap            , "swap"            , "b"    , nullptr    , T_VOID   ,  0, false, _swap           ) \
  XX(_iadd            , "iadd"            , "b"    , nullptr    , T_INT    , -1, false, _iadd           ) \
  XX(_ladd            , "ladd"            , "b"    , nullptr    , T_LONG   , -2, false, _ladd           ) \
  XX(_fadd            , "fadd"            , "b"    , nullptr    , T_FLOAT  , -1, false, _fadd           ) \
  XX(_dadd            , "dadd"            , "b"    , nullptr    , T_DOUBLE , -2, false, _dadd           ) \
  XX(_isub            , "isub"            , "b"    , nullptr    , T_INT    , -1, false, _isub           ) \
  XX(_lsub            , "lsub"            , "b"    , nullptr    , T_LONG   , -2, false, _lsub           ) \
  XX(_fsub            , "fsub"            , "b"    , nullptr    , T_FLOAT  , -1, false, _fsub           ) \
  XX(_dsub            , "dsub"            , "b"    , nullptr    , T_DOUBLE , -2, false, _dsub           ) \
  XX(_imul            , "imul"            , "b"    , nullptr    , T_INT    , -1, false, _imul           ) \
  XX(_lmul            , "lmul"            , "b"    , nullptr    , T_LONG   , -2, false, _lmul           ) \
  XX(_fmul            , "fmul"            , "b"    , nullptr    , T_FLOAT  , -1, false, _fmul           ) \
  XX(_dmul            , "dmul"            , "b"    , nullptr    , T_DOUBLE , -2, false, _dmul           ) \
  XX(_idiv            , "idiv"            , "b"    , nullptr    , T_INT    , -1, true , _idiv           ) \
  XX(_ldiv            , "ldiv"            , "b"    , nullptr    , T_LONG   , -2, true , _ldiv           ) \
  XX(_fdiv            , "fdiv"            , "b"    , nullptr    , T_FLOAT  , -1, false, _fdiv           ) \
  XX(_ddiv            , "ddiv"            , "b"    , nullptr    , T_DOUBLE , -2, false, _ddiv           ) \
  XX(_irem            , "irem"            , "b"    , nullptr    , T_INT    , -1, true , _irem           ) \
  XX(_lrem            , "lrem"            , "b"    , nullptr    , T_LONG   , -2, true , _lrem           ) \
  XX(_frem            , "frem"            , "b"    , nullptr    , T_FLOAT  , -1, false, _frem           ) \
  XX(_drem            , "drem"            , "b"    , nullptr    , T_DOUBLE , -2, false, _drem           ) \
  XX(_ineg            , "ineg"            , "b"    , nullptr    , T_INT    ,  0, false, _ineg           ) \
  XX(_lneg            , "lneg"            , "b"    , nullptr    , T_LONG   ,  0, false, _lneg           ) \
  XX(_fneg            , "fneg"            , "b"    , nullptr    , T_FLOAT  ,  0, false, _fneg           ) \
  XX(_dneg            , "dneg"            , "b"    , nullptr    , T_DOUBLE ,  0, false, _dneg           ) \
  XX(_ishl            , "ishl"            , "b"    , nullptr    , T_INT    , -1, false, _ishl           ) \
  XX(_lshl            , "lshl"            , "b"    , nullptr    , T_LONG   , -1, false, _lshl           ) \
  XX(_ishr            , "ishr"            , "b"    , nullptr    , T_INT    , -1, false, _ishr           ) \
  XX(_lshr            , "lshr"            , "b"    , nullptr    , T_LONG   , -1, false, _lshr           ) \
  XX(_iushr           , "iushr"           , "b"    , nullptr    , T_INT    , -1, false, _iushr          ) \
  XX(_lushr           , "lushr"           , "b"    , nullptr    , T_LONG   , -1, false, _lushr          ) \
  XX(_iand            , "iand"            , "b"    , nullptr    , T_INT    , -1, false, _iand           ) \
  XX(_land            , "land"            , "b"    , nullptr    , T_LONG   , -2, false, _land           ) \
  XX(_ior             , "ior"             , "b"    , nullptr    , T_INT    , -1, false, _ior            ) \
  XX(_lor             , "lor"             , "b"    , nullptr    , T_LONG   , -2, false, _lor            ) \
  XX(_ixor            , "ixor"            , "b"    , nullptr    , T_INT    , -1, false, _ixor           ) \
  XX(_lxor            , "lxor"            , "b"    , nullptr    , T_LONG   , -2, false, _lxor           ) \
  XX(_iinc            , "iinc"            , "bic"  , "wbiicc",    T_VOID   ,  0, false, _iinc           ) \
  XX(_i2l             , "i2l"             , "b"    , nullptr    , T_LONG   ,  1, false, _i2l            ) \
  XX(_i2f             , "i2f"             , "b"    , nullptr    , T_FLOAT  ,  0, false, _i2f            ) \
  XX(_i2d             , "i2d"             , "b"    , nullptr    , T_DOUBLE ,  1, false, _i2d            ) \
  XX(_l2i             , "l2i"             , "b"    , nullptr    , T_INT    , -1, false, _l2i            ) \
  XX(_l2f             , "l2f"             , "b"    , nullptr    , T_FLOAT  , -1, false, _l2f            ) \
  XX(_l2d             , "l2d"             , "b"    , nullptr    , T_DOUBLE ,  0, false, _l2d            ) \
  XX(_f2i             , "f2i"             , "b"    , nullptr    , T_INT    ,  0, false, _f2i            ) \
  XX(_f2l             , "f2l"             , "b"    , nullptr    , T_LONG   ,  1, false, _f2l            ) \
  XX(_f2d             , "f2d"             , "b"    , nullptr    , T_DOUBLE ,  1, false, _f2d            ) \
  XX(_d2i             , "d2i"             , "b"    , nullptr    , T_INT    , -1, false, _d2i            ) \
  XX(_d2l             , "d2l"             , "b"    , nullptr    , T_LONG   ,  0, false, _d2l            ) \
  XX(_d2f             , "d2f"             , "b"    , nullptr    , T_FLOAT  , -1, false, _d2f            ) \
  XX(_i2b             , "i2b"             , "b"    , nullptr    , T_BYTE   ,  0, false, _i2b            ) \
  XX(_i2c             , "i2c"             , "b"    , nullptr    , T_CHAR   ,  0, false, _i2c            ) \
  XX(_i2s             , "i2s"             , "b"    , nullptr    , T_SHORT  ,  0, false, _i2s            ) \
  XX(_lcmp            , "lcmp"            , "b"    , nullptr    , T_VOID   , -3, false, _lcmp           ) \
  XX(_fcmpl           , "fcmpl"           , "b"    , nullptr    , T_VOID   , -1, false, _fcmpl          ) \
  XX(_fcmpg           , "fcmpg"           , "b"    , nullptr    , T_VOID   , -1, false, _fcmpg          ) \
  XX(_dcmpl           , "dcmpl"           , "b"    , nullptr    , T_VOID   , -3, false, _dcmpl          ) \
  XX(_dcmpg           , "dcmpg"           , "b"    , nullptr    , T_VOID   , -3, false, _dcmpg          ) \
  XX(_ifeq            , "ifeq"            , "boo"  , nullptr    , T_VOID   , -1, false, _ifeq           ) \
  XX(_ifne            , "ifne"            , "boo"  , nullptr    , T_VOID   , -1, false, _ifne           ) \
  XX(_iflt            , "iflt"            , "boo"  , nullptr    , T_VOID   , -1, false, _iflt           ) \
  XX(_ifge            , "ifge"            , "boo"  , nullptr    , T_VOID   , -1, false, _ifge           ) \
  XX(_ifgt            , "ifgt"            , "boo"  , nullptr    , T_VOID   , -1, false, _ifgt           ) \
  XX(_ifle            , "ifle"            , "boo"  , nullptr    , T_VOID   , -1, false, _ifle           ) \
  XX(_if_icmpeq       , "if_icmpeq"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_icmpeq      ) \
  XX(_if_icmpne       , "if_icmpne"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_icmpne      ) \
  XX(_if_icmplt       , "if_icmplt"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_icmplt      ) \
  XX(_if_icmpge       , "if_icmpge"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_icmpge      ) \
  XX(_if_icmpgt       , "if_icmpgt"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_icmpgt      ) \
  XX(_if_icmple       , "if_icmple"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_icmple      ) \
  XX(_if_acmpeq       , "if_acmpeq"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_acmpeq      ) \
  XX(_if_acmpne       , "if_acmpne"       , "boo"  , nullptr    , T_VOID   , -2, false, _if_acmpne      ) \
  XX(_goto            , "goto"            , "boo"  , nullptr    , T_VOID   ,  0, false, _goto           ) \
  XX(_jsr             , "jsr"             , "boo"  , nullptr    , T_INT    ,  0, false, _jsr            ) \
  XX(_ret             , "ret"             , "bi"   , "wbii"     , T_VOID   ,  0, false, _ret            ) \
  XX(_tableswitch     , "tableswitch"     , ""     , nullptr    , T_VOID   , -1, false, _tableswitch    ) \
  XX(_lookupswitch    , "lookupswitch"    , ""     , nullptr    , T_VOID   , -1, false, _lookupswitch   ) \
  XX(_ireturn         , "ireturn"         , "b"    , nullptr    , T_INT    , -1, true , _ireturn        ) \
  XX(_lreturn         , "lreturn"         , "b"    , nullptr    , T_LONG   , -2, true , _lreturn        ) \
  XX(_freturn         , "freturn"         , "b"    , nullptr    , T_FLOAT  , -1, true , _freturn        ) \
  XX(_dreturn         , "dreturn"         , "b"    , nullptr    , T_DOUBLE , -2, true , _dreturn        ) \
  XX(_areturn         , "areturn"         , "b"    , nullptr    , T_OBJECT , -1, true , _areturn        ) \
  XX(_return          , "return"          , "b"    , nullptr    , T_VOID   ,  0, true , _return         ) \
  XX(_getstatic       , "getstatic"       , "bJJ"  , nullptr    , T_ILLEGAL,  1, true , _getstatic      ) \
  XX(_putstatic       , "putstatic"       , "bJJ"  , nullptr    , T_ILLEGAL, -1, true , _putstatic      ) \
  XX(_getfield        , "getfield"        , "bJJ"  , nullptr    , T_ILLEGAL,  0, true , _getfield       ) \
  XX(_putfield        , "putfield"        , "bJJ"  , nullptr    , T_ILLEGAL, -2, true , _putfield       ) \
  XX(_invokevirtual   , "invokevirtual"   , "bJJ"  , nullptr    , T_ILLEGAL, -1, true , _invokevirtual  ) \
  XX(_invokespecial   , "invokespecial"   , "bJJ"  , nullptr    , T_ILLEGAL, -1, true , _invokespecial  ) \
  XX(_invokestatic    , "invokestatic"    , "bJJ"  , nullptr    , T_ILLEGAL,  0, true , _invokestatic   ) \
  XX(_invokeinterface , "invokeinterface" , "bJJ__", nullptr    , T_ILLEGAL, -1, true , _invokeinterface) \
  XX(_invokedynamic   , "invokedynamic"   , "bJJJJ", nullptr    , T_ILLEGAL,  0, true , _invokedynamic  ) \
  XX(_new             , "new"             , "bkk"  , nullptr    , T_OBJECT ,  1, true , _new            ) \
  XX(_newarray        , "newarray"        , "bc"   , nullptr    , T_OBJECT ,  0, true , _newarray       ) \
  XX(_anewarray       , "anewarray"       , "bkk"  , nullptr    , T_OBJECT ,  0, true , _anewarray      ) \
  XX(_arraylength     , "arraylength"     , "b"    , nullptr    , T_INT    ,  0, true , _arraylength    ) \
  XX(_athrow          , "athrow"          , "b"    , nullptr    , T_VOID   , -1, true , _athrow         ) \
  XX(_checkcast       , "checkcast"       , "bkk"  , nullptr    , T_OBJECT ,  0, true , _checkcast      ) \
  XX(_instanceof      , "instanceof"      , "bkk"  , nullptr    , T_INT    ,  0, true , _instanceof     ) \
  XX(_monitorenter    , "monitorenter"    , "b"    , nullptr    , T_VOID   , -1, true , _monitorenter   ) \
  XX(_monitorexit     , "monitorexit"     , "b"    , nullptr    , T_VOID   , -1, true , _monitorexit    ) \
  XX(_wide            , "wide"            , ""     , nullptr    , T_VOID   ,  0, false, _wide           ) \
  XX(_multianewarray  , "multianewarray"  , "bkkc" , nullptr    , T_OBJECT ,  1, true , _multianewarray ) \
  XX(_ifnull          , "ifnull"          , "boo"  , nullptr    , T_VOID   , -1, false, _ifnull         ) \
  XX(_ifnonnull       , "ifnonnull"       , "boo"  , nullptr    , T_VOID   , -1, false, _ifnonnull      ) \
  XX(_goto_w          , "goto_w"          , "boooo", nullptr    , T_VOID   ,  0, false, _goto_w         ) \
  XX(_jsr_w           , "jsr_w"           , "boooo", nullptr    , T_INT    ,  0, false, _jsr_w          ) \
  XX(_breakpoint      , "breakpoint"      , ""     , nullptr    , T_VOID   ,  0, true , _breakpoint     ) \
  JVM_BYTECODES(XX)

bool Bytecodes::_is_initialized = false;

const char* const Bytecodes::_name[Bytecodes::number_of_codes] = {
#define BYTECODE_NAME(code, name, format, wide_format, result_type, depth, can_trap, java_code) name,
  BYTECODES(BYTECODE_NAME)
#undef BYTECODE_NAME
};

const BasicType Bytecodes::_result_type[Bytecodes::number_of_codes] = {
#define BYTECODE_RESULT_TYPE(code, name, format, wide_format, result_type, depth, can_trap, java_code) result_type,
  BYTECODES(BYTECODE_RESULT_TYPE)
#undef BYTECODE_RESULT_TYPE
};

const s_char Bytecodes::_depth[Bytecodes::number_of_codes] = {
#define BYTECODE_DEPTH(code, name, format, wide_format, result_type, depth, can_trap, java_code) depth,
  BYTECODES(BYTECODE_DEPTH)
#undef BYTECODE_DEPTH
};

// Helper for determining the size (a.k.a. length) of a string literal.
struct StringLiteralSize {
  template <size_t N>
  static constexpr size_t invoke(const char (&)[N]) {
    static_assert(N > 0, "N must be greater than 0");
    // The size is N - 1, as C strings have an implicit NUL at the end. So "foo" will result in N
    // being 4, but we actually want 3.
    return N - 1;
  }

  static constexpr size_t invoke(std::nullptr_t) {
    return 0;
  }
};

#define STRING_SIZE(string) StringLiteralSize::invoke(string)

const u_char Bytecodes::_lengths[Bytecodes::number_of_codes] = {
#define BYTECODE_LENGTHS(code, name, format, wide_format, result_type, depth, can_trap, java_code) (STRING_SIZE(wide_format) << 4) | (STRING_SIZE(format) & 0xf),
  BYTECODES(BYTECODE_LENGTHS)
#undef BYTECODE_LENGTHS
};

const Bytecodes::Code Bytecodes::_java_code[Bytecodes::number_of_codes] = {
#define BYTECODE_JAVA_CODE(code, name, format, wide_format, result_type, depth, can_trap, java_code) Bytecodes::java_code,
  BYTECODES(BYTECODE_JAVA_CODE)
#undef BYTECODE_JAVA_CODE
};

unsigned short Bytecodes::_flags[(1<<BitsPerByte)*2];

#ifdef ASSERT
bool Bytecodes::check_method(const Method* method, address bcp) {
  return method->contains(bcp);
}
#endif

bool Bytecodes::check_must_rewrite(Bytecodes::Code code) {
  assert(can_rewrite(code), "post-check only");

  // Some codes are conditionally rewriting.  Look closely at them.
  switch (code) {
  case Bytecodes::_aload_0:
    // Even if RewriteFrequentPairs is turned on,
    // the _aload_0 code might delay its rewrite until
    // a following _getfield rewrites itself.
    return false;

  case Bytecodes::_lookupswitch:
    return false;  // the rewrite is not done by the interpreter

  case Bytecodes::_new:
    // (Could actually look at the class here, but the profit would be small.)
    return false;  // the rewrite is not always done

  default:
    // No other special cases.
    return true;
  }
}

Bytecodes::Code Bytecodes::code_at(Method* method, int bci) {
  return code_at(method, method->bcp_from(bci));
}

Bytecodes::Code Bytecodes::non_breakpoint_code_at(const Method* method, address bcp) {
  assert(method != nullptr, "must have the method for breakpoint conversion");
  assert(method->contains(bcp), "must be valid bcp in method");
  return method->orig_bytecode_at(method->bci_from(bcp));
}

int Bytecodes::special_length_at(Bytecodes::Code code, address bcp, address end) {
  switch (code) {
  case _wide:
    if (end != nullptr && bcp + 1 >= end) {
      return -1; // don't read past end of code buffer
    }
    return wide_length_for(cast(*(bcp + 1)));
  case _tableswitch:
    { address aligned_bcp = align_up(bcp + 1, jintSize);
      if (end != nullptr && aligned_bcp + 3*jintSize >= end) {
        return -1; // don't read past end of code buffer
      }
      jlong lo = (jint)Bytes::get_Java_u4(aligned_bcp + 1*jintSize);
      jlong hi = (jint)Bytes::get_Java_u4(aligned_bcp + 2*jintSize);
      jlong len = (aligned_bcp - bcp) + (3 + hi - lo + 1)*jintSize;
      // only return len if it can be represented as a positive int;
      // return -1 otherwise
      return (len > 0 && len == (int)len) ? len : -1;
    }

  case _lookupswitch:      // fall through
  case _fast_binaryswitch: // fall through
  case _fast_linearswitch:
    { address aligned_bcp = align_up(bcp + 1, jintSize);
      if (end != nullptr && aligned_bcp + 2*jintSize >= end) {
        return -1; // don't read past end of code buffer
      }
      jlong npairs = (jint)Bytes::get_Java_u4(aligned_bcp + jintSize);
      jlong len = (aligned_bcp - bcp) + (2 + 2*npairs)*jintSize;
      // only return len if it can be represented as a positive int;
      // return -1 otherwise
      return (len > 0 && len == (int)len) ? len : -1;
    }
  default:
    // Note: Length functions must return <=0 for invalid bytecodes.
    return 0;
  }
}

// At a breakpoint instruction, this returns the breakpoint's length,
// otherwise, it's the same as special_length_at().  This is used by
// the RawByteCodeStream, which wants to see the actual bytecode
// values (including breakpoint).  RawByteCodeStream is used by the
// verifier when reading in bytecode to verify.  Other mechanisms that
// run at runtime (such as generateOopMaps) need to iterate over the code
// and don't expect to see breakpoints: they want to see the instruction
// which was replaced so that they can get the correct length and find
// the next bytecode.
//
// 'end' indicates the end of the code buffer, which we should not try to read
// past.
int Bytecodes::raw_special_length_at(address bcp, address end) {
  Code code = code_or_bp_at(bcp);
  if (code == _breakpoint) {
    return 1;
  } else {
    return special_length_at(code, bcp, end);
  }
}

void Bytecodes::def_flags(Code code, const char* format, const char* wide_format, bool can_trap, Code java_code) {
  assert(wide_format == nullptr || format != nullptr, "short form must exist if there's a wide form");
#ifdef ASSERT
  int len  = (format      != nullptr ? (int) strlen(format)      : 0);
  int wlen = (wide_format != nullptr ? (int) strlen(wide_format) : 0);
#endif
  int bc_flags = 0;
  if (can_trap)           bc_flags |= _bc_can_trap;
  if (java_code != code)  bc_flags |= _bc_can_rewrite;
  _flags[(u1)code+0*(1<<BitsPerByte)] = compute_flags(format,      bc_flags);
  _flags[(u1)code+1*(1<<BitsPerByte)] = compute_flags(wide_format, bc_flags);
  assert(is_defined(code)      == (format != nullptr),      "");
  assert(wide_is_defined(code) == (wide_format != nullptr), "");
  assert(length_for(code)      == len,  "");
  assert(wide_length_for(code) == wlen, "");
}


// Format strings interpretation:
//
// b: bytecode
// c: signed constant, Java byte-ordering
// i: unsigned local index, Java byte-ordering (I = native byte ordering)
// j: unsigned CP cache index, Java byte-ordering (J = native byte ordering)
// k: unsigned CP index, Java byte-ordering
// o: branch offset, Java byte-ordering
// _: unused/ignored
// w: wide bytecode
//
// Note: The format strings are used for 2 purposes:
//       1. to specify the length of the bytecode
//          (= number of characters in format string)
//       2. to derive bytecode format flags (_fmt_has_k, etc.)
//
// Note: For bytecodes with variable length, the format string is the empty string.

int Bytecodes::compute_flags(const char* format, int more_flags) {
  if (format == nullptr)  return 0;  // not even more_flags
  int flags = more_flags;
  const char* fp = format;
  switch (*fp) {
  case '\0':
    flags |= _fmt_not_simple; // but variable
    break;
  case 'b':
    flags |= _fmt_not_variable;  // but simple
    ++fp;  // skip 'b'
    break;
  case 'w':
    flags |= _fmt_not_variable | _fmt_not_simple;
    ++fp;  // skip 'w'
    guarantee(*fp == 'b', "wide format must start with 'wb'");
    ++fp;  // skip 'b'
    break;
  }

  int has_nbo = 0, has_jbo = 0, has_size = 0;
  for (;;) {
    int this_flag = 0;
    char fc = *fp++;
    switch (fc) {
    case '\0':  // end of string
      assert(flags == (jchar)flags, "change _format_flags");
      return flags;

    case '_': continue;         // ignore these

    case 'j': this_flag = _fmt_has_j; has_jbo = 1; break;
    case 'k': this_flag = _fmt_has_k; has_jbo = 1; break;
    case 'i': this_flag = _fmt_has_i; has_jbo = 1; break;
    case 'c': this_flag = _fmt_has_c; has_jbo = 1; break;
    case 'o': this_flag = _fmt_has_o; has_jbo = 1; break;

    // uppercase versions mark native byte order (from Rewriter)
    // actually, only the 'J' case happens currently
    case 'J': this_flag = _fmt_has_j; has_nbo = 1; break;
    case 'K': this_flag = _fmt_has_k; has_nbo = 1; break;
    case 'I': this_flag = _fmt_has_i; has_nbo = 1; break;
    case 'C': this_flag = _fmt_has_c; has_nbo = 1; break;
    case 'O': this_flag = _fmt_has_o; has_nbo = 1; break;
    default:  guarantee(false, "bad char in format");
    }

    flags |= this_flag;

    guarantee(!(has_jbo && has_nbo), "mixed byte orders in format");
    if (has_nbo)
      flags |= _fmt_has_nbo;

    int this_size = 1;
    if (*fp == fc) {
      // advance beyond run of the same characters
      this_size = 2;
      while (*++fp == fc)  this_size++;
      switch (this_size) {
      case 2: flags |= _fmt_has_u2; break;
      case 4: flags |= _fmt_has_u4; break;
      default: guarantee(false, "bad rep count in format");
      }
    }
    guarantee(has_size == 0 ||                     // no field yet
              this_size == has_size ||             // same size
              this_size < has_size && *fp == '\0', // last field can be short
              "mixed field sizes in format");
    has_size = this_size;
  }
}

void Bytecodes::initialize() {
  if (_is_initialized) return;

  // initialize bytecode tables - didn't use static array initializers
  // (such as {}) so we can do additional consistency checks and init-
  // code is independent of actual bytecode numbering.
  //
  // Note 1: The result type is T_ILLEGAL for bytecodes where the top of stack
  //         type after execution is not only determined by the bytecode itself.

#define BYTECODE(code, name, format, wide_format, result_type, depth, can_trap, java_code) \
  assert(strcmp(_name[code], name) == 0, "bytecode name mismatch");                        \
  assert(_result_type[code] == result_type, "bytecode result_type mismatch");              \
  assert(_depth[code] == depth, "bytecode depth mismatch");                                \
  assert(_lengths[code] == (STRING_SIZE(wide_format) << 4) | (STRING_SIZE(format) & 0xF),  \
         "bytecode lengths mismatch");                                                     \
  assert(_java_code[code] == java_code, "bytecode java_code mismatch");                    \
  def_flags(code, format, wide_format, can_trap, java_code);
  BYTECODES(BYTECODE)
#undef BYTECODE

  // compare can_trap information for each bytecode with the
  // can_trap information for the corresponding base bytecode
  // (if a rewritten bytecode can trap, so must the base bytecode)
  #ifdef ASSERT
    { for (int i = 0; i < number_of_codes; i++) {
        if (is_defined(i)) {
          Code code = cast(i);
          Code java = java_code(code);
          if (can_trap(code) && !can_trap(java))
            fatal("%s can trap => %s can trap, too", name(code), name(java));
        }
      }
    }
  #endif

  // initialization successful
  _is_initialized = true;
}


void bytecodes_init() {
  Bytecodes::initialize();
}

// Restore optimization
#ifdef _M_AMD64
#pragma optimize ("", on)
#endif
