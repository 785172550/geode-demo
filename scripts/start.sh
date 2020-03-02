#!/bin/sh

start locator --name=geo_locator --bind-address=localhost --J=-Xmx150m \
--log-level=config --J=-Dlog4j.configurationFile=../config/log4j2.xml \
--include-system-classpath=true

# start data node1
start server --name=geo_server1 --locators="localhost[10334]" --bind-address=localhost \
--include-system-classpath=true --log-level=config --cache-xml-file=config/cacheSchema.xml \
--J=-Xmx1024m --J=-Xms1024m --J=-XX:+DisableExplicitGC --J=-XX:+UseConcMarkSweepGC --J=-XX:+UseParNewGC \
--J=-Dgemfire.QueryService.allowUntrustedMethodInvocation=true --J=-Dlog4j.configurationFile=../config/log4j2.xml \
--start-rest-api=true --http-service-port=8888  --server-port=40001 \
--enable-time-statistics --statistic-archive-file=lucene1.gfs --group=test

# start data node2
start server --name=geo_server2 --locators=127.0.0.1[10334] --bind-address=127.0.0.1 \
--include-system-classpath=true --log-level=config --cache-xml-file=config/cacheSchema2.xml \
--J=-Xmx1024m --J=-Xms1024m --J=-XX:+DisableExplicitGC --J=-XX:+UseConcMarkSweepGC --J=-XX:+UseParNewGC \
--J=-Dgemfire.QueryService.allowUntrustedMethodInvocation=true --J=-Dlog4j.configurationFile=../config/log4j2.xml \
--server-port=40002 --group=test2

#list members
#list region
list index
list lucene indexes

####### commemt
# --security-properties-file=config/local/security.properties
# deploy --jar=build/libs/functions.jar

#
#
#
#