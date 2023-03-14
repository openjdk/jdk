#!/usr/bin/bash
java -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-UseTLAB -XX:CompileCommand='compileonly,MergeAllVirts::*' $* MergeAllVirts
