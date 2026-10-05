package io.github.imecuadorian.emitta;

import org.springframework.boot.SpringApplication;

public class TestEmittaApplication {

    static void main(String[] args) {
        SpringApplication.from(EmittaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
