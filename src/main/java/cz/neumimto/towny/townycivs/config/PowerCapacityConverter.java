package cz.neumimto.towny.townycivs.config;

import com.electronwill.nightconfig.core.conversion.Converter;
import com.electronwill.nightconfig.core.conversion.Path;

import java.util.HashMap;
import java.util.Map;

/**
 * Converter for power capacity configuration
 * Converts the power_capacity section from config into a Map<Integer, Double>
 */
public class PowerCapacityConverter implements Converter<Map<Integer, Double>, Object> {

    @Override
    public Map<Integer, Double> convertToField(Object o) {
        Map<Integer, Double> result = new HashMap<>();

        if (o instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Integer key = entry.getKey() instanceof Number ? (Integer) entry.getKey() : null;
                Object val = entry.getValue();

                if (val instanceof Number) {
                    result.put(key, ((Number) val).doubleValue());
                }
            }
        }

        return result;
    }

    @Override
    public Object convertFromField(Map<Integer, Double> integerDoubleMap) {
        return null;
    }
}
