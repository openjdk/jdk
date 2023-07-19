.DEFAULT_GOAL := all

JAVA_PATH := $(abspath ../build/linux-x86_64-server-fastdebug/jdk)
export JAVA_HOME := ${JAVA_PATH}
export PATH := ${JAVA_HOME}/bin:$(PATH)

CTW_DIR := $(abspath ../test/hotspot/jtreg/testlibrary/ctw)
CTW_DIST= ${CTW_DIR}/dist
CTW_JAVA_OPTIONS := -XX:+CITime -XX:-TieredCompilation -XX:+UseTLAB -XX:+PrintCompilation -XX:+PrintOptoStatistics -XX:+UnlockExperimentalVMOptions -XX:+DoPartialEscapeAnalysis
comma := ,
empty :=
space := $(empty) $(empty)
# CTWRunner accepts arguments with commas instead of spaces
CTW_RUNNER_JAVA_OPTIONS := $(subst $(space),$(comma),$(CTW_JAVA_OPTIONS))

set_jdk:
	if [ -e ${JAVA_PATH} ]; then \
		echo "choose ${JAVA_HOME}"; \
		java -version; \
	else \
		echo "can't find valid jdk!"; \
		exit 1; \
	fi

all: set_jdk
	javac --add-exports java.base/jdk.internal.misc=ALL-UNNAMED *.java

run: set_jdk
	./auto.rb

$(CTW_DIST):
	${MAKE} -C ${CTW_DIR} all

run-ctw: set_jdk ${CTW_DIST}
	cd ${CTW_DIST}; \
	JAVA_OPTIONS="-Dsun.hotspot.tools.ctwrunner.ctw_extra_args=${CTW_RUNNER_JAVA_OPTIONS}" ./ctwrunner.sh modules:java.base

run-ctw-no-inline: set_jdk ${CTW_DIST}
	cd ${CTW_DIST}; \
	JAVA_OPTIONS="${CTW_JAVA_OPTIONS} -XX:-Inline" ./ctw.sh modules:java.base

bootstrap-version: set_jdk
	java -Xcomp -XX:-TieredCompilation -XX:CICompilerCount=1 -Xbatch -XX:+UnlockExperimentalVMOptions -XX:+DoPartialEscapeAnalysis -XX:+PrintCompilation -version

hotspot-tier1:
	cd ..; make test TEST="hotspot:tier1"  CONF=linux-x86_64-server-fastdebug  JTREG="JAVA_OPTIONS=-XX:+UnlockExperimentalVMOptions -XX:+DoPartialEscapeAnalysis"
