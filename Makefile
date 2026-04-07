SHELL := /bin/sh

MVNW := ./mvnw

.PHONY: help clean version bump-version package run release release-copilot-commit

help:
	@echo "Targets:"
	@echo "  make clean         - remove stale release runner jars from target"
	@echo "  make version       - print current project version from pom.xml"
	@echo "  make bump-version  - bump patch version (x.y.z -> x.y.(z+1))"
	@echo "  make package       - build runner jar (skip tests)"
	@echo "  make run           - run the built runner jar for current version"
	@echo "  make release                 - bump version, package, and run"
	@echo "  make release-copilot-commit  - stage all changes and use gh copilot to commit"

clean:
	@find target -maxdepth 1 -type f -name 'pdhd-*-runner.jar' -delete
	@echo "Removed stale release jars from target/"

version:
	@$(MVNW) -q -DforceStdout help:evaluate -Dexpression=project.version

bump-version:
	@set -e; \
	CURRENT_VERSION="$$($(MVNW) -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
	if ! printf '%s' "$$CURRENT_VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$$'; then \
		echo "Cannot auto-bump non-semver version: $$CURRENT_VERSION"; \
		exit 1; \
	fi; \
	NEW_VERSION="$$(( $$(printf '%s' "$$CURRENT_VERSION" | cut -d. -f1) )).$$(( $$(printf '%s' "$$CURRENT_VERSION" | cut -d. -f2) )).$$(( $$(printf '%s' "$$CURRENT_VERSION" | cut -d. -f3) + 1 ))"; \
	$(MVNW) -q versions:set -DnewVersion="$$NEW_VERSION" -DgenerateBackupPoms=false; \
	echo "Version bumped: $$CURRENT_VERSION -> $$NEW_VERSION"

package:
	@$(MVNW) -q -DskipTests package

debug-package:
	@$(MVNW) package

run:
	@set -e; \
	VERSION="$$($(MVNW) -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
	JAR_PATH="target/pdhd-$$VERSION-runner.jar"; \
	if [ ! -f "$$JAR_PATH" ]; then \
		echo "Built jar not found: $$JAR_PATH"; \
		echo "Run 'make package' first."; \
		exit 1; \
	fi; \
	echo "Running $$JAR_PATH"; \
	java -jar "$$JAR_PATH"

release: bump-version package run
debug: package run

release-copilot-commit:
	@git add -A
	@DIFF="$$(git diff --cached --stat)"; \
	if [ -z "$$DIFF" ]; then \
		echo "Nothing staged to commit."; \
		exit 0; \
	fi; \
	echo "Staged changes:"; \
	echo "$$DIFF"; \
	gh copilot suggest -t git "Write a conventional commit message for these staged changes: $$DIFF"