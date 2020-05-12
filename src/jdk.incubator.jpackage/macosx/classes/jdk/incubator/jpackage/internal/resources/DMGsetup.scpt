tell application "Finder"
  set theDisk to a reference to (disks whose URL = "DEPLOY_VOLUME_URL")
  open theDisk

  set theWindow to a reference to (container window of disks whose URL = "DEPLOY_VOLUME_URL")

  set current view of theWindow to icon view
  set toolbar visible of theWindow to false
  set statusbar visible of theWindow to false

  -- size of window should match size of background
  set the bounds of theWindow to {400, 100, 917, 380}

  set theViewOptions to a reference to the icon view options of theWindow
  set arrangement of theViewOptions to not arranged
  set icon size of theViewOptions to 128
  set background picture of theViewOptions to POSIX file "DEPLOY_BG_FILE"

  -- Create alias for install location
  make new alias file at POSIX file "DEPLOY_VOLUME_PATH" to POSIX file "DEPLOY_INSTALL_LOCATION" with properties {name:"DEPLOY_INSTALL_NAME"}

  set allTheFiles to the name of every item of theWindow
  repeat with theFile in allTheFiles
    set theFilePath to POSIX path of theFile
    if theFilePath is "/DEPLOY_APPLICATION_NAME.app" then
      -- Position application location
      set position of item theFile of theWindow to {120, 130}
    else if theFilePath is "DEPLOY_INSTALL_NAME" then
      -- Position install location
      set position of item theFile of theWindow to {390, 130}
    else
      -- Move all other files far enough to be not visible if user has "show hidden files" option set
      set position of item theFile of theWindow to {1000, 130}
    end if
  end repeat

  update theDisk without registering applications
  delay 5
  close (get window of theDisk)
end tell
