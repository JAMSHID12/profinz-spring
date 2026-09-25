#!/bin/sh
set -eu
# A mounted Railway/Docker volume can be owned by root at container startup.
if [ "$(id -u)" = "0" ]; then
    mkdir -p /data
    chown education:education /data
    exec gosu education sh -c 'exec java $JAVA_OPTS -jar /app/app.jar' 
fi
exec sh -c 'exec java $JAVA_OPTS -jar /app/app.jar'
