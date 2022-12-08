package io.github.kbuntrock;

import ch.qos.logback.classic.Level;
import com.sun.nio.file.ExtendedWatchEventModifier;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * @author Kévin Buntrock
 */
@Component
@Command(name = "watchAndMergeCommand")
public class WatchAndMergeCommand implements Callable<Integer> {

	private static final Logger LOGGER = LoggerFactory.getLogger(WatchAndMergeCommand.class);

	@Option(names = {"-d", "--debug"}, description = "Turn on debug logs", required = false)
	boolean debug;

	@Option(names = {"-o", "--output"}, description = "Output file", required = true)
	String outputFilePath;

	@Parameters(description = "Directory to watch")
	String dirPath;

	private final Map<WatchKey, Path> keyPathMap = new HashMap<>();

	private final Set<String> watchedDirectories = new HashSet<>();
	private final Set<String> watchedFiles = new HashSet<>();

	private JavaFileMerger javaFileMerger;

	@Override
	public Integer call() throws Exception {

		try {
			if(debug) {
				((ch.qos.logback.classic.Logger) LOGGER).setLevel(Level.DEBUG);
				((ch.qos.logback.classic.Logger) JavaFileMerger.LOGGER).setLevel(Level.DEBUG);
			}
			javaFileMerger = new JavaFileMerger(outputFilePath);
			watch();
		} catch(final Exception ex) {
			LOGGER.error("Watcher error", ex);
			return -1;
		}
		return 0;
	}

	private void watch() throws IOException, InterruptedException {
		LOGGER.info("Output defined on : " + outputFilePath);
		try(final WatchService watchService = FileSystems.getDefault().newWatchService()) {
			final Path path = Paths.get(dirPath);
			LOGGER.info("Watching folder {}", path);
			// ExtendedWatchEventModifier ne marche peut-être pas sur linux. Pour gérer les changements dans une arborescence, il faudrait utiliser le
			// Files.walkFileTree pour parcourir tous les sous-dossiers et s'enregistrer dessus.
			// Mais cette technique sous windows met des locks sur les dossiers, ce qui empêche le renommage.
			// En l'état, ce programme est donc uniquement compatible avec Windows.
			final WatchKey globalKey = path.register(watchService, new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY}, ExtendedWatchEventModifier.FILE_TREE);
			keyPathMap.put(globalKey, path);

			registerAllSubfiles(path);
			LOGGER.debug("{} files are watched at initilization.", watchedFiles.size());
			javaFileMerger.process();

			WatchKey key;
			while((key = watchService.take()) != null) {
				for(final WatchEvent<?> event : key.pollEvents()) {
					LOGGER.trace("Event kind : {} - Key Valid? {} - File affected : {}", event.kind(), key.isValid(), event.context());
					processEvent(event, key, watchService);

				}
				if(!key.reset()) {
					LOGGER.info("Unregister path {}", keyPathMap.get(key).toAbsolutePath());
					keyPathMap.remove(key);
				}
				if(keyPathMap.isEmpty()) {
					break;
				}
			}
		}
		LOGGER.info("My watch is ended.");
	}

	private void processEvent(final WatchEvent<?> event, final WatchKey key, final WatchService watchService) throws IOException {
		// Le path résolu par rapport au dossier observé
		final Path realPath = keyPathMap.get(key).resolve((Path) event.context());
		switch(event.kind().name()) {
			case "ENTRY_DELETE":
				processDeleteEvent(key, realPath);
				break;
			case "ENTRY_CREATE":
				processCreateEvent(realPath, watchService);
				break;
			case "ENTRY_MODIFY":
				processModifyEvent(realPath);
				break;
			default:
				// nothing to do
				break;
		}
	}

	private void processDeleteEvent(final WatchKey key, final Path path) throws IOException {

		final boolean wasFile = watchedFiles.remove(path.toString());
		LOGGER.trace("Deleted file {}, wasFile? {}", path, wasFile);
		if(wasFile) {
			LOGGER.trace("Deleted file {} - is java? {}", path, isJavaFile(path.toString()));
			if(isJavaFile(path.toString())) {
				javaFileMerger.deleteFile(path.toString());
			}
		} else {
			// C'est un dossier (très certainement).
			// On supprime tous les sous-dossiers correspondant à ce path
			for(final String s : watchedFiles.toArray(new String[0])) {
				if(s.startsWith(path.toString())) {
					watchedFiles.remove(s);
					LOGGER.trace("Deleted file {} - is java? {}", s, isJavaFile(path.toString()));
					if(isJavaFile(s)) {
						javaFileMerger.deleteFile(s);
					}
				}
			}

		}

	}

	private void processCreateEvent(final Path path, final WatchService watchService) throws IOException {

		if(!Files.isDirectory(path)) {
			LOGGER.trace("Added file {} - is java? {}", path, isJavaFile(path.toString()));
			watchedFiles.add(path.toString());
			if(isJavaFile(path.toString())) {
				javaFileMerger.addFile(path.toString(), true);
			}
		} else {
			registerAllSubfiles(path);
			javaFileMerger.process();
		}
	}

	private void processModifyEvent(final Path path) {
		if(watchedFiles.contains(path.toString())) {
			LOGGER.trace("Modified file {} - is java? {}", path, isJavaFile(path.toString()));
			if(isJavaFile(path.toString())) {
				javaFileMerger.modifyFile(path.toString());
			}
		}

	}

	private static boolean isJavaFile(final String path) {
		return path.endsWith(".java");
	}


	private void registerAllSubfiles(final Path start) throws IOException {
		Files.walkFileTree(start, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) {
				LOGGER.trace("Added file {} - is java? {}", path, isJavaFile(path.toString()));
				watchedFiles.add(path.toString());
				if(isJavaFile(path.toString())) {
					javaFileMerger.addFile(path.toString(), false);
				}
				return FileVisitResult.CONTINUE;
			}
		});

	}

}