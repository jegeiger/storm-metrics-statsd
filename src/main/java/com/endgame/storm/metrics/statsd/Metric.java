package com.endgame.storm.metrics.statsd;

public class Metric {
	private final String name;
	private final Number value;
	private final MetricType type;

	public Metric(String name, Number value, MetricType type) {
		this.name = name;
		this.value = value;
		this.type = type;
	}

	public String name() {
		return name;
	}

	public Number value() {
		return value;
	}

	public MetricType type() {
		return type;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Metric metric = (Metric) o;

		if (value.longValue() != metric.value.longValue()) return false;
		if (!name.equals(metric.name)) return false;
		return type == metric.type;
	}

	@Override
	public int hashCode() {
		int result = name.hashCode();
		result = 31 * result + value.hashCode();
		result = 31 * result + type.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "Metric{" +
				"name='" + name + '\'' +
				", value=" + value +
				", type=" + type +
				'}';
	}

	public static Metric createTimerMetric(String name, Number value) {
		return new Metric(name, value, MetricType.TIMER);
	}

	public static Metric createGaugeMetric(String name, Number value) {
		return new Metric(name, value, MetricType.GAUGE);
	}

	public static Metric createCounterMetric(String name, Number value) {
		return new Metric(name, value, MetricType.COUNTER);
	}
}
