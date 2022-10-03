# antlr4-lab
A client/server for trying out and learning about ANTLR

## Building server

Ubuntu with lab.antlr.org static IP

```bash
cd ~
sudo apt-get update
sudo apt install -y openjdk-11-jre
sudo apt install -y maven
sudo apt install -y git
sudo apt install -y ghostscript # gets ps2pdf
sudo apt install -y texlive-extra-utils # gets pdfcrop
sudo apt install -y pdf2svg
git clone https://github.com/antlr/antlr4-lab.git
cd antlr4-lab
mvn install

sudo mkdir /var/log/antlrlab
sudo chmod 777 /var/log/antlrlab
ssh-keygen -t ed25519 -C 'parrt@...'  # add key to github
git config --global user.email "parrt@..."
git config --global user.name "Terence Parr"
```

Launch!

```bash
sudo nohup java -cp ~/.m2/repository/org/antlr/antlr4-lab/0.1-SNAPSHOT/antlr4-lab-0.1-SNAPSHOT-complete.jar org.antlr.v4.server.ANTLRHttpServer
```

If you are running the server locally on your box, visit [http://localhost/index.html](http://localhost/index.html) to run the client.
