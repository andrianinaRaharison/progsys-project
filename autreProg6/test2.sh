#!/bin/bash

# --- CONFIGURATION ---
MASTER_PORT=5001
SLAVE_PORTS=(5002 5003 5004 5005)
LIB_GSON="lib/gson-2.3.1.jar"
BIN_DIR="bin"

echo "--- 1. Nettoyage des processus existants ---"
for port in $MASTER_PORT "${SLAVE_PORTS[@]}"
do
    PID=$(lsof -t -i:$port)
    if [ ! -z "$PID" ]; then
        echo "Fermeture du processus sur le port $port (PID: $PID)..."
        kill -9 $PID 2>/dev/null
    fi
done

rm -rf $BIN_DIR
mkdir -p $BIN_DIR

echo "--- 2. Compilation du projet ---"
javac -cp ".:$LIB_GSON" -d $BIN_DIR \
    main/json/*.java \
    main/master/*.java \
    slave*/SlaveServer.java \
    main/client/*.java

if [ $? -ne 0 ]; then
    echo "❌ Erreur de compilation !"
    exit 1
fi

echo "--- 3. Lancement des Slaves ---"
java -cp "$BIN_DIR" slave1.SlaveServer 5002 slave1/storage &
java -cp "$BIN_DIR" slave2.SlaveServer 5003 slave2/storage &
java -cp "$BIN_DIR" slave3.SlaveServer 5004 slave3/storage &
java -cp "$BIN_DIR" slave4.SlaveServer 5005 slave4/storage &

sleep 2 # Attente pour que les Slaves soient prêts

echo "--- 4. Lancement du Master ---"
java -cp "$BIN_DIR:$LIB_GSON" main.master.MasterServer &

sleep 2 # Attente pour que le Master soit prêt

echo "--- 5. Test UPLOAD avec Client ---"
java -cp "$BIN_DIR" main.client.Client

sleep 2

echo "--- 6. Test DOWNLOAD avec ClientDownload ---"
java -cp "$BIN_DIR" main.client.ClientDownload

echo "✅ Test complet terminé."
