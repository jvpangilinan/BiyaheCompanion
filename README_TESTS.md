# Unit tests (optional)

1) Paste the CONTENTS of this folder into the project root (adds app/src/test/...).
2) In app/build.gradle.kts, inside `dependencies { ... }`, add ONE line:

       testImplementation("junit:junit:4.13.2")

3) Sync Gradle. Run the tests: right-click the folder
   app/src/test/java/com/biyahe/companion/scoring  ->  Run 'Tests in scoring'.
   (Needs no phone or emulator.)
