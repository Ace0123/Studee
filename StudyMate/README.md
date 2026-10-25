# StudyMate 1.1

Offline study app (JavaFX 21, Maven, PDFBox 3, SQLite). Import a PDF reviewer, edit the questions, then study with
**Multiple Choice**, **Fill in the Blank** or **Flashcards**. No internet or AI service is used after Maven has
downloaded the libraries.

## What is new in 1.1
- Quiz Setup screen: choose reviewer, quiz type, number of questions and shuffle.
- Three study modes (see "Question types" below) and a Results screen with Retry / Return to Dashboard.
- New screens: Study History and About. Redesigned Dashboard, reviewer cards and Question Editor (search + filter).
- Your existing reviewers, questions and results are kept. The database is upgraded automatically the first time
  you start 1.1, and a backup copy of the old file is saved as `studymate-backup-before-v1.1.db` in the same folder.

## Run it in IntelliJ IDEA (Windows)
1. Install JDK 21: File -> Project Structure -> SDKs -> "+" -> Download JDK -> version 21 (Temurin) -> Download.
   Then File -> Project Structure -> Project -> SDK = 21, Language level = 21.
2. Unzip StudyMate-Updated.zip. In IntelliJ: File -> Open -> choose the StudyMate folder (the one with pom.xml) -> Open as Project.
   If you already have the old project open, close it and open this folder instead (or copy the new files over it).
3. Wait for Maven to import (needs internet the first time). If nothing happens: right-click pom.xml -> Maven -> Reload project.
4. Run: open the Maven tool window -> StudyMate -> Plugins -> javafx -> javafx:run (double-click),
   or type in the IntelliJ terminal:  `mvn clean javafx:run`

Data file: `C:\Users\<YourName>\StudyMate\studymate.db`  (copy this file to back up your work).

## Question types
Each question has a type, set in the Question Editor ("Question type").
| Type | You enter | Used by |
|---|---|---|
| Multiple Choice | question, 4 options, the correct option (click its circle) | Multiple Choice mode, Flashcards mode |
| Fill in the Blank | sentence with `_____`, correct answer, optional other accepted answers (one per line) | Fill in the Blank mode, Flashcards mode |
| Flashcard | question, correct answer | Flashcards mode |

Quiz Setup only offers compatible questions: Multiple Choice and Fill in the Blank modes use questions of that type;
Flashcards mode can use every question. A session cannot start while any question it would use is incomplete
(no correct answer, or a multiple choice question without four different options and a correct one).

## How scoring works
- Every question can be scored only once per session.
- Multiple Choice: Submit, then the correct option is shown. Right or wrong is scored automatically.
- Fill in the Blank: capital letters and extra spaces are ignored, and alternative accepted answers count.
  If your answer is marked wrong but you think it is right (different wording), click "Count it as correct" -
  that changes the result of the same question, it is never counted twice. "I Don't Know" scores the question as incorrect.
- Flashcards: Reveal Answer, compare, then click "I Was Correct" or "I Was Incorrect".
- Results (percentage, correct/incorrect, missed questions with their answers) are saved to Study History.

## Try it
1. Dashboard -> Import PDF -> `sample/sample-biology.pdf` (any text-based PDF works; scanned PDFs need OCR, not included).
   Questions made from a PDF use simple rules, NOT AI: they are drafts. Sentence blanks become Fill in the Blank
   questions, "X is Y" sentences become Flashcards. Always check them and type the correct answers yourself.
2. Question Editor: create a Multiple Choice question (type -> Multiple Choice, four options, click the circle of the correct one, Save).
3. Study Mode: pick the reviewer and quiz type, Start Studying.
4. Close and reopen the app: everything is still there.

## Troubleshooting
- "JavaFX runtime components are missing": run with `mvn javafx:run` (not the green Run arrow on StudyMateApp).
- "release version 21 not supported" / "invalid target release": the project SDK is not 21 (step 1). Also check
  File -> Settings -> Build Tools -> Maven -> Importing -> JDK for importer = 21.
- Red dependencies / "Cannot resolve": check internet, then Maven tool window -> Reload All Maven Projects.
- PDFBox error mentioning `Loader`: keep pdfbox 3.0.3 in pom.xml (2.x has no Loader class).
- SQLite error / "No suitable driver": reload Maven and keep org.xerial:sqlite-jdbc in pom.xml.
- "No readable text": the PDF is a scan (pictures). Use a PDF where you can select the text.
- "database is locked": close other programs using studymate.db.
- To go back to a clean start, close the app and delete the folder `C:\Users\<YourName>\StudyMate`.
