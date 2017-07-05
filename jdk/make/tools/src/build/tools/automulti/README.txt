AutoMulti is the tool that automatically generates the
Multi*UI classes for the Multiplexing look and feel. 
Instructions for using it are in AutoMulti.java.

TestALFGenerator is a tool (a variation of AutoMulti)
that automatically generates an auxiliary look and
feel that you can use to test the Multiplexing look
and feel.  The TestALF look and feel implements every
method by printing the message "In the xxx method of
the TextALFYyyUI class." and, except in the case of
createUI, returning something meaningless (since,
except in the case of createUI, the return value is
ignored).  

TestALFLookAndFeel.java is the only non-auto-generated
file for the TestALF L&F.  If you specify a package
argument to TestALFGenerator, you'll have to change
the code in TestALFLookAndFeel.java to reflect the
package name.

To test any application with the TestALF, make sure the
compiled TestALF classes are in the class path.  Then add
this to the <JDK_HOME>/lib/swing.properties file (which
you'll probably have to create):

swing.auxiliarylaf=TestALFLookAndFeel

E.g., if you're running SwingSet2 against your solaris
build, then you'd create/edit the swing.properties file
in <wsdir>/build/solaris-sparc/lib.

Then run any app.  You'll see lots of thrilling "In the
Xxxx method of the Yyy class" messages.  If you get anything
else (especially an exception), then you've found a bug.
Probably in the default look and feel.

