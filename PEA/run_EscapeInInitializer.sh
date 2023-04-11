#!/bin/bash
java -XX:+UnlockExperimentalVMOptions  -XX:-TieredCompilation -XX:CompileCommand='compileonly,EscapeInInitializer::*' -XX:CompileCommand='inline,EscapeInInitializer::the_answer' -XX:-UseTLAB -XX:-UseOnStackReplacement -Xbatch $* EscapeInInitializer
