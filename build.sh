#!/bin/sh
set -e

# Increase build number
echo -n $(($(cat buildnumber | cut -d ',' -f 1)+1)), $(date +'%d.%m.%Y') > buildnumber

# Build Launcher.jar
echo 'Packing Launcher.jar binary'
pack200 -O -E9 -Htrue -mlatest -Ustrip -r Launcher.jar
jarsigner -keystore build/sashok724.jks -storepass PSP1004 -sigfile LAUNCHER Launcher.jar sashok724 > /dev/null
pack200 Launcher.pack.gz Launcher.jar

# Build LauncherAuthlib.jar
echo 'Packing LauncherAuthlib.jar binary'
pack200 -O -E9 -Htrue -mlatest -Ustrip -r LauncherAuthlib.jar
jarsigner -keystore build/sashok724.jks -storepass PSP1004 -sigfile LAUNCHER LauncherAuthlib.jar sashok724 > /dev/null

# Build LaunchServer.jar
echo 'Packing LaunchServer.jar binary'
zip -9 LaunchServer.jar Launcher.pack.gz
pack200 -O -E9 -Htrue -mlatest -Ustrip -r LaunchServer.jar
jarsigner -keystore build/sashok724.jks -storepass PSP1004 -sigfile LAUNCHER LaunchServer.jar sashok724 > /dev/null
rm Launcher.pack.gz
