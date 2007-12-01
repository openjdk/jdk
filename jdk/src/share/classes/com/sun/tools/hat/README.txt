--------------
This HAT source originally came from the http://hat.dev.java.net site.

The utility has been named 'jhat' in the JDK, it is basically the same tool.

Q: Where do I make changes? In the JDK or hat.dev.java.net?

A: It depends on whether the change is intended for the JDK jhat version only,
   or expected to be given back to the java.net project.
   In general, we should putback changes to the java.net project and
   bringover those changes to the JDK.

Q: I want to build just jhat.jar instead of building entire JDK. What should I do?

A: Use ant makefile (build.xml) in the current directory. This builds just the
jhat sources and creates jhat.jar under ./build directory.

To run the built jhat.jar, you can use the command:

    java -jar build/jhat.jar heap_dump
