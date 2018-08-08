package io.skalogs.skaetl.service.transform;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.skalogs.skaetl.domain.ParameterTransformation;
import io.skalogs.skaetl.domain.TypeValidation;
import io.skalogs.skaetl.service.TransformatorProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class LongFieldTransformator extends TransformatorProcess {

    public LongFieldTransformator(TypeValidation type) {
        super(type);
    }

    public void apply(String idProcess, ParameterTransformation parameterTransformation, ObjectNode jsonValue) {
        String valueToFormat = at(parameterTransformation.getKeyField(), jsonValue).asText();
        if (StringUtils.isNotBlank(valueToFormat)) {
            put(jsonValue, parameterTransformation.getKeyField() + "_long", Long.valueOf(valueToFormat));
            remove(jsonValue,parameterTransformation.getKeyField());
        }
    }
}
