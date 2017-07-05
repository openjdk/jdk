#!/bin/bash

set -x
set -e

options="$*"
option="$1"

tmp=/tmp/test_builds.$$
rm -f -r ${tmp}
mkdir -p ${tmp}

errMessages=${tmp}/error_messages.txt

#######
# Error function
error() # message
{
   echo "ERROR: $1" | tee -a ${errMessages}
}
# Check errors
checkErrors()
{
    if [ -s ${errMessages} ] ; then
        cat ${errMessages}
	exit 1
    fi
}
#######

os="`uname -s`"
arch="`uname -p`"
make=make

if [ "${os}" = "SunOS" ] ; then
  make=gmake
  export J7="/opt/java/jdk1.7.0"
elif [ "${os}" = "Darwin" ] ; then
  export J7="/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home"
elif [ "${os}" = "Linux" -a "${arch}" = "x86_64" ] ; then
  export J7="/usr/lib/jvm/java-7-openjdk-amd64/"
else
  echo "What os/arch is this: ${os}/${arch}"
  exit 1
fi

# Must have a jdk7
if [ ! -d ${J7} ] ; then
  echo "No JDK7 found at: ${J7}"
  exit 1
fi

# What sources we use
fromroot="http://hg.openjdk.java.net/build-infra/jdk8"

# Where we do it
root="testbuilds"
mkdir -p ${root}

# Three areas, last three are cloned from first to insure sameness
t0=${root}/t0
t1=${root}/t1
t2=${root}/t2
t3=${root}/t3
repolist="${t0} ${t1} ${t2} ${t3}"

# Optional complete clobber
if [ "${option}" = "clobber" ] ; then
  for i in ${repolist} ; do
    rm -f -r ${i}
  done
fi

# Get top repos
if [ ! -d ${t0}/.hg ] ; then
  rm -f -r ${t0}
  hg clone ${fromroot} ${t0}
fi
for i in ${t1} ${t2} ${t3} ; do
  if [ ! -d ${i}/.hg ] ; then
    hg clone ${t0} ${i}
  fi
done

# Get repos updated
for i in ${repolist} ; do
  ( \
    set -e \
    && cd ${i} \
    && sh ./get_source.sh \
    || error "Cannot get source" \
  ) 2>&1 | tee ${i}.get_source.txt
  checkErrors
done

# Optional clean
if [ "${option}" = "clean" ] ; then
  for i in ${repolist} ; do
    rm -f -r ${i}/build
    rm -f -r ${i}/*/build
    rm -f -r ${i}/*/dist
  done
fi

# Check changes on working set files
for i in ${repolist} ; do
  ( \
    set -e \
    && cd ${i} \
    && sh ./make/scripts/hgforest.sh status \
    || error "Cannot check status" \
  ) 2>&1 | tee ${i}.hg.status.txt
  checkErrors
done

# Configure for build-infra building
for i in ${t1} ${t2} ; do
  ( \
    set -e \
    && cd ${i}/common/makefiles \
    && sh ../autoconf/configure --with-boot-jdk=${J7} \
    || error "Cannot configure" \
  ) 2>&1 | tee ${i}.config.txt
  checkErrors
done

# Do build-infra builds
for i in ${t1} ${t2} ; do
  ( \
    set -e \
    && cd ${i}/common/makefiles \
    && ${make}  \
      FULL_VERSION:=1.8.0-internal-b00 \
      JRE_RELEASE_VERSION:=1.8.0-internal-b00 \
      USER_RELEASE_SUFFIX:=compare \
      RELEASE:=1.8.0-internal \
      VERBOSE= \
      LIBARCH= \
         all images \
    || error "Cannot build" \
  ) 2>&1 | tee ${i}.build.txt
  checkErrors
done

# Compare build-infra builds
( \
  sh ${t0}/common/bin/compareimage.sh \
    ${t1}/build/*/images/j2sdk-image \
    ${t2}/build/*/images/j2sdk-image \
    || error "Cannot compare" \
) 2>&1 | tee ${root}/build-infra-comparison.txt
checkErrors

# Do old build
unset JAVA_HOME
export ALT_BOOTDIR="${J7}"
( \
  cd ${t3} \
  && ${make} FULL_VERSION='"1.8.0-internal" sanity \
  || error "Cannot sanity" \
) 2>&1 | tee ${t3}.sanity.txt
checkErrors
( \
  cd ${t3} \
  && ${make} \
      FULL_VERSION='"1.8.0-internal" \
      JRE_RELEASE_VERSION:=1.8.0-internal-b00 \
      USER_RELEASE_SUFFIX:=compare \
      RELEASE:=1.8.0-internal \
  || error "Cannot build old way" \
) 2>&1 | tee ${t3}.build.txt
checkErrors

# Compare old build to build-infra build 
( \
  sh ${t0}/common/bin/compareimage.sh \
    ${t3}/build/*/j2sdk-image \
    ${t1}/build/*/images/j2sdk-image \
    || error "Cannot compare" \
) 2>&1 | tee ${root}/build-comparison.txt
checkErrors

exit 0

