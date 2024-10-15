import java.awt.EventQueue; 
import java.awt.event.ActionEvent; 
import java.awt.event.ActionListener; 

import javax.swing.*; 

public class Bug001 extends JFrame 
{ 
  final JMenuBar menuBar = new JMenuBar (); 
  final JMenu fileMenu = new JMenu ("File"); 

  final JMenuItem debuggingItem = new JCheckBoxMenuItem ("Debugging"); 
  // final JMenuItem debuggingItem = new JMenuItem ("Debugging"); 

  public Bug001 () 
  { 
    super ("Bug001"); 

    menuBar.add (fileMenu); 
    fileMenu.add (debuggingItem); 
    debuggingItem.setAccelerator (KeyStroke.getKeyStroke ("meta D")); 
    setJMenuBar (menuBar); 

    debuggingItem.addActionListener (new ActionListener () 
    { 
      @Override 
      public void actionPerformed (ActionEvent e) 
      { 
        System.out.printf ("modifiers: %d, isSelected: %s%n", e.getModifiers (), 
            ((JMenuItem) e.getSource ()).isSelected ()); 
      } 
    }); 
  } 

  public static void main (String[] args) 
  { 
    EventQueue.invokeLater (new Runnable () 
    { 
      @Override 
      public void run () 
      { 
        try 
        { 
          UIManager.setLookAndFeel (UIManager.getSystemLookAndFeelClassName ()); 
          System.setProperty ("apple.laf.useScreenMenuBar", "true"); 
          new Bug001 ().setVisible (true); 
        } 
        catch (Exception e) 
        { 
          e.printStackTrace (); 
        } 
      } 
    }); 
  } 
} 
