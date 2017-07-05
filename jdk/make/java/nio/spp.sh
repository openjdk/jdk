#! /bin/sh

#
# Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

# SPP: A simple/sed-based/stream preprocessor
# Mark Reinhold / mr@sun.com
#
# Usage: spp [-be] [-Kkey] -Dvar=value ... <in >out
#
# Source-file constructs
#
#   Meaningful only at beginning of line, works with any number of keys:
#
#     #if[key]              Includes text between #if/#end if -Kkey specified,
#     #else[key]               otherwise changes text to blank lines; key test
#     #end[key]                may be negated by prefixing !, e.g., #if[!key]
#
#     #begin                If -be is specified then lines up to and including
#     #end                     #begin, and from #end to EOF, are deleted
#
#     #warn                 Changed into warning that file is generated
#
#     // ##                 Changed into blank line
#
#   Meaningful anywhere in line, works only for first two keys:
#
#     {#if[key]?yes}        Expands to yes if -Kkey specified
#     {#if[key]?yes:no}     Expands to yes if -Kkey, otherwise no
#     {#if[!key]?yes}       Expands to yes if -Kother
#     {#if[!key]?yes:no}    Expands to yes if -Kother, otherwise no
#     $var$                 Expands to value if -Dvar=value given
#
#     yes, no must not contain whitespace
#
# If the environment variable SED is defined, uses that instead of sed
# If the environment variable NAWK is defined, uses that instead of awk
#

SED=${SED:-sed}
NAWK=${NAWK:-awk}

# Map a string of the form -Dvar=value into an appropriate sed command
#
subst() {
  # The first two lines are to avoid the direct use of echo,
  # which does not treat backslashes consistently across platforms
  echo '' \
    | $SED -e "s.*$*" \
    | $SED -e 's-D\([a-zA-Z_][-a-zA-Z_]*\)=\(.*\)'"s\\\\\$\\1\\\\\$\2gg" \
           -e 's-D\([a-zA-Z_][-a-zA-Z_]*\)'"s\\\\\$\\1\\\\\$1gg" \
           -e 's/ //g'
}

es=
be=
keys=
key1=_1_
key2=_2_
while [ $# -gt 0 ]; do
  case "$1" in
    -be)
      be='-e 1,/^#begin$/d -e /^#end$/,$d'
      ;;
    -D*)
      es="$es -e `subst $1`"
      ;;
    -K*)
      nk=`echo $1 | $SED -e 's/-K//'`
      if [ "x$keys" = x ]; then keys="$nk"; else keys="$keys $nk"; fi
      if [ "x$key1" = x_1_ ]; then key1="$nk";
      elif [ "x$key2" = x_2_ ]; then key2="$nk"; fi
      ;;
    *)
      echo "Usage: $0 [-be] [-Kkey] -Dvar=value ... <in >out"
      exit 1
      ;;
  esac
  shift
done

text='[-a-zA-Z0-9&;,.<>/#() ]'

$SED $es \
  -e 's// /g' \
  -e "s@^#warn .*@// -- This file was mechanically generated: Do not edit! -- //@" \
  -e 's-// ##.*$--' $be \
  -e "s/{#if\[$key1\]?\($text*\):\($text*\)}/\1/g" \
  -e "s/{#if\[!$key1\]?\($text*\):\($text*\)}/\2/g" \
  -e "s/{#if\[$key1\]?\($text*\)}/\1/g" \
  -e "s/{#if\[!$key1\]?\($text*\)}//g" \
  -e "s/{#if\[$key2\]?\($text*\):\($text*\)}/\1/g" \
  -e "s/{#if\[!$key2\]?\($text*\):\($text*\)}/\2/g" \
  -e "s/{#if\[$key2\]?\($text*\)}/\1/g" \
  -e "s/{#if\[!$key2\]?\($text*\)}//g" \
  -e "s/{#if\[[a-z]*\]?\($text*\):\($text*\)}/\2/g" \
  -e "s/{#if\[![a-z]*\]?\($text*\):\($text*\)}/\1/g" \
  -e "s/{#if\[[a-z]*\]?\($text*\)}//g" \
  -e "s/{#if\[![a-z]*\]?\($text*\)}/\1/g" \
| $NAWK \
  'function key(s) {
     i = match(s, "[a-zA-Z][a-zA-Z]*\\]");
     if (i > 0) return substr(s, i, RLENGTH - 1);
     return "XYZZY"; }
   function neg(s) { return match(s, "!") > 0; }
   BEGIN {
     KEYS = "'"$keys"'"
     n = split(KEYS, ks, "  *");
     for (i = 1; i <= n; i++) keys[ks[i]] = 1;
     top = 1; copy[top] = 1 }
   /^#if\[!?[a-zA-Z][a-zA-Z]*\]/ \
     { k = key($0);
       n = neg($0);
       stack[++top] = k;
       if ((k in keys) == !n) {
	 copy[top] = copy[top - 1];
       } else {
	 copy[top] = 0;
       }
       print ""; next }
   /^#else\[!?[a-zA-Z][a-zA-Z]*\]/ \
     { k = key($0);
       if (stack[top] == k) {
	 copy[top] = copy[top - 1] && !copy[top];
       } else {
	 printf "%d: Mismatched #else key\n", NR | "cat 1>&2";
	 exit 11
       }
       print ""; next }
   /^#end\[!?[a-zA-Z][a-zA-Z]*\]/ \
     { k = key($0);
       if (stack[top] == k) {
	 top--;
       } else {
	 printf "%d: Mismatched #end key\n", NR | "cat 1>&2"
	 exit 11
       }
       print ""; next }
   /^#/ {
     printf "%d: Malformed #directive\n", NR | "cat 1>&2"
     exit 11
   }
   { if (copy[top]) print; else print "" }'
