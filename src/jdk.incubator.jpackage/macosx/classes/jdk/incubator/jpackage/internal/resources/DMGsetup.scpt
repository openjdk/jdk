tell application "Finder"
  tell disk "DEPLOY_ACTUAL_VOLUME_NAME"
    open
    set current view of container window to icon view
    set toolbar visible of container window to false
    set statusbar visible of container window to false

    -- size of window should match size of background
    set the bounds of container window to {400, 100, 917, 380}

    set theViewOptions to the icon view options of container window
    set arrangement of theViewOptions to not arranged
    set icon size of theViewOptions to 128
    set background picture of theViewOptions to file ".background:background.tiff"

    -- Create alias for install location
    make new alias file at container window to DEPLOY_INSTALL_LOCATION with properties {name:"DEPLOY_INSTALL_NAME"}

    set allTheFiles to the name of every item of container window
    repeat with theFile in allTheFiles
      set theFilePath to POSIX Path of theFile
      if theFilePath is "/DEPLOY_APPLICATION_NAME.app"
        -- Position application location
        set position of item theFile of container window to {120, 130}
      else if theFilePath is "/DEPLOY_INSTALL_NAME"
        -- Position install location
        set position of item theFile of container window to {390, 130}
      else
        -- Move all other files far enough to be not visible if user has "show hidden files" option set
        set position of item theFile of container window to {1000, 130}
      end
    end repeat

    update without registering applications
    delay 5
    close
  end tell
end tell
