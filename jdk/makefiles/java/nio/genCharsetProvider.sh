#! /bin/sh

#
# Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# Generate a charset provider class

# Required environment variables
#   NAWK	awk tool
#   TEMPDIR      temporary directory
#   HASHER	Hasher program

SPEC=$1; shift
DST=$1; shift

eval `$NAWK <$SPEC '
  /^[ \t]*copyright / { printf "COPYRIGHT_YEARS=\"%s %s\"\n", $2, $3; }
  /^[ \t]*package / { printf "PKG=%s\n", $2; }
  /^[ \t]*class / { printf "CLASS=%s\n", $2; }
'`

OUT=$DST/$CLASS.java
echo '-->' $OUT


# Header
#

$SH ./addNotices.sh "$COPYRIGHT_YEARS" > $OUT

cat <<__END__ >>$OUT

// -- This file was mechanically generated: Do not edit! -- //

package $PKG;

import java.nio.charset.*;


public class $CLASS
    extends FastCharsetProvider
{

__END__


# Alias tables
#
$NAWK <$SPEC >>$OUT '
  BEGIN { n = 1; m = 1; }

  /^[ \t]*charset / {
    csn = $2; cln = $3;
    lcsn = tolower(csn);
    lcsns[n++] = lcsn;
    csns[lcsn] = csn;
    classMap[lcsn] = cln;
    if (n > 2)
      printf "    };\n\n";
    printf "    static final String[] aliases_%s = new String[] {\n", cln;
  }

  /^[ \t]*alias / {
    acsns[m++] = tolower($2);
    aliasMap[tolower($2)] = lcsn; 
    printf "        \"%s\",\n", $2;
  }

  END {
    printf "    };\n\n";
  }
'


# Prehashed alias and class maps
#
$NAWK <$SPEC >$TEMPDIR/aliases '
  /^[ \t]*charset / {
    csn = $2;
    lcsn = tolower(csn);
  }
  /^[ \t]*alias / {
    an = tolower($2);
    printf "%-20s \"%s\"\n", an, lcsn;
  }
'

$NAWK <$SPEC >$TEMPDIR/classes '
  /^[ \t]*charset / {
    csn = $2; cln = $3;
    lcsn = tolower(csn);
    printf "%-20s \"%s\"\n", lcsn, cln;
  }
'

${HASHER} -i Aliases <$TEMPDIR/aliases >>$OUT
${HASHER} -i Classes <$TEMPDIR/classes >>$OUT
${HASHER} -i -e Cache -t Charset <$TEMPDIR/classes >>$OUT


# Constructor
#
cat <<__END__ >>$OUT
    public $CLASS() {
        super("$PKG", new Aliases(), new Classes(), new Cache());
    }

}
__END__
