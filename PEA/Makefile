.DEFAULT_GOAL := all

JAVA_HOME ?= $(abspath ../build/linux-x86_64-server-fastdebug/jdk)
export PATH := ${JAVA_HOME}/bin:$(PATH)

CTW_DIR := $(abspath ../test/hotspot/jtreg/testlibrary/ctw)
CTW_DIST= ${CTW_DIR}/dist
CTW_JAVA_OPTIONS := -XX:+CITime -XX:-TieredCompilation -XX:+UseTLAB -XX:+PrintCompilation -XX:+PrintOptoStatistics -XX:+UnlockExperimentalVMOptions -XX:+DoPartialEscapeAnalysis
comma := ,
empty :=
space := $(empty) $(empty)
# CTWRunner accepts arguments with commas instead of spaces
CTW_RUNNER_JAVA_OPTIONS := $(subst $(space),$(comma),$(CTW_JAVA_OPTIONS))
CONF ?= linux-x86_64-server-fastdebug
JTREG_COMMON = JAVA_OPTIONS=-XX:+UnlockExperimentalVMOptions -XX:+DoPartialEscapeAnalysis
JTREG_PARANOID=VM_OPTIONS=-ea -esa -XX:CompileThreshold=100

set_jdk:
	if [ -e ${JAVA_HOME} ]; then \
		echo "choose ${JAVA_HOME}"; \
		java -version; \
	else \
		echo "can't find valid jdk!"; \
		exit 1; \
	fi

classes := $(patsubst %.java, %.class, $(wildcard *.java))

%.class: %.java
	javac --add-exports java.base/jdk.internal.misc=ALL-UNNAMED $<

smoketest: set_jdk $(classes)
	./auto.rb

$(CTW_DIST):
	${MAKE} -C ${CTW_DIR} all

run-ctw: set_jdk ${CTW_DIST}
	cd ${CTW_DIST}; \
	JAVA_OPTIONS="-Dsun.hotspot.tools.ctwrunner.ctw_extra_args=${CTW_RUNNER_JAVA_OPTIONS}" JAVA_HOME=${JAVA_HOME} ./ctwrunner.sh modules:java.base

run-ctw-no-inline: set_jdk ${CTW_DIST}
	cd ${CTW_DIST}; \
	JAVA_OPTIONS="${CTW_JAVA_OPTIONS} -XX:-Inline" JAVA_HOME=${JAVA_HOME} ./ctw.sh modules:java.base

bootstrap-version: set_jdk
	java -Xcomp -XX:-TieredCompilation -XX:CICompilerCount=1 -Xbatch -XX:+UnlockExperimentalVMOptions -XX:+DoPartialEscapeAnalysis -version

hotspot-tier1:
	cd ..; make test TEST="hotspot:tier1" CONF=${CONF} JREG="${JTREG_COMMON}"

hotspot-tier1-paranoid:
	cd ..; make test TEST="hotspot:tier1" CONF=${CONF} JTREG="${JTREG_COMMON};${JTREG_PARANOID}"

hotspot-tier2:
	cd ..; make test TEST="hotspot:tier2" CONF=${CONF} JREG="${JTREG_COMMON}"

all: smoketest bootstrap-version

clean:
	rm *.class *.log
