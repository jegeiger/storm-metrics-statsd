/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * <p/>
 * Copyright 2013 Endgame Inc.
 */

package com.endgame.storm.metrics.statsd;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import org.apache.storm.Config;
import org.apache.storm.metric.api.IMetricsConsumer.DataPoint;
import org.apache.storm.metric.api.IMetricsConsumer.TaskInfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jason Trost
 */
public class StatsdMetricConsumerTest extends TestCase {
	private static final Logger logger = LoggerFactory.getLogger(StatsdMetricConsumerTest.class);

	StatsdMetricConsumer undertest;

	@Override
	protected void setUp() throws Exception {
		undertest = new StatsdMetricConsumer();
	}

	public void testParseConfig() {
		assertNull(undertest.statsdHost);
		assertEquals("storm.metrics.", undertest.statsdPrefix);
		assertEquals(8125, undertest.statsdPort);
		assertNull(undertest.topologyName);

		Map conf = new HashMap();
		conf.put(StatsdMetricConsumer.STATSD_HOST, "localhost");
		// Test that storm/clojure would magically convert int to Long
		conf.put(StatsdMetricConsumer.STATSD_PORT, 5555l);
		conf.put(StatsdMetricConsumer.STATSD_PREFIX, "my.statsd.prefix");
		conf.put(Config.TOPOLOGY_NAME, "myTopologyName");
		conf.put(StatsdMetricConsumer.STATSD_USE_HOSTNAME, false);

		undertest.parseConfig(conf);

		assertEquals("localhost", undertest.statsdHost);
		assertEquals("my.statsd.prefix.", undertest.statsdPrefix);
		assertEquals(5555, undertest.statsdPort);
		assertEquals("myTopologyName", undertest.topologyName);
		assertFalse("Should have been configured to false", undertest.useHostname);

		// Test that int values are handled appropriately
		conf.put(StatsdMetricConsumer.STATSD_PORT, 5555);

		undertest.parseConfig(conf);

		assertEquals(5555, undertest.statsdPort);

		// Test that String values are handled appropriately
		conf.put(StatsdMetricConsumer.STATSD_PORT, "5555");

		undertest.parseConfig(conf);

		assertEquals(5555, undertest.statsdPort);
	}

	public void testParseConfigUseHostnameDefaultsTrueWhenNotDefined() {
		assertNull(undertest.statsdHost);
		assertEquals("storm.metrics.", undertest.statsdPrefix);
		assertEquals(8125, undertest.statsdPort);
		assertNull(undertest.topologyName);

		Map conf = new HashMap();
		conf.put(StatsdMetricConsumer.STATSD_HOST, "localhost");
		// Test that storm/clojure would magically convert int to Long
		conf.put(StatsdMetricConsumer.STATSD_PORT, 5555l);
		conf.put(StatsdMetricConsumer.STATSD_PREFIX, "my.statsd.prefix");
		conf.put(Config.TOPOLOGY_NAME, "myTopologyName");

		undertest.parseConfig(conf);

		assertEquals("localhost", undertest.statsdHost);
		assertEquals("my.statsd.prefix.", undertest.statsdPrefix);
		assertEquals(5555, undertest.statsdPort);
		assertEquals("myTopologyName", undertest.topologyName);
		assertTrue("Should have been configured to true by default", undertest.useHostname);
	}

	public void testCleanString() {
		assertEquals("test", undertest.clean("test"));
		assertEquals("test_name", undertest.clean("test/name"));
		assertEquals("test_name", undertest.clean("test@name"));
		assertEquals("test_name", undertest.clean("test:name"));
		assertEquals("test_name", undertest.clean("test|name"));

		// Allow dots thru
		assertEquals("test.name", undertest.clean("test.name"));
	}

	public void testPrepare() {
		assertNull(undertest.statsdHost);
		assertEquals("storm.metrics.", undertest.statsdPrefix);
		assertEquals(8125, undertest.statsdPort);
		assertNull(undertest.topologyName);
		assertNull(undertest.statsd);

		Map stormConf = new HashMap();
		stormConf.put(Config.TOPOLOGY_NAME, "myTopologyName");

		Map registrationArgument = new HashMap();
		registrationArgument.put(StatsdMetricConsumer.STATSD_HOST, "localhost");
		registrationArgument.put(StatsdMetricConsumer.STATSD_PORT, 5555);
		registrationArgument.put(StatsdMetricConsumer.STATSD_PREFIX,
				"my.statsd.prefix");

		undertest.prepare(stormConf, registrationArgument, null, null);

		assertEquals("localhost", undertest.statsdHost);
		assertEquals("my.statsd.prefix.", undertest.statsdPrefix);
		assertEquals(5555, undertest.statsdPort);
		assertEquals("myTopologyName", undertest.topologyName);
		assertNotNull(undertest.statsd);
	}

	public void testDataPointsToMetrics() {
		TaskInfo taskInfo = new TaskInfo("host1", 6701, "myBolt7", 12,
				123456789000L, 60);
		List<DataPoint> dataPoints = new LinkedList<>();

		dataPoints.add(new DataPoint("my.int", 57));
		dataPoints.add(new DataPoint("my.long", 57L));
		dataPoints.add(new DataPoint("my/float", 222f));
		dataPoints.add(new DataPoint("my_double", 56.0d));
		dataPoints.add(new DataPoint("ignored", "not a num"));
		dataPoints.add(new DataPoint("points", ImmutableMap
				.<String, Object>of("count", 123, "time", 2342234, "ignored",
						"not a num")));
		dataPoints.add(new DataPoint("reallybig", 10957511159L));


		undertest.topologyName = "testTop";
		undertest.statsdPrefix = "testPrefix";

		// Enable hostname
		undertest.useHostname = true;

		// topology and prefix are used when creating statsd, and statsd client
		// handles adding them, they should not show up here.
		List<Metric> expected = ImmutableList.<Metric>of(
			Metric.createTimerMetric("host1.myBolt7.my.int", 57),
			Metric.createTimerMetric("host1.myBolt7.my.long", 57),
			Metric.createTimerMetric("host1.myBolt7.my_float", 222),
			Metric.createTimerMetric("host1.myBolt7.my_double", 56),
			Metric.createTimerMetric("host1.myBolt7.points.count", 123),
			Metric.createTimerMetric("host1.myBolt7.points.time", 2342234),
			Metric.createTimerMetric("host1.myBolt7.reallybig", 10957511159L)
		);

		// get result
		final List<Metric> results = undertest.dataPointsToMetrics(taskInfo, dataPoints);

		// validate result
		validateResults(expected, results);
	}

	public void testDataPointsToMetricsWhenHostnameIsDisabled() {
		TaskInfo taskInfo = new TaskInfo("host1", 6701, "myBolt7", 12,
				123456789000L, 60);
		List<DataPoint> dataPoints = new LinkedList<>();

		dataPoints.add(new DataPoint("my.int", 57));
		dataPoints.add(new DataPoint("my.long", 57L));
		dataPoints.add(new DataPoint("my/float", 222f));
		dataPoints.add(new DataPoint("my_double", 56.0d));
		dataPoints.add(new DataPoint("ignored", "not a num"));
		dataPoints.add(new DataPoint("points", ImmutableMap
				.<String, Object>of("count", 123, "time", 2342234, "ignored",
						"not a num")));
		dataPoints.add(new DataPoint("reallybig", 10957511159L));

		undertest.topologyName = "testTop";
		undertest.statsdPrefix = "testPrefix";

		// Disable hostname
		undertest.useHostname = false;

		// topology and prefix are used when creating statsd, and statsd client
		// handles adding them
		// they should not show up here

		List<Metric> expected = ImmutableList.<Metric>of(
			Metric.createTimerMetric("myBolt7.my.int", 57),
			Metric.createTimerMetric("myBolt7.my.long", 57),
			Metric.createTimerMetric("myBolt7.my_float", 222),
			Metric.createTimerMetric("myBolt7.my_double", 56),
			Metric.createTimerMetric("myBolt7.points.count", 123),
			Metric.createTimerMetric("myBolt7.points.time", 2342234),
			Metric.createTimerMetric("myBolt7.reallybig", 10957511159L)
		);

		// get result
		final List<Metric> results = undertest.dataPointsToMetrics(taskInfo, dataPoints);

		// validate result
		validateResults(expected, results);
	}

	public void testSelectingCorrectMetricTypeBasedOnName() {
		TaskInfo taskInfo = new TaskInfo("host1", 6701, "myBolt7", 12,
				123456789000L, 60);
		List<DataPoint> dataPoints = new LinkedList<>();

		dataPoints.add(new DataPoint("GAUGE-my.int", 57));
		dataPoints.add(new DataPoint("GAUGE.my.long", 57L));
		dataPoints.add(new DataPoint("TIMER.my/float", 222f));
		dataPoints.add(new DataPoint("COUNTER-my_double", 56.0d));
		dataPoints.add(new DataPoint("COUNTERSignored", "not a num"));
		dataPoints.add(new DataPoint("GAUGES", ImmutableMap.<String, Object>of(
			"COUNT", 123,
			"TIME", 2342234,
			"ignored",
			"not a num")
		));
		dataPoints.add(new DataPoint("GAUGE-reallybig",10957511160L));
		dataPoints.add(new DataPoint("COUNTER-reallybig",10957511161L));
		dataPoints.add(new DataPoint("TIMER-reallybig",10957511162L));

		undertest.topologyName = "testTop";
		undertest.statsdPrefix = "testPrefix";

		// Enable hostname
		undertest.useHostname = true;

		// topology and prefix are used when creating statsd, and statsd client
		// handles adding them
		// they should not show up here

		List<Metric> expected = ImmutableList.<Metric>of(
			Metric.createGaugeMetric("host1.myBolt7.GAUGE-my.int", 57),
			Metric.createGaugeMetric("host1.myBolt7.GAUGE.my.long", 57),
			Metric.createTimerMetric("host1.myBolt7.TIMER.my_float", 222),
			Metric.createCounterMetric("host1.myBolt7.COUNTER-my_double", 56),
			Metric.createGaugeMetric("host1.myBolt7.GAUGES.COUNT", 123),
			Metric.createGaugeMetric("host1.myBolt7.GAUGES.TIME", 2342234),
			Metric.createGaugeMetric("host1.myBolt7.GAUGE-reallybig", 10957511160L),
			Metric.createCounterMetric("host1.myBolt7.COUNTER-reallybig", 10957511161L),
			Metric.createTimerMetric("host1.myBolt7.TIMER-reallybig", 10957511162L)
		);

		// get result
		final List<Metric> results = undertest.dataPointsToMetrics(taskInfo, dataPoints);

		// validate result
		validateResults(expected, results);
	}

	/**
	 * Utility method to validate result.
	 * @param expected The Metrics we expected to get back.
	 * @param results The Metrics we actually got back.
	 */
	private void validateResults(List<Metric> expected, List<Metric> results) {
		// Validate
		assertEquals("Should have same number of results", expected.size(), results.size());

		// Loop through one by one
		for (final Metric expectedMetric: expected) {
			if (!results.contains(expectedMetric)) {
				// debug log
				logger.error("failed to find {}", expectedMetric);
				logger.error("Result: {}", results);

			}
			assertTrue("Contains our expected result metric", results.contains(expectedMetric));
		}
	}
}
