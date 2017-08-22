# Storm Metrics Statsd

storm-metrics-statsd is a module for [Storm](http://storm-project.net/) that enables metrics collection and reporting to [statsd](https://github.com/etsy/statsd/).

## Building/Installation

    git clone https://github.com/jegeiger/storm-metrics-statsd.git
    cd storm-metrics-statsd
    mvn compile package install

## Usage

This module can be used in two ways:

1. Configure it for each topology by calling `Conf.registerMetricsConsumer()` prior to launching the topology.
2. Deploy and configure system wide so usage of this is transparent across all topologies.

### Configure each topology separately

Add this as a dependency to your `pom.xml`

    <dependency>
      <groupId>com.endgame</groupId>
      <artifactId>storm-metrics-statsd</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>

Configure the `StatsdMetricConsumer` when building your topology.  The example below is
based on the [storm-starter](https://github.com/nathanmarz/storm-starter) [ExclamationTopology](https://github.com/nathanmarz/storm-starter/blob/master/src/jvm/storm/starter/ExclamationTopology.java).

    import com.endgame.storm.metrics.statsd.StatsdMetricConsumer;

    ...

    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("word", new TestWordSpout(), 10);
    builder.setBolt("exclaim1", new ExclamationBolt(), 3).shuffleGrouping("word");
    builder.setBolt("exclaim2", new ExclamationBolt(), 2).shuffleGrouping("exclaim1");
    
    #
    #  Configure the StatsdMetricConsumer
    #
    Map statsdConfig = new HashMap();
    statsdConfig.put(StatsdMetricConsumer.STATSD_HOST, "statsd.server.mydomain.com");
    statsdConfig.put(StatsdMetricConsumer.STATSD_PORT, 8125);
    statsdConfig.put(StatsdMetricConsumer.STATSD_PREFIX, "storm.metrics.");
    statsdConfig.put(StatsdMetricConsumer.STATSD_USE_HOSTNAME, "true");
    
    Config conf = new Config();
    conf.registerMetricsConsumer(StatsdMetricConsumer.class, statsdConfig, 2);
     
    if (args != null && args.length > 0) {
      conf.setNumWorkers(3);
      StormSubmitter.submitTopology(args[0], conf, builder.createTopology());
    }
    else {
      LocalCluster cluster = new LocalCluster();
      cluster.submitTopology("test", conf, builder.createTopology());
      Utils.sleep(5*60*1000L);
      cluster.killTopology("test");
      cluster.shutdown();
    }

### System Wide Deployment

System wide deployment requires three steps:

#### 1. Add this section to your `$STORM_HOME/conf/storm.yaml`.  

    topology.metrics.consumer.register:
      - class: "com.endgame.storm.metrics.statsd.StatsdMetricConsumer"
        parallelism.hint: 2
        argument:
          metrics.statsd.host: "statsd.server.mydomain.com"
          metrics.statsd.port: 8125
          metrics.statsd.prefix: "storm.metrics."
          metrics.statsd.usehostname: true

#### 2. Install the `storm-metrics-statsd` and `java-statsd-client` JARs into `$STORM_HOME/lib/` ON EACH STORM NODE.

    $ mvn package
    $ mvn org.apache.maven.plugins:maven-dependency-plugin:2.7:copy-dependencies -DincludeArtifactIds=java-statsd-client
    $ cp target/dependency/java-statsd-client-2.0.0.jar $STORM_HOME/lib/
    $ cp target/storm-metrics-statsd-*.jar $STORM_HOME/lib/

#### 3. Restart storm and you will likely need to restart any topologies running prior to changing your `$STORM_HOME/conf/storm.yaml`.

### Notes

You can override the topology name used when reporting to statsd by calling:

    statsdConfig.put(Config.TOPOLOGY_NAME, "myTopologyName");
    // OR 
    statsdConfig.put("topology.name", "myTopologyName");

This will be useful if you use versioned topology names (.e.g. appending a timestamp or a version string), but only care to track them as one in statsd.    

You can choose to include or exclude the hostname from the keyspace reported to statsd via the `metrics.statsd.usehostname` configuration option.
Set this option to `false` to have the hostname excluded from the keyspace, or `true` if you'd like it included.  If you don't explicitly set this 
property it will default to `true` for backwards compatability.

## Changelog

### v1.6.0
- Update underlying statsd client to 3.1.0
- Metrics are no longer coerced into ints
- - This means long values will no longer overflow.

### v1.5.0
- Metrics now have *special* prefixes:
- - COUNTER - will be reported as a counter.
- - GAUGE - will be reported as a gauge.
- - all others default to being reported as a timer.

### v1.4.0
- No longer escaping periods (.) in metric key names.

### v1.3.0
- Added the `metrics.statsd.usehostname` configuration option to conditionally include the workers hostname in the reported keyspace.

### v1.2.0
- Removed the workerId key from the reported key space


## License

storm-metrics-statsd
        
Copyright 2014 [Endgame, Inc.](http://www.endgame.com/)

![Endgame, Inc.](http://www.endgame.com/images/logo.svg)
      

        Licensed under the Apache License, Version 2.0 (the "License"); you may
        not use this file except in compliance with the License. You may obtain
        a copy of the License at

             http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing,
        software distributed under the License is distributed on an
        "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
        KIND, either express or implied.  See the License for the
        specific language governing permissions and limitations
        under the License.


## Author

[Jason Trost](https://github.com/jt6211/) ([@jason_trost](https://twitter.com/jason_trost))

