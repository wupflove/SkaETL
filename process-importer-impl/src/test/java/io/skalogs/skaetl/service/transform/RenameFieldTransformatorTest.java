package io.skalogs.skaetl.service.transform;

/*-
 * #%L
 * process-importer-impl
 * %%
 * Copyright (C) 2017 - 2018 SkaLogs
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.skalogs.skaetl.RawDataGen;
import io.skalogs.skaetl.domain.ParameterTransformation;
import io.skalogs.skaetl.domain.ProcessKeyValue;
import io.skalogs.skaetl.utils.JSONUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class RenameFieldTransformatorTest {
    @Test
    public void should_Process_Ok() throws Exception {
        RenameFieldTransformator renameFieldValidator = new RenameFieldTransformator();
        RawDataGen rd = RawDataGen.builder().messageSend("gni").project("projectvalue").type("type").build();
        ObjectMapper obj = new ObjectMapper();
        String value = obj.writeValueAsString(rd);
        ObjectNode jsonValue = JSONUtils.getInstance().parseObj(value);

        renameFieldValidator.apply(null, ParameterTransformation.builder()
                .composeField(ProcessKeyValue.builder()
                        .key("project")
                        .value("@project")
                        .build()
                ).build(), jsonValue);
        assertThat(jsonValue.path("@project").asText()).isEqualTo("projectvalue");
    }

    @Test
    public void should_Process_Nested_Ok() throws Exception {
        RenameFieldTransformator renameFieldValidator = new RenameFieldTransformator();
        RawDataGen rd = RawDataGen.builder().messageSend("gni").project("projectvalue").type("type").build();
        ObjectMapper obj = new ObjectMapper();
        String value = obj.writeValueAsString(rd);
        ObjectNode jsonValue = JSONUtils.getInstance().parseObj(value);

        renameFieldValidator.apply(null, ParameterTransformation.builder()
                .composeField(ProcessKeyValue.builder()
                        .key("project")
                        .value("my.project.name")
                        .build()
                ).build(), jsonValue);
        assertThat(JSONUtils.getInstance().at(jsonValue, "my.project.name").asText()).isEqualTo("projectvalue");
    }

    @Test
    public void should_Process_Nested_SameObject_Ok() throws Exception {
        RenameFieldTransformator renameFieldValidator = new RenameFieldTransformator();
        String value = "{\"something\":\"test\",\"comment\": {\"value\":\"value1\"}}";
        ObjectNode jsonValue = JSONUtils.getInstance().parseObj(value);

        renameFieldValidator.apply(null, ParameterTransformation.builder()
                .composeField(ProcessKeyValue.builder()
                        .key("comment.value")
                        .value("comment")
                        .build()
                ).build(), jsonValue);
        assertThat(JSONUtils.getInstance().at(jsonValue, "comment").asText()).isEqualTo("value1");
    }

    @Test
    public void should_Process_Ko() throws Exception {
        RenameFieldTransformator renameFieldValidator = new RenameFieldTransformator();
        RawDataGen rd = RawDataGen.builder().messageSend("gni").project("project").type("type").build();
        ObjectMapper obj = new ObjectMapper();
        String value = obj.writeValueAsString(rd);
        ObjectNode jsonValue = JSONUtils.getInstance().parseObj(value);

        renameFieldValidator.apply(null, ParameterTransformation.builder()
                .composeField(ProcessKeyValue.builder()
                        .key("project2")
                        .value("@project")
                        .build()
                ).build(), jsonValue);
        assertThat(jsonValue.get("@project")).isNull();
    }

    @Test
    public void should_Process_RenameEmpty() throws Exception {
        RenameFieldTransformator renameFieldValidator = new RenameFieldTransformator();
        RawDataGen rd = RawDataGen.builder().messageSend("gni").project("").type("type").build();
        ObjectMapper obj = new ObjectMapper();
        String value = obj.writeValueAsString(rd);
        ObjectNode jsonValue = JSONUtils.getInstance().parseObj(value);

        renameFieldValidator.apply(null, ParameterTransformation.builder()
                .composeField(ProcessKeyValue.builder()
                        .key("project")
                        .value("@project")
                        .build()
                ).build(), jsonValue);
        assertThat(jsonValue.get("@project").asText()).isEqualTo("");
    }

}
