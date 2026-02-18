#!/bin/bash

MASTER_PORT=5001
SLAVE_PORTS=(5002 5003 5004 5005)
LIB_GSON="lib/gson-2.3.1.jar"
BIN_DIR="bin"

# Nettoyer les processus existants
echo "--- Nettoyage des processus existants ---"
for port in $MASTER_PORT "${SLAVE_PORTS[@]}"; do
    PID=$(lsof -t -i:$port)
    if [ ! -z "$PID" ]; then
        echo "Fermeture du processus sur le port $port (PID: $PID)..."
        kill -9 $PID 2>/dev/null
    fi
done

# Compilation
echo "--- Compilation du projet ---"
rm -rf $BIN_DIR
mkdir -p $BIN_DIR
javac -cp ".:$LIB_GSON" -d $BIN_DIR \
    main/json/*.java \
    main/master/*.java \
    main/slave1/*.java \
    main/client/*.java

if [ $? -ne 0 ]; then
    echo "❌ Erreur de compilation !"
    exit 1
fi

# Lancer les Slaves
echo "--- Lancement des Slaves ---"
for i in ${!SLAVE_PORTS[@]}; do
    PORT=${SLAVE_PORTS[$i]}
    DIR="slave$((i+1))/storage"
    mkdir -p $DIR
    java -cp "$BIN_DIR" main.slave1.SlaveServer $PORT $DIR &
done

sleep 2

# Lancer le Master
echo "--- Lancement du Master ---"
java -cp "$BIN_DIR:$LIB_GSON" main.master.MasterServer &

sleep 2
echo "✅ Master et Slaves démarrés."
