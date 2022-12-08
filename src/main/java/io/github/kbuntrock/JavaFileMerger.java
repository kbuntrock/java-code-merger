package io.github.kbuntrock;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Kévin Buntrock
 */
public class JavaFileMerger {

	public static final Logger LOGGER = LoggerFactory.getLogger(WatchAndMergeCommand.class);

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH'h'mm:ss (dd/MM/yyyy)");

	private final Map<String, JavaFile> files = new HashMap<>();
	private final Map<String, JavaFile> mainFiles = new HashMap<>();

	private final String outputFilePath;

	private final JavaParser javaParser;

	public JavaFileMerger(final String outputFilePath) {
		final ParserConfiguration parserConfiguration = new ParserConfiguration();
		parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
		parserConfiguration.setCharacterEncoding(StandardCharsets.UTF_8);
		javaParser = new JavaParser(parserConfiguration);
		this.outputFilePath = outputFilePath;
	}

	public void addFile(final String path, final boolean process) {
		LOGGER.debug("Add java file {}", path);
		final JavaFile file = new JavaFile(Path.of(path));
		files.put(path, file);
		parseFile(file);
		if(process) {
			process();
		}
	}

	public void modifyFile(final String path) {
		LOGGER.debug("Modify java file {}", path);
		parseFile(files.get(path));
		process();
	}

	public void deleteFile(final String path) {
		LOGGER.debug("Deleted java file {}", path);
		files.remove(path);
		// Peut-être qu'on ne va rien supprimer dans "mainFiles" mais ce n'est pas grave.
		mainFiles.remove(path);
		process();
	}

	private void parseFile(final JavaFile javaFile) {
		try {
			final ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile.getPath());
			if(!parseResult.isSuccessful()) {
				LOGGER.error("Java file parsing error", new ParseProblemException(parseResult.getProblems()));
			} else {

				if(parseResult.getResult().isPresent()) {
					final CompilationUnit compilationUnit = parseResult.getResult().get();
					final JavaCodeVisitor visitor = new JavaCodeVisitor();
					javaFile.setMain(false);
					javaFile.setPackageName(null);
					javaFile.setCompilationUnit(compilationUnit);
					visitor.visit(compilationUnit, javaFile);

				}

			}
		} catch(final IOException ex) {
			LOGGER.error("Java file io error", ex);
		}
	}

	public void process() {
		final Optional<JavaFile> mainClass = findMainClasses();

		if(mainClass.isPresent()) {

			LOGGER.info("Writing file {} ", outputFilePath);
			final JavaFile main = mainClass.get();
			try(final BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
				for(final String imp : findImports()) {
					writer.write("import " + imp + ";\n");
				}
				writer.write("\n");

				final Optional<String> mainClassContent = extractClassContent(main);
				if(mainClassContent.isPresent()) {
					writer.write(mainClassContent.get());
				}

				for(final JavaFile javaFile : files.values()) {
					if(!javaFile.isMain()) {

						final Optional<String> classContent = extractClassContent(javaFile);
						if(classContent.isPresent()) {
							writer.write("\n\n");
							writer.write(classContent.get());
							writer.write("\n");
						}
					}
				}
				writer.write("\n\n");
				writer.write("// Last genereted at " + LocalDateTime.now().format(DATE_TIME_FORMATTER));

			} catch(final IOException e) {
				throw new RuntimeException(e);
			}


		}

	}

	private Optional<String> extractClassContent(final JavaFile javaFile) {
		final List<ClassOrInterfaceDeclaration> nodes = javaFile.getCompilationUnit().getParentNodeForChildren()
			.findAll(ClassOrInterfaceDeclaration.class);
		if(!nodes.isEmpty()) {
			String content = nodes.get(0).toString();
			content = content.substring(content.indexOf("public") + 7, content.length());
			return Optional.of(content);
		}
		return Optional.empty();
	}

	private Optional<String> findShortestPackage() {
		String shortest = null;
		for(final JavaFile javaFile : files.values()) {
			if(javaFile.getPackageName() != null && (shortest == null || shortest.length() > javaFile.getPackageName().length())) {
				shortest = javaFile.getPackageName();
			}
		}
		return Optional.ofNullable(shortest);
	}

	private Optional<JavaFile> findMainClasses() {
		JavaFile mainClass = null;
		for(final JavaFile javaFile : files.values()) {
			if(javaFile.isMain()) {
				if(mainClass == null) {
					mainClass = javaFile;
				} else {
					LOGGER.warn("Two main classes found : {} and {}", mainClass.getPath(), javaFile.getPath());
					return Optional.empty();
				}

			}
		}
		return Optional.ofNullable(mainClass);
	}

	private Set<String> findImports() {
		final Set<String> imports = new HashSet<>();
		for(final JavaFile file : files.values()) {
			imports.addAll(file.getCompilationUnit().getImports().stream().map(x -> x.getNameAsString()).collect(Collectors.toList()));
		}
		final Set<String> toRemove = findImportToRemove();
		imports.removeAll(toRemove);
		return imports;
	}

	private Set<String> findImportToRemove() {
		final Set<String> importToRemove = new HashSet<>();
		for(final JavaFile file : files.values()) {
			importToRemove.add(file.getImportToRemove());
		}
		return importToRemove;

	}

	private class JavaCodeVisitor extends VoidVisitorAdapter<JavaFile> {

		@Override
		public void visit(final PackageDeclaration packageDeclaration, final JavaFile javaFile) {
			super.visit(packageDeclaration, javaFile);
			javaFile.setPackageName(packageDeclaration.getNameAsString());
		}

		@Override
		public void visit(final MethodDeclaration methodDeclaration, final JavaFile javaFile) {
			super.visit(methodDeclaration, javaFile);

			if(methodDeclaration.getName().getIdentifier().equals("main") && methodDeclaration.hasModifier(Keyword.STATIC)
				&& methodDeclaration.hasModifier(Keyword.PUBLIC)) {

				javaFile.setMain(true);
			}
		}

		@Override
		public void visit(final ClassOrInterfaceDeclaration classOrInterfaceDeclaration, final JavaFile javaFile) {
			super.visit(classOrInterfaceDeclaration, javaFile);
			javaFile.setClassName(classOrInterfaceDeclaration.getNameAsString());
		}
	}
}
