package com.barinventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class LiqBottleShopV01Application {

	public static void main(String[] args) {
		SpringApplication.run(LiqBottleShopV01Application.class, args);
		  // ✅ Must match SecurityConfig strength = 10
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String raw = "bar123";
        String hash = encoder.encode(raw);
        System.out.println("=================================");
        System.out.println("Raw password : " + raw);
        System.out.println("Encoded hash : " + hash);
        System.out.println("=================================");
	}

}
