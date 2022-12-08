package io.github.kbuntrock;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;

/**
 * @author Kévin Buntrock
 */
public class JavaFile {

	private final Path path;
	private String packageName;
	private String className;
	private boolean main;

	private CompilationUnit compilationUnit;

	public JavaFile(final Path path) {
		this.path = path;
	}

	public Path getPath() {
		return path;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(final String packageName) {
		this.packageName = packageName;
	}

	public boolean isMain() {
		return main;
	}

	public void setMain(final boolean main) {
		this.main = main;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(final String className) {
		this.className = className;
	}

	public String getImportToRemove() {
		return packageName + "." + className;
	}

	public CompilationUnit getCompilationUnit() {
		return compilationUnit;
	}

	public void setCompilationUnit(final CompilationUnit compilationUnit) {
		this.compilationUnit = compilationUnit;
	}
}
