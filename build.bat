@ECHO OFF

REM Build Launcher.jar
echo Building Launcher.jar...
jar -uf Launcher.jar buildnumber
java -jar build/proguard.jar @Launcher.pro
del Launcher.jar
ren Launcher-obf.jar Launcher.jar
java -jar build/stringer.jar -configFile Launcher.stringer Launcher.jar Launcher.jar
pack200 -E9 -Htrue -mlatest -Upass -r Launcher.jar
jarsigner -keystore build/sashok724.jks -storepass PSP1004 -sigfile LAUNCHER Launcher.jar sashok724
pack200 Launcher.pack.gz Launcher.jar

REM Build LaunchServer.jar
echo Building LaunchServer.jar...
jar -uf LaunchServer.jar Launcher.pack.gz buildnumber
pack200 -E9 -Htrue -mlatest -Upass -r LaunchServer.jar
jarsigner -keystore build/sashok724.jks -storepass PSP1004 -sigfile LAUNCHER LaunchServer.jar sashok724
del Launcher.pack.gz
