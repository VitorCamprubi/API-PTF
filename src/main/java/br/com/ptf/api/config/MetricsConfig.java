package br.com.ptf.api.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    /**
     * Carimba toda metrica com o nome da aplicacao.
     *
     * Num servidor de metricas compartilhado por varios servicos, metrica sem essa
     * tag e metrica que ninguem consegue atribuir a ninguem. O customizer aplica em
     * tudo que o registry publicar, inclusive nas metricas que o proprio Spring
     * cria, sem precisar tocar em cada ponto de medicao.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name}") String aplicacao) {

        return registry -> registry.config().commonTags("application", aplicacao);
    }
}
