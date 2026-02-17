#!/bin/bash

# --- CONFIGURATION DES PORTS ---
MASTER_PORT=5001
SLAVE_PORTS=(5002 5003 5005 5004)
LIB_GSON="lib/gson-2.3.1.jar"
BIN_DIR="bin"

echo "--- 1. Nettoyage des processus (Kill sur ports $MASTER_PORT et ${SLAVE_PORTS[*]}) ---"
for port in $MASTER_PORT "${SLAVE_PORTS[@]}"
do
    PID=$(lsof -t -i:$port)
    if [ ! -z "$PID" ]; then
        echo "Fermeture du processus sur le port $port (PID: $PID)..."
        kill -9 $PID 2>/dev/null
    fi
done

# Nettoyage du dossier binaire
rm -rf $BIN_DIR
mkdir -p $BIN_DIR

echo "--- 2. Compilation ---"
# On compile tout le projet en incluant Gson
javac -cp ".:$LIB_GSON" -d $BIN_DIR main/json/*.java main/master/*.java slave1/*.java slave2/*.java slave3/*.java slave4/*.java main/client/*.java

if [ $? -ne 0 ]; then
    echo "❌ Erreur de compilation !"
    exit 1
fi

echo "--- 3. Lancement des Slaves ---"
# Lancement de chaque esclave avec son port et son dossier de stockage
java -cp "$BIN_DIR" slave1.SlaveServer 5002 slave1/storage &
java -cp "$BIN_DIR" slave2.SlaveServer 5003 slave2/storage &
java -cp "$BIN_DIR" slave3.SlaveServer 5005 slave3/storage &
java -cp "$BIN_DIR" slave4.SlaveServer 5004 slave4/storage &

sleep 2 # Temps d'attente pour l'ouverture des ServerSockets

echo "--- 4. Lancement du Master (Port $MASTER_PORT) ---"
java -cp "$BIN_DIR:$LIB_GSON" main.master.MasterServer &

echo "--- 5. Lancement automatique du Client ---"
java -cp "$BIN_DIR:$LIB_GSON" main.client.Client
sleep 1
echo "✅ Système prêt sur les ports configurés."
echo "------------------------------------------------"