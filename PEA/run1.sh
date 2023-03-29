#!/usr/bin/bash
java -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileCommand='compileOnly,Example1.test*' $* Example1
