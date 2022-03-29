#!/bin/bash
#
# Script to execute sanning command line tool.
#  Usage: sanning.sh <sanning path> [<identity> [<answer option>]]

# Set to Java 11 home.
JAVA_HOME=/opt/my/java/jdk-11

# Java options.
JAVA_OPTS=

# Java main class.
MAIN_CLASS=sanning.Sanning

# APP_HOME.
BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
APP_HOME="$(dirname "$BIN_DIR")"
LIB_PATH="$APP_HOME/lib"

# Java command.
JAVACMD="$JAVA_HOME/bin/java"
if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  exit 1
fi

# Set CLASSPATH.
CLASSPATH="${LIB_PATH}/*"

# Execute command
"$JAVACMD" $JAVA_OPTS -classpath "$CLASSPATH" $MAIN_CLASS $@
RESULT=$?
exit $RESULT
