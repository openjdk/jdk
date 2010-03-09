#!/bin/sh

nm --defined-only $* | awk '
   { if ($3 ~ /^_ZTV/ || $3 ~ /^gHotSpotVM/) print "\t" $3 ";" }
   '
