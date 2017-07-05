#!/bin/sh

# do two runs of a script, one optimistic and one pessimistic, expect identical outputs
# if not, display and error message and a diff

which opendiff >/dev/null
RES=$?
if [ $RES = 0 ]; then
    DIFFTOOL=opendiff
else
    DIFFTOOL=diff
fi

OPTIMISTIC=out_optimistic
PESSIMISTIC=out_pessimistic
$JAVA_HOME/bin/java -ea -jar ../dist/nashorn.jar ${@} >$PESSIMISTIC
$JAVA_HOME/bin/java -ea -Dnashorn.optimistic -jar ../dist/nashorn.jar ${@} >$OPTIMISTIC

if ! diff -q $PESSIMISTIC $OPTIMISTIC >/dev/null ; then
    echo "Failure! Results are different"
    echo ""
    $DIFFTOOL $PESSIMISTIC $OPTIMISTIC
else
    echo "OK - Results are identical"
fi
