#!/bin/sh
set -e

function pack {
    local input=$1
    local output=$2
    shift 2

    # $3..$n - additional files
    echo "Packing $input binary"
    zip -9 "$input" "buildnumber" $@
    pack200 -E9 -Htrue -mlatest -Uerror -r "$input"
    jarsigner -keystore "build/sashok724.jks" -storepass PSP1004 -sigfile LAUNCHER "$input" sashok724 > /dev/null
    [ ! -z "$output" ] && pack200 -E9 -Htrue -mlatest -Uerror "$output" "$input"

    # Return
    true
}

# Increase build number
echo -n $(($(cat buildnumber | cut -d "," -f 1)+1)), $(date +"%d.%m.%Y") > buildnumber

# Pack files
pack "Launcher.jar" "Launcher.pack.gz"
pack "LauncherAuthlib.jar" ""
pack "LauncherRuntime.jar" "runtime.pack.gz"
pack "LaunchServer.jar" "" "Launcher.pack.gz" "runtime.pack.gz"

# Cleanup
rm Launcher.pack.gz runtime.pack.gz
