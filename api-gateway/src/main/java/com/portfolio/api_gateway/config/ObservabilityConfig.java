package com.portfolio.api_gateway.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    /**
     * Cria manualmente o bean OpenTelemetry exigido pelo Micrometer Tracing.
     * Isso resolve o erro "No beans of 'OpenTelemetry' type found".
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        // Cria um provedor de rastreamento básico (necessário para o SDK)
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();

        // Inicializa o OpenTelemetry SDK com propagação de contexto W3C (padrão web)
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    /**
     * 2. Cria o bean Tracer (Micrometer) manualmente usando a bridge Otel.
     * Isso resolve o erro "Parameter 0 ... required a bean of type ... Tracer".
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return new OtelTracer(
                openTelemetry.getTracer("com.portfolio.api-gateway"),
                new OtelCurrentTraceContext(),
                event -> {
                    // Implementação de OtelTracer.EventPublisher
                    // Aqui você pode processar eventos de span se necessário.
                    // Por enquanto, deixamos vazio (NO-OP).
                }
        );
    }
}
