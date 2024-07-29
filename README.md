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
cd ~/antlr4-lab
sudo nohup java -cp ~/.m2/repository/org/antlr/antlr4-lab/0.4-SNAPSHOT/antlr4-lab-0.4-SNAPSHOT-complete.jar org.antlr.v4.server.ANTLRHttpServer
```

Or to restart if it fails, do:

```bash
while true
do
  sudo java -cp ~/.m2/repository/org/antlr/antlr4-lab/0.4-SNAPSHOT/antlr4-lab-0.4-SNAPSHOT-complete.jar org.antlr.v4.server.ANTLRHttpServer
  sudo cp /var/log/antlrlab/antlrlab.log /var/log/antlrlab/antlrlab-died.log
  sleep 1
done
```

which I've put into `~/antlr4-lab/launch.sh`:

```bash
nohup launch.sh &
```

If you are running the server locally on your box, visit [http://localhost/index.html](http://localhost/index.html) to run the client.

### Docker

Docker builds both jar and the image:
```bash
docker build -t antlr4-lab .
```

To run docker image:
```bash
docker run -p 8080:80 antlr4-lab
```

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
