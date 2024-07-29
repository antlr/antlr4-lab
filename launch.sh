while true
do
  sudo java -cp ~/.m2/repository/org/antlr/antlr4-lab/0.4-SNAPSHOT/antlr4-lab-0.4-SNAPSHOT-complete.jar org.antlr.v4.server.ANTLRHttpServer
  sudo cp /var/log/antlrlab/antlrlab.log /var/log/antlrlab/antlrlab-died.log
  sleep 1
done
