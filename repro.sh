#!/bin/bash

BASE="/Users/cesar/wf/"
OPTS="-XX:-UseCompressedOops -XX:+TraceReduceAllocationMerges"

for i in `seq 1 100` ; do
	echo "Execution $i"

#	# EXECUTE ONLY THE TEST
#	make test CONF=release TEST=test/langtools/tools/javac/annotations/typeAnnotations/api/ArrayCreationTree.java JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"
#	cp ${BASE}/jdk/build/macosx-aarch64-server-release/test-support/jtreg_test_langtools_tools_javac_annotations_typeAnnotations_api_ArrayCreationTree_java/tools/javac/annotations/typeAnnotations/api/ArrayCreationTree.jtr "${BASE}/logs/ArrayCreationTree.jfr-$(date +'%Y%m%d-%H%M%S').txt"

	# EXECUTE WHOLE GROUP
	make test CONF=release TEST=test/langtools/:tier1 JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"
	cp ${BASE}/jdk/build/macosx-aarch64-server-release/test-support/jtreg_test_langtools_tier1/tools/javac/annotations/typeAnnotations/api/ArrayCreationTree.jtr "${BASE}/logs/ArrayCreationTree.jfr-$(date +'%Y%m%d-%H%M%S').txt"

done
