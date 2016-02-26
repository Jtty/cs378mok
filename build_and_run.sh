#!/bin/bash
#dir = $(pwd)
pid_java=`ps | grep java | head -c 5`

# Kill previous server
if [ -z $pid_java ]
	then
		echo "Old server not found. Skipping the kill"
else
		echo "Found exisitng server instance. Killing it."
		kill $pid_java
fi

# Rebuild codebase
echo "Rebuilding all code: javac *.java. There is normally one warning"
javac *.java -Xlint:deprecation



# Restart server
echo "Restarting Server"
java ControlServer &

# Start appletviewer
echo "Launching client applet"
appletviewer Client.java
