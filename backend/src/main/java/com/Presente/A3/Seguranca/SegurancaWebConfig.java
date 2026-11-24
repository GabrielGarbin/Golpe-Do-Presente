package com.Presente.A3.Seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Liga o porteiro ({@link AutenticacaoInterceptor}) e configura o CORS.
 *
 * <p><b>CORS</b>: o navegador só deixa o front (ex.: localhost:5500) chamar a API
 * (ex.: localhost:8081) se a API disser que aceita aquela origem. Como o front
 * agora envia o cabeçalho {@code Authorization}, ele precisa estar na lista de
 * cabeçalhos permitidos. As origens vêm de {@code app.cors.origens} (variável
 * {@code CORS_ORIGENS}); em produção, informe o endereço real do front em vez
 * de {@code *}. Não se usa cookie, então não há {@code allowCredentials}.</p>
 *
 * <p>Se o projeto tiver outra classe de CORS, mantenha só uma delas.</p>
 */
@Configuration
public class SegurancaWebConfig implements WebMvcConfigurer {

    private final AutenticacaoInterceptor interceptor;
    private final String[] origens;

    public SegurancaWebConfig(AutenticacaoInterceptor interceptor,
                              @Value("${app.cors.origens:}") String origens) {
        this.interceptor = interceptor;
        this.origens = Arrays.stream(origens.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // "/error" fica de fora para que erros internos (404 etc.) não virem 401.
        registry.addInterceptor(interceptor).addPathPatterns("/**").excludePathPatterns("/error");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (origens.length == 0) {
            return; // sem origens configuradas: só mesma origem (ex.: atrás do proxy do nginx)
        }
        registry.addMapping("/**")
                .allowedOrigins(origens)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}
