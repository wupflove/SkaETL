package io.skalogs.skaetl.service.transform;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.skalogs.skaetl.domain.ParameterTransformation;
import io.skalogs.skaetl.domain.TypeValidation;
import io.skalogs.skaetl.service.TransformatorProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Slf4j
public class GeoPointTransformator extends TransformatorProcess {

    public GeoPointTransformator(TypeValidation type) {
        super(type);
    }

    public void apply(String idProcess, ParameterTransformation parameterTransformation, ObjectNode jsonValue, String value) {
        String valueToFormat = at(parameterTransformation.getKeyField(), jsonValue).asText();
        if (StringUtils.isNotBlank(valueToFormat)) {
            put(jsonValue, parameterTransformation.getKeyField() + "_gp", valueToFormat);
            remove(jsonValue,parameterTransformation.getKeyField());
        }
    }
}
