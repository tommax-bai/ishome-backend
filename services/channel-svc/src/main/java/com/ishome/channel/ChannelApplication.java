package com.ishome.channel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChannelApplication {

  public static void main(String[] args) {
    SpringApplication.run(ChannelApplication.class, args);
  }
}
