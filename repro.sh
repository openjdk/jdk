#!/bin/bash

OPTS="-XX:+UnlockDiagnosticVMOptions -XX:-UseCompressedOops"

# EXECUTE ONLY THE TEST
#  make test CONF=release TEST=test/langtools/tools/javac/annotations/typeAnnotations/ZAPI/ZZZArrayCreationTree.java JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"

# EXECUTE ONLY A SUBSET OF TESTS
# make test CONF=release TEST=test/langtools/tools/javac/annotations/ JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"

# EXECUTE ONLY A SUBSET OF TESTS
# make test CONF=release TEST=test/langtools/:repro JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS};REPEAT_COUNT=50"

# EXECUTE WHOLE GROUP
  make test CONF=release TEST=test/langtools/:tier1 JTREG="JAVA_OPTIONS=${OPTS};REPEAT_COUNT=50"
