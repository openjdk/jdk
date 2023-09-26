#!/usr/bin/bash
set -x
java -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:-UseOnStackReplacement -XX:-TieredCompilation -XX:-UseTLAB -XX:CompileCommand='compileOnly,MatInMonitor.test*' $* MatInMonitor
java -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:-UseOnStackReplacement -XX:-TieredCompilation -XX:-UseTLAB -XX:CompileCommand='compileOnly,MatInMonitor.test*' -XX:-DoEscapeAnalysis $* MatInMonitor
