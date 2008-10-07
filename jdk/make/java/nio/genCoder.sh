#! /bin/sh

#
# Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# Generate charset coder and decoder classes

# Environment variables required from make: SED SPP

what=$1
SRC=$2
DST=$3

if [ x$what = xdecoder ]; then

  echo '  '$SRC '--('$what')-->' $DST
  $SPP <$SRC >$DST \
    -K$what \
    -DA='A' \
    -Da='a' \
    -DCode='Decode' \
    -Dcode='decode' \
    -DitypesPhrase='bytes in a specific charset' \
    -DotypesPhrase='sixteen-bit Unicode characters' \
    -Ditype='byte' \
    -Dotype='character' \
    -DItype='Byte' \
    -DOtype='Char' \
    -Dcoder='decoder' \
    -DCoder='Decoder' \
    -Dcoding='decoding' \
    -DOtherCoder='Encoder' \
    -DreplTypeName='string' \
    -DdefaultRepl='"\\uFFFD"' \
    -DdefaultReplName='<tt>"\&#92;uFFFD"<\/tt>' \
    -DreplType='String' \
    -DreplFQType='java.lang.String' \
    -DreplLength='length()' \
    -DItypesPerOtype='CharsPerByte' \
    -DnotLegal='not legal for this charset' \
    -Dotypes-per-itype='chars-per-byte' \
    -DoutSequence='Unicode character'

elif [ x$what = xencoder ]; then

  echo '  '$SRC '--('$what')-->' $DST
  $SPP <$SRC >$DST \
    -K$what \
    -DA='An' \
    -Da='an' \
    -DCode='Encode' \
    -Dcode='encode' \
    -DitypesPhrase='sixteen-bit Unicode characters' \
    -DotypesPhrase='bytes in a specific charset' \
    -Ditype='character' \
    -Dotype='byte' \
    -DItype='Char' \
    -DOtype='Byte' \
    -Dcoder='encoder' \
    -DCoder='Encoder' \
    -Dcoding='encoding' \
    -DOtherCoder='Decoder' \
    -DreplTypeName='byte array' \
    -DdefaultRepl='new byte[] { (byte)'"'"\\?"'"' }' \
    -DdefaultReplName='<tt>{<\/tt>\&nbsp;<tt>(byte)'"'"\\?"'"'<\/tt>\&nbsp;<tt>}<\/tt>' \
    -DreplType='byte[]' \
    -DreplFQType='byte[]' \
    -DreplLength='length' \
    -DItypesPerOtype='BytesPerChar' \
    -DnotLegal='not a legal sixteen-bit Unicode sequence' \
    -Dotypes-per-itype='bytes-per-char' \
    -DoutSequence='byte sequence in the given charset'

else
  echo Illegal coder type: $what
  exit 1
fi
