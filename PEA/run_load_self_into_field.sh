#!/usr/bin/bash
java -ea -Xcomp -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileCommand='compileOnly,LoadSelfIntoField.test*' $* LoadSelfIntoField
