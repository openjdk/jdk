# Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#  With this script one can generate a new version XKeysym.java file out
#  of keysym2ucs.h prototype and UnicodeData.txt database.
#  Latter file should be fetched from a unicode.org site, most
#  probably http://www.unicode.org/Public/UNIDATA/UnicodeData.txt
#
BEGIN {   FS=";";
          while((getline < "UnicodeData.txt")){
              unic[$1]=$2;
          }
          FS=" ";
          print("// This is a generated file: do not edit! Edit keysym2ucs.h if necessary.\n");          
      }

/^0x/{
         if( $1 != "0x0000" ) {
             ndx =  toupper($1);
             sub(/0X/, "", ndx);
             printf("        keysym2UCSHash.put( (long)%s, (char)%s); // %s -->%s\n",
                        $4, $1, $3, (unic[ndx]=="" ? "" : " " unic[ndx]));
         }
     }
/tojava/ { sub(/tojava /, ""); sub(/tojava$/, ""); print}    
