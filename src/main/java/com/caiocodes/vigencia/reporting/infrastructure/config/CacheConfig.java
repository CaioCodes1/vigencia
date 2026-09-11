package com.caiocodes.vigencia.reporting.infrastructure.config;

import com.caiocodes.vigencia.reporting.application.DashboardCache;
import com.caiocodes.vigencia.reporting.application.DashboardSummary;
import com.caiocodes.vigencia.reporting.application.DelinquentClient;
import com.caiocodes.vigencia.reporting.application.ExpiringBucket;
import com.caiocodes.vigencia.reporting.application.RenewalRate;
import com.caiocodes.vigencia.reporting.application.RevenuePoint;
import com.caiocodes.vigencia.shared.domain.PageResult;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * O cache do painel, no Redis.
 *
 * <p><b>Um serializador por cache, com o tipo declarado.</b> A alternativa
 * comum — {@code GenericJackson2JsonRedisSerializer} com <i>default typing</i>
 * — grava o nome da classe dentro do JSON e o usa para instanciar na leitura.
 * Isso é desserialização polimórfica de conteúdo vindo de fora do processo, que
 * é a família de CVE mais explorada do ecossistema Java: quem conseguir
 * escrever no Redis escolhe qual classe a aplicação instancia. Aqui cada cache
 * sabe o tipo que guarda, e o que não couber nesse tipo não vira objeto.
 *
 * <p>De quebra, o JSON fica legível no {@code redis-cli GET} — sem o
 * {@code "@class"} em cada nó — que é o que se quer às duas da manhã.
 *
 * <p><b>TTL diferente por cache</b>, conforme o custo de estar desatualizado:
 * o resumo muda o tempo todo e vale 5 minutos; a fila de vencimentos muda uma
 * vez por dia e vale uma hora.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {
        // copy() para não mexer no mapper da API: aqui não pode entrar nem
        // sair configuração que mude como o JSON da resposta HTTP é escrito.
        ObjectMapper mapper = objectMapper.copy();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                // Null em cache é veneno silencioso: guarda "não existe" e
                // devolve isso por cinco minutos depois de o dado aparecer.
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withCacheConfiguration(DashboardCache.SUMMARY,
                        comTipo(base, mapper, tipo(mapper, DashboardSummary.class),
                                Duration.ofMinutes(5)))
                .withCacheConfiguration(DashboardCache.REVENUE,
                        comTipo(base, mapper, lista(mapper, RevenuePoint.class),
                                Duration.ofMinutes(15)))
                .withCacheConfiguration(DashboardCache.RENEWAL,
                        comTipo(base, mapper, tipo(mapper, RenewalRate.class),
                                Duration.ofMinutes(15)))
                .withCacheConfiguration(DashboardCache.DELINQUENT,
                        comTipo(base, mapper, pagina(mapper, DelinquentClient.class),
                                Duration.ofMinutes(5)))
                .withCacheConfiguration(DashboardCache.EXPIRING,
                        comTipo(base, mapper, lista(mapper, ExpiringBucket.class),
                                Duration.ofHours(1)))
                .build();
    }

    /**
     * Redis fora do ar deixa o sistema lento, não errado.
     *
     * <p>Sem isto, a exceção de conexão sobe pelo interceptador e o painel
     * responde 500 — o cache, que existe para melhorar a experiência, passa a
     * ser o único motivo de a tela não abrir. Com isto, a falha vira log e a
     * consulta vai ao banco, que é a fonte da verdade de qualquer jeito.
     *
     * <p>Vale só para leitura e gravação de cache. Erro em {@code evict} e
     * {@code clear} continua subindo ({@link SimpleCacheErrorHandler}): engolir
     * uma limpeza que não aconteceu deixaria dado velho servido como novo.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("cache.leitura_falhou cache={} chave={}: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key,
                                            Object value) {
                log.warn("cache.gravacao_falhou cache={} chave={}: {}",
                        cache.getName(), key, e.getMessage());
            }
        };
    }

    private static RedisCacheConfiguration comTipo(RedisCacheConfiguration base,
                                                   ObjectMapper mapper, JavaType tipo,
                                                   Duration ttl) {
        return base.entryTtl(ttl).serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(mapper, tipo)));
    }

    private static JavaType tipo(ObjectMapper mapper, Class<?> classe) {
        return mapper.getTypeFactory().constructType(classe);
    }

    private static JavaType lista(ObjectMapper mapper, Class<?> elemento) {
        return mapper.getTypeFactory().constructCollectionType(List.class, elemento);
    }

    private static JavaType pagina(ObjectMapper mapper, Class<?> elemento) {
        return mapper.getTypeFactory().constructParametricType(PageResult.class, elemento);
    }
}
