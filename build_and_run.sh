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
gcc_exit=$?
if [ $gcc_exit -ne 0 ]
then 
    echo "There was a compilation problem. Not starting server or applet. GCC EXIT CODE: $gcc_exit"
    exit 1
fi

clear
# Restart server
echo "Restarting Server"
java ControlServer &


# Start appletviewer
echo "Launching client applet"
appletviewer Client.java &> ./client.log
