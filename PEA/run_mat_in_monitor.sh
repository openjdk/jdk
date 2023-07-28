#!/usr/bin/bash
java -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:-UseOnStackReplacement -XX:+StressRecompilation -XX:+UseTLAB -XX:CompileCommand='compileOnly,MatInMonitor.test*' $* MatInMonitor
