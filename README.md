# antlr4-lab
A client/server for trying out and learning about ANTLR

## Building and launching server

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

### Docker

I created a [Dockerfile](Dockerfile), although I'm not sure how useful it will be to people. This might be useful for deploying in the cloud later.

Here's how to build the docker file:

```bash
cd antlr4-lab
mvn clean package  # has to be built first as docker copies in the jar
docker build --tag antlr4-lab-docker .
```

and here's how to use the docker to launch:

```bash
docker run -p80:80 --rm antlr4-lab-docker
```

@kaby76 reports the following: Seems to work fine. But I had to do some things to get it to work on Windows/WSL2.

In Windows: Install Docker Desktop

In WSL2/Ubuntu:

```bash
sudo apt install docker.io
git clone https://github.com/antlr/antlr4-lab.git
cd antlr4-lab
git checkout docker
mvn clean; mvn install
docker build .
docker image ls # get image name.
docker run -d -p 127.0.0.1:80:80 -e BIND_ADDR=0.0.0.0:80 image-name-from-above
```

In Windows again, run Firefox and connect to 127.0.0.1.

It looks like docker binds port 80 to 0.0.0.0 by default (after installing `net-tools`, and doing `netstat -a`).
