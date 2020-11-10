#!/bin/bash

set -x

set -e
if [ -z "$BASH" ]; then
  # The script relies on Bash arrays, rerun in Bash.
  /bin/bash $0 $@
  exit
fi

sources=()
classes=()
for s in $(find "${TESTSRC}" -name  "*.java" | grep -v junit.java); do
  sources+=( "$s" )
  classes+=( $(echo "$s" | sed -e "s|${TESTSRC}/||" -e 's|/|.|g' -e 's/.java$//') )
done

common_args=(\
  --add-modules jdk.jpackage \
  --patch-module jdk.jpackage="${TESTSRC}${PS}${TESTCLASSES}" \
  --add-reads jdk.jpackage=ALL-UNNAMED \
  --add-exports jdk.jpackage/jdk.jpackage.internal=ALL-UNNAMED \
  -classpath "${TESTCLASSPATH}" \
)

# Compile classes for junit
"${COMPILEJAVA}/bin/javac" ${TESTTOOLVMOPTS} ${TESTJAVACOPTS} \
  "${common_args[@]}" -d "${TESTCLASSES}" "${sources[@]}"

# Run junit
"${TESTJAVA}/bin/java" ${TESTVMOPTS} ${TESTJAVAOPTS} \
  "${common_args[@]}" org.junit.runner.JUnitCore "${classes[@]}"
