#!/bin/sh

# If we're cross compiling use that path for nm
if [ "$ALT_COMPILER_PATH" != "" ]; then 
NM=$ALT_COMPILER_PATH/nm
else
NM=nm
fi

$NM --defined-only $* | awk '
   { if ($3 ~ /^_ZTV/ || $3 ~ /^gHotSpotVM/) print "\t" $3 ";" }
   '
