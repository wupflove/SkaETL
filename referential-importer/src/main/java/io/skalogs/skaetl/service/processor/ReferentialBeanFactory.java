package io.skalogs.skaetl.service.processor;

import io.skalogs.skaetl.config.ESBufferConfiguration;
import io.skalogs.skaetl.config.ESConfiguration;
import io.skalogs.skaetl.domain.ESBuffer;
import io.skalogs.skaetl.domain.RetentionLevel;
import io.skalogs.skaetl.service.ESErrorRetryWriter;
import lombok.AllArgsConstructor;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AllArgsConstructor
public class ReferentialBeanFactory {
    private final ESErrorRetryWriter esErrorRetryWriter;
    private final RestHighLevelClient client;
    private final ESConfiguration esConfiguration;
    private final ESBufferConfiguration esBufferConfiguration;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ReferentialElasticsearchProcessor referentialElasticsearchProcessor(RetentionLevel retentionLevel) {
        ESBuffer esBuffer = new ESBuffer(client, esBufferConfiguration, esConfiguration);
        return new ReferentialElasticsearchProcessor(esBuffer, esErrorRetryWriter, retentionLevel);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ReferentialEventToElasticSearchProcessor referentialEventToElasticSearchProcessor(RetentionLevel retentionLevel) {
        ESBuffer esBuffer = new ESBuffer(client, esBufferConfiguration, esConfiguration);
        return new ReferentialEventToElasticSearchProcessor(esBuffer, esErrorRetryWriter, retentionLevel);
    }


}