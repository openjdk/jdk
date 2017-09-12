This directory contains tests which exercises all possible TypeAnnotations
structure. These tests are borrowed from 
langtools/test/tools.javac/annotations/typeAnnotations. 

The reason it is copied over is that we need to test pack200 and these 
annotation may not be present in any of the JDK/JRE jars, yet we need to test
all these cases.


Therefore it would be a good practice to sync these tests with the  original
if there are any changes.
