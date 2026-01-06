default: versioncheck

clean:
	./gradlew clean

build: clean
	./gradlew build

versioncheck:
	./gradlew dependencyUpdates

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.2.1 --distribution-type=bin
