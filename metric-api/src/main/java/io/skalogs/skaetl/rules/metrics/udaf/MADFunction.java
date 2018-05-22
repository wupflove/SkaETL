package io.skalogs.skaetl.rules.metrics.udaf;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.math.Quantiles;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MADFunction extends AggregateFunction<JsonNode, Double> {
    @Getter
    private List<Double> values = new ArrayList<>();

    @Override
    public AggregateFunction addValue(JsonNode value) {
        values.add(value.doubleValue());
        return this;
    }

    @Override
    public Double compute() {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        return mad();
    }

    private Double mad() {
        double median = Quantiles.median().compute(values);
        List<Double> deviations = deviation(values, median);
        return Quantiles.median().compute(deviations);

    }

    private List<Double> deviation(List<Double> values, Double median) {
        return values.stream()
                .map(value -> Math.abs(value - median))
                .collect(Collectors.toList());
    }

    @Override
    public AggregateFunction<JsonNode, Double> merge(AggregateFunction<JsonNode, Double> newValue) {
        return compute() > newValue.compute() ? this : newValue;
    }
}
