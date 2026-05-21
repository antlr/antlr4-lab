# antlr4-lab
A client/server for trying out and learning about ANTLR

## Building and launching server locally

### Prerequisites
- Java 11+ (tested with Java 21)
- Maven 3.8+
- [Optional] Ghostscript (ps2pdf) and pdf2svg — for parse tree SVG rendering.
  If missing, the server auto-installs them via Homebrew (macOS) or apt-get
  (Linux) on first use, or shows installation instructions.

```bash
git clone https://github.com/antlr/antlr4-lab.git
cd antlr4-lab
mvn install
java -jar target/antlr4-lab-0.4-SNAPSHOT-complete.jar
```

Then visit [http://localhost:8080/index.html](http://localhost:8080/index.html).

### Configuration
The server can be configured with system properties:

| Property | Default | Description |
|---|---|---|
| `antlrlab.port` | `8080` | HTTP port |
| `antlrlab.log.dir` | `/tmp/antlrlab` | Log directory |
| `antlrlab.share.dir` | `/tmp/antlrlab/share` | Share storage directory |

Example:
```bash
java -Dantlrlab.port=9090 -Dantlrlab.log.dir=/var/log/antlrlab \
  -jar target/antlr4-lab-0.4-SNAPSHOT-complete.jar
```

### Running tests
```bash
mvn test
```

### Ubuntu / production server

```bash
cd ~
sudo apt-get update
sudo apt install -y openjdk-11-jre
sudo apt install -y maven
sudo apt install -y git
sudo apt install -y ghostscript # gets ps2pdf
sudo apt install -y pdf2svg
git clone https://github.com/antlr/antlr4-lab.git
cd antlr4-lab
mvn install

sudo mkdir /var/log/antlrlab
sudo chmod 777 /var/log/antlrlab
sudo java -Dantlrlab.port=80 -Dantlrlab.log.dir=/var/log/antlrlab \
  -jar target/antlr4-lab-0.4-SNAPSHOT-complete.jar
```

Visit [http://localhost/index.html](http://localhost/index.html) to run the client.

### Docker

```bash
cd antlr4-lab
mvn clean package
docker build --tag antlr4-lab-docker .
docker run -p80:80 --rm antlr4-lab-docker
```

## Local modifications (Michele Fadda, 2026)

This fork includes the following changes to make the project run locally
without external cloud services or root privileges:

| Issue | Fix |
|---|---|
| Port 80 (requires root) | Changed to 8080, configurable via `-Dantlrlab.port` |
| `/var/log/antlrlab` (requires root) | Changed to `/tmp/antlrlab`, configurable via `-Dantlrlab.log.dir` |
| `us.parr:parrtlib` SNAPSHOT not in Maven Central | `execInDir()` and `StreamVacuum` inlined into `GrammarProcessor.java` |
| Google Cloud Storage dependency in ShareServlet | Replaced with local filesystem storage in `/tmp/antlrlab/share/` |
| `pdf2svg`/`ps2pdf` missing → exception | Auto-installs via Homebrew (macOS) or apt-get (Linux); shows guidance on Windows |
| `execInDir` couldn't find brew-installed binaries | `ProcessBuilder` now appends `/opt/homebrew/bin` to PATH |
| Deprecated `StringBufferInputStream` | Replaced with `CharStreams.fromString()` |
| JUnit 5 tests | Added 14 unit tests covering `interp`, `execInDir`, `commandExists`, `escXml`, `makeErrorSVG`, and `ensureSVGDeps` |
