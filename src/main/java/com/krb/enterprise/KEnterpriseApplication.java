package com.krb.enterprise;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KEnterpriseApplication {
	

	public static void main(String[] args) {

		 TimeZone.setDefault(
                TimeZone.getTimeZone("UTC")
        );

		SpringApplication.run(KEnterpriseApplication.class, args);
	}

}
