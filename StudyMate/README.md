# StudyMate (Phase 1 + multiple reviewers + saved sessions)

Offline flashcard study app. JavaFX 21, Maven, PDFBox 3, SQLite. Not AI: PDF questions come from simple rules
and the correct answers are ALWAYS entered/confirmed by you.

## 1. Install JDK 21 (once)
1. IntelliJ IDEA -> File -> Project Structure -> SDKs -> "+" -> Download JDK -> Version 21 (Temurin or Oracle OpenJDK) -> Download.
2. File -> Project Structure -> Project -> SDK: 21, Language level: 21.

## 2. Open the project
1. Unzip StudyMate.zip (for example to C:\Projects\StudyMate).
2. IntelliJ: File -> Open -> choose the StudyMate folder (the one containing pom.xml) -> Open as Project -> Trust.
3. Wait for Maven to import (bottom-right progress bar). If it does not start: right-click pom.xml -> Maven -> Reload project.
   (Internet is needed only for this first download.)

## 3. Run
Option A (IntelliJ): open the Maven tool window (right side) -> StudyMate -> Plugins -> javafx -> javafx:run (double-click).
Option B (terminal at the bottom of IntelliJ):  mvn clean javafx:run
If "mvn" is not found, use the Maven tool window or install Maven and add it to PATH.

## 4. Try it (use sample/sample-biology.pdf)
1. Dashboard -> Import PDF -> choose sample/sample-biology.pdf.
2. Preview window: untick bad questions, optionally edit text, type a new reviewer name (or pick one),
   then "Import Selected Questions". (Leave "Pre-fill answers" unticked to start with empty answers.)
3. Question Editor opens. Questions with a warning sign have no answer. Click one, TYPE the correct answer, Save.
4. Study Mode: choose the reviewer. If any answer is empty, Start is refused with an explanation.
5. Type an answer, Reveal Answer, compare, click "I Was Correct" or "I Was Incorrect".
   Each question can be marked only once. Previous/Next let you move around.
6. Final screen: percentage, correct/incorrect, list of missed questions, Restart button. Result is saved.
7. Close the app, run it again: reviewers, questions and the Dashboard history are still there.

Data file: C:\Users\<YourName>\StudyMate\studymate.db  (delete it to reset everything).

## Troubleshooting
- "JavaFX runtime components are missing": run with mvn javafx:run (not the green Run arrow on StudyMateApp).
- "release version 21 not supported" / "invalid target release": project SDK is not 21 (see step 1). Also File -> Settings ->
  Build Tools -> Maven -> Runtime -> JDK for importer = 21.
- Dependencies in red / "Cannot resolve": check internet, then Maven tool window -> Reload All Maven Projects.
- Error mentioning "Loader" or PDFBox: make sure pom.xml still says pdfbox 3.0.3 (the 2.x versions have no Loader class).
- "No suitable driver" / SQLite error: reload Maven; keep org.xerial:sqlite-jdbc in pom.xml.
- "No readable text": the PDF is scanned images. OCR is not included. Use a text-based PDF (you can select text in it).
- "database is locked": close any other StudyMate window or DB tool using studymate.db.
