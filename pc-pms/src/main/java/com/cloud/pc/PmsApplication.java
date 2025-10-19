/*
 * Copyright (c) 2025 Yangagile. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloud.pc;

import com.cloud.pc.config.Envs;
import okhttp3.OkHttpClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.plugins.WebFluxRequestHandlerProvider;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@EnableSwagger2
@EnableWebMvc
@MapperScan("com.cloud.pc.mapper")
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PmsApplication {

    public static void main(String[] args) {

        System.setProperty("LOG_HOME", Envs.logDir);
        System.setProperty("APP_NAME", "pms");

        SpringApplication.run(PmsApplication.class, args);

        System.out.println("\n========================================");
        System.out.printf("🚀 PMS is running at port: %d \n", Envs.port);
        System.out.println("========================================");

    }

    @Bean
    public ClientHttpRequestFactory newClientHttpRequestFactory(OkHttpClient okHttpClient) {
        return new OkHttp3ClientHttpRequestFactory(okHttpClient);
    }

    @Bean
    public OkHttpClient okHttpClient(@Value("${okhttp.socket.readTimeoutInMillis:10000}") int readTimeoutInMillis,
                                     @Value("${okhttp.socket.writeTimeoutInMillis:10000}") int writeTimeoutInMillis,
                                     @Value("${okhttp.socket.connectTimeoutInMillis:10000}") int connectTimeoutInMillis,
                                     @Value("${okhttp.callTimeoutInMillis:0}") int callTimeoutInMillis) {
        return new OkHttpClient().newBuilder()
                .connectTimeout(connectTimeoutInMillis, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutInMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutInMillis, TimeUnit.MILLISECONDS)
                .callTimeout(callTimeoutInMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    @Bean
    public RestTemplate newRestTemplate(ClientHttpRequestFactory factory) {
        return new RestTemplate(factory);
    }

    @Bean
    public BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof WebMvcRequestHandlerProvider || bean instanceof WebFluxRequestHandlerProvider) {
                    this.customizeSpringfoxHandlerMappings(this.getHandlerMappings(bean));
                }
                return bean;
            }

            private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(List<T> mappings) {
                List<T> copy = mappings.stream()
                        .filter(mapping -> mapping.getPatternParser() == null)
                        .collect(Collectors.toList());
                mappings.clear();
                mappings.addAll(copy);
            }

            @SuppressWarnings("unchecked")
            private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
                try {
                    Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
                    field.setAccessible(true);
                    return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    @Bean
    public Docket docket() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.cloud.pc.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("Parallel Cache Meta Server（PMS）")
                .version("0.1.0")
                .contact(new Contact("Yang Yun", "", "28581556@qq.com"))
                .description("Parallel Cache Meta Server（PMS) API Description")
                .build();
    }
}
