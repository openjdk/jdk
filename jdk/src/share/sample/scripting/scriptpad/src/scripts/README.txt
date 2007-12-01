Sample scripts:

(1) browse.js 

 -- Open and run this script in scriptpad. You will see 
    Tools->Browse menu. Using this you can start your
    desktop default browser with the given URL.

(2) insertfile.js

 -- Open and run this script in scriptpad. You will see 
    "Tools->Insert File..." menu. Using this you can start 
    insert content of a selected file into currently
    edited document

(3) linewrap.js

 -- Open and run this script in scriptpad. You will see 
    "Tools->Line Wrap" menu. Using this you can toggle
    the line wrapping mode of the editor

(4) mail.js

 -- Open and run this script in scriptpad. You will see 
    Tools->Mail menu. Using this you can start your
    desktop default mail client with the given "To" mail id.

(5) memmonitor.js

 -- This is a simple Monitoring & Management script. To use this,
    you need an application to monitor. You can use memory.bat
    or memory.sh in the current directory to start an application
    that will be monitored. After that please follow these steps:

   1. Start the target application using memory.sh or memory.bat
   2. Start scriptpad 
   3. Use "Tools->JMX Connect" menu and specify "localhost:1090"
      to connect
   4. Open "memmonitor.js" and run it (using "Tools->Run")       
      in scriptpad
   5. A new "Tools-Memory Monitor" menu appears. Use this menu
      and specify 4 and 500 as threshold and interval values.
   6. In the target application shell (where memory.bat/.sh was
      started), enter an integer value and press "enter".
   7. You'll see an alert box from scriptpad -- alerting you for
      memory threshold exceeded!

(6) textcolor.js

 -- Open and run this script in scriptpad. You will see 
    "Tools->Selected Text Color..." menu. Using this you
    change the color of "selected text" in the editor.
