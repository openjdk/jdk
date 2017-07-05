# @test
# @bug 6888954
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
guarantee_re='guarantee[(](str|num).*failed: *'
fatal_re='fatal error: *'
signal_re='(SIGSEGV|EXCEPTION_ACCESS_VIOLATION).* at pc='
tail_1='.*expected null'
tail_2='.*num='

for re in                                                 \
    "${assert_re}${tail_1}"    "${assert_re}${tail_2}"    \
    "${guarantee_re}${tail_1}" "${guarantee_re}${tail_2}" \
    "${fatal_re}${tail_1}"     "${fatal_re}${tail_2}"     \
    "${fatal_re}.*truncated"   "ChunkPool::allocate"      \
    "ShouldNotCall"            "ShouldNotReachHere"       \
    "Unimplemented"            "$signal_re"
    
do
    i2=$i
    [ $i -lt 10 ] && i2=0$i

    "$TESTJAVA/bin/java" $TESTVMOPTS -XX:+IgnoreUnrecognizedVMOptions \
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

    i=$(expr $i + 1)
done

exit $rc
