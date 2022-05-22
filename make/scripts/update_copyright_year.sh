#!/bin/bash -f

#
# Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

# Script to update the Copyright YEAR range in Mercurial & Git sources.
#  (Originally from xdono, Thanks!)

#------------------------------------------------------------
copyright="Copyright (c)"
company="Oracle"
#------------------------------------------------------------

awk="awk"

# Stop on any error
set -e

# To allow total changes counting
shopt -s lastpipe

# Get an absolute path to this script, since that determines the top-level directory.
this_script_dir=`dirname $0`
this_script_dir=`cd $this_script_dir > /dev/null && pwd`

# Temp area
tmp=/tmp/`basename $0`.${USER}.$$
rm -f -r ${tmp}
mkdir -p ${tmp}
total=0

# Default or supplied company name
if [ "$3" != "" ] ; then
  company="$3"
fi

# This year or supplied year
if [ "$2" != "" ] ; then
  year="$2"
else
  year=`date +%Y`
fi

# VCS select
vcs="$1"

if [ -z "$vcs" ] ; then
  git_found=false
  hg_found=false

  [ -d "${this_script_dir}/../../.git" ] && git_found=true
  [ -d "${this_script_dir}/../../.hg" ] && hg_found=true

  if [ "$git_found" == "true" ] && [ "$hg_found" == "false" ] ; then
    vcs="git"
  elif [ "$hg_found" == "true" ] && [ "$git_found" == "false" ] ; then
    vcs="hg"
  else
    echo "Error: could not auto-detect version control system"
    vcs=""
  fi
fi

case "$vcs" in
  "git")
    echo "Using Git version control system"
    vcs_status=(git ls-files -m)
    vcs_list_changesets=(git log --no-merges --since="${year}-01-01T00:00:00Z" --until="${year}-12-31T23:59:59Z" --pretty=tformat:"%H")
    vcs_changeset_message=(git log -1 --pretty=tformat:"%B") # followed by ${changeset}
    vcs_changeset_files=(git diff-tree --no-commit-id --name-only -r) # followed by ${changeset}
    ;;

  "hg")
    echo "Using Mercurial version control system"
    vcs_status=(hg status)
    vcs_list_changesets=(hg log --no-merges -v -d "${year}-01-01 to ${year}-12-31" --template '{node}\n')
    vcs_changeset_message=(hg log -l1 --template '{desc}\n' --rev) # followed by ${changeset}
    vcs_changeset_files=(hg log -l1 -v --template '{files}\n' --rev) # followed by ${changeset}
    ;;

  *)
    echo "Usage: `basename "$0"` <git|hg> [year [company]]"
    exit 1
    ;;
esac

# Return true if it makes sense to edit this file
saneFileToCheck()
{
  if [ "$1" != "" -a -f $1 ] ; then
    isText=`file "$1" | egrep -i '(text|source)' | cat`
    hasCopyright=`grep 'Copyright' "$1" | cat`
    lastLineCount=`tail -1 "$1" | wc -l`
    if [ "${isText}" != ""  \
         -a "${hasCopyright}" != "" \
	 -a ${lastLineCount} -eq 1 ] ; then
      echo "true"
    else
      echo "false"
    fi
  else
    echo "false"
  fi
}

# Update the copyright year on a file
updateFile() # file
{
  changed="false"
  if [ `saneFileToCheck "$1"` = "true" ] ; then
    rm -f $1.OLD
    mv $1 $1.OLD
    cat $1.OLD | \
      sed -e "s@\(${copyright} [12][0-9][0-9][0-9],\) [12][0-9][0-9][0-9], ${company}@\1 ${year}, ${company}@" | \
      sed -e "s@\(${copyright} [12][0-9][0-9][0-9],\) ${company}@\1 ${year}, ${company}@" | \
      sed -e "s@${copyright} ${year}, ${year}, ${company}@${copyright} ${year}, ${company}@"  \
      > $1
    if ! diff -b -w $1.OLD $1 > /dev/null ; then \
      changed="true"
      rm -f $1.OLD
    else
      rm -f $1
      mv $1.OLD $1
    fi
  fi
  echo "${changed}"
}

# Update the copyright year on all files changed by this changeset
updateChangesetFiles() # changeset
{
  count=0
  files=${tmp}/files.$1
  rm -f ${files}
  "${vcs_changeset_files[@]}" "$1" | expand \
    | ${awk} -F' ' '{for(i=1;i<=NF;i++)print $i}' \
    > ${files}
  if [ -f "${files}" -a -s "${files}" ] ; then
    fcount=`cat ${files}| wc -l`
    for i in `cat ${files}` ; do
      if [ `updateFile "${i}"` = "true" ] ; then
        count=`expr ${count} '+' 1`
      fi
    done
    if [ ${count} -gt 0 ] ; then
      printf "  UPDATED year on %d of %d files.\n" ${count} ${fcount}
      total=`expr ${total} '+' ${count}`
    else
      printf "  None of the %d files were changed.\n" ${fcount}
    fi
  else
    printf "  ERROR: No files changed in the changeset? Must be a mistake.\n"
    set -x
    ls -al ${files}
    "${vcs_changeset_files[@]}" "$1"
    "${vcs_changeset_files[@]}" "$1" | expand \
      | ${awk} -F' ' '{for(i=1;i<=NF;i++)print $i}'
    set +x
    exit 1
  fi
  rm -f ${files}
}

# Check if repository is clean
previous=`"${vcs_status[@]}"|wc -l`
if [ ${previous} -ne 0 ] ; then
  echo "WARNING: This repository contains previously edited working set files."
  echo "  ${vcs_status[*]} | wc -l = `"${vcs_status[@]}" | wc -l`"
fi

# Get all changesets this year
all_changesets=${tmp}/all_changesets
rm -f ${all_changesets}
"${vcs_list_changesets[@]}" > ${all_changesets}

# Check changeset to see if it is Copyright only changes, filter changesets
if [ -s ${all_changesets} ] ; then
  echo "Changesets made in ${year}: `cat ${all_changesets} | wc -l`"
  index=0
  cat ${all_changesets} | while read changeset ; do
    index=`expr ${index} '+' 1`
    desc=${tmp}/desc.${changeset}
    rm -f ${desc}
    echo "------------------------------------------------"
    "${vcs_changeset_message[@]}" "${changeset}" > ${desc}
    printf "%d: %s\n%s\n" ${index} "${changeset}" "`cat ${desc}|head -1`"
    if [ "${year}" = "2010" ] ; then
      if cat ${desc} | fgrep -i "Added tag" > /dev/null ; then
        printf "  EXCLUDED tag changeset.\n"
      elif cat ${desc} | fgrep -i rebrand > /dev/null ; then
        printf "  EXCLUDED rebrand changeset.\n"
      elif cat ${desc} | fgrep -i copyright > /dev/null ; then
        printf "  EXCLUDED copyright changeset.\n"
      else
        updateChangesetFiles ${changeset}
      fi
    else
      if cat ${desc} | fgrep -i "Added tag" > /dev/null ; then
        printf "  EXCLUDED tag changeset.\n"
      elif cat ${desc} | fgrep -i "copyright year" > /dev/null ; then
        printf "  EXCLUDED copyright year changeset.\n"
      else
        updateChangesetFiles ${changeset}
      fi
    fi
    rm -f ${desc}
  done
fi

if [ ${total} -gt 0 ] ; then
   echo "---------------------------------------------"
   echo "Updated the copyright year on a total of ${total} files."
   if [ ${previous} -eq 0 ] ; then
     echo "This count should match the count of modified files in the repository: ${vcs_status[*]}"
   else
     echo "WARNING: This repository contained previously edited working set files."
   fi
   echo "  ${vcs_status[*]} | wc -l = `"${vcs_status[@]}" | wc -l`"
else
   echo "---------------------------------------------"
   echo "No files were changed"
   if [ ${previous} -ne 0 ] ; then
     echo "WARNING: This repository contained previously edited working set files."
   fi
   echo "  ${vcs_status[*]} | wc -l = `"${vcs_status[@]}" | wc -l`"
fi

# Cleanup
rm -f -r ${tmp}
exit 0
