#!/usr/bin/bash 
java -Xcomp -Xms16M -Xmx16M -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:CompileOnly='Str.strBuilderRepro' -XX:-Inline $* Str
