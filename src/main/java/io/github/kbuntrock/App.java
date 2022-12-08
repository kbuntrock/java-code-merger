package io.github.kbuntrock;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootApplication
public class App implements CommandLineRunner, ExitCodeGenerator {

	private final IFactory factory;
	private final WatchAndMergeCommand command;
	private int exitCode;

	public static void main(final String[] args) {
		SpringApplication.run(App.class, args);
	}

	public App(final IFactory factory, final WatchAndMergeCommand command) {
		this.factory = factory;
		this.command = command;
	}

	@Override
	public void run(final String... args) throws Exception {
		exitCode = new CommandLine(command, factory).execute(args);
	}

	@Override
	public int getExitCode() {
		return 0;
	}
}
