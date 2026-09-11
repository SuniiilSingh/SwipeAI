package com.match.SwipeAI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication
public class SwipeAiApplication {

	public static void main(String[] args) {
		loadDotEnv();
		SpringApplication.run(SwipeAiApplication.class, args);
	}

	private static void loadDotEnv() {
		File envFile = new File(".env");
		if (!envFile.exists()) {
			envFile = new File("../.env");
		}
		if (envFile.exists()) {
			try {
				List<String> lines = Files.readAllLines(envFile.toPath());
				for (String line : lines) {
					String trimmed = line.trim();
					if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
					int eqIdx = trimmed.indexOf('=');
					if (eqIdx > 0) {
						String key = trimmed.substring(0, eqIdx).trim();
						String val = trimmed.substring(eqIdx + 1).trim();
						if (System.getProperty(key) == null && System.getenv(key) == null) {
							System.setProperty(key, val);
						}
					}
				}
			} catch (Exception ignored) {
			}
		}
	}
}
