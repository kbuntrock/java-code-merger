package io.github.kbuntrock;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App implements CommandLineRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(final String[] args) {
		SpringApplication.run(App.class, args);
	}

	@Override
	public void run(final String... args) throws Exception {
		LOGGER.warn("Started watching");
		if(args.length < 1) {
			return;
		}
		final String dirToWatch = args[0];
		final WatchService watchService = FileSystems.getDefault().newWatchService();
		final Path path = Paths.get(dirToWatch);
		path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
			StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

		WatchKey key;
		while((key = watchService.take()) != null) {
			for(final WatchEvent<?> event : key.pollEvents()) {
				System.out.println(
					"Event kind:" + event.kind()
						+ ". File affected: " + event.context() + ".");
			}
			key.reset();
		}
	}
}
