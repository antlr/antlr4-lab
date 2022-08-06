# antlr4-lab
A client/server for trying out and learning about ANTLR

## Building server

Ubuntu with lab.antlr.org static IP

```bash
cd ~
sudo apt-get update
sudo apt install openjdk-11-jre-headless
sudo apt install maven
sudo apt install git
git clone https://github.com/antlr/antlr4-lab.git
cd antlr4-lab
mvn install

sudo mkdir /var/log/antlrlab
sudo chmod 777 /var/log/antlrlab
```

Launch!

```bash
sudo java -cp ~/.m2/repository/org/antlr/antlr4-lab/0.1-SNAPSHOT/antlr4-lab-0.1-SNAPSHOT-complete.jar org.antlr.v4.server.ANTLRHttpServer
```
