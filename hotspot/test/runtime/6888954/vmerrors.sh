# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 6888954
# @bug 8015884
# @summary exercise HotSpot error handling code
# @author John Coomes
# @run shell vmerrors.sh

# Repeatedly invoke java with a command-line option that causes HotSpot to
# produce an error report and terminate just after initialization.  Each
# invocation is identified by a small integer, <n>, which provokes a different
# error (assertion failure, guarantee failure, fatal error, etc.).  The output
# from stdout/stderr is written to <n>.out and the hs_err_pidXXX.log file is
# renamed to <n>.log.
#
# The automated checking done by this script is minimal.  When updating the
# fatal error handler it is more useful to run it manually or to use the -retain
# option with the jtreg so that test directories are not removed automatically.
# To run stand-alone:
#
# TESTJAVA=/java/home/dir
# TESTVMOPTS=...
# export TESTJAVA TESTVMOPTS
# sh test/runtime/6888954/vmerrors.sh

ulimit -c 0 # no core files

i=1
rc=0

assert_re='(assert|guarantee)[(](str|num).*failed: *'
# for bad_data_ptr_re:
# EXCEPTION_ACCESS_VIOLATION - Win-*
# SIGILL - MacOS X
# SIGSEGV - Linux-*, Solaris SPARC-*, Solaris X86-*
#
bad_data_ptr_re='(SIGILL|SIGSEGV|EXCEPTION_ACCESS_VIOLATION).* at pc='
#
# for bad_func_ptr_re:
# EXCEPTION_ACCESS_VIOLATION - Win-*
# SIGBUS - Solaris SPARC-64
# SIGSEGV - Linux-*, Solaris SPARC-32, Solaris X86-*
#
# Note: would like to use "pc=0x00*0f," in the pattern, but Solaris SPARC-*
# gets its signal at a PC in test_error_handler().
#
bad_func_ptr_re='(SIGBUS|SIGSEGV|EXCEPTION_ACCESS_VIOLATION).* at pc='
guarantee_re='guarantee[(](str|num).*failed: *'
fatal_re='fatal error: *'
tail_1='.*expected null'
tail_2='.*num='

for re in                                                 \
    "${assert_re}${tail_1}"    "${assert_re}${tail_2}"    \
    "${guarantee_re}${tail_1}" "${guarantee_re}${tail_2}" \
    "${fatal_re}${tail_1}"     "${fatal_re}${tail_2}"     \
    "${fatal_re}.*truncated"   "ChunkPool::allocate"      \
    "ShouldNotCall"            "ShouldNotReachHere"       \
    "Unimplemented"            "$bad_data_ptr_re"         \
    "$bad_func_ptr_re"

do
    i2=$i
    [ $i -lt 10 ] && i2=0$i

    "$TESTJAVA/bin/java" $TESTVMOPTS -XX:+IgnoreUnrecognizedVMOptions \
        -XX:-TransmitErrorReport \
        -XX:ErrorHandlerTest=${i} -version > ${i2}.out 2>&1

    # If ErrorHandlerTest is ignored (product build), stop.
    #
    # Using the built-in variable $! to get the pid does not work reliably on
    # windows; use a wildcard instead.
    mv hs_err_pid*.log ${i2}.log || exit $rc

    for f in ${i2}.log ${i2}.out
    do
        egrep -- "$re" $f > $$
        if [ $? -ne 0 ]
        then
            echo "ErrorHandlerTest=$i failed ($f)"
            rc=1
        fi
    done
    rm -f $$

    i=`expr $i + 1`
done

exit $rc
