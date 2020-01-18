#!/bin/sh

# common config
log_level=config
log_configFile="../config/log4j2.xml"

# locator config
locator_address=localhost
locator_name=geo_locator

# server config
server_address=localhost
server_name=geo_server1
server_port=40001
server_http_port=8888
group=group1


# locator start
start locator --name=${locator_name} --bind-address=${locator_address} --J=-Xmx180m \
--log-level=${log_level} --J=-Dlog4j.configurationFile=${log_configFile} \
--include-system-classpath=true

#configure pdx --auto-serializable-classes=com\.ken\.domain\.*
create disk-store --name=cluster_config --allow-force-compaction=true --auto-compact=true --dir=../data/node1/ds1

# start data node1
start server --name=${server_name} --locators="${locator_address}[10334]" --bind-address=${server_address} \
--include-system-classpath=true --log-level=${log_level} --groups=${group} --cache-xml-file=config/cacheClusterxml \
--J=-Xmx1024m --J=-Xms1024m --J=-XX:+DisableExplicitGC --J=-XX:+UseConcMarkSweepGC --J=-XX:+UseParNewGC \
--J=-Dgemfire.QueryService.allowUntrustedMethodInvocation=true --J=-Dlog4j.configurationFile=${log_configFile} \
--start-rest-api=true --http-service-port=${server_http_port} --server-port=${server_port} \
--enable-time-statistics --statistic-archive-file=lucene1.gfs


create region --name=region1 --groups=group1 --type=REPLICATE

