#!/bin/bash
java -XX:+UnlockExperimentalVMOptions -XX:-TieredCompilation -XX:+DeoptimizeALot -XX:CompileCommand=dontinline,WrongBCIAfterMotion::blackhole -XX:-UseTLAB $* WrongBCIAfterMotion
