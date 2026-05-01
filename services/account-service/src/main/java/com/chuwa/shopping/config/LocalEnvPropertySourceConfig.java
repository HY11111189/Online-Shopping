package com.chuwa.shopping.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = {"file:../.env.local", "file:.env.local"}, ignoreResourceNotFound = true)
public class LocalEnvPropertySourceConfig {
}
