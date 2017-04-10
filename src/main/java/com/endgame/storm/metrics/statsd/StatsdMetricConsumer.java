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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.storm.Config;
import org.apache.storm.metric.api.IMetricsConsumer;
import org.apache.storm.task.IErrorReporter;
import org.apache.storm.task.TopologyContext;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

/**
 * @author Jason Trost
 */
public class StatsdMetricConsumer implements IMetricsConsumer {

	public static final Logger LOG = LoggerFactory.getLogger(StatsdMetricConsumer.class);

	public static final String STATSD_HOST = "metrics.statsd.host";
	public static final String STATSD_PORT = "metrics.statsd.port";
	public static final String STATSD_PREFIX = "metrics.statsd.prefix";

	// Used to enable or disable appending the hostname key
	// defaulted to use it
	public static final String STATSD_USE_HOSTNAME = "metrics.statsd.usehostname";

	String topologyName;
	String statsdHost;
	int statsdPort = 8125;
	String statsdPrefix = "storm.metrics.";
	boolean useHostname = true;

	transient StatsDClient statsd;

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, Object registrationArgument,
						TopologyContext context, IErrorReporter errorReporter) {
		parseConfig(stormConf);

		if (registrationArgument instanceof Map) {
			parseConfig((Map) registrationArgument);
		}

		statsd = new NonBlockingStatsDClient(statsdPrefix + clean(topologyName), statsdHost, statsdPort);
	}

	void parseConfig(@SuppressWarnings("rawtypes") Map conf) {
		if (conf.containsKey(Config.TOPOLOGY_NAME)) {
			topologyName = (String) conf.get(Config.TOPOLOGY_NAME);
		}

		if (conf.containsKey(STATSD_HOST)) {
			statsdHost = (String) conf.get(STATSD_HOST);
		}

		if (conf.containsKey(STATSD_PORT)) {
			Object configPortValue = conf.get(STATSD_PORT);

			if (configPortValue instanceof String) {
				statsdPort = Integer.parseInt((String) configPortValue);
			} else {
				statsdPort = ((Number) conf.get(STATSD_PORT)).intValue();
			}
		}

		if (conf.containsKey(STATSD_PREFIX)) {
			statsdPrefix = (String) conf.get(STATSD_PREFIX);
			if (!statsdPrefix.endsWith(".")) {
				statsdPrefix += ".";
			}
		}

		// The no hostname check
		if (conf.containsKey(STATSD_USE_HOSTNAME)) {
			useHostname = (boolean) conf.get(STATSD_USE_HOSTNAME);
		}
	}

	String clean(String s) {
		return s.replace('/', '_')
				.replace(':', '_')
				.replace('|', '_')
				.replace('@', '_');
	}

	@Override
	public void handleDataPoints(TaskInfo taskInfo,
								 Collection<DataPoint> dataPoints) {
		for (Metric metric : dataPointsToMetrics(taskInfo, dataPoints)) {
			report(metric);
		}
	}

	List<Metric> dataPointsToMetrics(TaskInfo taskInfo,
									 Collection<DataPoint> dataPoints) {
		List<Metric> res = new LinkedList<>();

		StringBuilder sb = new StringBuilder();

		// Conditionally append the hostname key
		if (useHostname) {
			sb.append(clean(taskInfo.srcWorkerHost)).append(".");
		}
		sb.append(clean(taskInfo.srcComponentId)).append(".");

		int hdrLength = sb.length();

		for (DataPoint p : dataPoints) {

			sb.delete(hdrLength, sb.length());
			sb.append(clean(p.name));

			// Get type from metric name
			final MetricType metricType = getMetricTypeFromName(p.name);

			if (p.value instanceof Number) {
				res.add(new Metric(sb.toString(), ((Number) p.value).intValue(), metricType));
			} else if (p.value instanceof Map) {
				int hdrAndNameLength = sb.length();
				@SuppressWarnings("rawtypes")
				Map map = (Map) p.value;
				for (Object subName : map.keySet()) {
					Object subValue = map.get(subName);
					if (subValue instanceof Number) {
						sb.delete(hdrAndNameLength, sb.length());
						sb.append(".").append(clean(subName.toString()));

						res.add(new Metric(sb.toString(), ((Number) subValue).intValue(), metricType));
					}
				}
			}
		}
		return res;
	}

	private MetricType getMetricTypeFromName(String name) {
		if (name.startsWith("gauge")) {
			return MetricType.GAUGE;
		} else if (name.startsWith("counter")) {
			return MetricType.COUNTER;
		}
		return MetricType.TIMER;
	}

	private void report(Metric metric) {
		LOG.debug("reporting: {}", metric.toString());

		if (MetricType.COUNTER.equals(metric.type())) {
			statsd.count(metric.name(), metric.value());
		} else if (MetricType.GAUGE.equals(metric.type())) {
			statsd.gauge(metric.name(), metric.value());
		} else {
			// Fall back to timer.
			statsd.recordExecutionTime(metric.name(), metric.value());
		}
	}

	@Override
	public void cleanup() {
		statsd.stop();
	}
}
