java -jar target/jmxclient.jar get hostname:8090 user pass com.mchange.v2.c3p0:type=PooledDataSource.* numBusyConnections true 1
